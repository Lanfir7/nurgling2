# Indoor Home Territories Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep buildings, their floors, and cellars classified as home when their entrance is on a saved home claim or village, including after relogging inside, while never inheriting this status into mines or caves.

**Architecture:** Chunk Nav remains the portal-discovery source. A separate, versioned, per-genus `homeInteriors` registry stores stable indoor bindings and their claim/village origins. One resolver combines direct territory overlays with active indoor bindings. Confirmed building/stair/cellar traversals learn and propagate bindings; mine, cave, gate, unknown, and teleport transitions are rejected.

**Tech Stack:** Java 8, Haven/Nurgling client UI, `NConfig`, Chunk Nav graph and portal tracker, JUnit Jupiter 1.14.2, Ant.

**Spec:** `docs/superpowers/specs/2026-09-09-indoor-home-territories-design.md`

## Global Constraints

- Preserve the existing uncommitted user work; stage only files named by the current task and inspect every staged diff before committing.
- Run every shell command through `rtk`.
- Use `NConfig.update(...)` for all registry mutations; never perform read-modify-write with separate `get` and `set` calls.
- Partition all data by world genus so all characters in one world share it and different worlds cannot collide.
- Persist only server grid IDs and portal-local tile coordinates. Do not persist session coordinates, gob IDs, map-file segment IDs, or transient UI state.
- Do not modify `ChunkNavBinaryFormat`; indoor-home state is separate from Chunk Nav binary files.
- Do not infer home through `MINEHOLE`, `LADDER`, `MINE_ENTRANCE`, `CAVEIN`, or `CAVEOUT`, or through any `mine*`/cave layer.
- Direct claims inside mines and caves continue to work through the existing territory detector.
- Portal tracking for home learning must work with `chunkNavOverlay=false` whenever at least one surface home exists.
- Unknown or partially loaded state is never promoted to home.
- Automatic bindings remain active only while at least one saved origin still matches. Manual bindings remain active until unmarked or removed.
- Removing an automatic indoor row records a portal tombstone so startup backfill cannot immediately recreate it; the next confirmed traversal may clear the tombstone and relearn it.
- Follow red-green-refactor for every behavior change. No production code is added before the corresponding failing test.

## File Structure

### New production files

- `src/nurgling/tools/HomeInteriorRegistry.java` — immutable binding/origin model, versioned codec, activation, removal, and merge rules.
- `src/nurgling/tools/HomeInteriorStore.java` — atomic `NConfig` access scoped by genus.
- `src/nurgling/tools/HomeLocationResolver.java` — single direct-plus-indoor home resolver.
- `src/nurgling/navigation/HomePortalInheritance.java` — pure allow/reject and propagation policy for confirmed transitions.
- `src/nurgling/navigation/HomePortalLearningService.java` — runtime capture, persistence, and claim backfill around Chunk Nav.
- `src/nurgling/widgets/ChunkHomePresentation.java` — pure visualizer state and manual-mark eligibility.

### Modified production files

- `src/nurgling/NConfig.java` — add `homeInteriors` and its empty map default.
- `src/nurgling/tools/HomeTerritories.java` — expose normalized origin matching without duplicating claim/village rules.
- `src/nurgling/tools/CurrentHomeTerritories.java` — resolve direct status plus current stable grid/instance.
- `src/nurgling/tools/HomeTerritoryDebug.java` — include indoor status, source, grid, instance, and loading state.
- `src/nurgling/navigation/PortalTraversalTracker.java` — capture source home context and publish confirmed transitions.
- `src/nurgling/navigation/ChunkNavManager.java` — own the learning service, run portal tracking independently of overlay visibility, and backfill after graph load.
- `src/nurgling/widgets/nsettings/HomeSetup.java` — show removable indoor bindings alongside surface homes.
- `src/nurgling/widgets/ChunkNavVisualizerWindow.java` — home tint, selected status, and manual mark/unmark action.
- `src/lang/messages.properties` and `src/lang/messages_ru.properties` — Home Setup and debug labels.

### New tests

- `test/nurgling/tools/HomeInteriorRegistryTest.java`
- `test/nurgling/NConfigHomeInteriorsTest.java`
- `test/nurgling/tools/HomeLocationResolverTest.java`
- `test/nurgling/navigation/HomePortalInheritanceTest.java`
- `test/nurgling/navigation/HomePortalLearningServiceTest.java`
- `test/nurgling/tools/HomeTerritoryDebugTest.java` — extend the existing file.
- `test/nurgling/widgets/ChunkHomePresentationTest.java`
- `test/nurgling/navigation/HomeInteriorFlowTest.java`

### Test command used below

After `rtk ant test-compile`, run selected tests with:

```powershell
rtk java -cp "build/classes;build/test-classes;lib/ext/junit/junit-platform-console-standalone-1.14.2.jar;lib/ext/jogl/jogl-all.jar;lib/ext/jogl/gluegen-rt.jar;lib/ext/lwjgl/lwjgl-fat.jar;lib/ext/lwjgl/lwjgl-awt.jar;lib/ext/lwjgl/lwjgl-opengl-fat.jar;lib/ext/steamworks/steamworks4j.jar;etc/json-java.jar;etc/postgresql-42.7.5.jar;etc/sqlite-jdbc-3.49.1.0.jar;lib/jglob.jar;bin/builtin-res.jar;bin/hafen-res.jar;bin/nurgling-res.jar" org.junit.platform.console.ConsoleLauncher execute --disable-banner --select-class=CLASS_NAME --details=summary
```

Replace only `CLASS_NAME` with the exact test class named in each task.

---

## Task 1: Stable origins and the versioned indoor registry

