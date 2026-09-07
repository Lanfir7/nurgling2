package nurgling.hotkeys;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Completeness guard for gameplay shortcut registration and action labels. */
class GameplayHotkeyAuditTest {
    private static final Pattern DIRECT_CONDITION = Pattern.compile(
            "\\b(?:if|else\\s+if)\\s*\\([^\\n]*(?:ui\\.mod(?:shift|ctrl|meta)|ev\\.mods|ev\\.code|KeyEvent\\.VK_)");
    private static final Pattern KEY_ID = Pattern.compile(
            "KeyBinding\\.get\\(\\s*\"([^\"]+)\"");
    @Test
    void sourceHasNoUncataloguedGameplayShortcuts() throws IOException {
        Set<String> knownIds = HotkeyCatalog.knownStaticIds();
        List<String> allowlist = readAllowlist();
        List<String> findings = new ArrayList<>();
        for(Path root : Arrays.asList(Paths.get("src", "haven"), Paths.get("src", "nurgling"))) {
            requireDirectory(root);
            Files.walk(root).filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                    String relative = Paths.get("src").relativize(path).toString().replace('\\', '/');
                    for(int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        if(line.trim().startsWith("//"))
                            continue;
                        Matcher ids = KEY_ID.matcher(line);
                        while(ids.find()) {
                            String id = ids.group(1);
                            if(!knownIds.contains(id) && !isExplicitDynamicId(id, relative, line))
                                findings.add(relative + ":" + (i + 1) + ": KeyBinding.get(\"" + id + "\")");
                        }
                        Matcher conditions = DIRECT_CONDITION.matcher(line);
                        while(conditions.find()) {
                            String normalized = normalize(conditions.group());
                            String record = relative + ":" + (i + 1) + ": " + normalized;
                            if(!isDirectConditionAllowed(relative, i + 1, normalized, allowlist))
                                findings.add(record);
                        }
                    }
                } catch(IOException e) {
                    throw new AuditFailure(e);
                }
            });
        }
        assertTrue(findings.isEmpty(), "Unclassified gameplay shortcuts:\n" + String.join("\n", findings));
    }

    @Test
    void englishAndRussianBundlesContainEveryStaticActionLabel() throws IOException {
        Properties en = loadProperties(Paths.get("src", "lang", "messages.properties"));
        Properties ru = loadProperties(Paths.get("src", "lang", "messages_ru.properties"));
        Set<String> expected = new HashSet<>();
        for(String id : HotkeyCatalog.knownStaticIds()) {
            expected.add("hotkeys.action." + id);
        }
        Set<String> englishKeys = actionKeys(en);
        Set<String> russianKeys = actionKeys(ru);
        assertEquals(englishKeys, russianKeys, "EN/RU action-key coverage must match exactly");
        assertEquals(expected, englishKeys, "Action bundles must cover exactly the registered static actions");
        List<String> missing = new ArrayList<>();
        for(String id : HotkeyCatalog.knownStaticIds()) {
            String key = "hotkeys.action." + id;
            if(isMissingLocalization(en.getProperty(key), key) || isMissingLocalization(ru.getProperty(key), key))
                missing.add(key);
        }
        assertTrue(missing.isEmpty(), "Missing action labels in EN/RU bundles: " + String.join(", ", missing));
    }

    @Test
    void auditDoesNotBlessAWholeMethodContainingHotkeys() {
        assertFalse(isDirectConditionAllowed("nurgling/NMapView.java", 1754, "if(ev.code == KeyEvent.VK_ESCAPE)", new ArrayList<>()));
        assertTrue(isDirectConditionAllowed("nurgling/NMapView.java", 1754, "if(Hotkeys.matchesKey(id, ev.awt))", new ArrayList<>()));
    }

    @Test
    void allowlistMatchingRequiresExactCondition() {
        List<String> allowlist = Arrays.asList("haven/GameUI.java|if(ev.code == KeyEvent.VK_ESCAPE)|system");
        assertTrue(isAllowlisted("haven/GameUI.java", "if(ev.code == KeyEvent.VK_ESCAPE)", allowlist));
        assertFalse(isAllowlisted("haven/GameUI.java", "if(ev.code == KeyEvent.VK_ESCAPE || belt)", allowlist));
        assertFalse(isAllowlisted("haven/GameUI.java", "if(ev.code", allowlist));
    }

    @Test
    void dynamicBindingAllowanceIsNarrow() {
        assertTrue(isExplicitDynamicId("belt0", "nurgling/hotkeys/HotkeyCatalog.java", "KeyBinding.get(\"belt0\" + slot)"));
        assertFalse(isExplicitDynamicId("belt0", "haven/GameUI.java", "KeyBinding.get(\"belt0\" + slot)"));
        assertFalse(isExplicitDynamicId("belt0x", "nurgling/conf/NToolBeltProp.java", "KeyBinding.get(\"belt0x\")"));
    }

    @Test
    void localizationFallbackSentinelsAreRejected() {
        assertTrue(isMissingLocalization("hotkeys.action.demo", "hotkeys.action.demo"));
        assertTrue(isMissingLocalization("[hotkeys.action.demo]", "hotkeys.action.demo"));
        assertFalse(isMissingLocalization("Demo action", "hotkeys.action.demo"));
    }

    @Test
    void missingAuditInputsFailExplicitly() {
        assertThrows(AuditFailure.class, () -> requireDirectory(Paths.get("src", "missing-hotkey-root")));
        assertThrows(IOException.class, () -> readAllowlist(Paths.get("test", "missing-hotkey-allowlist.txt")));
    }

    @Test
    void taskHandlersCannotBeExemptedBySystemAllowlist() throws IOException {
        for(String entry : readAllowlist()) {
            int separator = entry.indexOf('|');
            assertTrue(separator > 0, "Malformed allowlist entry: " + entry);
            String path = entry.substring(0, separator);
            assertFalse(TASK_HANDLER_FILES.contains(path), "Task 7-8 handler must use Hotkeys: " + path);
        }
    }

    private static boolean isExplicitDynamicId(String id, String relative, String sourceLine) {
        return id.startsWith("scm/") || id.startsWith("wgk/")
                || (relative.endsWith("ConsoleHost.java") && id.startsWith("history/"))
                || id.startsWith("test/")
                || (id.equals("belt0") && relative.endsWith("HotkeyCatalog.java") && sourceLine.contains("\"belt0\" +"))
                || (id.equals("belt0") && relative.endsWith("NToolBeltProp.java") && sourceLine.contains("\"belt0\" +"));
    }

    private static boolean isExactlyClassified(String relative, int line, String condition) {
        return EXACT_CLASSIFIED_CONDITIONS.contains(relative + ":" + line + "|" + condition);
    }

    private static boolean isDirectConditionAllowed(String relative, int line, String condition, List<String> allowlist) {
        return hasHotkeysReference(condition) || isExactlyClassified(relative, line, condition) ||
                isAllowlisted(relative, condition, allowlist);
    }

    private static boolean hasHotkeysReference(String text) {
        return text.contains("Hotkeys.");
    }

    private static String normalize(String condition) {
        return condition.replaceAll("\\s+", " ").trim();
    }

    private static boolean isAllowlisted(String path, String condition, List<String> allowlist) {
        for(String entry : allowlist) {
            int first = entry.indexOf('|');
            int last = entry.lastIndexOf('|');
            if(first <= 0 || last <= first)
                continue;
            if(path.equals(entry.substring(0, first)) && condition.equals(entry.substring(first + 1, last)))
                return true;
        }
        return false;
    }

    private static List<String> readAllowlist() throws IOException {
        return readAllowlist(Paths.get("test", "nurgling", "hotkeys", "system-shortcut-allowlist.txt"));
    }

    private static List<String> readAllowlist(Path path) throws IOException {
        if(!Files.isRegularFile(path))
            throw new IOException("Missing required hotkey audit allowlist: " + path.toAbsolutePath());
        List<String> result = new ArrayList<>();
        for(String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if(!trimmed.isEmpty() && !trimmed.startsWith("#"))
                result.add(trimmed);
        }
        return result;
    }

    private static Properties loadProperties(Path path) throws IOException {
        Properties result = new Properties();
        java.io.Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
        try {
            result.load(reader);
        } finally {
            reader.close();
        }
        return result;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean isMissingLocalization(String value, String key) {
        return isBlank(value) || value.equals(key) || value.equals("[" + key + "]");
    }

    private static Set<String> actionKeys(Properties properties) {
        Set<String> result = new HashSet<>();
        for(String key : properties.stringPropertyNames()) {
            if(key.startsWith("hotkeys.action."))
                result.add(key);
        }
        return result;
    }

    private static void requireDirectory(Path path) {
        if(!Files.isDirectory(path))
            throw new AuditFailure(new IOException("Missing required hotkey audit source root: " + path.toAbsolutePath()));
    }

    private static final Set<String> EXACT_CLASSIFIED_CONDITIONS = new HashSet<>(Arrays.asList(
            "haven/WItem.java:101|if(ui.modshift",
            "nurgling/NWItem.java:85|if (ui.modshift",
            "haven/MenuSearch.java:343|if(ev.code",
            "haven/MenuSearch.java:351|else if(ev.code",
            "nurgling/NGameUI.java:1446|if (k == null || k == KeyMatch.nil || k.code == KeyEvent.VK_",
            "nurgling/NMapView.java:1923|if(ev.code",
            "nurgling/NMapView.java:1932|if(ev.code",
            "nurgling/widgets/NMiniMap.java:2470|if(ui == null || !ui.modshift",
            "nurgling/widgets/NMapWnd.java:141|if(ev.code == java.awt.event.KeyEvent.VK_",
            "nurgling/widgets/LocalizedResourceTimersWindow.java:210|if(ev.code == java.awt.event.KeyEvent.VK_"
    ));

    private static final Set<String> TASK_HANDLER_FILES = new HashSet<>(Arrays.asList(
            "haven/WItem.java", "nurgling/NWItem.java", "haven/Inventory.java", "nurgling/NInventory.java",
            "haven/ItemDrag.java", "haven/MapView.java", "nurgling/NMapView.java", "nurgling/widgets/NMiniMap.java",
            "nurgling/widgets/NMapWnd.java", "nurgling/NMiniMapWnd.java", "nurgling/NFlowerMenu.java",
            "nurgling/widgets/NMakewindow.java", "haven/FightWnd.java", "haven/ISBox.java",
            "nurgling/NBuddyWnd.java", "nurgling/NWoundBox.java", "nurgling/NDraggableWidget.java",
            "nurgling/NCompassWidget.java", "nurgling/NGameUI.java",
            "nurgling/widgets/LocalizedResourceTimersWindow.java", "haven/GobIcon.java", "haven/MenuSearch.java",
            "nurgling/RosterButton.java", "nurgling/ItemStack.java", "nurgling/LandSurvey.java", "haven/Fightsess.java"));

    private static final class AuditFailure extends RuntimeException {
        AuditFailure(IOException cause) { super(cause); }
    }
}
