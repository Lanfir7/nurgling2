package nurgling.widgets;


import haven.*;
import nurgling.NMapView;
import nurgling.NUtils;
import nurgling.i18n.L10n;
import nurgling.widgets.nsettings.*;
import nurgling.widgets.nsettings.AnimalMarkersSettings;
import nurgling.widgets.nsettings.QOLLanfirSettings;
import nurgling.widgets.options.*;

import java.awt.event.KeyEvent;
import java.util.*;

public class NSettingsWindow extends Widget {

    private static TexI rbtn = new TexI(Resource.loadsimg("nurgling/hud/buttons/right/u"));
    private static TexI dbtn = new TexI(Resource.loadsimg("nurgling/hud/buttons/down/u"));
    private final SettingsList list;
    private final Scrollport settingsView;
    public World world;
    public Navigation navigation;
    Widget container;
    public Panel currentPanel = null;
    private Button saveBtn, cancelBtn, backBtn;
    public QuickActions qa;
    public AutoSelection as;
    public QoL qol;
    private Runnable backAction;
    private TextEntry search;
    private SearchDrop drop;
    private SettingFlash flash;
    private int contentTop = 0;

    public NSettingsWindow() {
        this(null);
    }

    public NSettingsWindow(Runnable backAction) {
        this.backAction = backAction;
        sz = UI.scale(800, 600);
        list = add(new SettingsList(UI.scale(200, 580)), UI.scale(10, 10));

        saveBtn = add(new Button(UI.scale(100), L10n.get("nsettings.btn.save")) {
            public void click() {
                if(currentPanel != null) {
                    currentPanel.save();
                }
            }
        }, UI.scale(680, 560));

        cancelBtn = add(new Button(UI.scale(100), L10n.get("nsettings.btn.cancel")) {
            public void click() {
                if(currentPanel != null) {
                    currentPanel.load();
                }
            }
        }, UI.scale(580, 560));

        // Add Back button only if back action is provided
        if(backAction != null) {
            backBtn = add(new Button(UI.scale(100), L10n.get("nsettings.btn.back")) {
                public void click() {
                    backAction.run();
                }
                
                public boolean keydown(KeyDownEvent ev) {
                    if(ev.c == 27) { // ESC key
                        backAction.run();
                        return true;
                    }
                    return super.keydown(ev);
                }
            }, UI.scale(480, 560));
        }

        addSearch();
        settingsView = add(new Scrollport(UI.scale(580, 526)), UI.scale(210, contentTop));
        container = settingsView.cont;
        fillSettings();
        resize(sz);
    }