**Files:**

- Create: `src/nurgling/tools/HomeInteriorRegistry.java`
- Modify: `src/nurgling/tools/HomeTerritories.java`
- Create: `test/nurgling/tools/HomeInteriorRegistryTest.java`

- [ ] **Step 1: Write failing identity and codec tests**

Cover these exact cases:

```java
@Test
void originKeysUseVillageNameClaimAnchorAndLegacyOwner() {
    HomeTerritories.Entry village = new HomeTerritories.Entry(HomeTerritories.Type.VILLAGE, " Oak Vale ");
    ClaimArea area = new ClaimArea(new ClaimArea.Tile(42L, 7, 9),
            Collections.singleton(new ClaimArea.Tile(42L, 7, 9)));
    HomeTerritories.Entry claim = new HomeTerritories.Entry(HomeTerritories.Type.CLAIM, "Lanfir", area);
    HomeTerritories.Entry legacy = new HomeTerritories.Entry(HomeTerritories.Type.CLAIM, "Lanfir");

    assertEquals("village:oak vale", HomeInteriorRegistry.OriginKey.from(village).value());
    assertEquals("claim-anchor:42:7:9", HomeInteriorRegistry.OriginKey.from(claim).value());
    assertEquals("claim-owner:lanfir", HomeInteriorRegistry.OriginKey.from(legacy).value());
}

@Test
void registryRoundTripPreservesBindingAndWorldIsolation() {
    HomeInteriorRegistry.Binding binding = HomeInteriorRegistry.Binding.automatic(
            "auto:42:7:9:gfx/terobjs/arch/stonemansion", 9001L,
            setOf(1001L, 1002L),
            setOf(HomeInteriorRegistry.OriginKey.parse("claim-anchor:42:7:9")),
            new HomeInteriorRegistry.PortalIdentity(42L, 7, 9,
                    "gfx/terobjs/arch/stonemansion"),
            "Lanfir's Claim -> Stone Mansion", 1234L);
    HomeInteriorRegistry first = HomeInteriorRegistry.empty().put(binding);
    Object stored = HomeInteriorRegistry.encodeForWorld(null, "world-one", first);
    stored = HomeInteriorRegistry.encodeForWorld(stored, "world-two", HomeInteriorRegistry.empty());

    assertEquals(first, HomeInteriorRegistry.decodeForWorld(stored, "world-one"));
    assertTrue(HomeInteriorRegistry.decodeForWorld(stored, "world-two").bindings().isEmpty());
}
```

Also test malformed fields are ignored, a version newer than supported yields an empty registry, exact grid matching precedes instance matching, dual origins stay active if either survives, and legacy owner matching is case-insensitive.

- [ ] **Step 2: Verify the test is red**

Run:

```powershell
rtk ant test-compile
```

Expected: compilation fails because `HomeInteriorRegistry` does not exist.

- [ ] **Step 3: Implement the immutable registry model and codec**

Use these public boundaries:

```java
public final class HomeInteriorRegistry {
    public static final int VERSION = 1;

    public static final class OriginKey {
        public static OriginKey from(HomeTerritories.Entry entry);
        public static OriginKey parse(String value);
        public String value();
        public boolean matches(Collection<HomeTerritories.Entry> saved);
    }

    public static final class PortalIdentity {
        public final long gridId;
        public final int x;
        public final int y;
        public final String resource;
        public String stableKey();
    }

    public static final class Binding {
        public final String id;
        public final long instanceId;
        public final Set<Long> gridIds;
        public final Set<OriginKey> origins;
        public final PortalIdentity rootPortal;
        public final boolean manual;
        public final String displayName;
        public final long lastSeen;

        public static Binding automatic(String id, long instanceId, Collection<Long> gridIds,
                Collection<OriginKey> origins, PortalIdentity rootPortal,
                String displayName, long lastSeen);
        public Binding merge(Binding incoming);
        public boolean active(Collection<HomeTerritories.Entry> saved);
    }

    public static HomeInteriorRegistry empty();
    public Collection<Binding> bindings();
    public HomeInteriorRegistry put(Binding binding);
    public HomeInteriorRegistry remove(String bindingId, boolean suppressAutomatic);
    public HomeInteriorRegistry markManual(long instanceId, Collection<Long> gridIds, String displayName);
    public HomeInteriorRegistry unmarkManual(long instanceId);
    public Binding findActive(long gridId, long instanceId, Collection<HomeTerritories.Entry> saved);
    public boolean isSuppressed(PortalIdentity portal);
    public HomeInteriorRegistry clearSuppression(PortalIdentity portal);
    public static Map<String, Object> encodeForWorld(Object stored, String genus,
            HomeInteriorRegistry registry);
    public static HomeInteriorRegistry decodeForWorld(Object stored, String genus);
}
```

Normalize names with `trim().toLowerCase(Locale.ROOT)`. Serialize a world value as a map containing `version`, `bindings`, and `suppressedPortals`. Sort binding IDs, grid IDs, and origin strings before encoding so repeated saves are deterministic.

Add one shared matcher to `HomeTerritories`:

```java
public static List<Entry> matchingHomes(Collection<Entry> saved,
        Collection<Entry> current, ClaimArea currentClaimArea)
```

Make the existing `status(...)` derive its booleans from this method so origin matching cannot diverge between direct detection and indoor learning.

- [ ] **Step 4: Run focused tests**

Run:

