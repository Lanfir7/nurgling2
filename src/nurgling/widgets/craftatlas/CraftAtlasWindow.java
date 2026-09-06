package nurgling.widgets.craftatlas;

import haven.Button;
import haven.Coord;
import haven.GOut;
import haven.KeyMatch;
import haven.Loading;
import haven.MenuGrid;
import haven.TextEntry;
import haven.Text;
import haven.UI;
import haven.Widget;
import haven.Window;
import nurgling.NGameUI;
import nurgling.NWindowDeco;
import nurgling.NUtils;
import nurgling.actions.bots.CraftAtlasResourceCollector;
import nurgling.craftatlas.CraftAtlasController;
import nurgling.craftatlas.CraftAtlasEntry;
import nurgling.craftatlas.CraftAtlasMaterialPlanner;
import nurgling.craftatlas.CraftAtlasMaterialSource;
import nurgling.craftatlas.CraftAtlasObservationStore;
import nurgling.craftatlas.CraftAtlasPreferences;
import nurgling.craftatlas.CraftAtlasRecipeProbe;
import nurgling.craftatlas.CraftAtlasSearch;
import nurgling.craftatlas.CraftExecutionBridge;
import nurgling.craftatlas.MenuCraftCatalog;
import nurgling.i18n.L10n;
import nurgling.sessions.BotExecutor;
import monitoring.NGlobalSearchItems;

import java.awt.event.KeyEvent;
import java.awt.Color;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/** Separate crafting encyclopedia. Crafting itself stays in the normal NCraftWindow. */
public class CraftAtlasWindow extends Window {
    @Override protected Deco makedeco() {
        return new NWindowDeco(false).freeformResize(UI.scale(620, 420));
    }

    private MenuGrid menu;
    private MenuCraftCatalog catalog;
    private final CraftAtlasObservationStore observationStore;
    private final CraftAtlasController controller;
    private final CraftAtlasPreferences preferences;
    private final CraftAtlasRecipeProbe recipeProbe = new CraftAtlasRecipeProbe();
    private final CraftAtlasController.Listener listener = this::stateChanged;
    private final TextEntry search;
    private final CraftAtlasRecipeList recipeList;
    private final CraftAtlasDetails details;
    private final CraftAtlasPaneDivider paneDivider;
    private final CraftAtlasRecipeChooser chooser;
    private final CraftAtlasSearchHistory searchHistory;
    private final TextEntry craftCount;
    private final Button help, favoriteFilterButton, recentFilterButton;
    private final FavoriteStar favorite;
    private final Button collectResources, openCraft;
    private final Button[] sectionButtons = new Button[CraftAtlasSections.MAIN.size()];
    private final Button[] equipmentButtons = new Button[CraftAtlasSections.EQUIPMENT.size() + 1];
    private String section;
    private int observedMenuRevision = Integer.MIN_VALUE;
    private long observedStoreRevision = Long.MIN_VALUE;
    private boolean subscribed;
    private boolean narrowDetails;
    private boolean collectionPreparing;
    private long collectionRequestToken;
    private CraftAtlasEntry selectedEntry;
    private Thread collectionThread;
    private CraftAtlasSearchHelp searchHelp;

    public CraftAtlasWindow(MenuGrid menu) {
        this(menu, CraftAtlasPreferences.loadProfile());
    }

