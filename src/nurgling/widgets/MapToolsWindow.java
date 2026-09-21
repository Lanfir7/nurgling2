package nurgling.widgets;

import haven.*;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.conf.ProspectKind;
import nurgling.conf.ProspectMarkSettings;
import nurgling.i18n.L10n;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single home for everything that controls what the map draws: tree/fish icon toggles,
 * the prospected-sample layer with an independent quality threshold per resource kind,
 * and the terrain/ore tile search.
 *
 * Opened from the gear button on the map window. All state lives in NConfig, so the
 * toolbar toggle buttons and these controls are two views of the same values.
 */
public class MapToolsWindow extends Window {
    private static final int MARGIN = UI.scale(5);
    private static final int OVERLAY_W = UI.scale(300);
    private static final int ROW_GAP = UI.scale(3);
    private static final int TAB_BTN_W = UI.scale(90);
    private static final int ENTRY_W = UI.scale(44);
    private static final int ENTRY_X = OVERLAY_W - UI.scale(102);
    private static final int COUNT_X = OVERLAY_W - UI.scale(50);
    private static final int COUNT_W = OVERLAY_W - COUNT_X;
    private static final int SEARCH_BTN_W = UI.scale(70);
    private static final double COUNT_INTERVAL = 0.5;

    private final List<KindRow> rows = new ArrayList<>();
    private final Tabs tabs;
    private final Tabs.Tab searchTab;
    private final TerrainSearchPanel terrainSearchPanel;
    private MarkerIconsWindow markerIconsWindow;
    private TextEntry masterEntry;
    private double countTimer = COUNT_INTERVAL;

    public MapToolsWindow() {
        super(new Coord(OVERLAY_W, UI.scale(260)), L10n.get("maptools.title"), true);

        tabs = new Tabs(Coord.z, Coord.z, this) {
            @Override
            public void changed(Tab from, Tab to) {
                /* The tabs are different sizes; follow the visible one. */
                MapToolsWindow.this.pack();
            }
        };
        Tabs.Tab overlays = tabs.add();
        searchTab = tabs.add();

        buildOverlays(overlays);
        terrainSearchPanel = searchTab.add(new TerrainSearchPanel(), 0, 0);

        Widget tabBtn = add(tabs.new TabButton(TAB_BTN_W, L10n.get("maptools.tab_overlays"), overlays), 0, 0);
        add(tabs.new TabButton(TAB_BTN_W, L10n.get("maptools.tab_search"), searchTab), TAB_BTN_W + MARGIN, 0);
        add(new Button(TAB_BTN_W, L10n.get("maptools.tab_marker_icons")) {
            @Override
            public void click() {
                openMarkerIcons();
            }
        }, (TAB_BTN_W + MARGIN) * 2, 0);

        /* Place the tab bodies under the buttons, whatever height the buttons turned out to be. */
        tabs.c = new Coord(0, tabBtn.sz.y + MARGIN);
        overlays.c = tabs.c;
        searchTab.c = tabs.c;

        tabs.showtab(overlays);
        pack();
    }

    /** Opens the marker filter separately so the long dynamic list cannot resize the map tools window. */
    private void openMarkerIcons() {
        if(markerIconsWindow != null && markerIconsWindow.parent != null) {
            MapSearchFront.showInFront(markerIconsWindow);
            return;
        }
        NGameUI gui = NUtils.getGameUI();
        if(gui == null)
            return;
        MarkerIconsWindow window = new MarkerIconsWindow();
        markerIconsWindow = window;
        window.onClosed = () -> {
            if(markerIconsWindow == window)
                markerIconsWindow = null;
        };
        gui.add(window, Coord.of(120, 120));
        MapSearchFront.showInFront(window);
    }

    /** The world-map marker filter. It owns a fixed viewport over the dynamic marker list. */
    private class MarkerIconsWindow extends Window {
        private final Scrollport list;
        /* Scrollport measures direct children, so this spacer must carry the full list height. */
        private final Widget listContent;
        private int mapSequence = Integer.MIN_VALUE;
        private double refresh;
        private String typeSignature = "";
        private MapWnd listedMap;
        private Runnable onClosed;

