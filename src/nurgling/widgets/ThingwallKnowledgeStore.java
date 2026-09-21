package nurgling.widgets;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Persistent server-confirmed Thingwall names, separate from local map visibility. */
final class ThingwallKnowledgeStore {
    private ThingwallKnowledgeStore() {
    }

    static String scope(String characterId, String mapFilename, long segment) {
        return((characterId == null ? "" : characterId) + '\u0000'
            + (mapFilename == null ? "" : mapFilename) + '\u0000' + segment);
    }

    static Set<String> names(String saved, String scope) {
        Set<String> names = decode(saved).get(scope);
        return((names == null) ? Collections.<String>emptySet() : new LinkedHashSet<>(names));
    }

    static String record(String saved, String scope, Collection<String> additions) {
        if(scope == null)
            return(saved == null ? "" : saved);
        Map<String, Set<String>> known = decode(saved);
        Set<String> names = known.get(scope);
        if(names == null) {
            names = new TreeSet<>();
            known.put(scope, names);
        }
        if(additions != null) {
            for(String name : additions) {
                if((name != null) && !name.isEmpty())
                    names.add(name);
            }
        }
        return(encode(known));
    }

    static boolean isUniqueName(String name, Collection<String> markerNames) {
        if((name == null) || (markerNames == null))
            return(false);
        int matches = 0;
        for(String markerName : markerNames) {
            if(name.equals(markerName) && (++matches > 1))
                return(false);
        }
        return(matches == 1);
    }

    private static Map<String, Set<String>> decode(String saved) {
        Map<String, Set<String>> result = new TreeMap<>();
        if((saved == null) || saved.isEmpty())
            return(result);
        for(String entry : saved.split("\\n")) {
            int separator = entry.indexOf('\t');
            if(separator <= 0)
                continue;
            try {
                String scope = decodePart(entry.substring(0, separator));
                String name = decodePart(entry.substring(separator + 1));
                if(!name.isEmpty())
                    result.computeIfAbsent(scope, ignored -> new TreeSet<>()).add(name);
            } catch(IllegalArgumentException ignored) {
            }
        }
        return(result);
    }

    private static String encode(Map<String, Set<String>> known) {
        StringBuilder result = new StringBuilder();
        for(Map.Entry<String, Set<String>> entry : known.entrySet()) {
            for(String name : entry.getValue()) {
                if(result.length() > 0)
                    result.append('\n');
                result.append(encodePart(entry.getKey())).append('\t').append(encodePart(name));
            }
        }
        return(result.toString());
    }

    private static String encodePart(String value) {
        return(Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String decodePart(String value) {
        return(new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8));
    }
}