    private CraftAtlasWindow(MenuGrid menu, CraftAtlasPreferences preferences) {
        super(Coord.of(preferences.windowW > 0 ? preferences.windowW : UI.scale(1160),
                preferences.windowH > 0 ? preferences.windowH : UI.scale(700)), L10n.get("craft_atlas.title"));
        this.menu = menu;
        this.observationStore = CraftAtlasObservationStore.current();
        this.catalog = new MenuCraftCatalog(menu, observationStore);
        this.preferences = preferences;
        this.section = preferences.lastSection;
        if("favorites".equals(section)) { section = "all"; preferences.favoriteFilter = true; }
        if("recent".equals(section)) { section = "all"; preferences.recentFilter = true; }
        CraftExecutionBridge bridge = new CraftExecutionBridge(resource -> {
            MenuGrid current = this.menu;
            MenuGrid.Pagina page = current == null ? null : current.recipeByResource(resource);
            return page == null ? null : () -> page.button().use(new MenuGrid.Interaction());
        }, System::nanoTime);
        this.controller = new CraftAtlasController(catalog.rebuild(), bridge);

        help = add(new Button(UI.scale(34), "?").action(this::showSearchHelp));
        search = add(new TextEntry(UI.scale(520), "") {
            @Override protected void changed() {
                super.changed();
                if(searchHistory != null) searchHistory.hide();
                applyQuery();
            }
            @Override public boolean mousedown(MouseDownEvent ev) {
                boolean handled = super.mousedown(ev);
                if(ev.b == 1) showSearchHistory();
                return handled;
            }
            @Override public boolean keydown(KeyDownEvent ev) {
                if(ev.code == KeyEvent.VK_ENTER) { rememberSearch(); return true; }
                if(ev.code == KeyEvent.VK_ESCAPE && searchHistory != null && searchHistory.visible) {
                    searchHistory.hide();
                    return true;
                }
                return super.keydown(ev);
            }
        });
        search.autofocus = false;
        search.tooltip = L10n.get("craft_atlas.search_placeholder");
        favoriteFilterButton = add(new Button(UI.scale(120), "").action(() -> toggleHeaderFilter(true)));
        recentFilterButton = add(new Button(UI.scale(120), "").action(() -> toggleHeaderFilter(false)));
        updateFilterButtons();

        for(int i = 0; i < CraftAtlasSections.MAIN.size(); i++) {
            final String value = CraftAtlasSections.MAIN.get(i);
            sectionButtons[i] = add(new Button(UI.scale(170), L10n.get("craft_atlas.section." + value))
                    .action(() -> selectSection(value)));
        }
        equipmentButtons[0] = add(new Button(UI.scale(170), L10n.get("craft_atlas.section.back"))
                .action(() -> selectSection("all")));
        for(int i = 0; i < CraftAtlasSections.EQUIPMENT.size(); i++) {
            final String value = CraftAtlasSections.EQUIPMENT.get(i);
            equipmentButtons[i + 1] = add(new Button(UI.scale(170),
                    L10n.get("craft_atlas.section." + value)).action(() -> selectSection(value)));
        }

        recipeList = add(new CraftAtlasRecipeList(UI.scale(330, 600), controller));
        recipeList.setSection(section);
        details = add(new CraftAtlasDetails(UI.scale(620, 600), controller, preferences, this::savePreferences));
        paneDivider = add(new CraftAtlasPaneDivider(this::movePaneDivider, this::savePreferences));
        paneDivider.tooltip = L10n.get("craft_atlas.resize_table_hint");
        favorite = add(new FavoriteStar());
        craftCount = add(new TextEntry(UI.scale(54), "1") {
            @Override protected void changed() { super.changed(); craftCountChanged(); }
        });
        craftCount.tooltip = L10n.get("craft_atlas.craft_count_hint");
        collectResources = add(new Button(UI.scale(160), L10n.get("craft_atlas.collect_resources"))
                .action(this::collectResources));
        openCraft = add(new Button(UI.scale(170), L10n.get("craft_atlas.open_craft")).action(this::openSelectedCraft));
        openCraft.tooltip = L10n.get("craft_atlas.normal_craft_hint");
        details.setPlanListener(this::refreshCollectionState);
        chooser = add(new CraftAtlasRecipeChooser(UI.scale(360, 240), controller));
        chooser.hide();
        searchHistory = add(new CraftAtlasSearchHistory(UI.scale(520), this::applySearchExample));
        searchHistory.setItems(preferences.searchHistory);
        searchHistory.hide();
        releaseSearchFocus();
        applyLayout();
        applyQuery();
    }

    public CraftAtlasController controller() { return controller; }
    public CraftAtlasRecipeProbe recipeProbe() { return recipeProbe; }
    public CraftAtlasRecipeProbe.Claim claimRecipeProbe(String windowName) { return recipeProbe.claim(windowName); }
    public void onCraftWindowOpened() { controller.onCraftWindowOpened(); }
    public void setMenu(MenuGrid value) {
        if(menu == value) return;
        menu = value;
        catalog = new MenuCraftCatalog(value, observationStore);
        observedMenuRevision = Integer.MIN_VALUE;
        refreshCatalog();
    }

