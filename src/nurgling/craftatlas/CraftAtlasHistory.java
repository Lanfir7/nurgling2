package nurgling.craftatlas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Browser-style, bounded navigation history for recipe cards. */
public final class CraftAtlasHistory {
    public static final int LIMIT = 64;
    private final List<CardState> states = new ArrayList<>();
    private int cursor = -1;

    public static final class CardState {
        public final String recipeResource;
        public final int scroll;
        public final Set<String> expandedGroups;

        public CardState(String recipeResource, int scroll, Set<String> expandedGroups) {
            if(recipeResource == null || recipeResource.trim().isEmpty())
                throw new IllegalArgumentException("recipeResource must not be empty");
            this.recipeResource = recipeResource;
            this.scroll = Math.max(0, scroll);
            this.expandedGroups = Collections.unmodifiableSet(new LinkedHashSet<>(
                    expandedGroups == null ? Collections.<String>emptySet() : expandedGroups));
        }
    }

    public void visit(CardState state) {
        if(state == null) return;
        while(states.size() > cursor + 1) states.remove(states.size() - 1);
        states.add(state);
        cursor = states.size() - 1;
        if(states.size() > LIMIT) {
            states.remove(0);
            cursor--;
        }
    }

    public boolean canBack() { return cursor > 0; }
    public boolean canForward() { return cursor >= 0 && cursor < states.size() - 1; }
    public CardState current() { return cursor < 0 ? null : states.get(cursor); }
    public CardState back() { if(canBack()) cursor--; return current(); }
    public CardState forward() { if(canForward()) cursor++; return current(); }
    public void clear() { states.clear(); cursor = -1; }
}