    private void fillSettings() {
        SettingsCategory general = new SettingsCategory(L10n.get("nsettings.cat.general"), new Panel(L10n.get("nsettings.cat.general")), container);
        general.addChild(new SettingsItem(L10n.get("nsettings.item.fonts"), new Fonts(), container));
        general.addChild(new SettingsItem(L10n.get("nsettings.item.item_overlays"), new ItemOverlaySettings(), container));
        general.addChild(new SettingsItem(L10n.get("nsettings.item.navigation"), navigation = new Navigation(), container));
        general.addChild(new SettingsItem(L10n.get("nsettings.item.map_settings"), new MapSettings(), container));
        general.addChild(new SettingsItem(L10n.get("nsettings.item.qol"), qol = new QoL(), container));
        general.addChild(new SettingsItem("QOL Lanfir", new QOLLanfirSettings(), container));
        general.addChild(new SettingsItem("Disable Animations", new DisableGobAnimSettings(), container));
        general.addChild(new SettingsItem(L10n.get("nsettings.item.database"), new DatabaseSettings(), container));
        general.addChild(new SettingsItem(L10n.get("nsettings.item.auto_mapper"), new AutoMapper(), container));
        general.addChild(new SettingsItem(L10n.get("nsettings.item.cookbook_upload"), new CookbookSettings(), container));
        general.addChild(new SettingsItem(L10n.get("nsettings.item.auto_selection"), as = new AutoSelection(), container));
        general.addChild(new SettingsItem(L10n.get("nsettings.item.quick_actions"), qa = new QuickActions(), container));
        general.addChild(new SettingsItem(L10n.get("nsettings.item.discord"), new DiscordSettings(), container));
        general.addChild(new SettingsItem(L10n.get("nsettings.item.llm_agent"), new AgentSettings(), container));

        SettingsCategory gameenvironment = new SettingsCategory(L10n.get("nsettings.cat.game_environment"), new Panel(L10n.get("nsettings.cat.game_environment")), container);
        gameenvironment.addChild(new SettingsItem(L10n.get("nsettings.item.world"), world = new World(), container));
        gameenvironment.addChild(new SettingsItem("Tree Finder", new TreeFinder(), container));
        gameenvironment.addChild(new SettingsItem(L10n.get("nsettings.item.object_hiding"), new ObjectHiding(), container));
        gameenvironment.addChild(new SettingsItem(L10n.get("nsettings.item.animal_rings"), new NRingSettings(), container));
        gameenvironment.addChild(new SettingsItem(L10n.get("nsettings.item.critter_circles"), new nurgling.widgets.options.NCritterCircleSettings(), container));
        gameenvironment.addChild(new SettingsItem("Combat HUD", new CombatSettings(), container));

        SettingsCategory scenarios = new SettingsCategory(L10n.get("nsettings.cat.autorunner"), new Panel(L10n.get("nsettings.cat.autorunner")), container);
        scenarios.addChild(new SettingsItem(L10n.get("nsettings.item.scenarios"), new ScenarioPanel(), container));
        scenarios.addChild(new SettingsItem(L10n.get("nsettings.item.craft_presets"), new CraftPresetsPanel(), container));

        SettingsCategory bots = new SettingsCategory(L10n.get("nsettings.cat.bots"), new Panel(L10n.get("nsettings.cat.bots")), container);
        bots.addChild(new SettingsItem(L10n.get("nsettings.item.feed_clover"), new FeedClover(), container));
        bots.addChild(new SettingsItem(L10n.get("nsettings.item.auto_drop"), new Dropper(), container));
        bots.addChild(new SettingsItem(L10n.get("nsettings.item.eating_bot"), new Eater(), container));
        bots.addChild(new SettingsItem(L10n.get("nsettings.item.farming"), new FarmingSettingsPanel(), container));
        bots.addChild(new SettingsItem(L10n.get("nsettings.item.cheese_orders"), new CheeseOrdersPanel(), container));
        bots.addChild(new SettingsItem(L10n.get("nsettings.item.pickling"), new PicklingSettings(), container));
        bots.addChild(new SettingsItem(L10n.get("nsettings.item.parasite"), new ParasiteSettings(), container));
        bots.addChild(new SettingsItem("Mining Mastery", new MiningMasterySettings(), container));
        bots.addChild(new SettingsItem(L10n.get("nsettings.item.animal_markers"), new AnimalMarkersSettings(), container));
        bots.addChild(new SettingsItem(L10n.get("nsettings.item.equipment"), new EquipmentBotSettings(), container));
        bots.addChild(new SettingsItem(L10n.get("nsettings.item.starvation"), new StarvationAlertSettings(), container));
        bots.addChild(new SettingsItem(L10n.get("nsettings.item.autologout"), new AutoLogoutSettings(), container));
        bots.addChild(new SettingsItem("Icon Generator", new IconGeneratorPanel(), container));

        list.addCategory(general);
        list.addCategory(gameenvironment);
        list.addCategory(scenarios);
        list.addCategory(bots);
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if (msg.equals("close")) {
            hide();
            if (NUtils.getGameUI() != null && NUtils.getGameUI().map != null) {
                ((NMapView) NUtils.getGameUI().map).destroyRouteDummys();
                NUtils.getGameUI().map.glob.oc.paths.pflines = null;
            }
        } else {
            super.wdgmsg(sender, msg, args);
        }
    }

    private class SettingsList extends SListBox<SettingsItem, SettingsListItem> {
        public SettingsList(Coord sz) {
            super(sz, UI.scale(24));
        }

