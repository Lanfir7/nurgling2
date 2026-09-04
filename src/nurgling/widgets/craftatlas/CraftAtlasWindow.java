package nurgling.widgets.craftatlas;

import haven.Button;
import haven.Coord;
import haven.KeyMatch;
import haven.MenuGrid;
import haven.TextEntry;
import haven.UI;
import haven.Widget;
import haven.Window;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.bots.CraftAtlasResourceCollector;
import nurgling.craftatlas.CraftAtlasController;
import nurgling.craftatlas.CraftAtlasEntry;
import nurgling.craftatlas.CraftAtlasMaterialPlanner;
import nurgling.craftatlas.CraftAtlasMaterialSource;
import nurgling.craftatlas.CraftAtlasObservationStore;
import nurgling.craftatlas.CraftAtlasPreferences;
import nurgling.craftatlas.CraftAtlasSearch;
import nurgling.craftatlas.CraftExecutionBridge;
import nurgling.craftatlas.MenuCraftCatalog;
import nurgling.i18n.L10n;
import nurgling.sessions.BotExecutor;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/** Separate crafting encyclopedia. Crafting itself stays in the normal NCraftWindow. */
public class CraftAtlasWindow extends Window {
    private MenuGrid menu;
    private MenuCraftCatalog catalog;
    private final CraftAtlasObservationStore observationStore;
    private final CraftAtlasController controller;
    private final CraftAtlasPreferences preferences;
    private final CraftAtlasController.Listener listener = this::stateChanged;
    private final TextEntry search;
    private final CraftAtlasRecipeList recipeList;
    private final CraftAtlasDetails details;
    private final CraftAtlasRecipeChooser chooser;
    private final TextEntry craftCount;
    private final Button back, forward, favorite, collectResources, openCraft;
    private final Button[] sectionButtons = new Button[CraftAtlasSections.MAIN.size()];
    private final Button[] equipmentButtons = new Button[CraftAtlasSections.EQUIPMENT.size() + 1];
    private String section;
    private int observedMenuRevision = Integer.MIN_VALUE;
    private long observedStoreRevision = Long.MIN_VALUE;
    private boolean subscribed;
    private boolean narrowDetails;
    private Thread collectionThread;

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
        CraftExecutionBridge bridge = new CraftExecutionBridge(resource -> {
            MenuGrid current = this.menu;
            MenuGrid.Pagina page = current == null ? null : current.recipeByResource(resource);
            return page == null ? null : () -> page.button().use(new MenuGrid.Interaction());
        }, System::nanoTime);
        this.controller = new CraftAtlasController(catalog.rebuild(), bridge);