        MarkerIconsWindow() {
            super(new Coord(OVERLAY_W, UI.scale(430)), L10n.get("map.marker_visibility.title"), true);
            CheckBox enabled = add(new CheckBox(L10n.get("map.marker_visibility.hide_unchecked")), UI.scale(4), 0);
            enabled.state(() -> activeMap() != null && activeMap().mapIconVisibilityEnabled());
            enabled.set(value -> {
                MapWnd map = activeMap();
                if(map != null)
                    map.setMapIconVisibilityEnabled(value);
            });
            add(new Label(L10n.get("map.marker_visibility.help")), UI.scale(4, 24));
            list = add(new Scrollport(new Coord(OVERLAY_W - UI.scale(8), UI.scale(380))), UI.scale(4, 45));
            list.showbar(true);
            listContent = list.cont.add(new Widget(Coord.of(list.cont.sz.x, list.sz.y)));
            rebuild();
        }

        private MapWnd activeMap() {
            NGameUI gui = NUtils.getGameUI();
            return(gui == null ? null : gui.mapfile);
        }

        private void rebuild() {
            MapWnd map = activeMap();
            if(map == null)
                return;
            List<MapWnd.MapIconType> types = map.markerVisibilityTypes();
            if(types == null)
                return;
            if(map.view instanceof NMiniMap)
                types.addAll(((NMiniMap)map.view).mapIconTypes());
            Map<String, MapWnd.MapIconType> unique = new HashMap<>();
            for(MapWnd.MapIconType type : types)
                unique.put(type.identity, type);
            types = new ArrayList<>(unique.values());
            types.sort(java.util.Comparator.comparing(type -> type.label, String.CASE_INSENSITIVE_ORDER));
            String signature = types.stream().map(type -> type.identity + '\u0000' + type.label)
                    .collect(java.util.stream.Collectors.joining("\u0001"));
            if(signature.equals(typeSignature) && map == listedMap) {
                mapSequence = map.file.markerseq;
                return;
            }
            int scroll = list.bar.val;
            for(Widget child = listContent.child, next; child != null; child = next) {
                next = child.next;
                child.destroy();
            }
            int y = 0;
            for(MapWnd.MapIconType type : types) {
                MarkerIconRow row = listContent.add(new MarkerIconRow(map, type));
                row.c = Coord.of(0, y);
                y += row.sz.y + ROW_GAP;
            }
            listContent.resize(Coord.of(list.cont.sz.x, Math.max(list.sz.y, y)));
            list.cont.update();
            list.bar.val = Math.min(scroll, list.bar.max);
            list.cont.sy = list.bar.val;
            typeSignature = signature;
            mapSequence = map.file.markerseq;
            listedMap = map;
        }

        public void tick(double dt) {
            super.tick(dt);
            refresh += dt;
            MapWnd map = activeMap();
            if(map != null && (mapSequence != map.file.markerseq || refresh >= COUNT_INTERVAL)) {
                refresh = 0;
                rebuild();
            }
        }

        private class MarkerIconRow extends Widget {
            private final MapWnd map;
            private final MapWnd.MapIconType type;

            MarkerIconRow(MapWnd map, MapWnd.MapIconType type) {
                super(Coord.of(listContent.sz.x, UI.scale(24)));
                this.map = map;
                this.type = type;
                add(new CheckBox("").state(() -> map.isMapIconVisible(type.identity))
                    .set(visible -> map.setMapIconHidden(type.identity, !visible)), Coord.of(0, (sz.y - CheckBox.sbox.sz().y) / 2));
                add(new Label(type.label), UI.scale(32, 3));
            }

            public void draw(GOut g) {
                try {
                    Tex icon = type.icon();
                    if(icon != null)
                        g.aimage(icon, Coord.of(UI.scale(25), sz.y / 2), 0.5, 0.5);
                    else {
                        g.chcolor(160, 160, 160, 180);
                        g.fellipse(Coord.of(UI.scale(25), sz.y / 2), Coord.of(UI.scale(5)));
                        g.chcolor();
                    }
                } catch(Loading loading) {
                    g.chcolor(160, 160, 160, 180);
                    g.fellipse(Coord.of(UI.scale(25), sz.y / 2), Coord.of(UI.scale(5)));
                    g.chcolor();
                }
                super.draw(g);
            }
        }