        @Override
        protected List<? extends SettingsItem> items() {
            List<SettingsItem> allItems = new ArrayList<>();
            for (SettingsItem item : categories) {
                allItems.add(item);
                if(item.expanded)
                    allItems.addAll(item.getChildren());
            }
            return allItems;
        }

        @Override
        protected SettingsListItem makeitem(SettingsItem item, int idx, Coord sz) {
            return new SettingsListItem(this, sz, item);
        }

        private final List<SettingsCategory> categories = new ArrayList<>();

        public void addCategory(SettingsCategory category) {
            categories.add(category);
            update();
        }

        public void update() {
            super.update();
        }
    }

    private class SettingsListItem extends SListWidget.ItemWidget<SettingsItem> {
        private final Text text;


        public SettingsListItem(SListWidget<SettingsItem, ?> list, Coord sz, SettingsItem item) {
            super(list, sz, item);

            int indent = item.getLevel() * UI.scale(15);

            this.text = Text.render(item.getName());

            if (!item.getChildren().isEmpty()) {
                add(new Button(UI.scale(20), "+"), indent, 0).action(() -> {
                    item.expanded = !item.expanded;
                    ((SettingsList)list).update();
                });
            }
        }

        @Override
        public void draw(GOut g) {
            if(!item.getChildren().isEmpty()) {
                g.image(item.expanded ? dbtn : rbtn, Coord.of(UI.scale(5), (sz.y - text.sz().y) / 2));
            }
            int indent = item.getLevel() * UI.scale(5);
            g.image(text.tex(), Coord.of(indent + UI.scale(25), (sz.y - text.sz().y) / 2));
        }

        @Override
        public boolean mousedown(MouseDownEvent ev) {
            if (super.mousedown(ev)) {
                NSettingsWindow.this.showSettings(item);
                return true;
            }
            list.change(item);
            return true;
        }
    }

    private class SettingsItem {
        public Widget panel;
        private boolean expanded = false;
        private final String name;
        private final List<SettingsItem> children = new ArrayList<>();
        private SettingsItem parent;

        public SettingsItem(String name, Widget panel, Widget container) {
            this.name = name;
            this.panel = panel;
            container.add(panel, Coord.z);
            panel.hide();
        }

        public String getName() { return name; }
        public List<SettingsItem> getChildren() { return children; }

        public void addChild(SettingsItem child) {
            child.parent = this;
            children.add(child);
        }

        public int getLevel() {
            return parent == null ? 0 : parent.getLevel() + 1;
        }
    }

    private class SettingsCategory extends SettingsItem {
        public SettingsCategory(String name, Widget panel, Widget container) {
            super(name, panel, container);
        }
    }

    private void showSettings(SettingsItem item) {
        if(currentPanel != null)
            currentPanel.hide();
        currentPanel = (Panel)item.panel;
        currentPanel.show();
        currentPanel.load();
        settingsView.bar.ch(-settingsView.bar.val);
        settingsView.cont.update();
    }

    @Override
    public void resize(Coord size) {
        super.resize(size);
        if((list == null) || (settingsView == null) || (saveBtn == null) || (cancelBtn == null))
            return;
        int margin = UI.scale(10);
        NSettingsLayout layout = NSettingsLayout.calculate(
                size, UI.scale(210), contentTop, saveBtn.sz, backBtn != null, margin);
        list.resize(layout.sidebarSize);
        settingsView.move(layout.panelPosition);
        settingsView.resize(layout.panelSize);
        saveBtn.move(layout.saveButton);
        cancelBtn.move(layout.cancelButton);
        if((backBtn != null) && (layout.backButton != null))
            backBtn.move(layout.backButton);
        search.move(Coord.of(size.x - margin - search.sz.x, 0));
        drop.move(search.c.add(0, search.sz.y + UI.scale(2)));
        settingsView.cont.update();
        settingsView.bar.ch(0);
    }