        back = add(new Button(UI.scale(34), "\u2039").action(controller::back));
        forward = add(new Button(UI.scale(34), "\u203a").action(controller::forward));
        search = add(new TextEntry(UI.scale(520), "") {
            @Override protected void changed() { super.changed(); applyQuery(); }
        });
        search.tooltip = L10n.get("craft_atlas.search_placeholder");

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
        details = add(new CraftAtlasDetails(UI.scale(620, 600), controller));
        favorite = add(new Button(UI.scale(42), "\u2606").action(this::toggleFavorite));
        craftCount = add(new TextEntry(UI.scale(54), "1") {
            @Override protected void changed() { super.changed(); craftCountChanged(); }
        });
        craftCount.tooltip = L10n.get("craft_atlas.craft_count_hint");
        collectResources = add(new Button(UI.scale(160), L10n.get("craft_atlas.collect_resources"))
                .action(this::collectResources));
        openCraft = add(new Button(UI.scale(170), L10n.get("craft_atlas.open_craft")).action(controller::openCraft));
        openCraft.tooltip = L10n.get("craft_atlas.normal_craft_hint");
        back.tooltip = L10n.get("craft_atlas.back");
        forward.tooltip = L10n.get("craft_atlas.forward");
        details.setPlanListener(this::refreshCollectionState);
        chooser = add(new CraftAtlasRecipeChooser(UI.scale(360, 240), controller));
        chooser.hide();
        applyLayout();
        applyQuery();
    }

    public CraftAtlasController controller() { return controller; }
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
        savePreferences();
        super.destroy();
    }

    @Override public void show() {
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
        if((menu != null && observedMenuRevision != menu.pagseq) || observedStoreRevision != observationStore.revision())
            refreshCatalog();
        if(collectionThread != null && !collectionThread.isAlive()) {
            collectionThread = null;
            details.refreshMaterials();
        }
    }

    private void refreshCatalog() {
        observedMenuRevision = menu == null ? 0 : menu.pagseq;
        observedStoreRevision = observationStore.revision();
        controller.replaceSnapshot(catalog.rebuild());
    }

    private void selectSection(String value) {
        section = value;
        preferences.lastSection = value;
        narrowDetails = false;
        applyQuery();
        applyLayout();
    }

    private void applyQuery() {
        CraftAtlasSearch.Query.Builder query = CraftAtlasSearch.Query.builder().text(search == null ? "" : search.text());
        String category = CraftAtlasSections.category(section);
        if(category != null) query.category(category);
        if("favorites".equals(section)) query.restrictTo(preferences.favorites);
        if("recent".equals(section)) query.restrictTo(new LinkedHashSet<>(preferences.recent));
        controller.setQuery(query.build());
    }

    private void stateChanged(CraftAtlasController.ViewState state) {
        recipeList.setState(state);
        details.setState(state);
        back.disable(!state.canBack);
        forward.disable(!state.canForward);
        openCraft.disable(state.selected == null || state.selected.availability != CraftAtlasEntry.Availability.OPEN);
        refreshCollectionState();
        boolean starred = state.selected != null && preferences.favorites.contains(state.selected.recipeResource);
        favorite.change(starred ? "\u2605" : "\u2606");
        if(state.selected != null) {
            preferences.recordRecent(state.selected.recipeResource);
            CraftAtlasLayout current = layoutFor(csz(), uiScale());
            if(current.detailsAsPage) { narrowDetails = true; applyLayout(); }
        }
        chooser.setChoices(state.choices);
        if(!state.choices.isEmpty()) chooser.setfocusctl(true);
    }

    private void toggleFavorite() {
        CraftAtlasEntry entry = controller.state().selected;
        if(entry == null) return;
        if(!preferences.favorites.remove(entry.recipeResource)) preferences.favorites.add(entry.recipeResource);
        if("favorites".equals(section)) applyQuery(); else stateChanged(controller.state());
        savePreferences();
    }

    private void craftCountChanged() {
        Integer value = parseCraftCount(craftCount.text());
        if(value != null) details.setCraftCount(value);
        refreshCollectionState();
    }

    private void collectResources() {
        Integer count = parseCraftCount(craftCount.text());
        if(count == null) {
            showError(L10n.get("craft_atlas.collect_bad_count"));
            return;
        }
        details.setCraftCount(count);
        details.refreshMaterials();
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
        } catch(IllegalArgumentException error) {
            showError(error.getMessage());
        }
        refreshCollectionState();
    }

    private void refreshCollectionState() {
        if(collectResources == null || craftCount == null) return;
        CraftAtlasMaterialSource.Snapshot snapshot = details.materialSnapshot();
        CraftAtlasMaterialPlanner.Plan plan = details.materialPlan();
        collectResources.disable(collectionThread != null && collectionThread.isAlive()
                || parseCraftCount(craftCount.text()) == null || snapshot == null || !snapshot.collectible
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

    private void applyLayout() {
        Coord content = csz();
        CraftAtlasLayout layout = layoutFor(content, uiScale());
        back.move(UI.scale(8, 12)); forward.move(UI.scale(48, 12));
        search.move(UI.scale(96, 12)); search.resize(Math.max(UI.scale(220), content.x - UI.scale(112)));
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
        CraftAtlasLayout.Rect[] controls = CraftAtlasLayout.footerControls(layout.footer,
                UI.scale(42), UI.scale(54), UI.scale(160), UI.scale(170),
                UI.scale(8), UI.scale(12));
        favorite.resize(Coord.of(controls[0].w, favorite.sz.y));
        craftCount.resize(controls[1].w);
        collectResources.resize(Coord.of(controls[2].w, collectResources.sz.y));
        openCraft.resize(Coord.of(controls[3].w, openCraft.sz.y));
        favorite.move(Coord.of(controls[0].x, layout.footer.y + Math.max(0, (layout.footer.h - favorite.sz.y) / 2)));
        craftCount.move(Coord.of(controls[1].x, layout.footer.y + Math.max(0, (layout.footer.h - craftCount.sz.y) / 2)));
        collectResources.move(Coord.of(controls[2].x,
                layout.footer.y + Math.max(0, (layout.footer.h - collectResources.sz.y) / 2)));
        openCraft.move(Coord.of(controls[3].x, layout.footer.y + Math.max(0, (layout.footer.h - openCraft.sz.y) / 2)));
        favorite.visible = details.visible;
        craftCount.visible = details.visible;
        collectResources.visible = details.visible;
        openCraft.visible = details.visible;
        chooser.move(Coord.of(Math.max(0, (content.x - chooser.sz.x) / 2),
                Math.max(layout.header.h, (content.y - chooser.sz.y) / 2)));
        chooser.raise();
    }

    static CraftAtlasLayout layoutFor(Coord content, double scale) {
        return CraftAtlasLayout.compute(content.x, content.y, scale);
    }

    private double uiScale() { return UI.scale(1000) / 1000.0; }

    @Override public boolean keydown(KeyDownEvent ev) {
        if(ev.code == KeyEvent.VK_F && (ev.mods & KeyMatch.C) != 0) { setfocus(search); return true; }
        if(ev.code == KeyEvent.VK_ESCAPE) {
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