        @Override
        public void reqclose() {
            reqdestroy();
        }

        @Override
        public void destroy() {
            Runnable callback = onClosed;
            onClosed = null;
            if(callback != null)
                callback.run();
            super.destroy();
        }
    }

    private void buildOverlays(Widget tab) {
        int y = 0;

        tab.add(new Label(L10n.get("maptools.section_icons")), 0, y);
        y += UI.scale(17);

        y = addIconRow(tab, y, L10n.get("maptools.tree_icons"),
                () -> NMiniMap.showTreeIcons(), val -> NMiniMap.showTreeIcons(val), MapToolsWindow::openTreeSearch);
        y = addIconRow(tab, y, L10n.get("maptools.fish_icons"),
                () -> NMiniMap.showFishIcons(), val -> NMiniMap.showFishIcons(val), MapToolsWindow::openFishSearch);

        y += MARGIN;
        Label samplesLbl = tab.add(new Label(L10n.get("maptools.section_samples")), 0, y);
        Button minedSearch = tab.add(new Button(SEARCH_BTN_W, L10n.get("maptools.search_btn")) {
            @Override
            public void click() {
                openMineralSearch(null);
            }
        }, OVERLAY_W - SEARCH_BTN_W, y);
        minedSearch.settip(L10n.get("mineral.search_tip"));
        y += alignRow(y, samplesLbl, minedSearch) + ROW_GAP;

        // Master row: hides the whole layer without losing the per-kind settings.
        CheckBox master = tab.add(new CheckBox(L10n.get("maptools.show_samples")), UI.scale(4), y);
        master.state(() -> settings().master);
        master.set(val -> {
            settings().master = val;
            store();
        });
        Label masterLbl = tab.add(new Label(L10n.get("maptools.threshold")), ENTRY_X - UI.scale(26), y);
        masterEntry = tab.add(new TextEntry(ENTRY_W, "0") {
            @Override
            public boolean keydown(KeyDownEvent ev) {
                if(ev.code == java.awt.event.KeyEvent.VK_ENTER) {
                    applyToAll();
                    return true;
                }
                return super.keydown(ev);
            }
        }, ENTRY_X, y);
        Button setAll = tab.add(new Button(COUNT_W, L10n.get("maptools.set_all")) {
            @Override
            public void click() {
                applyToAll();
            }
        }, COUNT_X, y);
        y += alignRow(y, master, masterLbl, masterEntry, setAll) + ROW_GAP;

        for(ProspectKind kind : ProspectKind.values()) {
            KindRow row = new KindRow(tab, kind, y);
            rows.add(row);
            y += row.height;
        }

        tab.pack();
    }

    private int addIconRow(Widget tab, int y, String label, java.util.function.Supplier<Boolean> state,
                           java.util.function.Consumer<Boolean> set, Runnable search) {
        CheckBox box = tab.add(new CheckBox(label), UI.scale(4), y);
        box.state(state);
        box.set(set);
        Button btn = tab.add(new Button(SEARCH_BTN_W, L10n.get("maptools.search_btn")) {
            @Override
            public void click() {
                search.run();
            }
        }, OVERLAY_W - SEARCH_BTN_W, y);
        return y + alignRow(y, box, btn) + ROW_GAP;
    }

    /**
     * Vertically centre a row of widgets against the tallest one and report its height.
     * Buttons, checkboxes and text entries all have image-derived heights, so a hardcoded
     * row height either overlaps them or leaves a gap.
     */
    private static int alignRow(int y, Widget... widgets) {
        int height = 0;
        for(Widget widget : widgets)
            height = Math.max(height, widget.sz.y);
        for(Widget widget : widgets)
            widget.c = new Coord(widget.c.x, y + ((height - widget.sz.y) / 2));
        return height;
    }

