package nurgling.widgets;

import java.util.Locale;

final class SpecialisationSearch {
    private SpecialisationSearch() {
    }

    static boolean matches(String query, String internalName, String displayName) {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty()
                || internalName.toLowerCase(Locale.ROOT).contains(normalized)
                || displayName.toLowerCase(Locale.ROOT).contains(normalized);
    }
}