    @Override protected void added() {
        super.added();
        if(preferences.windowX >= 0 && preferences.windowY >= 0)
            c = Coord.of(preferences.windowX, preferences.windowY);
        if(!subscribed) { controller.addListener(listener); subscribed = true; }
        stateChanged(controller.state());
    }

    @Override public void destroy() {
        if(subscribed) { controller.removeListener(listener); subscribed = false; }
        if(searchHelp != null) { searchHelp.reqdestroy(); searchHelp = null; }
        savePreferences();
        super.destroy();
    }

    @Override public void show() {
        releaseSearchFocus();
        refreshCatalog();
        if(parent != null) {
            Coord maximumOuter = parent.sz.sub(UI.scale(20, 20)).max(Coord.of(1, 1));
            Coord frame = sz.sub(csz());
            Coord maximumContent = maximumOuter.sub(frame).max(Coord.of(1, 1));
            Coord content = csz();
            if(content.x > maximumContent.x || content.y > maximumContent.y)
                resize(Coord.of(Math.min(content.x, maximumContent.x), Math.min(content.y, maximumContent.y)));
        }
        if(parent != null && (c.x < 0 || c.y < 0 || c.x + sz.x > parent.sz.x || c.y + sz.y > parent.sz.y))
            c = parent.sz.sub(sz).div(2).max(Coord.z);
        super.show();
    }

    @Override public void tick(double dt) {
        super.tick(dt);
        requestRecipeProbe(controller.state().selected);
        if((menu != null && observedMenuRevision != menu.pagseq) || observedStoreRevision != observationStore.revision())
            refreshCatalog();
        if(collectionThread == null)
            details.refreshMaterialsIfDue(System.nanoTime(), NGlobalSearchItems.storageRevision());
        if(collectionThread != null && !collectionThread.isAlive()) {
            collectionThread = null;
            details.refreshMaterialsAsync(NGlobalSearchItems.storageRevision(), null);
        }
    }

    private void refreshCatalog() {
        observedMenuRevision = menu == null ? 0 : menu.pagseq;
        observedStoreRevision = observationStore.revision();
        controller.replaceSnapshot(catalog.rebuild());
    }

    private void selectSection(String value) {
        section = value;
        recipeList.setSection(value);
        preferences.lastSection = value;
        narrowDetails = false;
        applyQuery();
        applyLayout();
    }

    private void toggleHeaderFilter(boolean favorites) {
        if(favorites) preferences.favoriteFilter = !preferences.favoriteFilter;
        else preferences.recentFilter = !preferences.recentFilter;
        updateFilterButtons();
        applyQuery();
        savePreferences();
    }

    private void updateFilterButtons() {
        if(favoriteFilterButton != null) favoriteFilterButton.change(
                (preferences.favoriteFilter ? "\u2713  " : "") + L10n.get("craft_atlas.filter.favorites"));
        if(recentFilterButton != null) recentFilterButton.change(
                (preferences.recentFilter ? "\u2713  " : "") + L10n.get("craft_atlas.filter.recent"));
    }

    private void applyQuery() {
        CraftAtlasSearch.Query.Builder query = CraftAtlasSearch.Query.builder().text(search == null ? "" : search.text());
        recipeList.setPreserveSourceOrder(preferences.recentFilter);
        String category = CraftAtlasSections.category(section);
        if(category != null) query.category(category);
        Set<String> restricted = null;
        if(preferences.favoriteFilter) restricted = new LinkedHashSet<>(preferences.favorites);
        if(preferences.recentFilter) {
            Set<String> recent = new LinkedHashSet<>(preferences.recent);
            if(restricted == null) restricted = recent;
            else restricted.retainAll(recent);
            query.preferredOrder(preferences.recent);
        }
        if(restricted != null) query.restrictTo(restricted);
        controller.setQuery(query.build());
    }