    /** One resource kind: enable flag, its own quality threshold, and a live shown/total count. */
    private class KindRow {
        private final ProspectKind kind;
        private final TextEntry entry;
        private final Label count;
        private final int height;

        KindRow(Widget tab, ProspectKind kind, int y) {
            this.kind = kind;
            CheckBox box = tab.add(new CheckBox(L10n.get(kind.l10nKey)), UI.scale(14), y);
            box.state(() -> NMiniMap.showProspectKind(kind));
            box.set(val -> NMiniMap.showProspectKind(kind, val));
            entry = tab.add(new TextEntry(ENTRY_W, String.valueOf(settings().threshold(kind))) {
                @Override
                public void changed() {
                    super.changed();
                    Integer val = parseThreshold(text());
                    if(val != null) {
                        settings().setThreshold(kind, val);
                        store();
                    }
                }

                @Override
                public boolean keydown(KeyDownEvent ev) {
                    if(ev.code == java.awt.event.KeyEvent.VK_ENTER) {
                        sync();
                        return true;
                    }
                    return super.keydown(ev);
                }
            }, ENTRY_X, y);
            count = tab.add(new Label("-"), COUNT_X, y);
            height = alignRow(y, box, entry, count) + ROW_GAP;
        }

        /** Rewrite the field from the stored (clamped) value. */
        void sync() {
            String val = String.valueOf(settings().threshold(kind));
            if(!val.equals(entry.text()))
                entry.settext(val);
        }

        void setCount(int shown, int total) {
            count.settext((total == 0) ? "-" : (shown + "/" + total));
        }
    }

    private void applyToAll() {
        Integer val = parseThreshold(masterEntry.text());
        if(val == null)
            return;
        ProspectMarkSettings settings = settings();
        settings.setAllThresholds(val);
        store();
        masterEntry.settext(String.valueOf(ProspectMarkSettings.clamp(val)));
        for(KindRow row : rows)
            row.sync();
    }

    /** Lenient parse: blank counts as 0, anything unparseable leaves the stored value alone. */
    private static Integer parseThreshold(String text) {
        String trimmed = (text == null) ? "" : text.trim();
        if(trimmed.isEmpty())
            return 0;
        try {
            return ProspectMarkSettings.clamp(Integer.parseInt(trimmed));
        } catch(NumberFormatException e) {
            return null;
        }
    }

    private ProspectMarkSettings settings() {
        ProspectMarkSettings settings = NMiniMap.prospectSettings();
        if(settings == null) {
            settings = new ProspectMarkSettings();
            NConfig.set(NConfig.Key.prospectMarks, settings);
        }
        return settings;
    }

    /** The settings object is mutated in place; re-setting it flags the config as dirty. */
    private void store() {
        NConfig.set(NConfig.Key.prospectMarks, settings());
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        if(!visible())
            return;
        countTimer += dt;
        if(countTimer < COUNT_INTERVAL)
            return;
        countTimer = 0;
        updateCounts();
    }

    private void updateCounts() {
        Map<ProspectKind, int[]> tally = new EnumMap<>(ProspectKind.class);
        NGameUI gui = NUtils.getGameUI();
        if(gui != null && gui.labeledMarkService != null && gui.mmap != null && gui.mmap.sessloc != null) {
            for(LabeledMinimapMark mark : gui.labeledMarkService.getMarksForSegment(gui.mmap.sessloc.seg.id)) {
                int[] counts = tally.computeIfAbsent(mark.kind, k -> new int[2]);
                counts[1]++;
                if(NMiniMap.markVisible(mark))
                    counts[0]++;
            }
        }
        for(KindRow row : rows) {
            int[] counts = tally.get(row.kind);
            if(counts == null)
                row.setCount(0, 0);
            else
                row.setCount(counts[0], counts[1]);
        }
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if(msg.equals("close")) {
            hide();
        } else {
            super.wdgmsg(sender, msg, args);
        }
    }