```powershell
rtk ant test-compile
rtk java -cp "build/classes;build/test-classes;lib/ext/junit/junit-platform-console-standalone-1.14.2.jar;lib/ext/jogl/jogl-all.jar;lib/ext/jogl/gluegen-rt.jar;lib/ext/lwjgl/lwjgl-fat.jar;lib/ext/lwjgl/lwjgl-awt.jar;lib/ext/lwjgl/lwjgl-opengl-fat.jar;lib/ext/steamworks/steamworks4j.jar;etc/json-java.jar;etc/postgresql-42.7.5.jar;etc/sqlite-jdbc-3.49.1.0.jar;lib/jglob.jar;bin/builtin-res.jar;bin/hafen-res.jar;bin/nurgling-res.jar" org.junit.platform.console.ConsoleLauncher execute --disable-banner --select-class=nurgling.tools.HomeInteriorRegistryTest --select-class=nurgling.tools.HomeTerritoriesTest --details=summary
```

Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```powershell
rtk git add src/nurgling/tools/HomeInteriorRegistry.java src/nurgling/tools/HomeTerritories.java test/nurgling/tools/HomeInteriorRegistryTest.java test/nurgling/tools/HomeTerritoriesTest.java
rtk git diff --cached --check
rtk git commit -m "feat: model indoor home bindings"
```

---

## Task 2: Atomic per-world configuration storage

**Files:**

- Modify: `src/nurgling/NConfig.java`
- Create: `src/nurgling/tools/HomeInteriorStore.java`
- Create: `test/nurgling/NConfigHomeInteriorsTest.java`

- [ ] **Step 1: Write failing default, isolation, and concurrency tests**

Use two threads and latches following `NConfigHomeTerritoriesTest`. One thread learns a binding for `world-one`; the other marks an instance manually for `world-two`. Assert both survive in `NConfig.getGlobal(NConfig.Key.homeInteriors)`.

```java
@Test
void defaultRegistryIsEmptyAndPartitionedByGenus() {
    NConfig previous = NConfig.current;
    try {
        NConfig.current = new NConfig();
        assertTrue(HomeInteriorStore.load("world-one").bindings().isEmpty());
        assertTrue(HomeInteriorStore.load("world-two").bindings().isEmpty());
    } finally {
        NConfig.current = previous;
    }
}
```

The final test must restore the previous global config in `finally`, matching the existing NConfig concurrency test pattern.

- [ ] **Step 2: Verify the test is red**

Run `rtk ant test-compile`.

Expected: compilation fails on the missing key/store.

- [ ] **Step 3: Add the key, default, and atomic store**

Add `homeInteriors` immediately after `homeTerritories` in `NConfig.Key` and initialize it with `new LinkedHashMap<String, Object>()`.

Implement:

```java
public final class HomeInteriorStore {
    public static HomeInteriorRegistry load(String genus) {
        return HomeInteriorRegistry.decodeForWorld(
                NConfig.getGlobal(NConfig.Key.homeInteriors), genus);
    }

    public static HomeInteriorRegistry update(String genus,
            UnaryOperator<HomeInteriorRegistry> updater) {
        Object stored = NConfig.update(NConfig.Key.homeInteriors, raw -> {
            HomeInteriorRegistry current = HomeInteriorRegistry.decodeForWorld(raw, genus);
            HomeInteriorRegistry changed = Objects.requireNonNull(updater.apply(current));
            return HomeInteriorRegistry.encodeForWorld(raw, genus, changed);
        });
        return HomeInteriorRegistry.decodeForWorld(stored, genus);
    }
}
```

Do not call `NConfig.needUpdate()` here: `NConfig.update(...)` already marks the write state dirty and publishes the result to live sessions.

- [ ] **Step 4: Run focused tests**

Run `rtk ant test-compile`, then select `nurgling.NConfigHomeInteriorsTest` and `nurgling.NConfigHomeTerritoriesTest` with the shared test command.

Expected: both classes pass.

- [ ] **Step 5: Commit**

```powershell
rtk git add src/nurgling/NConfig.java src/nurgling/tools/HomeInteriorStore.java test/nurgling/NConfigHomeInteriorsTest.java test/nurgling/NConfigHomeTerritoriesTest.java
rtk git diff --cached --check
rtk git commit -m "feat: persist indoor homes per world"
```

---

## Task 3: One direct-plus-indoor resolver

**Files:**

- Create: `src/nurgling/tools/HomeLocationResolver.java`
- Create: `test/nurgling/tools/HomeLocationResolverTest.java`

- [ ] **Step 1: Write failing resolver tests**

Cover direct village, direct claim, exact indoor grid, instance fallback, inactive deleted origin, manual binding, unknown IDs, and relog while already inside.

```java
@Test
void exactStoredGridRestoresHomeAfterRestartInside() {
    HomeInteriorRegistry registry = registryWithAutomaticBinding(
            700L, setOf(701L, 702L), "claim-anchor:42:7:9");
    HomeLocationResolver.Status status = HomeLocationResolver.resolve(
            savedClaim(), Collections.emptyList(), null, false,
            registry, 702L, 700L, true);

    assertFalse(status.villageHome);
    assertFalse(status.claimHome);
    assertTrue(status.indoorHome);
    assertTrue(status.home);
    assertEquals(HomeLocationResolver.Source.INDOOR_AUTO, status.source);
}

@Test
void unknownChunkNavIdentityNeverBecomesHome() {
    HomeLocationResolver.Status status = HomeLocationResolver.resolve(
            savedClaim(), Collections.emptyList(), null, false,
            registryWithAutomaticBinding(700L, setOf(701L), "claim-anchor:42:7:9"),
            -1L, 0L, false);

    assertFalse(status.indoorHome);
    assertFalse(status.home);
    assertTrue(status.navigationLoading);
}
```

- [ ] **Step 2: Verify the test is red**

