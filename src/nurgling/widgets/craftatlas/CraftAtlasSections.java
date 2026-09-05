package nurgling.widgets.craftatlas;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Stable section ids shared by the Craft Atlas menu and its filters. */
final class CraftAtlasSections {
    static final List<String> MAIN = Collections.unmodifiableList(Arrays.asList(
            "all", "gildings", "foods", "curiosities", "equipment"));
    static final List<String> EQUIPMENT = Collections.unmodifiableList(Arrays.asList(
            "equipment", "equipment-shoes", "equipment-pants", "equipment-shirts",
            "equipment-shoulders", "equipment-hats", "equipment-capes", "equipment-cloaks",
            "equipment-rings"));

    private CraftAtlasSections() { }

    static boolean isEquipment(String section) {
        return section != null && (section.equals("equipment") || section.startsWith("equipment-"));
    }

    static String category(String section) {
        if("gildings".equals(section) || "foods".equals(section) ||
                "curiosities".equals(section) || isEquipment(section)) return section;
        return null;
    }

    static boolean hasMetricTable(String section) {
        return "foods".equals(section) || "gildings".equals(section) || "curiosities".equals(section);
    }
}