    /** Toggle the panel, creating it on first use. */
    public static void toggle() {
        NGameUI gui = NUtils.getGameUI();
        if(gui == null)
            return;
        if(gui.mapToolsWindow != null) {
            if(gui.mapToolsWindow.visible()) {
                gui.mapToolsWindow.hide();
            } else {
                MapSearchFront.showInFront(gui.mapToolsWindow);
            }
        } else {
            gui.mapToolsWindow = new MapToolsWindow();
            gui.add(gui.mapToolsWindow, new Coord(100, 100));
            MapSearchFront.showInFront(gui.mapToolsWindow);
        }
    }

    /** Open the existing terrain controls and replace their filter with these forage biomes. */
    public static void openTerrainSearch(Collection<String> terrains) {
        NGameUI gui = NUtils.getGameUI();
        if(gui == null)
            return;
        if(gui.mapToolsWindow == null) {
            gui.mapToolsWindow = new MapToolsWindow();
            gui.add(gui.mapToolsWindow, new Coord(100, 100));
        }
        MapSearchFront.showInFront(gui.mapToolsWindow);
        gui.mapToolsWindow.tabs.showtab(gui.mapToolsWindow.searchTab);
        gui.mapToolsWindow.terrainSearchPanel.selectTerrains(terrains);
        gui.mapToolsWindow.pack();
    }

    /** Open the terrain controls and highlight exact tile resources (used for quest rocks). */
    public static void openTerrainResources(Collection<String> resources) {
        NGameUI gui = NUtils.getGameUI();
        if(gui == null)
            return;
        if(gui.mapToolsWindow == null) {
            gui.mapToolsWindow = new MapToolsWindow();
            gui.add(gui.mapToolsWindow, new Coord(100, 100));
        }
        MapSearchFront.showInFront(gui.mapToolsWindow);
        gui.mapToolsWindow.tabs.showtab(gui.mapToolsWindow.searchTab);
        gui.mapToolsWindow.terrainSearchPanel.selectResources(resources);
        gui.mapToolsWindow.pack();
    }

    public static void openTreeSearch() {
        NGameUI gui = NUtils.getGameUI();
        if(gui == null)
            return;
        if(gui.treeSearchWindow != null) {
            if(gui.treeSearchWindow.visible()) {
                gui.treeSearchWindow.hide();
            } else {
                MapSearchFront.showInFront(gui.treeSearchWindow);
            }
        } else {
            gui.treeSearchWindow = new TreeSearchWindow(gui);
            gui.add(gui.treeSearchWindow, new Coord(100, 100));
            MapSearchFront.showInFront(gui.treeSearchWindow);
        }
    }

    public static void openMineralSearch() {
        openMineralSearch(null);
    }

    /**
     * Open the ore/gemstone/stone search, preselecting one category.
     *
     * @param preset category to start on, or null for all three
     */
    public static void openMineralSearch(ProspectKind preset) {
        NGameUI gui = NUtils.getGameUI();
        if(gui == null)
            return;
        if(gui.mineralSearchWindow != null) {
            /* Reopen on the asked-for category even when it is already up, so right-clicking
             * the gem button while an ore search is showing does what it looks like. */
            if(gui.mineralSearchWindow.visible() && preset == null) {
                gui.mineralSearchWindow.hide();
                return;
            }
        } else {
            gui.mineralSearchWindow = new MineralSearchWindow(gui);
            gui.add(gui.mineralSearchWindow, new Coord(100, 100));
        }
        MapSearchFront.showInFront(gui.mineralSearchWindow);
        gui.mineralSearchWindow.preset(preset);
    }

    public static void openFishSearch() {
        NGameUI gui = NUtils.getGameUI();
        if(gui == null)
            return;
        if(gui.fishSearchWindow != null) {
            if(gui.fishSearchWindow.visible()) {
                gui.fishSearchWindow.hide();
            } else {
                MapSearchFront.showInFront(gui.fishSearchWindow);
            }
        } else {
            gui.fishSearchWindow = new FishSearchWindow(gui);
            gui.add(gui.fishSearchWindow, new Coord(100, 100));
            MapSearchFront.showInFront(gui.fishSearchWindow);
        }
    }
}