Run `rtk ant test-compile`.

Expected: compilation fails because `HomeLocationResolver` is missing.

- [ ] **Step 3: Implement the pure resolver**

Use this boundary:

```java
public final class HomeLocationResolver {
    public enum Source { NONE, DIRECT_VILLAGE, DIRECT_CLAIM, DIRECT_BOTH, INDOOR_AUTO, INDOOR_MANUAL }

    public static final class Status {
        public final boolean villageHome;
        public final boolean claimHome;
        public final boolean indoorHome;
        public final boolean home;
        public final boolean territoryLoading;
        public final boolean navigationLoading;
        public final Source source;
        public final String sourceLabel;
        public final long gridId;
        public final long instanceId;
        public final String bindingId;
    }

    public static Status resolve(Collection<HomeTerritories.Entry> saved,
            Collection<HomeTerritories.Entry> current, ClaimArea currentClaimArea,
            boolean territoryLoading, HomeInteriorRegistry registry,
            long gridId, long instanceId, boolean navigationReady);
}
```

Resolution order is direct territory, exact grid binding, then instance binding. A direct result wins the `source` label while indoor matching is still reported independently. `navigationReady=false`, `gridId=-1`, or `instanceId=0` cannot create an indoor match.

- [ ] **Step 4: Run focused tests**

Run `rtk ant test-compile`, then select `nurgling.tools.HomeLocationResolverTest` and `nurgling.tools.HomeInteriorRegistryTest`.

Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```powershell
rtk git add src/nurgling/tools/HomeLocationResolver.java test/nurgling/tools/HomeLocationResolverTest.java
rtk git diff --cached --check
rtk git commit -m "feat: resolve direct and indoor home status"
```

---

## Task 4: Pure portal inheritance rules

**Files:**

- Create: `src/nurgling/navigation/HomePortalInheritance.java`
- Create: `test/nurgling/navigation/HomePortalInheritanceTest.java`

- [ ] **Step 1: Write the rejection tests first**

Create immutable `SourceContext` and `Traversal` fixtures. Assert rejection for every mine/cave portal type, `GATE`, null/unknown portal type, teleport, unconfirmed transition, destination `outside`, destination `mine1`, and a market entrance with no direct or inherited source home.

```java
@ParameterizedTest
@EnumSource(value = ChunkPortal.PortalType.class,
        names = {"MINE_ENTRANCE", "MINEHOLE", "LADDER", "CAVEIN", "CAVEOUT"})
void neverInheritsIntoMineOrCave(ChunkPortal.PortalType type) {
    assertFalse(HomePortalInheritance.canInherit(type, "outside", "mine1", true, false));
}

@Test
void marketBuildingDoesNotBecomeHomeWithoutAHomeSource() {
    HomePortalInheritance.Change change = HomePortalInheritance.apply(
            HomeInteriorRegistry.empty(), SourceContext.notHome(), buildingTraversal());
    assertFalse(change.changed);
    assertTrue(change.registry.bindings().isEmpty());
}
```

- [ ] **Step 2: Verify red**

Run `rtk ant test-compile`.

- [ ] **Step 3: Add allowed propagation tests**

Test surface-home `DOOR -> inside`, indoor-home `STAIRS_UP -> inside`, `STAIRS_DOWN -> inside`, and `CELLAR -> cellar`. Test a dual claim/village source records both origins. Test returning from `inside` to `outside` leaves the surface registry unchanged.

- [ ] **Step 4: Implement the policy**

Use these exact value objects:

```java
public final class HomePortalInheritance {
    public static final class SourceContext {
        public final Set<HomeInteriorRegistry.OriginKey> directOrigins;
        public final HomeInteriorRegistry.Binding inheritedBinding;
    }

    public static final class Traversal {
        public final long fromGridId;
        public final long toGridId;
        public final long fromInstanceId;
        public final long toInstanceId;
        public final String fromLayer;
        public final String toLayer;
        public final ChunkPortal.PortalType portalType;
        public final HomeInteriorRegistry.PortalIdentity rootPortal;
        public final String destinationPortalResource;
        public final boolean confirmed;
        public final boolean teleport;
        public final long occurredAt;
    }

    public static boolean canInherit(ChunkPortal.PortalType type, String fromLayer,
            String toLayer, boolean confirmed, boolean teleport);
    public static Change apply(HomeInteriorRegistry registry,
            SourceContext source, Traversal traversal);
}
```

`canInherit` returns true only for `DOOR`, `STAIRS_UP`, `STAIRS_DOWN`, and `CELLAR`, with a confirmed non-teleport destination layer of `inside` or `cellar`. Automatic root IDs derive from `PortalIdentity.stableKey()`. Nested traversal merges destination grid IDs into the existing binding instead of creating a second binding.

- [ ] **Step 5: Run focused tests**

Run `rtk ant test-compile`, then select `nurgling.navigation.HomePortalInheritanceTest`.

Expected: all policy cases pass.

- [ ] **Step 6: Commit**

```powershell
rtk git add src/nurgling/navigation/HomePortalInheritance.java test/nurgling/navigation/HomePortalInheritanceTest.java
rtk git diff --cached --check
rtk git commit -m "feat: define indoor home inheritance policy"
```

---

## Task 5: Runtime portal learning and independent tracking

**Files:**

- Create: `src/nurgling/navigation/HomePortalLearningService.java`
- Modify: `src/nurgling/navigation/PortalTraversalTracker.java`
- Modify: `src/nurgling/navigation/ChunkNavManager.java`
- Create: `test/nurgling/navigation/HomePortalLearningServiceTest.java`
- Modify: `test/nurgling/navigation/ThatchedHutPortalTest.java`