    private void stateChanged(CraftAtlasController.ViewState state) {
        if(selectedEntry != state.selected) {
            selectedEntry = state.selected;
            collectionRequestToken++;
            collectionPreparing = false;
        }
        recipeList.setState(state);
        details.setState(state);
        openCraft.disable(state.selected == null || state.selected.availability != CraftAtlasEntry.Availability.OPEN);
        refreshCollectionState();
        boolean starred = state.selected != null && preferences.favorites.contains(state.selected.recipeResource);
        favorite.setStarred(starred);
        positionFavorite();
        if(state.selected != null) {
            CraftAtlasLayout current = layoutFor(csz(), uiScale(), section);
            if(current.detailsAsPage) { narrowDetails = true; applyLayout(); }
        }
        chooser.setChoices(state.choices);
        if(!state.choices.isEmpty()) chooser.setfocusctl(true);
        requestRecipeProbe(state.selected);
    }

    private void requestRecipeProbe(CraftAtlasEntry entry) {
        if(entry == null || entry.availability != CraftAtlasEntry.Availability.OPEN || menu == null) return;
        MenuGrid.Pagina page = menu.recipeByResource(entry.recipeResource);
        if(page == null) return;
        try {
            recipeProbe.request(entry.recipeResource, entry.displayName,
                    () -> page.button().use(new MenuGrid.Interaction()));
        } catch(Loading ignored) {
            /* The menu page is still loading; tick() will retry. */
        }
    }

    private void toggleFavorite() {
        CraftAtlasEntry entry = controller.state().selected;
        if(entry == null) return;
        if(!preferences.favorites.remove(entry.recipeResource)) preferences.favorites.add(entry.recipeResource);
        if(preferences.favoriteFilter) applyQuery(); else stateChanged(controller.state());
        savePreferences();
    }

    private final class FavoriteStar extends Widget {
        private boolean starred;

        private FavoriteStar() {
            super(Coord.of(UI.scale(28), UI.scale(28)));
            tooltip = L10n.get("craft_atlas.filter.favorites");
        }

        private void setStarred(boolean value) { starred = value; }

        @Override public void draw(GOut g) {
            g.chcolor(starred ? new Color(232, 183, 72) : new Color(180, 187, 187));
            g.atext(starred ? "\u2605" : "\u2606", Coord.of(sz.x / 2, sz.y / 2), 0.5, 0.5);
            g.chcolor();
        }

        @Override public boolean mousedown(MouseDownEvent ev) {
            if(ev.b != 1) return false;
            toggleFavorite();
            return true;
        }
    }

    private void openSelectedCraft() {
        CraftAtlasEntry entry = controller.state().selected;
        if(entry != null) recipeProbe.cancel(entry.recipeResource);
        if(controller.openCraft()) recordRecentAction();
    }

    private void recordRecentAction() {
        CraftAtlasEntry entry = controller.state().selected;
        if(entry == null) return;
        preferences.recordRecent(entry.recipeResource);
        if(preferences.recentFilter) applyQuery();
        savePreferences();
    }

    private void craftCountChanged() {
        if(collectionPreparing) {
            collectionRequestToken++;
            collectionPreparing = false;
        }
        Integer value = parseCraftCount(craftCount.text());
        if(value != null && details.supportsCraftCount(value)) details.setCraftCount(value);
        refreshCollectionState();
    }

    private void collectResources() {
        Integer count = parseCraftCount(craftCount.text());
        if(count == null || !details.supportsCraftCount(count)) {
            showError(L10n.get("craft_atlas.collect_bad_count"));
            return;
        }
        details.setCraftCount(count);
        CraftAtlasEntry selected = controller.state().selected;
        String recipeResource = selected == null ? null : selected.recipeResource;
        long requestToken = ++collectionRequestToken;
        collectionPreparing = true;
        refreshCollectionState();
        details.refreshMaterialsAsync(NGlobalSearchItems.storageRevision(), fresh -> {
            if(requestToken != collectionRequestToken) return;
            collectionPreparing = false;
            CraftAtlasEntry current = controller.state().selected;
            Integer currentCount = parseCraftCount(craftCount.text());
            if(!fresh || current == null || !current.recipeResource.equals(recipeResource)
                    || currentCount == null || currentCount.intValue() != count.intValue()) {
                if(!fresh && current != null && current.recipeResource.equals(recipeResource))
                    showError(L10n.get("craft_atlas.collect_unavailable"));
                refreshCollectionState();
                return;
            }
            startResourceCollection();
        });
    }

