package nurgling.tools;

import nurgling.NConfig;
import nurgling.NUI;
import nurgling.sessions.ThreadLocalUI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudyDeskConfigTest {
    private NConfig previous;

    @BeforeEach
    void useIsolatedConfig() throws Exception {
        previous = NConfig.current;
        NConfig.current = bareConfig();
        NConfig.set(NConfig.Key.studyDeskLayout, null);
    }

    @AfterEach
    void restoreConfig() {
        ThreadLocalUI.clear();
        NConfig.current = previous;
    }

    @Test
    void migratesEveryLegacyCharacterDeskWithoutClaimingOwnership() {
        Map<String, Object> legacy = new HashMap<>();
        legacy.put("Alice", legacyDesk("alice-hash", "Cone Cow"));
        legacy.put("Bob", legacyDesk("bob-hash", "Feather Trinket"));
        NConfig.set(NConfig.Key.studyDeskLayout, legacy);

        Map<String, Object> desks = StudyDeskConfig.allDesks();

        assertEquals(2, desks.size());
        assertEquals("Desk (Alice)", desk(desks, "alice-hash").get("label"));
        assertEquals("Cone Cow", itemName(desk(desks, "alice-hash")));
        assertEquals("Feather Trinket", itemName(desk(desks, "bob-hash")));
        assertNull(StudyDeskConfig.findOwnedDeskHash("Alice"));
    }

    @Test
    void claimingAnotherDeskMovesOwnershipButKeepsBothPlans() {
        StudyDeskConfig.putDesk("first", "First", Collections.emptyMap(), "char-id");
        StudyDeskConfig.putDesk("second", "Second", Collections.emptyMap(), "char-id");

        assertEquals("second", StudyDeskConfig.findOwnedDeskHash("char-id"));
        assertNull(desk(StudyDeskConfig.allDesks(), "first").get("owner"));
        assertEquals(2, StudyDeskConfig.allDesks().size());
    }

    @Test
    void returnedDeskSnapshotCannotMutateStoredConfig() {
        StudyDeskConfig.putDesk("desk", "Original", Collections.emptyMap(), "owner");

        desk(StudyDeskConfig.allDesks(), "desk").put("label", "Changed outside config");

        assertEquals("Original", StudyDeskConfig.getDesk("desk").get("label"));
    }

    @Test
    void sharedDeskReadsIgnoreStaleSessionSnapshot() throws Exception {
        StudyDeskConfig.putDesk("global-desk", "Global", Collections.emptyMap(), "global-owner");
        NConfig staleSession = bareConfig();
        Map<String, Object> staleDesks = new HashMap<>();
        staleDesks.put("stale-desk", savedDesk("Stale", "stale-owner"));
        Map<String, Object> staleWrapper = new HashMap<>();
        staleWrapper.put("desks", staleDesks);
        setRaw(staleSession, NConfig.Key.studyDeskLayout, staleWrapper);
        NUI sessionUi = (NUI) unsafe().allocateInstance(NUI.class);
        sessionUi.sessionConfig = staleSession;
        ThreadLocalUI.set(sessionUi);

        Map<String, Object> desks = StudyDeskConfig.allDesks();

        assertTrue(desks.containsKey("global-desk"));
        assertNull(desks.get("stale-desk"));
    }

    @Test
    void firstSaveCreatesStableDefaultLabel() {
        StudyDeskConfig.putDesk("abcdef123456", null, Collections.emptyMap(), "owner");

        assertEquals("Study Desk (abcdef12)", StudyDeskConfig.getDesk("abcdef123456").get("label"));
    }

    @Test
    void ownerIdFallsBackToGameUiCharacterDuringEarlyUiSetup() {
        assertEquals("game-ui-id", StudyDeskConfig.resolveOwnerId(null, "game-ui-id"));
        assertEquals("character-info-id",
                StudyDeskConfig.resolveOwnerId("character-info-id", "game-ui-id"));
    }

    @Test
    void duplicateLegacyDeskMigrationIsDeterministicAndKeepsBothLayouts() {
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("Bob", legacyDesk("shared-hash", "Bob's Curio"));
        legacy.put("Alice", legacyDesk("shared-hash", "Alice's Curio"));
        NConfig.set(NConfig.Key.studyDeskLayout, legacy);

        Map<String, Object> migrated = desk(StudyDeskConfig.allDesks(), "shared-hash");

        assertEquals("Desk (Alice)", migrated.get("label"));
        assertEquals("Alice's Curio", itemName(migrated));
        @SuppressWarnings("unchecked")
        Map<String, Object> backups = (Map<String, Object>) migrated.get("legacyLayouts");
        assertEquals(2, backups.size());
        assertTrue(backups.containsKey("Alice"));
        assertTrue(backups.containsKey("Bob"));
    }

    @Test
    void partialLegacyMigrationKeepsCompleteBackupAcrossLaterWrites() {
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("Alice", legacyDesk("alice-hash", "Cone Cow"));
        Map<String, Object> incomplete = new HashMap<>();
        incomplete.put("gobHash", "broken-hash");
        legacy.put("Bob", incomplete);
        NConfig.set(NConfig.Key.studyDeskLayout, legacy);

        StudyDeskConfig.allDesks();
        StudyDeskConfig.renameDesk("alice-hash", "Renamed");

        @SuppressWarnings("unchecked")
        Map<String, Object> wrapper = (Map<String, Object>)
                NConfig.getGlobal(NConfig.Key.studyDeskLayout);
        @SuppressWarnings("unchecked")
        Map<String, Object> backup = (Map<String, Object>) wrapper.get("legacyBackup");
        assertEquals(2, backup.size());
        assertEquals("Cone Cow", itemName(desk(backup, "Alice")));
        assertEquals("broken-hash", desk(backup, "Bob").get("gobHash"));
        assertNull(desk(backup, "Bob").get("layout"));
    }

    @Test
    void simultaneousSessionSavesDoNotLoseDesks() throws Exception {
        int workers = 32;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);
        try {
            for (int i = 0; i < workers; i++) {
                final int index = i;
                pool.execute(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        StudyDeskConfig.putDesk("desk-" + index, "Desk " + index,
                                Collections.emptyMap(), "owner-" + index);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertEquals(workers, StudyDeskConfig.allDesks().size());
    }

    private static Map<String, Object> legacyDesk(String hash, String itemName) {
        Map<String, Object> item = new HashMap<>();
        item.put("name", itemName);
        Map<String, Object> layout = new HashMap<>();
        layout.put("0,0", item);
        Map<String, Object> desk = new HashMap<>();
        desk.put("gobHash", hash);
        desk.put("layout", layout);
        return desk;
    }

    private static Map<String, Object> savedDesk(String label, String owner) {
        Map<String, Object> desk = new HashMap<>();
        desk.put("label", label);
        desk.put("owner", owner);
        desk.put("layout", new HashMap<String, Object>());
        return desk;
    }

    private static NConfig bareConfig() throws Exception {
        NConfig config = (NConfig) unsafe().allocateInstance(NConfig.class);
        Field confField = NConfig.class.getDeclaredField("conf");
        confField.setAccessible(true);
        confField.set(config, new HashMap<NConfig.Key, Object>());
        return config;
    }

    private static Unsafe unsafe() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (Unsafe) unsafeField.get(null);
    }

    @SuppressWarnings("unchecked")
    private static void setRaw(NConfig config, NConfig.Key key, Object value) throws Exception {
        Field confField = NConfig.class.getDeclaredField("conf");
        confField.setAccessible(true);
        ((Map<NConfig.Key, Object>) confField.get(config)).put(key, value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> desk(Map<String, Object> desks, String hash) {
        return (Map<String, Object>) desks.get(hash);
    }

    @SuppressWarnings("unchecked")
    private static String itemName(Map<String, Object> desk) {
        Map<String, Object> layout = (Map<String, Object>) desk.get("layout");
        return (String) ((Map<String, Object>) layout.get("0,0")).get("name");
    }
}