- [ ] **Step 1: Write failing service tests with injected boundaries**

Avoid reflection for new behavior. Inject a store adapter and direct-context supplier:

```java
interface RegistryAccess {
    HomeInteriorRegistry load(String genus);
    HomeInteriorRegistry update(String genus,
            UnaryOperator<HomeInteriorRegistry> updater);
}

interface SourceContextSupplier {
    HomePortalInheritance.SourceContext capture(long sourceGridId,
            int portalX, int portalY);
}
```

Test that source origins are captured before the transition, a confirmed thatched-hut traversal writes immediately once, overlay-disabled tracking remains enabled when any surface or manual indoor home exists, and hearth teleport writes nothing.

```java
@Test
void confirmedBuildingTraversalPersistsImmediately() {
    FakeRegistryAccess store = new FakeRegistryAccess();
    HomePortalLearningService service = service(store, savedClaimContext());
    HomePortalLearningService.Pending pending = service.capture(
            42L, 7, 9, "gfx/terobjs/arch/thatchedhut", 1L, "outside");

    service.confirm(pending, confirmedInsideTraversal(901L, 8001L));

    assertEquals(1, store.updateCount);
    assertNotNull(store.registry.findActive(901L, 8001L, savedClaim()));
}
```

- [ ] **Step 2: Verify red**

Run `rtk ant test-compile`.

- [ ] **Step 3: Implement the learning service**

The runtime constructor reads the genus from `ChunkNavManager.getCurrentGenus()`, saved homes from `NConfig.Key.homeTerritories`, and direct territory data from `CurrentHomeTerritories.detect(gui)`. Convert only `HomeTerritories.matchingHomes(...)` results into origins.

Expose:

```java
public boolean shouldTrack(boolean chunkOverlayEnabled);
public Pending capture(long sourceGridId, Coord portalCoord, String portalResource,
        long sourceInstanceId, String sourceLayer);
public void confirm(Pending pending, HomePortalInheritance.Traversal traversal);
```

`confirm` calls `HomeInteriorStore.update(...)` synchronously after policy acceptance. It clears the matching tombstone only for a new confirmed traversal, never during backfill.

- [ ] **Step 4: Wire the portal tracker**

In `PortalTraversalTracker`:

- add a four-argument constructor with a `HomePortalLearningService` dependency, and retain the existing three-argument constructor by delegating to `HomePortalLearningService.disabled()` for focused legacy tests;
- store a `Pending` alongside the cached last-action portal;
- capture it after the actual portal grid/local coordinate has been resolved but before grid change;
- after `updateChunkLayer(...)` and `updateInstanceIdAfterTraversal(...)`, build one confirmed `Traversal` using the source and destination chunks;
- invoke `confirm(...)` only after the expected portal pair is found;
- clear pending state with the existing cached portal state;
- keep hearth teleport, missing pair, and missing exit portal as early exits with no home mutation.
- make `primitivetent` trackable and classify paired `primitivetent-door` and `thatchedhut-door` destinations as `inside`, matching the building resources already recognized by `ChunkPortal` and `GateDetector`.

Replace the overlay-only guard with:

```java
boolean chunkOverlayEnabled = Boolean.TRUE.equals(NConfig.get(NConfig.Key.chunkNavOverlay));
boolean trackingEnabled = homeLearning.shouldTrack(chunkOverlayEnabled);
```

`shouldTrack` returns true when the overlay is enabled, a surface home is configured, or the registry contains a manual/active indoor binding that may propagate through stairs or a cellar. Reset stale tracking state on `false -> true` for this combined condition. Do not couple home learning to the visual overlay checkbox.

- [ ] **Step 5: Wire manager lifecycle**

Construct a new learning service in both the `ChunkNavManager()` constructor and `initialize(genus)`. Add:

```java
public String getCurrentGenus() {
    return currentGenus;
}
```

The existing `tick()` order remains portal tracking, throttled save, visible-grid recording, visualization refresh.

- [ ] **Step 6: Run focused tests**

Run `rtk ant test-compile`, then select `nurgling.navigation.HomePortalLearningServiceTest`, `nurgling.navigation.HomePortalInheritanceTest`, and `nurgling.navigation.ThatchedHutPortalTest`.

Expected: all selected tests pass with `chunkNavOverlay=false` covered.

- [ ] **Step 7: Commit**

```powershell
rtk git add src/nurgling/navigation/HomePortalLearningService.java src/nurgling/navigation/PortalTraversalTracker.java src/nurgling/navigation/ChunkNavManager.java test/nurgling/navigation/HomePortalLearningServiceTest.java test/nurgling/navigation/ThatchedHutPortalTest.java
rtk git diff --cached --check
rtk git commit -m "feat: learn indoor homes from portal travel"
```

---

## Task 6: Claim-based backfill from existing Chunk Nav data

**Files:**

- Modify: `src/nurgling/navigation/HomePortalLearningService.java`
- Modify: `src/nurgling/navigation/ChunkNavManager.java`
- Modify: `test/nurgling/navigation/HomePortalLearningServiceTest.java`

- [ ] **Step 1: Write failing backfill tests**

Build an in-memory graph with an `outside` source chunk, a connected building `DOOR`, and an `inside` destination chunk. Assert a saved claim containing the portal tile creates an automatic binding for the destination instance. Then assert:

- village-only homes are not backfilled;
- a portal outside the claim is ignored;
- mine/cave connections are ignored;
- a suppressed root portal is ignored;
- all chunks sharing the destination instance ID are attached;
- running backfill twice is idempotent.

