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

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Completeness guard for gameplay shortcut registration and action labels. */
class GameplayHotkeyAuditTest {
    private static final Pattern DIRECT_CONDITION = Pattern.compile(
            "\\b(?:if|else\\s+if)\\s*\\([^\\n]*(?:ui\\.mod(?:shift|ctrl|meta)|ev\\.mods|ev\\.code|KeyEvent\\.VK_)");
    private static final Pattern KEY_ID = Pattern.compile(
            "KeyBinding\\.get\\(\\s*\"([^\"]+)\"");
    private static final Set<String> CONVERTED_HANDLERS = new HashSet<>(Arrays.asList(
            "WItem", "NWItem", "Inventory", "NInventory", "ItemDrag", "MapView", "NMapView",
            "NMiniMap", "NMapWnd", "NMiniMapWnd", "NFlowerMenu", "NMakewindow", "FightWnd", "ISBox",
            "NBuddyWnd", "NWoundBox", "NDraggableWidget", "NCompassWidget", "NGameUI",
            "LocalizedResourceTimersWindow", "GobIcon", "MenuSearch", "RosterButton", "ItemStack",
            "LandSurvey", "Fightsess"));

    @Test
    void sourceHasNoUncataloguedGameplayShortcuts() throws IOException {
        Set<String> knownIds = HotkeyCatalog.knownStaticIds();
        List<String> allowlist = readAllowlist();
        List<String> findings = new ArrayList<>();
        for(Path root : Arrays.asList(Paths.get("src", "haven"), Paths.get("src", "nurgling"))) {
            if(!Files.exists(root))
                continue;
            Files.walk(root).filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                    String source = String.join("\n", lines);
                    String relative = Paths.get("src").relativize(path).toString().replace('\\', '/');
                    for(int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        if(line.trim().startsWith("//"))
                            continue;
                        Matcher ids = KEY_ID.matcher(line);
                        while(ids.find()) {
                            String id = ids.group(1);
                            if(!knownIds.contains(id) && !isExplicitDynamicId(id, relative))
                                findings.add(relative + ":" + (i + 1) + ": KeyBinding.get(\"" + id + "\")");
                        }
                        Matcher conditions = DIRECT_CONDITION.matcher(line);
                        while(conditions.find()) {
                            String normalized = normalize(conditions.group());
                            String record = relative + ":" + (i + 1) + ": " + normalized;
                            // Converted gameplay handlers retain a few fixed mouse/key routing
                            // branches, but each file is wired through the unified dispatcher.
                            if(isConvertedHandler(relative)) {
                                if(!isFixedSystemCondition(relative, normalized) && !hasHotkeysReference(source, i))
                                    findings.add(record);
                            } else if(!hasHotkeysReference(conditions.group()) && !isAllowlisted(relative, normalized, allowlist))
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
        List<String> missing = new ArrayList<>();
        for(String id : HotkeyCatalog.knownStaticIds()) {
            String key = "hotkeys.action." + id;
            if(isBlank(en.getProperty(key)) || isBlank(ru.getProperty(key)))
                missing.add(key);
        }
        assertTrue(missing.isEmpty(), "Missing action labels in EN/RU bundles: " + String.join(", ", missing));
    }

    private static boolean isExplicitDynamicId(String id, String relative) {
        return id.startsWith("scm/") || id.startsWith("wgk/") || id.startsWith("belt0")
                || (relative.endsWith("ConsoleHost.java") && id.startsWith("history/"))
                || relative.endsWith("NToolBeltProp.java") || id.startsWith("test/");
    }

    private static boolean isConvertedHandler(String relative) {
        String file = relative.substring(relative.lastIndexOf('/') + 1);
        return file.endsWith(".java") && CONVERTED_HANDLERS.contains(file.substring(0, file.length() - 5));
    }

    private static boolean isFixedSystemCondition(String relative, String condition) {
        String file = relative.substring(relative.lastIndexOf('/') + 1);
        return (file.equals("WItem.java") || file.equals("NWItem.java")) && condition.contains("modshift")
                || file.equals("MenuSearch.java")
                || file.equals("NMapView.java") && condition.startsWith("if(ev.code")
                || file.equals("NMapWnd.java") && condition.startsWith("if(ev.code")
                || file.equals("NMiniMap.java") && condition.startsWith("if(ui == null")
                || file.equals("NGameUI.java") && condition.startsWith("if (k == null");
    }

    private static boolean hasHotkeysReference(String text, int line) {
        String[] lines = text.split("\\n", -1);
        Pattern method = Pattern.compile("\\b(?:public|protected|private)\\b.*\\([^;]*\\)\\s*\\{\\s*$");
        int start = line;
        while(start >= 0 && !method.matcher(lines[start].trim()).find())
            start--;
        if(start < 0)
            return false;
        for(int i = start; i < lines.length; i++) {
            if(i > start && method.matcher(lines[i].trim()).find())
                break;
            if(lines[i].contains("Hotkeys."))
                return true;
        }
        return false;
    }

    private static boolean hasHotkeysReference(String text) {
        return text.contains("Hotkeys.");
    }

    private static String normalize(String condition) {
        return condition.replaceAll("\\s+", " ").trim();
    }

    private static boolean isAllowlisted(String path, String condition, List<String> allowlist) {
        String prefix = path + "|";
        for(String entry : allowlist) {
            if(!entry.startsWith(prefix))
                continue;
            String allowed = entry.substring(prefix.length());
            int separator = allowed.indexOf('|');
            if(separator >= 0)
                allowed = allowed.substring(0, separator);
            if(condition.equals(allowed) || condition.startsWith(allowed) || allowed.startsWith(condition))
                return true;
        }
        return false;
    }

    private static List<String> readAllowlist() throws IOException {
        Path path = Paths.get("test", "nurgling", "hotkeys", "system-shortcut-allowlist.txt");
        if(!Files.exists(path))
            return new ArrayList<>();
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

    private static final class AuditFailure extends RuntimeException {
        AuditFailure(IOException cause) { super(cause); }
    }
}
