package nurgling.widgets;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/** Compact global preference format for favorite Thingwall names. */
final class ThingwallFavorites {
    private ThingwallFavorites() {
    }

    static Set<String> names(String saved) {
        if((saved == null) || saved.isEmpty())
            return(Collections.emptySet());
        Set<String> names = new TreeSet<>();
        for(String encoded : saved.split("\\n")) {
            if(encoded.isEmpty())
                continue;
            try {
                String name = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
                if(!name.isEmpty())
                    names.add(name);
            } catch(IllegalArgumentException ignored) {
            }
        }
        return(Collections.unmodifiableSet(names));
    }

    static String toggle(String saved, String name) {
        Set<String> names = new TreeSet<>(names(saved));
        if(!names.remove(name) && (name != null) && !name.isEmpty())
            names.add(name);
        StringBuilder result = new StringBuilder();
        for(String favorite : names) {
            if(result.length() > 0)
                result.append('\n');
            result.append(Base64.getUrlEncoder().withoutPadding().encodeToString(
                favorite.getBytes(StandardCharsets.UTF_8)));
        }
        return(result.toString());
    }
}
