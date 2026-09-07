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
    @Test void configuredCallsCannotLaunderPhysicalAliasClauses() {
        String aliasSource = "boolean control=ui.modctrl; if(Hotkeys.matchesKey(id, ev.awt)||control) gameplay();";
        String aliasCondition = extractConditions(aliasSource).get(0);
        assertFalse(isSourceConditionAllowed("nurgling/NMapView.java", aliasCondition, aliasSource, new ArrayList<>()));
    }

    @Test void fixedNavigationCannotLaunderUnregisteredMatcherClauses() {
        String rawCondition = "if(InputNavigation.confirm(ev.code)||raw.matchesMouse(ev.b, ui.modflags()))";
        assertFalse(isDirectConditionAllowed("nurgling/NMapView.java", 0, rawCondition, new ArrayList<>()));
        assertEquals(Arrays.asList(rawCondition), extractConditions(rawCondition + " gameplay();"));
        assertEquals(Arrays.asList("return raw.matchesMouse(button, mods);"),
                extractConditions("boolean raw(int button,int mods) { return raw.matchesMouse(button, mods); }"));
    }
    @Test void extractedConditionsIncludeKeysOperatorsAndAddedGameplayClauses() {
        String original = "if(ev.code == KeyEvent.VK_ESCAPE) cancel();";
        List<String> allowed = Arrays.asList("haven/Widget.java|if(ev.code == KeyEvent.VK_ESCAPE)|cancel dialog");
        assertEquals(Arrays.asList("if(ev.code == KeyEvent.VK_ESCAPE)"), extractConditions(original));
        assertTrue(isAllowlisted("haven/Widget.java", extractConditions(original).get(0), allowed));
        for(String changed : Arrays.asList(original.replace("VK_ESCAPE", "VK_F9"),
                original.replace(" == ", " != "), original.replace(") cancel", " || gameplay()) cancel")))
            assertFalse(isAllowlisted("haven/Widget.java", extractConditions(changed).get(0), allowed), changed);
    }

    @Test void mutationsOfActualAllowlistedSourceMustFailAudit() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get("src/haven/Widget.java")), StandardCharsets.UTF_8);
        String original = "if(code == KeyEvent.VK_ESCAPE)";
        assertTrue(source.contains(original));
        List<String> allowed = readAllowlist();
        assertTrue(isAllowlisted("haven/Widget.java", original, allowed));
        for(String changed : Arrays.asList(original.replace("VK_ESCAPE", "VK_F9"),
                original.replace(" == ", " != "), original.replace(")", " || gameplay())"))) {
            List<String> conditions = extractConditions(source.replace(original, changed));
            assertTrue(conditions.contains(changed));
            assertFalse(isDirectConditionAllowed("haven/Widget.java", 0, changed, allowed));
        }
    }

    @Test void aliasesHelperReturnsAndMixedConfiguredConditionsRemainAudited() {
        assertEquals(Arrays.asList("if(flags == 2)"), extractConditions(
                "int flags = ui.modflags(); if(flags == 2) act();"));
        assertEquals(Arrays.asList("return button == 3 && (flags & UI.MOD_CTRL) != 0;"), extractConditions(
                "boolean legacy(int button, int flags) { return button == 3 && (flags & UI.MOD_CTRL) != 0; }"));
        assertFalse(isDirectConditionAllowed("nurgling/NMapView.java", 0,
                "if(Hotkeys.matchesKey(id, ev.awt) || ev.code == KeyEvent.VK_F9)", new ArrayList<>()));
    }

    @Test void booleanAliasesOfPhysicalModifiersCannotHideGameplayBranches() {
        assertEquals(Arrays.asList("if(control)"), extractConditions(
                "boolean control = (ev.mods & KeyMatch.C) != 0; if(control) gameplay();"));
        assertEquals(Arrays.asList("return ui.modctrl;"), extractConditions(
                "boolean legacy() { return ui.modctrl; }"));
        assertEquals(Arrays.asList("return control;"), extractConditions(
                "boolean legacy() { boolean control = ui.modctrl; return control; }"));
    }

    private static List<String> extractConditions(String source) {
        List<String> result = new ArrayList<>();
        Set<String> aliases = inputAliases(source);
        Set<String> booleanAliases = new HashSet<>();
        Matcher booleans = Pattern.compile("\\bboolean\\s+(\\w+)\\s*=").matcher(mask(source, true));
        while(booleans.find())
            if(aliases.contains(booleans.group(1))) booleanAliases.add(booleans.group(1));
        Matcher matcher = Pattern.compile("\\bif\\s*\\(").matcher(mask(source, true));
        while(matcher.find()) {
            int end = balancedEnd(source, source.indexOf('(', matcher.start()));
            String condition = source.substring(matcher.start(), end);
            if(usesInput(condition, aliases)) result.add(normalize(condition));
        }
        Matcher returns = Pattern.compile("\\breturn\\b([^;]+);").matcher(mask(source, true));
        while(returns.find()) {
            String expression = source.substring(returns.start(), returns.end());
            String predicate = mask(expression, true).replaceAll("ui\\.modflags\\(\\)(?=\\s*[,\\)])|ev\\.mods(?=\\s*[,\\)])", "forwarded");
            boolean booleanInput = Pattern.compile("\\bui\\.mod(?:ctrl|shift|meta)\\b").matcher(predicate).find();
            for(String alias : booleanAliases)
                booleanInput |= Pattern.compile("\\b" + Pattern.quote(alias) + "\\b").matcher(predicate).find();
            if(booleanInput || UNVERIFIED_MATCHER.matcher(predicate).find() || (Pattern.compile("[!=]=|&&|\\|\\||[<>]=?").matcher(predicate).find() && usesInput(predicate, aliases)))
                result.add(normalize(expression));
        }
        return result;
    }

    private static Set<String> inputAliases(String source) {
        Set<String> aliases = new HashSet<>();
        Matcher assignments = Pattern.compile("\\b(\\w+)\\s*=\\s*\\(*\\s*(?:(?:\\w+\\.)*ui\\.mod(?:flags\\(\\)|shift|ctrl|meta)|(?:ev|event)\\.(?:mods|code)|(?:ev|event)\\.getKeyCode\\(\\))").matcher(mask(source, true));
        while(assignments.find()) aliases.add(assignments.group(1));
        return aliases;
    }

    private static boolean usesInput(String text, Set<String> aliases) {
        if(DIRECT_CONDITION.matcher(text).find() || UNVERIFIED_MATCHER.matcher(text).find()) return true;
        for(String alias : aliases)
            if(Pattern.compile("\\b" + Pattern.quote(alias) + "\\b").matcher(text).find()) return true;
        return false;
    }

    private static String mask(String source, boolean literals) {
        StringBuilder result = new StringBuilder(source);
        Matcher tokens = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|/\\*[\\s\\S]*?\\*/|//[^\\n]*").matcher(source);
        while(tokens.find()) {
            if(literals || tokens.group().startsWith("/"))
                for(int i = tokens.start(); i < tokens.end(); i++)
                    if(result.charAt(i) != '\n') result.setCharAt(i, ' ');
        }
        return result.toString();
    }

    private static int balancedEnd(String source, int start) {
        int depth = 0;
        char quote = 0;
        for(int i = start; i < source.length(); i++) {
            char c = source.charAt(i);
            if(quote != 0) {
                if(c == '\\') { i++; continue; }
                if(c == quote) quote = 0;
            } else if(c == '\'' || c == '"') quote = c;
            else if(c == '(') depth++;
            else if(c == ')' && --depth == 0) return i + 1;
        }
        throw new AssertionError("Unbalanced input condition: " + source.substring(start));
    }
    private static final Pattern DIRECT_CONDITION = Pattern.compile(
            "ui\\.mod(?:shift|ctrl|meta|flags\\s*\\()|\\b(?:ev|event)\\.(?:mods|code)\\b|KeyEvent\\.VK_|\\b(?:mods|modflags)\\s*(?:[&|]|[!=]=(?!\\s*null))|\\bUI\\.MOD_");
    private static final Pattern UNVERIFIED_MATCHER = Pattern.compile("\\.matches(?:Mouse|Wheel|Modifiers)\\s*\\(");
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
                    }
                    String source = mask(String.join("\n", lines), false);
                    for(String condition : extractConditions(source))
                        if(!isSourceConditionAllowed(relative, condition, source, allowlist))
                            findings.add(relative + "|" + condition);
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
    void englishAndRussianBundlesContainEveryPresetLabel() throws IOException {
        Properties en = loadProperties(Paths.get("src", "lang", "messages.properties"));
        Properties ru = loadProperties(Paths.get("src", "lang", "messages_ru.properties"));
        Set<String> required = new HashSet<>(Arrays.asList(
                "hotkeys.presets.label", "hotkeys.presets.default",
                "hotkeys.presets.create", "hotkeys.presets.copy", "hotkeys.presets.paste",
                "hotkeys.presets.delete", "hotkeys.presets.user_name",
                "hotkeys.presets.name.title", "hotkeys.presets.name.question",
                "hotkeys.presets.name.empty", "hotkeys.presets.discard.title",
                "hotkeys.presets.discard.question", "hotkeys.presets.discard",
                "hotkeys.presets.keep_editing", "hotkeys.presets.confirm",
                "hotkeys.presets.error.invalid_code", "hotkeys.presets.error.clipboard",
                "hotkeys.presets.error.store", "hotkeys.presets.error.create",
                "hotkeys.presets.error.select", "hotkeys.presets.warning.corrupt"));
        Set<String> englishKeys = prefixedKeys(en, "hotkeys.presets.");
        Set<String> russianKeys = prefixedKeys(ru, "hotkeys.presets.");
        assertEquals(englishKeys, russianKeys, "EN/RU preset-key coverage must match exactly");
        assertTrue(englishKeys.containsAll(required), "Missing preset labels: " + missing(required, englishKeys));
        for(String key : required) {
            assertFalse(isMissingLocalization(en.getProperty(key), key), "Missing English value: " + key);
            assertFalse(isMissingLocalization(ru.getProperty(key), key), "Missing Russian value: " + key);
        }
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

    private static boolean isDirectConditionAllowed(String relative, int line, String condition, List<String> allowlist) {
        return isSourceConditionAllowed(relative, condition, "", allowlist);
    }

    private static boolean isSourceConditionAllowed(String relative, String condition, String source, List<String> allowlist) {
        return hasHotkeysReference(condition, inputAliases(source), registryAliases(source)) ||
                isAllowlisted(relative, condition, allowlist);
    }

    private static Set<String> registryAliases(String source) {
        Set<String> good = new HashSet<>(), bad = new HashSet<>();
        Matcher assignments = Pattern.compile("\\b(\\w+)\\s*=(?!=)\\s*([^;]+);").matcher(mask(source, true));
        while(assignments.find()) {
            if(assignments.group(2).matches("(?:nurgling\\.hotkeys\\.)?Hotkeys\\.action\\([\\s\\S]*")) good.add(assignments.group(1));
            else bad.add(assignments.group(1));
        }
        good.removeAll(bad);
        return good;
    }

    private static boolean hasHotkeysReference(String text, Set<String> inputAliases, Set<String> registryAliases) {
        String remaining = text;
        boolean configured = false;
        // Consume the complete verified action.current().matches(...) chain;
        // a similarly named method on any arbitrary object gets no exemption.
        Pattern actionCall = Pattern.compile("\\bHotkeys\\.action\\s*\\(");
        Matcher calls = actionCall.matcher(remaining);
        while(calls.find()) {
            int start = calls.start();
            int end = balancedEnd(remaining, remaining.indexOf('(', start));
            Matcher tail = Pattern.compile("\\s*\\.current\\(\\)\\s*\\.matches(?:Mouse|Wheel|Modifiers)?\\s*\\(").matcher(remaining);
            tail.region(end, remaining.length());
            if(!tail.lookingAt()) continue;
            end = balancedEnd(remaining, remaining.indexOf('(', tail.end() - 1));
            remaining = remaining.substring(0, start) + "configured" + remaining.substring(end);
            configured = true;
            calls = actionCall.matcher(remaining);
        }
        String verified = "(?:Hotkeys|InputNavigation)\\.(?!action\\b)\\w+";
        for(String alias : registryAliases)
            verified += "|\\b" + Pattern.quote(alias) + "\\.current\\(\\)\\.matches(?:Mouse|Wheel|Modifiers)?";
        Pattern helpers = Pattern.compile("(?:" + verified + ")\\s*\\(");
        calls = helpers.matcher(remaining);
        while(calls.find()) {
            int start = calls.start(), end = balancedEnd(remaining, remaining.indexOf('(', calls.end() - 1));
            remaining = remaining.substring(0, start) + "configured" + remaining.substring(end);
            configured = true;
            calls = helpers.matcher(remaining);
        }
        return configured && !usesInput(remaining, inputAliases);
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

    private static Set<String> prefixedKeys(Properties properties, String prefix) {
        Set<String> result = new HashSet<>();
        for(String key : properties.stringPropertyNames())
            if(key.startsWith(prefix)) result.add(key);
        return result;
    }

    private static Set<String> missing(Set<String> required, Set<String> actual) {
        Set<String> result = new HashSet<>(required);
        result.removeAll(actual);
        return result;
    }

    private static void requireDirectory(Path path) {
        if(!Files.isDirectory(path))
            throw new AuditFailure(new IOException("Missing required hotkey audit source root: " + path.toAbsolutePath()));
    }

    private static final Set<String> TASK_HANDLER_FILES = new HashSet<>(Arrays.asList(
            "haven/WItem.java", "nurgling/NWItem.java", "haven/Inventory.java", "nurgling/NInventory.java",
            "haven/ItemDrag.java", "haven/MapView.java", "nurgling/NMapView.java", "nurgling/widgets/NMiniMap.java",
            "nurgling/widgets/NMapWnd.java", "nurgling/widgets/NMiniMapWnd.java", "nurgling/NFlowerMenu.java",
            "nurgling/widgets/NMakewindow.java", "haven/FightWnd.java", "haven/ISBox.java",
            "nurgling/widgets/NBuddyWnd.java", "nurgling/NWoundBox.java", "nurgling/widgets/NDraggableWidget.java",
            "nurgling/widgets/compass/NCompassWidget.java", "nurgling/NGameUI.java",
            "nurgling/widgets/LocalizedResourceTimersWindow.java", "haven/GobIcon.java", "haven/MenuSearch.java",
            "haven/res/ui/croster/RosterButton.java", "haven/res/ui/stackinv/ItemStack.java", "haven/res/ui/surv/LandSurvey.java", "haven/Fightsess.java"));

    private static final class AuditFailure extends RuntimeException {
        AuditFailure(IOException cause) { super(cause); }
    }
}