    private void startResourceCollection() {
        CraftAtlasMaterialSource.Snapshot snapshot = details.materialSnapshot();
        CraftAtlasMaterialPlanner.Plan plan = details.materialPlan();
        if(snapshot == null || !snapshot.collectible) {
            showError(L10n.get("craft_atlas.collect_unavailable"));
            return;
        }
        if(plan == null || !plan.complete) {
            showError(shortageMessage(plan));
            return;
        }
        try {
            collectionThread = BotExecutor.runAsync("CraftAtlasResourceCollector",
                    new CraftAtlasResourceCollector(plan, snapshot.storageByCandidateId));
            recordRecentAction();
        } catch(IllegalArgumentException error) {
            showError(error.getMessage());
        }
        refreshCollectionState();
    }

    private void refreshCollectionState() {
        if(collectResources == null || craftCount == null) return;
        CraftAtlasMaterialSource.Snapshot snapshot = details.materialSnapshot();
        CraftAtlasMaterialPlanner.Plan plan = details.materialPlan();
        Integer count = parseCraftCount(craftCount.text());
        collectResources.disable(collectionPreparing || collectionThread != null && collectionThread.isAlive()
                || count == null || !details.supportsCraftCount(count)
                || snapshot == null || !snapshot.collectible
                || plan == null || !plan.complete);
    }

    private String shortageMessage(CraftAtlasMaterialPlanner.Plan plan) {
        if(plan != null) for(CraftAtlasMaterialPlanner.SlotPlan slot : plan.slots) {
            if(slot.missing <= 0) continue;
            String name = Integer.toString(slot.slotIndex + 1);
            CraftAtlasMaterialSource.Snapshot snapshot = details.materialSnapshot();
            if(snapshot != null && slot.slotIndex < snapshot.slots.size()) {
                java.util.List<String> allowed = snapshot.slots.get(slot.slotIndex).allowedMaterials;
                if(!allowed.isEmpty()) name = allowed.get(0);
            }
            return L10n.get("craft_atlas.collect_missing")
                    .replace("{0}", name).replace("{1}", Integer.toString(slot.missing));
        }
        return L10n.get("craft_atlas.collect_unavailable");
    }

    private void showError(String message) {
        NGameUI gui = NUtils.getGameUI();
        if(gui != null) gui.error(message);
    }

    private void rememberSearch() {
        preferences.recordSearch(search.text());
        searchHistory.setItems(preferences.searchHistory);
        searchHistory.hide();
        savePreferences();
    }

    private void showSearchHistory() {
        if(searchHistory == null || preferences.searchHistory.isEmpty()) return;
        searchHistory.setItems(preferences.searchHistory);
        searchHistory.show();
        searchHistory.raise();
    }

    private void hideSearchHistoryOutside(Coord point) {
        if(searchHistory == null || !searchHistory.visible) return;
        if(point.isect(search.c, search.sz) || point.isect(searchHistory.c, searchHistory.sz)) return;
        searchHistory.hide();
    }

    private void releaseSearchFocus() {
        if(search == null) return;
        search.setcanfocus(false);
        search.setcanfocus(true);
        search.autofocus = false;
    }

    private void applySearchExample(String query) {
        search.settext(query);
        preferences.recordSearch(query);
        searchHistory.setItems(preferences.searchHistory);
        searchHistory.hide();
        applyQuery();
        savePreferences();
    }

    private void showSearchHelp() {
        if(searchHelp != null) { searchHelp.raise(); return; }
        NGameUI gui = NUtils.getGameUI();
        if(gui == null) return;
        searchHelp = new CraftAtlasSearchHelp(section, this::applySearchExample, () -> searchHelp = null);
        gui.add(searchHelp, gui.sz.sub(searchHelp.sz).div(2).max(Coord.z));
    }

