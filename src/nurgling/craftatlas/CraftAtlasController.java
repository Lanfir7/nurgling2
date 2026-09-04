package nurgling.craftatlas;

import nurgling.tools.RecipeIngredientCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/** Owns Atlas search, selection, dependency navigation and browser history. */
public final class CraftAtlasController {
    public interface Listener { void changed(ViewState state); }

    public static final class ViewState {
        public final CraftAtlasSnapshot snapshot;
        public final List<CraftAtlasEntry> results;
        public final CraftAtlasEntry selected;
        public final List<CraftAtlasEntry> choices;
        public final String cycleResource;
        public final CraftAtlasEntry.Requirement requirementDescription;
        public final boolean canBack, canForward;

        private ViewState(CraftAtlasSnapshot snapshot, List<CraftAtlasEntry> results, CraftAtlasEntry selected,
                          List<CraftAtlasEntry> choices, String cycleResource,
                          CraftAtlasEntry.Requirement requirementDescription, boolean canBack, boolean canForward) {
            this.snapshot = snapshot;
            this.results = Collections.unmodifiableList(new ArrayList<>(results));
            this.selected = selected;
            this.choices = Collections.unmodifiableList(new ArrayList<>(choices));
            this.cycleResource = cycleResource;
            this.requirementDescription = requirementDescription;
            this.canBack = canBack;
            this.canForward = canForward;
        }
    }

    private CraftAtlasSnapshot snapshot;
    private CraftRecipeGraph graph;
    private final CraftAtlasHistory history = new CraftAtlasHistory();
    private final CraftExecutionBridge bridge;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private CraftAtlasSearch.Query query = CraftAtlasSearch.Query.text("");
    private List<CraftAtlasEntry> results;
    private CraftAtlasEntry selected;
    private List<CraftAtlasEntry> choices = Collections.emptyList();
    private String cycleResource;
    private CraftAtlasEntry.Requirement requirementDescription;
    private final List<String> activePath = new ArrayList<>();

    public CraftAtlasController(CraftAtlasSnapshot snapshot, CraftExecutionBridge bridge) {
        this.snapshot = snapshot == null ? CraftAtlasSnapshot.of(0, Collections.<CraftAtlasEntry>emptyList()) : snapshot;
        this.graph = new CraftRecipeGraph(this.snapshot);
        this.bridge = bridge;
        this.results = CraftAtlasSearch.query(this.snapshot, query);
    }

    public void addListener(Listener listener) { if(listener != null) listeners.add(listener); }
    public void removeListener(Listener listener) { listeners.remove(listener); }
    public ViewState state() { return buildState(); }

    public void replaceSnapshot(CraftAtlasSnapshot value) {
        snapshot = value == null ? CraftAtlasSnapshot.of(0, Collections.<CraftAtlasEntry>emptyList()) : value;
        graph = new CraftRecipeGraph(snapshot);
        results = CraftAtlasSearch.query(snapshot, query);
        if(selected != null) selected = snapshot.byRecipe(selected.recipeResource);
        notifyListeners();
    }

    public void setQuery(CraftAtlasSearch.Query value) {
        query = value == null ? CraftAtlasSearch.Query.text("") : value;
        results = CraftAtlasSearch.query(snapshot, query);
        notifyListeners();
    }

    public void select(String recipeResource) {
        CraftAtlasEntry entry = snapshot.byRecipe(recipeResource);
        if(entry == null) return;
        activePath.clear();
        activePath.add(entry.recipeResource);
        visit(entry);
    }

    private void visit(CraftAtlasEntry entry) {
        selected = entry;
        choices = Collections.emptyList();
        cycleResource = null;
        requirementDescription = null;
        history.visit(new CraftAtlasHistory.CardState(entry.recipeResource, 0, Collections.<String>emptySet()));
        notifyListeners();
    }

    public CraftRecipeGraph.LinkState linkState(String resource) { return graph.linkState(resource, activePath); }

    public CraftRecipeGraph.LinkState linkState(String resource, String displayName) {
        CraftRecipeGraph.LinkState direct = linkState(resource);
        if(direct != CraftRecipeGraph.LinkState.NONE) return direct;
        List<CraftAtlasEntry> producers = producers(resource, displayName);
        for(CraftAtlasEntry producer : producers)
            if(activePath.contains(producer.recipeResource)) return CraftRecipeGraph.LinkState.CYCLE;
        if(producers.size() == 1) return CraftRecipeGraph.LinkState.SINGLE;
        if(producers.size() > 1) return CraftRecipeGraph.LinkState.MULTIPLE;
        return CraftRecipeGraph.LinkState.NONE;
    }

    public void openIngredient(String resource) { openIngredient(resource, null); }

    public void openIngredient(String resource, String displayName) {
        List<CraftAtlasEntry> producers = producers(resource, displayName);
        if(producers.isEmpty()) return;
        for(CraftAtlasEntry producer : producers) if(activePath.contains(producer.recipeResource)) {
            cycleResource = resource;
            choices = Collections.emptyList();
            notifyListeners();
            return;
        }
        if(producers.size() == 1) {
            CraftAtlasEntry producer = producers.get(0);
            activePath.add(producer.recipeResource);
            visit(producer);
        } else {
            choices = Collections.unmodifiableList(producers);
            cycleResource = null;
            notifyListeners();
        }
    }

    private List<CraftAtlasEntry> producers(String resource, String displayName) {
        List<CraftAtlasEntry> producers = new ArrayList<>(graph.producers(resource));
        if(producers.isEmpty() && displayName != null) {
            for(RecipeIngredientCache.RecipeEntry cached : RecipeIngredientCache.findOutputRecipesForItem(displayName)) {
                CraftAtlasEntry entry = snapshot.byRecipe(cached.paginaResource);
                if(entry != null && !producers.contains(entry)) producers.add(entry);
            }
        }
        return producers;
    }

    public void chooseProducer(String recipeResource) {
        for(CraftAtlasEntry choice : choices) if(choice.recipeResource.equals(recipeResource)) {
            activePath.add(choice.recipeResource);
            visit(choice);
            return;
        }
    }

    public void openRequirement(CraftAtlasEntry.Requirement requirement) {
        if(requirement == null) return;
        if(requirement.kind == CraftAtlasEntry.RequirementKind.SKILL ||
                requirement.kind == CraftAtlasEntry.RequirementKind.DISCOVERY || requirement.resource == null) {
            requirementDescription = requirement;
            choices = Collections.emptyList();
            notifyListeners();
        } else {
            openIngredient(requirement.resource, requirement.name);
        }
    }

    public void back() { restore(history.back()); }
    public void forward() { restore(history.forward()); }
    private void restore(CraftAtlasHistory.CardState card) {
        if(card == null) return;
        selected = snapshot.byRecipe(card.recipeResource);
        activePath.clear();
        if(selected != null) activePath.add(selected.recipeResource);
        choices = Collections.emptyList(); cycleResource = null; requirementDescription = null;
        notifyListeners();
    }

    public boolean openCraft() {
        return bridge != null && selected != null && bridge.open(selected.recipeResource, selected.availability);
    }
    public void onCraftWindowOpened() { if(bridge != null) bridge.completed(); }

    private ViewState buildState() {
        return new ViewState(snapshot, results, selected, choices, cycleResource,
                requirementDescription, history.canBack(), history.canForward());
    }

    private void notifyListeners() {
        ViewState state = buildState();
        for(Listener listener : listeners) listener.changed(state);
    }
}