    private void addSearch() {
        int searchW = UI.scale(250);
        search = adda(new TextEntry(searchW, "") {
            @Override
            protected void changed() {
                super.changed();
                refreshSearch();
            }

            @Override
            public void activate(String text) {
                drop.pickHover();
            }

            @Override
            public boolean keydown(KeyDownEvent ev) {
                if(ev.code == KeyEvent.VK_DOWN) {
                    drop.moveHover(1);
                    return true;
                }
                if(ev.code == KeyEvent.VK_UP) {
                    drop.moveHover(-1);
                    return true;
                }
                if(key_esc.match(ev)) {
                    hideDrop();
                    return true;
                }
                return super.keydown(ev);
            }

            @Override
            public void draw(GOut g) {
                super.draw(g);
                if(text().isEmpty()) {
                    g.chcolor(180, 180, 180, 180);
                    g.atext(L10n.get("nsettings.search.hint"), Coord.of(UI.scale(6), sz.y / 2), 0, 0.5);
                    g.chcolor();
                }
            }
        }, sz.x - UI.scale(10), 0, 1.0, 0.0);
        search.z(20);
        contentTop = search.sz.y + UI.scale(4);
        drop = add(new SearchDrop(Coord.of(searchW, UI.scale(20))), search.c.add(0, search.sz.y + UI.scale(2)));
        drop.z(21);
        drop.hide();
    }

    private void refreshSearch() {
        if(drop == null || search == null)
            return;
        List<BoundHit> catalog = buildCatalog();
        List<SettingsSearch.Entry> entries = new ArrayList<>(catalog.size());
        for(BoundHit hit : catalog)
            entries.add(hit.entry);
        List<SettingsSearch.Match> matches = SettingsSearch.query(entries, search.text());
        List<BoundHit> shown = new ArrayList<>(matches.size());
        for(SettingsSearch.Match match : matches) {
            for(BoundHit hit : catalog) {
                if(hit.entry == match.entry) {
                    shown.add(hit);
                    break;
                }
            }
        }
        drop.setItems(shown);
    }

    private List<BoundHit> buildCatalog() {
        List<BoundHit> catalog = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for(SettingsCategory category : list.categories) {
            addItemHits(catalog, seen, category);
            for(SettingsItem child : category.getChildren())
                addItemHits(catalog, seen, child);
        }
        return catalog;
    }

    private void addItemHits(List<BoundHit> catalog, Set<String> seen, SettingsItem item) {
        String cat = item.parent == null ? item.getName() : item.parent.getName();
        catalog.add(new BoundHit(SettingsSearch.tab(cat, item.getName()), item, null));
        if(item.panel == null)
            return;
        for(Widget w : item.panel.children(Widget.class)) {
            String label = labelOf(w);
            if(!SettingsSearch.isSearchableLabel(label))
                continue;
            if(label.equalsIgnoreCase(item.getName()))
                continue;
            if(!seen.add(item.getName() + "\0" + label))
                continue;
            catalog.add(new BoundHit(SettingsSearch.setting(cat, item.getName(), label.trim()), item, w));
        }
    }

    private static String labelOf(Widget w) {
        if(w instanceof Label)
            return ((Label)w).text();
        if(w instanceof CheckBox)
            return ((CheckBox)w).label();
        if(w instanceof Button && ((Button)w).text != null)
            return ((Button)w).text.text;
        return null;
    }

    private void openHit(BoundHit hit) {
        hideDrop();
        if(hit.item.parent != null)
            hit.item.parent.expanded = true;
        list.update();
        list.change(hit.item);
        list.display(hit.item);
        showSettings(hit.item);
        if(hit.widget != null) {
            revealWidget(hit.widget);
            flashWidget(hit.widget);
        }
    }

    private void hideDrop() {
        if(drop != null)
            drop.hide();
    }

    private void revealWidget(Widget w) {
        Scrollport sp = w.getparent(Scrollport.class);
        if(sp == null)
            return;
        sp.cont.update();
        Coord pos = w.parentpos(sp.cont);
        int margin = UI.scale(24);
        int view = sp.cont.sz.y;
        int top = pos.y;
        int bottom = pos.y + w.sz.y;
        int sy = sp.cont.sy;
        if(top < sy + margin)
            sp.bar.ch(top - margin - sy);
        else if(bottom > sy + view - margin)
            sp.bar.ch(bottom - (sy + view - margin));
    }