    static Integer parseCraftCount(String text) {
        try {
            int value = Integer.parseInt(text == null ? "" : text.trim());
            return value > 0 ? value : null;
        } catch(NumberFormatException ignored) {
            return null;
        }
    }

    private void savePreferences() {
        Coord content = csz();
        preferences.windowW = content.x; preferences.windowH = content.y;
        preferences.windowX = c.x; preferences.windowY = c.y;
        try { preferences.saveProfile(); } catch(IOException e) { System.err.println("Unable to save Craft Atlas preferences: " + e.getMessage()); }
    }

    @Override public void resize(Coord size) { super.resize(size); if(recipeList != null) applyLayout(); }

    @Override public boolean mousedown(MouseDownEvent ev) {
        hideSearchHistoryOutside(ev.c);
        return super.mousedown(ev);
    }

    @Override public void hide() {
        if(searchHistory != null) searchHistory.hide();
        releaseSearchFocus();
        super.hide();
    }

    private void applyLayout() {
        Coord content = csz();
        CraftAtlasLayout layout = layoutFor(content, uiScale(), section);
        int buttonY = UI.scale(8), searchY = UI.scale(12);
        help.move(Coord.of(UI.scale(8), buttonY));
        int searchX = UI.scale(48), gap = UI.scale(8), filterWidth = UI.scale(120);
        int oldSearchWidth = Math.max(UI.scale(220), content.x - UI.scale(112));
        int availableSearchWidth = content.x - searchX - filterWidth * 2 - gap * 3;
        int searchWidth = Math.max(UI.scale(160), Math.min(oldSearchWidth / 2, availableSearchWidth));
        search.move(Coord.of(searchX, searchY));
        search.resize(searchWidth);
        favoriteFilterButton.move(Coord.of(searchX + searchWidth + gap, buttonY));
        recentFilterButton.move(Coord.of(searchX + searchWidth + gap * 2 + filterWidth, buttonY));
        favoriteFilterButton.resize(Coord.of(filterWidth, favoriteFilterButton.sz.y));
        recentFilterButton.resize(Coord.of(filterWidth, recentFilterButton.sz.y));
        searchHistory.move(Coord.of(searchX, searchY + search.sz.y + UI.scale(2)));
        searchHistory.setWidth(searchWidth);
        int sideY = layout.sidebar.y + UI.scale(8);
        boolean equipmentMenu = CraftAtlasSections.isEquipment(section);
        for(int i = 0; i < sectionButtons.length; i++) {
            sectionButtons[i].move(Coord.of(layout.sidebar.x + UI.scale(7), sideY + i * UI.scale(38)));
            sectionButtons[i].resize(Coord.of(Math.max(UI.scale(100), layout.sidebar.w - UI.scale(14)), sectionButtons[i].sz.y));
            sectionButtons[i].visible = !equipmentMenu;
        }
        for(int i = 0; i < equipmentButtons.length; i++) {
            equipmentButtons[i].move(Coord.of(layout.sidebar.x + UI.scale(7), sideY + i * UI.scale(38)));
            equipmentButtons[i].resize(Coord.of(Math.max(UI.scale(100), layout.sidebar.w - UI.scale(14)), equipmentButtons[i].sz.y));
            equipmentButtons[i].visible = equipmentMenu;
        }
        recipeList.move(Coord.of(layout.list.x, layout.list.y)); recipeList.resize(Coord.of(layout.list.w, layout.list.h));
        details.move(Coord.of(layout.details.x, layout.details.y)); details.resize(Coord.of(layout.details.w, layout.details.h));
        int dividerX = layout.list.x + layout.list.w;
        paneDivider.move(Coord.of(dividerX, layout.list.y));
        paneDivider.resize(Coord.of(Math.max(UI.scale(4), layout.details.x - dividerX), layout.list.h));
        if(layout.detailsAsPage && narrowDetails) {
            for(Button button : sectionButtons) button.hide();
            for(Button button : equipmentButtons) button.hide();
            recipeList.hide();
            details.move(Coord.of(0, layout.header.h));
            details.resize(Coord.of(content.x, Math.max(0, layout.footer.y - layout.header.h - UI.scale(8))));
            details.show();
        } else {
            recipeList.show();
            details.visible = !layout.detailsAsPage;
        }
        paneDivider.visible = !layout.detailsAsPage && !narrowDetails;
        CraftAtlasLayout.Rect[] controls = CraftAtlasLayout.footerControls(layout.footer,
                UI.scale(54), UI.scale(160), UI.scale(170),
                UI.scale(8), UI.scale(12));
        craftCount.resize(controls[0].w);
        collectResources.resize(Coord.of(controls[1].w, collectResources.sz.y));
        openCraft.resize(Coord.of(controls[2].w, openCraft.sz.y));
        positionFavorite();
        craftCount.move(Coord.of(controls[0].x, layout.footer.y + Math.max(0, (layout.footer.h - craftCount.sz.y) / 2)));
        collectResources.move(Coord.of(controls[1].x,
                layout.footer.y + Math.max(0, (layout.footer.h - collectResources.sz.y) / 2)));
        openCraft.move(Coord.of(controls[2].x, layout.footer.y + Math.max(0, (layout.footer.h - openCraft.sz.y) / 2)));
        craftCount.visible = details.visible;
        collectResources.visible = details.visible;
        openCraft.visible = details.visible;
        chooser.move(Coord.of(Math.max(0, (content.x - chooser.sz.x) / 2),
                Math.max(layout.header.h, (content.y - chooser.sz.y) / 2)));
        chooser.raise();
        if(searchHistory.visible) searchHistory.raise();
    }