- [ ] **Step 2: Verify the new test is red**

Run the focused learning-service test.

- [ ] **Step 3: Implement conservative backfill**

Add:

```java
public HomeInteriorRegistry backfillClaims(ChunkNavGraph graph,
        Collection<HomeTerritories.Entry> savedHomes,
        HomeInteriorRegistry registry);
```

Iterate source chunks only when `instanceId == ChunkNavManager.SURFACE_INSTANCE` and `layer` is `outside`. Accept only connected `DOOR` portals whose destination chunk is `inside` or `cellar`, has `instanceId > SURFACE_INSTANCE`, and whose source tile is contained by a saved claim area. Gather every graph chunk with the destination instance ID. Respect suppression and do not clear it.

In `ChunkNavManager.initialize`, call backfill once after `load()` and before setting `initialized=true`; persist only when the returned registry differs from the loaded registry.

- [ ] **Step 4: Run focused tests**

Run `rtk ant test-compile`, then select `nurgling.navigation.HomePortalLearningServiceTest`.

Expected: all traversal and backfill cases pass.

- [ ] **Step 5: Commit**

```powershell
rtk git add src/nurgling/navigation/HomePortalLearningService.java src/nurgling/navigation/ChunkNavManager.java test/nurgling/navigation/HomePortalLearningServiceTest.java
rtk git diff --cached --check
rtk git commit -m "feat: backfill claimed home interiors"
```

---

## Task 7: Runtime resolver and Home Setup diagnostics

**Files:**

- Modify: `src/nurgling/tools/CurrentHomeTerritories.java`
- Modify: `src/nurgling/tools/HomeTerritoryDebug.java`
- Modify: `src/nurgling/widgets/nsettings/HomeSetup.java`
- Modify: `src/lang/messages.properties`
- Modify: `src/lang/messages_ru.properties`
- Modify: `test/nurgling/tools/HomeTerritoryDebugTest.java`

- [ ] **Step 1: Extend the debug test first**

```java
@Test
void describesInheritedHomeWithStableNavigationIdentity() {
    HomeLocationResolver.Status status = indoorAutoStatus(
            "Lanfir's Claim -> Stone Mansion", 701L, 700L);
    HomeTerritoryDebug.Snapshot snapshot = HomeTerritoryDebug.inspect(
            Collections.emptyList(), null, status);

    assertTrue(snapshot.indoorHome);
    assertTrue(snapshot.home);
    assertEquals("Lanfir's Claim -> Stone Mansion", snapshot.homeSource);
    assertEquals(701L, snapshot.gridId);
    assertEquals(700L, snapshot.instanceId);
}
```

Also test direct village/claim lines remain unchanged and navigation-loading displays unknown rather than `Yes`.

- [ ] **Step 2: Verify red**

Run `rtk ant test-compile`.

- [ ] **Step 3: Wire `CurrentHomeTerritories` to the resolver**

Change `status(NGameUI)` to return `HomeLocationResolver.Status`. Obtain the current grid with `manager.getGraph().getPlayerChunkId()`, obtain persistent instance ID from `manager.getGraph().getChunk(gridId)`, and set navigation ready only when manager initialization and chunk lookup succeed.

Keep the existing helpers and add overall/indoor helpers:

```java
public static boolean isCurrentVillageHome(NGameUI gui);
public static boolean isCurrentClaimHome(NGameUI gui);
public static boolean isCurrentIndoorHome(NGameUI gui);
public static boolean isCurrentHome(NGameUI gui);
```

Do not use `ChunkNavManager.getCurrentInstanceId()` as the persisted source after relog; use `ChunkNavData.instanceId` for the current stable grid.

- [ ] **Step 4: Extend debug data and UI**

Make `HomeTerritoryDebug.Snapshot` include `indoorHome`, `homeSource`, `gridId`, `instanceId`, and `navigationLoading`. Update the `?` window to show:

```text
Indoor home: Yes/No/Unknown
Home source: Lanfir's Claim -> Stone Mansion
Stable grid: 701
Instance: 700
```

Add English and Russian localization keys for these labels and `Unknown`. Keep the existing village, claim, home-zone, and claim-tile lines.

- [ ] **Step 5: Run focused tests**

Run `rtk ant test-compile`, then select `nurgling.tools.HomeTerritoryDebugTest`, `nurgling.tools.HomeLocationResolverTest`, and `nurgling.tools.HomeTerritoriesTest`.

Expected: all selected tests pass.

- [ ] **Step 6: Commit**

```powershell
rtk git add src/nurgling/tools/CurrentHomeTerritories.java src/nurgling/tools/HomeTerritoryDebug.java src/nurgling/widgets/nsettings/HomeSetup.java src/lang/messages.properties src/lang/messages_ru.properties test/nurgling/tools/HomeTerritoryDebugTest.java
rtk git diff --cached --check
rtk git commit -m "feat: report inherited home status"
```

---

## Task 8: Indoor home rows in Home Setup

**Files:**

- Modify: `src/nurgling/widgets/nsettings/HomeSetup.java`
- Modify: `src/lang/messages.properties`
- Modify: `src/lang/messages_ru.properties`
- Modify: `test/nurgling/NConfigHomeInteriorsTest.java`

- [ ] **Step 1: Write the concurrent edit test**

Model the UI operation as a set of binding IDs removed from the snapshot loaded when the panel opened. While that edit is staged, atomically add another learned binding. Apply the removals and assert the concurrent binding survives.

