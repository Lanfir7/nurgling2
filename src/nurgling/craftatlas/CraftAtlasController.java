package nurgling.craftatlas;

import nurgling.tools.RecipeIngredientCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** Owns Atlas search, selection, dependency navigation and browser history. */
public final class CraftAtlasController {
    public interface Listener { void changed(ViewState state); }

    /** Snapshot and its normalized-name index are published together. */
    private static final class Catalog {
        final CraftAtlasSnapshot snapshot;
        final Map<String, List<CraftAtlasEntry>> byName;

        Catalog(CraftAtlasSnapshot snapshot) {
            this.snapshot = snapshot == null
                    ? CraftAtlasSnapshot.of(0, Collections.<CraftAtlasEntry>emptyList()) : snapshot;
            Map<String, List<CraftAtlasEntry>> names = new LinkedHashMap<>();
            for(CraftAtlasEntry entry : this.snapshot.entries)
                names.computeIfAbsent(CraftAtlasSearch.normalize(entry.displayName), key -> new ArrayList<>()).add(entry);
            Map<String, List<CraftAtlasEntry>> immutable = new LinkedHashMap<>();
            for(Map.Entry<String, List<CraftAtlasEntry>> group : names.entrySet())
                immutable.put(group.getKey(), Collections.unmodifiableList(group.getValue()));
            byName = Collections.unmodifiableMap(immutable);
        }
    }

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

    private volatile Catalog catalog;
    private CraftRecipeGraph graph;
    private final CraftAtlasHistory history = new CraftAtlasHistory();
    private final CraftExecutionBridge bridge;
    private final Function<String, Set<RecipeIngredientCache.RecipeEntry>> outputLookup;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private CraftAtlasSearch.Query query = CraftAtlasSearch.Query.text("");
    private List<CraftAtlasEntry> results;
    private CraftAtlasEntry selected;
    private List<CraftAtlasEntry> choices = Collections.emptyList();
    private String cycleResource;
    private CraftAtlasEntry.Requirement requirementDescription;
    private final List<String> activePath = new ArrayList<>();

    public CraftAtlasController(CraftAtlasSnapshot snapshot, CraftExecutionBridge bridge) {
        this(snapshot, bridge, name -> Collections.emptySet());
    }

    public CraftAtlasController(CraftAtlasSnapshot snapshot, CraftExecutionBridge bridge,
            Function<String, Set<RecipeIngredientCache.RecipeEntry>> outputLookup) {
        this.catalog = new Catalog(snapshot);
        this.graph = new CraftRecipeGraph(this.catalog.snapshot);
        this.bridge = bridge;
        this.outputLookup = outputLookup;
        this.results = CraftAtlasSearch.query(this.catalog.snapshot, query);
    }

    public void addListener(Listener listener) { if(listener != null) listeners.add(listener); }
    public void removeListener(Listener listener) { listeners.remove(listener); }
    public ViewState state() { return buildState(); }

    public void replaceSnapshot(CraftAtlasSnapshot value) {
        Catalog replacement = new Catalog(value);
        graph = new CraftRecipeGraph(replacement.snapshot);
        results = CraftAtlasSearch.query(replacement.snapshot, query);
        if(selected != null) selected = replacement.snapshot.byRecipe(selected.recipeResource);
        catalog = replacement;
        notifyListeners();
    }

    public void setQuery(CraftAtlasSearch.Query value) {
        query = value == null ? CraftAtlasSearch.Query.text("") : value;
        results = CraftAtlasSearch.query(catalog.snapshot, query);
        notifyListeners();
    }

    public void select(String recipeResource) {
        CraftAtlasEntry entry = catalog.snapshot.byRecipe(recipeResource);
        if(entry == null) return;
        activePath.clear();
        activePath.add(entry.recipeResource);
        visit(entry);
    }

    /** Whether the Atlas has exactly one recipe with this display name. */
    public boolean hasUniqueExactName(String displayName) {
        return uniqueExactName(catalog, displayName) != null;
    }

    /** Select a recipe opened from another UI, falling back to one unambiguous exact name. */
    public boolean selectExact(String recipeResource, String displayName) {
        Catalog current = catalog;
        CraftAtlasEntry entry = recipeResource == null ? null : current.snapshot.byRecipe(recipeResource);
        if(entry == null) entry = uniqueExactName(current, displayName);
        if(entry == null) return false;
        activePath.clear();
        activePath.add(entry.recipeResource);
        visit(entry);
        return true;
    }

    private static CraftAtlasEntry uniqueExactName(Catalog current, String displayName) {
        String wanted = CraftAtlasSearch.normalize(displayName);
        if(wanted.isEmpty()) return null;
        List<CraftAtlasEntry> matches = current.byName.get(wanted);
        return matches != null && matches.size() == 1 ? matches.get(0) : null;
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
        Catalog current = catalog;
        List<CraftAtlasEntry> producers = new ArrayList<>(graph.producers(resource));
        if(producers.isEmpty() && displayName != null) {
            Set<RecipeIngredientCache.RecipeEntry> cachedOutputs = new LinkedHashSet<>(
                    RecipeIngredientCache.peekOutputRecipesForItem(displayName));
            Set<RecipeIngredientCache.RecipeEntry> loaded = outputLookup.apply(displayName);
            if(loaded != null) cachedOutputs.addAll(loaded);
            for(RecipeIngredientCache.RecipeEntry cached : cachedOutputs) {
                CraftAtlasEntry entry = current.snapshot.byRecipe(cached.paginaResource);
                if(entry != null && !producers.contains(entry)) producers.add(entry);
            }
        }
        if(producers.isEmpty() && displayName != null) {
            List<CraftAtlasEntry> named = current.byName.get(CraftAtlasSearch.normalize(displayName));
            if(named != null) producers.addAll(named);
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
        selected = catalog.snapshot.byRecipe(card.recipeResource);
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
        return new ViewState(catalog.snapshot, results, selected, choices, cycleResource,
                requirementDescription, history.canBack(), history.canForward());
    }

    private void notifyListeners() {
        ViewState state = buildState();
        for(Listener listener : listeners) listener.changed(state);
    }
}