    private void positionFavorite() {
        int titleWidth = selectedEntry == null ? 0 : Text.render(selectedEntry.displayName).sz().x;
        CraftAtlasLayout.Rect bounds = new CraftAtlasLayout.Rect(details.c.x, details.c.y, details.sz.x, details.sz.y);
        CraftAtlasLayout.Rect star = CraftAtlasLayout.favoriteAfterTitle(bounds,
                UI.scale(98), titleWidth, favorite.sz.x, UI.scale(6), UI.scale(10));
        favorite.move(Coord.of(star.x, star.y));
        favorite.visible = details.visible && selectedEntry != null;
    }

    static CraftAtlasLayout layoutFor(Coord content, double scale) {
        return CraftAtlasLayout.compute(content.x, content.y, scale);
    }

    private CraftAtlasLayout layoutFor(Coord content, double scale, String section) {
        return CraftAtlasLayout.compute(content.x, content.y, scale,
                CraftAtlasSections.hasMetricTable(section), requestedListWidth());
    }

    private int requestedListWidth() {
        Integer value = preferences.columnWidths.get("layout.recipe-list");
        return value == null ? -1 : value;
    }

    private void movePaneDivider(int pointerX) {
        Coord content = csz();
        CraftAtlasLayout natural = CraftAtlasLayout.compute(content.x, content.y, uiScale(),
                CraftAtlasSections.hasMetricTable(section));
        if(natural.detailsAsPage) return;
        int requested = pointerX - natural.list.x;
        CraftAtlasLayout adjusted = CraftAtlasLayout.compute(content.x, content.y, uiScale(),
                CraftAtlasSections.hasMetricTable(section), requested);
        preferences.columnWidths.put("layout.recipe-list", adjusted.list.w);
        applyLayout();
    }

    private double uiScale() { return UI.scale(1000) / 1000.0; }

    @Override public boolean keydown(KeyDownEvent ev) {
        if(ev.code == KeyEvent.VK_F && (ev.mods & KeyMatch.C) != 0) { setfocus(search); return true; }
        if(ev.code == KeyEvent.VK_ESCAPE) {
            if(searchHistory.visible) { searchHistory.hide(); return true; }
            if(chooser.visible) { chooser.close(); return true; }
            if(narrowDetails) { narrowDetails = false; applyLayout(); return true; }
            hide(); return true;
        }
        return super.keydown(ev);
    }

    @Override public void wdgmsg(Widget sender, String msg, Object... args) {
        if(sender == this && "close".equals(msg)) { savePreferences(); hide(); return; }
        super.wdgmsg(sender, msg, args);
    }
}