```java
@Test
void stagedRemovalPreservesBindingLearnedWhileSettingsWereOpen() {
    HomeInteriorRegistry baseline = registryWithBindings("old-home");
    HomeInteriorRegistry concurrent = baseline.put(binding("new-home"));

    HomeInteriorRegistry result = concurrent.applyRemovals(
            Collections.singleton("old-home"));

    assertNull(result.find("old-home"));
    assertNotNull(result.find("new-home"));
    assertTrue(result.isSuppressed(portalOf("old-home")));
}
```

- [ ] **Step 2: Verify red, then add `applyRemovals`**

Run `rtk ant test-compile`, implement `HomeInteriorRegistry.applyRemovals(...)` as repeated immutable `remove(id, true)`, then rerun `nurgling.NConfigHomeInteriorsTest`.

- [ ] **Step 3: Add the Home Setup list**

Under the surface-home list, add `Indoor home zones` and an `SListBox<HomeInteriorRegistry.Binding, Widget>`. Each row shows:

```text
Stone Mansion — Auto — Lanfir's Claim
Cellar — Manual
```

At `load()`, snapshot the registry and clear `removedIndoorBindingIds`. The row `×` only adds the ID to that set and refreshes the visible list. At `save()`, call one `HomeInteriorStore.update(genus, current -> current.applyRemovals(ids))`. This preserves bindings learned concurrently and honors settings cancellation.

Automatic rows display the first currently active origin; dual origins use a comma-separated deterministic list. Inactive rows display `Inactive`. Manual rows display `Manual` even if their old automatic origin was deleted.

- [ ] **Step 4: Add localization**

Add English/Russian strings for the section title and `Auto`, `Manual`, `Inactive`. Do not hard-code new user-facing row-state labels.

- [ ] **Step 5: Run focused tests and compile**

Run `rtk ant test-compile`, then select `nurgling.NConfigHomeInteriorsTest`. Run `rtk ant compile` to catch UI type/layout errors.

Expected: tests and compilation pass.

- [ ] **Step 6: Commit**

```powershell
rtk git add src/nurgling/tools/HomeInteriorRegistry.java src/nurgling/widgets/nsettings/HomeSetup.java src/lang/messages.properties src/lang/messages_ru.properties test/nurgling/NConfigHomeInteriorsTest.java
rtk git diff --cached --check
rtk git commit -m "feat: manage indoor homes in settings"
```

---

## Task 9: Chunk Nav visualizer home state and manual override

**Files:**

- Create: `src/nurgling/widgets/ChunkHomePresentation.java`
- Modify: `src/nurgling/widgets/ChunkNavVisualizerWindow.java`
- Create: `test/nurgling/widgets/ChunkHomePresentationTest.java`

- [ ] **Step 1: Write failing presentation tests**

Cover `AUTO`, `MANUAL`, `NONE`, and `RESTRICTED`, active-origin text, all grids of one instance, and action availability.

```java
@ParameterizedTest
@ValueSource(strings = {"outside", "mine1", "mine2", "cave"})
void manualMarkIsDisabledOutsideAndInMinesOrCaves(String layer) {
    ChunkNavData chunk = chunk(701L, 700L, layer);
    ChunkHomePresentation presentation = ChunkHomePresentation.forChunk(
            chunk, HomeInteriorRegistry.empty(), Collections.emptyList());

    assertFalse(presentation.canMarkManual);
    assertEquals(ChunkHomePresentation.Kind.RESTRICTED, presentation.kind);
}
```

Also assert `inside` and `cellar` can be marked, and unmarking manual does not delete still-active automatic origins.

- [ ] **Step 2: Verify red**

Run `rtk ant test-compile`.

- [ ] **Step 3: Implement the pure presentation helper**

Use:

```java
public final class ChunkHomePresentation {
    public enum Kind { AUTO, MANUAL, NONE, RESTRICTED }
    public final Kind kind;
    public final boolean active;
    public final boolean canMarkManual;
    public final boolean canUnmarkManual;
    public final String sourceLabel;

    public static ChunkHomePresentation forChunk(ChunkNavData chunk,
            HomeInteriorRegistry registry,
            Collection<HomeTerritories.Entry> savedHomes);
}
```

Manual eligibility is true only for `inside` and `cellar` with `instanceId > SURFACE_INSTANCE`.

- [ ] **Step 4: Update the visualizer**

On reload, load the current genus's surface homes and indoor registry. Tint active-home chunks with a distinct green-gold overlay in both world and detail canvases. Do not replace portal/path/selection colors.

Extend selected details with:

```text
Instance: 700
Home: Auto
Source: Lanfir's Claim -> Stone Mansion
```

Add one dynamic button:

- `Mark instance as home` for eligible non-manual chunks;
- `Unmark manual home` for manual chunks;
- disabled for outside, mines, caves, unknown instance IDs, and no selection.

Marking gathers every currently loaded chunk sharing the selected `instanceId` and calls `HomeInteriorStore.update(...)`. Unmarking clears only the manual flag; an automatic binding remains active if a saved origin still matches. Reload data after each mutation.

- [ ] **Step 5: Run focused tests and compile**

Run `rtk ant test-compile`, select `nurgling.widgets.ChunkHomePresentationTest`, then run `rtk ant compile`.

Expected: tests and compilation pass.

- [ ] **Step 6: Commit**

```powershell
rtk git add src/nurgling/widgets/ChunkHomePresentation.java src/nurgling/widgets/ChunkNavVisualizerWindow.java test/nurgling/widgets/ChunkHomePresentationTest.java
rtk git diff --cached --check
rtk git commit -m "feat: show home instances in chunk nav"
```

---

## Task 10: End-to-end flow, regression suite, and packaged client

**Files:**

- Create: `test/nurgling/navigation/HomeInteriorFlowTest.java`
- Modify only if a failing regression proves it necessary: files already listed in Tasks 1–9.

