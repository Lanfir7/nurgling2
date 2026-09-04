package nurgling.widgets.craftatlas;

import haven.Button;
import haven.Coord;
import haven.KeyMatch;
import haven.MenuGrid;
import haven.TextEntry;
import haven.UI;
import haven.Widget;
import haven.Window;
import nurgling.craftatlas.CraftAtlasController;
import nurgling.craftatlas.CraftAtlasEntry;
import nurgling.craftatlas.CraftAtlasObservationStore;
import nurgling.craftatlas.CraftAtlasPreferences;
import nurgling.craftatlas.CraftAtlasSearch;
import nurgling.craftatlas.CraftExecutionBridge;
import nurgling.craftatlas.MenuCraftCatalog;
import nurgling.i18n.L10n;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/** Separate crafting encyclopedia. Crafting itself stays in the normal NCraftWindow. */
public class CraftAtlasWindow extends Window {
    private final MenuGrid menu;
    private final MenuCraftCatalog catalog;
    private final CraftAtlasController controller;
    private final CraftAtlasPreferences preferences;
    private final CraftAtlasController.Listener listener = this::stateChanged;
    private final TextEntry search;
    private final CraftAtlasRecipeList recipeList;
    private final CraftAtlasDetails details;
    private final CraftAtlasRecipeChooser chooser;
    private final Button back, forward, favorite, openCraft;
    private final Button[] sectionButtons = new Button[5];
    private String section;
    private int observedMenuRevision = Integer.MIN_VALUE;
    private boolean subscribed;
    private boolean narrowDetails;

    public CraftAtlasWindow(MenuGrid menu) {
        super(UI.scale(1160, 700), L10n.get("craft_atlas.title"));
        this.menu = menu;
        this.catalog = new MenuCraftCatalog(menu, CraftAtlasObservationStore.current());
        this.preferences = CraftAtlasPreferences.loadProfile();
        this.section = preferences.lastSection;
        CraftExecutionBridge bridge = new CraftExecutionBridge(menu);
        this.controller = new CraftAtlasController(catalog.rebuild(), bridge);

        back = add(new Button(UI.scale(34), "‹").action(controller::back));
        forward = add(new Button(UI.scale(34), "›").action(controller::forward));
        search = add(new TextEntry(UI.scale(520), "") {
            @Override protected void changed() { super.changed(); applyQuery(); }
        });
        search.tooltip = L10n.get("craft_atlas.search_placeholder");

        String[] sections = {"all", "favorites", "recent", "gildings", "foods"};
        for(int i = 0; i < sections.length; i++) {
            final String value = sections[i];
            sectionButtons[i] = add(new Button(UI.scale(170), L10n.get("craft_atlas.section." + value))
                    .action(() -> selectSection(value)));
        }

        recipeList = add(new CraftAtlasRecipeList(UI.scale(330, 600), controller));
        details = add(new CraftAtlasDetails(UI.scale(620, 600), controller));
        favorite = add(new Button(UI.scale(42), "☆").action(this::toggleFavorite));
        openCraft = add(new Button(UI.scale(170), L10n.get("craft_atlas.open_craft")).action(controller::openCraft));
        chooser = add(new CraftAtlasRecipeChooser(UI.scale(360, 240), controller));
        chooser.hide();
        applyLayout();
        applyQuery();
    }

    public CraftAtlasController controller() { return controller; }
    public void onCraftWindowOpened() { controller.onCraftWindowOpened(); }

    @Override protected void added() {
        super.added();
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
        if(parent != null && (c.x < 0 || c.y < 0 || c.x + sz.x > parent.sz.x || c.y + sz.y > parent.sz.y))
            c = parent.sz.sub(sz).div(2).max(Coord.z);
        super.show();
    }

    @Override public void tick(double dt) {
        super.tick(dt);
        if(menu != null && observedMenuRevision != menu.pagseq) refreshCatalog();
    }

    private void refreshCatalog() {
        observedMenuRevision = menu == null ? 0 : menu.pagseq;
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
        if("gildings".equals(section) || "foods".equals(section)) query.category(section);
        if("favorites".equals(section)) query.restrictTo(preferences.favorites);
        if("recent".equals(section)) query.restrictTo(new LinkedHashSet<>(preferences.recent));
        controller.setQuery(query.build());
    }

    private void stateChanged(CraftAtlasController.ViewState state) {
        recipeList.setState(state);
        details.setEntry(state.selected);
        back.disable(!state.canBack);
        forward.disable(!state.canForward);
        openCraft.disable(state.selected == null || state.selected.availability != CraftAtlasEntry.Availability.OPEN);
        boolean starred = state.selected != null && preferences.favorites.contains(state.selected.recipeResource);
        favorite.change(starred ? "★" : "☆");
        if(state.selected != null) {
            preferences.recordRecent(state.selected.recipeResource);
            CraftAtlasLayout current = CraftAtlasLayout.compute(sz.x, sz.y, 1.0);
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

    private void savePreferences() {
        preferences.windowW = sz.x; preferences.windowH = sz.y;
        preferences.windowX = c.x; preferences.windowY = c.y;
        try { preferences.saveProfile(); } catch(IOException e) { System.err.println("Unable to save Craft Atlas preferences: " + e.getMessage()); }
    }

    @Override public void resize(Coord size) { super.resize(size); if(recipeList != null) applyLayout(); }

    private void applyLayout() {
        CraftAtlasLayout layout = CraftAtlasLayout.compute(sz.x, sz.y, 1.0);
        back.move(UI.scale(8, 12)); forward.move(UI.scale(48, 12));
        search.move(UI.scale(96, 12)); search.resize(Math.max(UI.scale(220), sz.x - UI.scale(112)));
        int sideY = layout.sidebar.y + UI.scale(8);
        for(int i = 0; i < sectionButtons.length; i++) {
            sectionButtons[i].move(Coord.of(layout.sidebar.x + UI.scale(7), sideY + i * UI.scale(38)));
            sectionButtons[i].resize(Coord.of(Math.max(UI.scale(100), layout.sidebar.w - UI.scale(14)), sectionButtons[i].sz.y));
            sectionButtons[i].show();
        }
        recipeList.move(Coord.of(layout.list.x, layout.list.y)); recipeList.resize(Coord.of(layout.list.w, layout.list.h));
        details.move(Coord.of(layout.details.x, layout.details.y)); details.resize(Coord.of(layout.details.w, layout.details.h));
        if(layout.detailsAsPage && narrowDetails) {
            for(Button button : sectionButtons) button.hide();
            recipeList.hide();
            details.move(Coord.of(0, layout.header.h)); details.resize(Coord.of(sz.x, sz.y - layout.header.h)); details.show();
        } else {
            recipeList.show();
            details.visible = !layout.detailsAsPage;
        }
        Coord detailBase = details.c;
        favorite.move(Coord.of(detailBase.x + UI.scale(12), Math.max(layout.header.h, sz.y - UI.scale(36))));
        openCraft.move(Coord.of(Math.max(detailBase.x + UI.scale(60), sz.x - UI.scale(182)), Math.max(layout.header.h, sz.y - UI.scale(36))));
        favorite.visible = details.visible;
        openCraft.visible = details.visible;
        chooser.move(Coord.of(Math.max(0, (sz.x - chooser.sz.x) / 2), Math.max(layout.header.h, (sz.y - chooser.sz.y) / 2)));
        chooser.raise();
    }

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