    private void flashWidget(Widget w) {
        if(flash != null)
            flash.destroy();
        if(w.parent == null)
            return;
        flash = w.parent.add(new SettingFlash(w));
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
        boolean r = super.mousedown(ev);
        if(drop != null && drop.visible) {
            Coord sc = ev.c.sub(search.c);
            Coord dc = ev.c.sub(drop.c);
            if(!search.checkhit(sc) && !drop.checkhit(dc))
                hideDrop();
        }
        return r;
    }

    private static class BoundHit {
        final SettingsSearch.Entry entry;
        final SettingsItem item;
        final Widget widget;

        BoundHit(SettingsSearch.Entry entry, SettingsItem item, Widget widget) {
            this.entry = entry;
            this.item = item;
            this.widget = widget;
        }
    }

    private class SearchDrop extends Widget {
        private List<BoundHit> items = Collections.emptyList();
        private final List<Text> lines = new ArrayList<>();
        private int hover = 0;
        private final int rowH = UI.scale(20);

        SearchDrop(Coord sz) {
            super(sz);
        }

        void setItems(List<BoundHit> items) {
            for(Text t : lines)
                t.dispose();
            lines.clear();
            this.items = items;
            for(BoundHit hit : items)
                lines.add(Text.render(hit.entry.display()));
            hover = 0;
            int h = items.isEmpty() ? 0 : items.size() * rowH + UI.scale(4);
            resize(search.sz.x, h);
            show(!items.isEmpty());
        }

        void moveHover(int d) {
            if(items.isEmpty())
                return;
            hover = Math.floorMod(hover + d, items.size());
        }

        void pickHover() {
            if(hover >= 0 && hover < items.size())
                openHit(items.get(hover));
        }

        @Override
        public void draw(GOut g) {
            g.chcolor(0, 0, 0, 235);
            g.frect(Coord.z, sz);
            g.chcolor(160, 160, 160, 255);
            g.rect(Coord.z, sz);
            g.chcolor();
            int y = UI.scale(2);
            for(int i = 0; i < lines.size(); i++) {
                if(i == hover) {
                    g.chcolor(255, 210, 40, 90);
                    g.frect(Coord.of(1, y), Coord.of(sz.x - 2, rowH));
                    g.chcolor();
                }
                g.image(lines.get(i).tex(), Coord.of(UI.scale(6), y + (rowH - lines.get(i).sz().y) / 2));
                y += rowH;
            }
        }

        @Override
        public void mousemove(MouseMoveEvent ev) {
            if(ev.c.y >= 0)
                hover = Utils.clip(ev.c.y / rowH, 0, Math.max(0, items.size() - 1));
            super.mousemove(ev);
        }

        @Override
        public boolean mousedown(MouseDownEvent ev) {
            if(ev.b == 1 && !items.isEmpty()) {
                hover = Utils.clip(ev.c.y / rowH, 0, items.size() - 1);
                pickHover();
                return true;
            }
            return super.mousedown(ev);
        }
    }

    private class SettingFlash extends Widget {
        private final Widget target;
        private double t = 0;

        SettingFlash(Widget target) {
            this.target = target;
            Coord pad = UI.scale(4, 3);
            this.c = target.c.sub(pad);
            resize(target.sz.add(pad.mul(2)));
            z(50);
        }

        @Override
        public boolean checkhit(Coord c) {
            return false;
        }

        @Override
        public boolean mousedown(MouseDownEvent ev) {
            return false;
        }

        @Override
        public void tick(double dt) {
            t += dt;
            if(t > 2.4 || target.parent == null) {
                if(flash == this)
                    flash = null;
                destroy();
                return;
            }
            Coord pad = UI.scale(4, 3);
            this.c = target.c.sub(pad);
        }

        @Override
        public void draw(GOut g) {
            double pulse = 0.45 + 0.45 * (0.5 + 0.5 * Math.sin(t * 10));
            int a = (int)(pulse * 220);
            g.chcolor(255, 196, 32, a / 4);
            g.frect(Coord.z, sz);
            g.chcolor(255, 210, 40, a);
            g.rect(Coord.z, sz);
            g.chcolor();
        }
    }
}