- [ ] **Step 1: Write the complete flow test**

Exercise this exact state sequence with pure graph/registry/service fixtures:

```text
saved claim + village surface
-> confirmed building entrance
-> upstairs
-> cellar
-> back to building
-> outside
-> restart while current grid is a learned indoor grid
```

Assert:

- both origins are attached at the entrance;
- floors/cellar keep the same binding ID and instance-wide home result;
- outside uses direct territory detection instead of inherited surface marking;
- no stale inherited frame remains after exit;
- restart resolves by exact stable grid before any new traversal;
- deleting the claim alone leaves the village origin active;
- deleting both origins deactivates the automatic binding;
- a parallel mine/cave branch never receives the binding.

- [ ] **Step 2: Run the new flow test**

Run `rtk ant test-compile`, then select `nurgling.navigation.HomeInteriorFlowTest`.

Expected: pass. If it fails, make only the smallest implementation correction, add a focused regression assertion, and rerun the affected task test.

- [ ] **Step 3: Run all focused home and portal tests**

```powershell
rtk java -cp "build/classes;build/test-classes;lib/ext/junit/junit-platform-console-standalone-1.14.2.jar;lib/ext/jogl/jogl-all.jar;lib/ext/jogl/gluegen-rt.jar;lib/ext/lwjgl/lwjgl-fat.jar;lib/ext/lwjgl/lwjgl-awt.jar;lib/ext/lwjgl/lwjgl-opengl-fat.jar;lib/ext/steamworks/steamworks4j.jar;etc/json-java.jar;etc/postgresql-42.7.5.jar;etc/sqlite-jdbc-3.49.1.0.jar;lib/jglob.jar;bin/builtin-res.jar;bin/hafen-res.jar;bin/nurgling-res.jar" org.junit.platform.console.ConsoleLauncher execute --disable-banner --select-class=nurgling.tools.HomeTerritoriesTest --select-class=nurgling.tools.HomeInteriorRegistryTest --select-class=nurgling.tools.HomeLocationResolverTest --select-class=nurgling.tools.HomeTerritoryDebugTest --select-class=nurgling.NConfigHomeTerritoriesTest --select-class=nurgling.NConfigHomeInteriorsTest --select-class=nurgling.navigation.ThatchedHutPortalTest --select-class=nurgling.navigation.HomePortalInheritanceTest --select-class=nurgling.navigation.HomePortalLearningServiceTest --select-class=nurgling.navigation.HomeInteriorFlowTest --select-class=nurgling.widgets.ChunkHomePresentationTest --details=summary
```

Expected: all selected tests pass.

- [ ] **Step 4: Run the complete suite**

```powershell
rtk ant test
```

If the known Ant resource-classpath problem still affects unrelated `NMapView` tests, confirm it is the same failure and run the complete suite directly with the resource jars:

```powershell
rtk java -cp "build/classes;build/test-classes;lib/ext/junit/junit-platform-console-standalone-1.14.2.jar;lib/ext/jogl/jogl-all.jar;lib/ext/jogl/gluegen-rt.jar;lib/ext/lwjgl/lwjgl-fat.jar;lib/ext/lwjgl/lwjgl-awt.jar;lib/ext/lwjgl/lwjgl-opengl-fat.jar;lib/ext/steamworks/steamworks4j.jar;etc/json-java.jar;etc/postgresql-42.7.5.jar;etc/sqlite-jdbc-3.49.1.0.jar;lib/jglob.jar;bin/builtin-res.jar;bin/hafen-res.jar;bin/nurgling-res.jar" org.junit.platform.console.ConsoleLauncher execute --disable-banner --scan-class-path=build/test-classes --fail-if-no-tests --details=summary
```

Record the total passed/failed counts. Do not claim success if either run shows a new failure.

- [ ] **Step 5: Build the distributable jar**

```powershell
rtk ant bin
```

Expected: `BUILD SUCCESSFUL` and an updated `bin/hafen.jar`.

- [ ] **Step 6: Self-review against the specification**

Check every requirement explicitly:

- [ ] claim and village origins can coexist;
- [ ] relog inside resolves from stable grid/instance;
- [ ] overlay disabled still learns portals;
- [ ] floors and cellars inherit;
- [ ] mines/caves/gates/teleports/unknown transitions do not inherit;
- [ ] automatic origins deactivate when removed;
- [ ] manual home remains until unmarked;
- [ ] Home Setup removal preserves concurrent learning and creates suppression;
- [ ] claim backfill respects suppression and never guesses village geometry;
- [ ] debug and visualizer report the same resolver result;
- [ ] no Chunk Nav binary format change exists.

Scan new production files for unfinished markers:

```powershell
rtk rg -n "TODO|FIXME|UnsupportedOperationException|return null;|return false;" src/nurgling/tools/HomeInteriorRegistry.java src/nurgling/tools/HomeInteriorStore.java src/nurgling/tools/HomeLocationResolver.java src/nurgling/navigation/HomePortalInheritance.java src/nurgling/navigation/HomePortalLearningService.java src/nurgling/widgets/ChunkHomePresentation.java
```

Review every match and confirm it is a legitimate guard/decoder path, not an unfinished implementation.

- [ ] **Step 7: Commit final flow coverage and packaged jar**

```powershell
rtk git add test/nurgling/navigation/HomeInteriorFlowTest.java
rtk git diff --cached --check
rtk git commit -m "test: verify indoor home territory flow"
```

`bin/hafen.jar` is a verified build artifact. Do not stage it if it already contained unrelated working-tree changes before this plan began.
