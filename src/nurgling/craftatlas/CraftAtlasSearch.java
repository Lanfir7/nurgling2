package nurgling.craftatlas;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Search and sorting independent of widgets and the game database. */
public final class CraftAtlasSearch {
    private CraftAtlasSearch() { }

    public static final class Query {
        public final String text;
        public final String bonusResource;
        public final boolean descending;
        public final String category;
        public final Set<String> favorites;
        public final Set<String> restrictedResources;
        public final boolean restricted;

        private Query(Builder b) {
            text = normalize(b.text);
            bonusResource = b.bonusResource;
            descending = b.descending;
            category = normalize(b.category);
            favorites = Collections.unmodifiableSet(new LinkedHashSet<>(b.favorites));
            restrictedResources = Collections.unmodifiableSet(new LinkedHashSet<>(b.restrictedResources));
            restricted = b.restricted;
        }

        public static Query text(String value) { return builder().text(value).build(); }
        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private String text = "";
            private String bonusResource;
            private boolean descending;
            private String category = "";
            private Set<String> favorites = Collections.emptySet();
            private Set<String> restrictedResources = Collections.emptySet();
            private boolean restricted;
            public Builder text(String value) { text = value; return this; }
            public Builder bonus(String value) { bonusResource = value; return this; }
            public Builder descending(boolean value) { descending = value; return this; }
            public Builder category(String value) { category = value; return this; }
            public Builder favorites(Set<String> value) { favorites = value == null ? Collections.<String>emptySet() : value; return this; }
            public Builder restrictTo(Set<String> value) {
                restrictedResources = value == null ? Collections.<String>emptySet() : value;
                restricted = true;
                return this;
            }
            public Query build() { return new Query(this); }
        }
    }

    public static List<CraftAtlasEntry> query(CraftAtlasSnapshot snapshot, Query query) {
        if(snapshot == null) return Collections.emptyList();
        final Query q = query == null ? Query.text("") : query;
        final String[] tokens = q.text.isEmpty() ? new String[0] : q.text.split(" ");
        List<CraftAtlasEntry> result = new ArrayList<>();
        for(CraftAtlasEntry entry : snapshot.entries) {
            if(!q.favorites.isEmpty() && !q.favorites.contains(entry.recipeResource)) continue;
            if(q.restricted && !q.restrictedResources.contains(entry.recipeResource)) continue;
            if(!q.category.isEmpty() && !normalizedCategories(entry).contains(q.category)) continue;
            String haystack = searchableText(entry);
            boolean matches = true;
            for(String token : tokens) if(!haystack.contains(token)) { matches = false; break; }
            if(matches) result.add(entry);
        }
        if(q.bonusResource != null) {
            result.sort((a, b) -> compareBonus(a, b, q.bonusResource, q.descending));
        } else {
            result.sort(Comparator.comparing(e -> normalize(e.displayName)));
        }
        return Collections.unmodifiableList(result);
    }

    private static int compareBonus(CraftAtlasEntry a, CraftAtlasEntry b, String resource, boolean descending) {
        Double av = bonusValue(a, resource), bv = bonusValue(b, resource);
        if(av == null && bv == null) return normalize(a.displayName).compareTo(normalize(b.displayName));
        if(av == null) return 1;
        if(bv == null) return -1;
        int cmp = Double.compare(av, bv);
        if(descending) cmp = -cmp;
        return cmp != 0 ? cmp : normalize(a.displayName).compareTo(normalize(b.displayName));
    }

    private static Double bonusValue(CraftAtlasEntry entry, String resource) {
        for(CraftAtlasEntry.Bonus bonus : entry.bonuses)
            if(resource.equals(bonus.attributeResource)) return bonus.value;
        return null;
    }

    private static String normalizedCategories(CraftAtlasEntry e) {
        StringBuilder out = new StringBuilder();
        for(String category : e.categories) out.append(' ').append(normalize(category));
        return out.toString();
    }

    private static String searchableText(CraftAtlasEntry e) {
        StringBuilder out = new StringBuilder(normalize(e.displayName));
        for(CraftAtlasEntry.Bonus bonus : e.bonuses)
            out.append(' ').append(normalize(bonus.name)).append(' ').append(normalize(bonus.attributeResource));
        return out.toString();
    }

    public static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replace('ё', 'е').trim().replaceAll("\\s+", " ");
    }
}
