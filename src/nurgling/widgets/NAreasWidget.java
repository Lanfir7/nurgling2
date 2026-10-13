package nurgling.widgets;

import haven.*;
import haven.Frame;
import haven.Label;
import haven.Window;
import haven.render.*;
import nurgling.*;
import nurgling.actions.bots.*;
import nurgling.areas.*;
import nurgling.navigation.ChunkNavManager;
import nurgling.navigation.ChunkPath;
import nurgling.overlays.map.*;
import nurgling.tools.*;
import nurgling.sessions.BotExecutor;
import nurgling.sessions.ThreadLocalUI;
import nurgling.hotkeys.InputNavigation;
import org.json.*;

import javax.swing.*;
import javax.swing.colorchooser.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

import static nurgling.widgets.Specialisation.findSpecialisation;
import static nurgling.i18n.L10n.get;
import nurgling.i18n.L10n;

public class NAreasWidget extends Window
{
    private static final Color COLLAPSE_TAB_BG = new Color(29, 43, 43, 245);
    private static final Color COLLAPSE_TAB_BORDER = new Color(218, 139, 40);
    private static final Color COLLAPSE_TAB_ARROW = new Color(246, 181, 68);

    public IngredientContainer in_items;
    public IngredientContainer out_items;
    CurrentSpecialisationList csl;
    public AreaList al;
    public boolean createMode = false;
    public String currentPath = "";
    public String searchQuery = "";
    private final List<Widget> detailWidgets = new ArrayList<>();
    private AreasWindowCollapseState collapseState;
    private CollapseTab collapseTab;
    final static Tex folderIcon = new TexI(Resource.loadsimg("nurgling/hud/folder/d"));
    final static Tex openfolderIcon = new TexI(Resource.loadsimg("nurgling/hud/folder/u"));
    NCatSelection catSelection;
    PathBar pathBar;
    /* A folder only exists while some area's path runs through it. Folders made this session
     * stay listed while still empty, so things can be moved into them; they are not saved. */
    public final Set<String> emptyFolders = new HashSet<>();
    /* Ctrl/Shift-clicked rows, keyed by area id (Integer) or folder path (String) so the marks
     * survive the list being rebuilt. */
    final Set<Object> marked = new HashSet<>();
    private Object anchor;
    private List<Moved> lastMove;
    private Set<String> lastMoveFolders;
    private NAreaMoveDialog moveDialog;

    private static class Moved
    {
        final int id;
        final String from, to;

        Moved(int id, String from, String to) {
            this.id = id;
            this.from = from;
            this.to = to;
        }
    }

    public NAreasWidget()
    {
        super(UI.scale(new Coord(700,500)), get("area.title"));

        IButton createNewFolder;
        prev = add(createNewFolder = new IButton(NStyle.addfolder[0].back,NStyle.addfolder[1].back,NStyle.addfolder[2].back){
            @Override
            public void click()
            {
                super.click();
                NEditFolderName.createFolder(currentPath);
            }
        },new Coord(0,UI.scale(5)));
        createNewFolder.settip(get("area.btn.create_folder"));

        IButton create;
        add(create = new IButton(NStyle.addarea[0].back,NStyle.addarea[1].back,NStyle.addarea[2].back){
            @Override
            public void click()
            {
                super.click();
                NUtils.getGameUI().msg(get("area.msg.select_area"));
                new Thread(new NAreaSelector(NAreaSelector.Mode.CREATE)).start();
            }
        },prev.pos("ur").adds(UI.scale(5,0)));
        create.settip(get("area.btn.create_area"));

        IButton showCat;
        add(showCat = new IButton(NStyle.catmenu[0].back,NStyle.catmenu[1].back,NStyle.catmenu[2].back){
            @Override
            public void click()
            {
                super.click();
                if(al.sel!=null) {
//                    if(catSelection == null) {
                        ui.gui.add(catSelection = new NCatSelection(), NAreasWidget.this.c.add(0, NAreasWidget.this.sz.y));
//                    }
                    catSelection.visible = true;
                }
            }
        },create.pos("ur").adds(UI.scale(5,0)));
        showCat.settip(get("area.btn.show_categories"));

        IButton importbt;
        add(importbt = new IButton(NStyle.importb[0].back,NStyle.importb[1].back,NStyle.importb[2].back){
            @Override
            public void click()
            {
                super.click();
                java.awt.EventQueue.invokeLater(() -> {
                    JFileChooser fc = new JFileChooser();
                    fc.setFileFilter(new FileNameExtensionFilter("Areas setting file", "json"));
                    if(fc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
                        return;
                    if(fc.getSelectedFile()!=null)
                    {
                        // Show import strategy dialog
                        NImportStrategyDialog.showDialog(fc.getSelectedFile());
                    }
                });
            }
        },showCat.pos("ur").adds(UI.scale(25,0)));
        importbt.settip(get("area.btn.import"));

        IButton exportbt;
        add(exportbt = new IButton(NStyle.exportb[0].back,NStyle.exportb[1].back,NStyle.exportb[2].back){
            @Override
            public void click()
            {
                super.click();
                java.awt.EventQueue.invokeLater(() -> {
                    JFileChooser fc = new JFileChooser();
                    fc.setFileFilter(new FileNameExtensionFilter("Areas setting file", "json"));
                    if(fc.showSaveDialog(null) != JFileChooser.APPROVE_OPTION)
                        return;
                    NUtils.getUI().core.config.writeAreas(fc.getSelectedFile().getAbsolutePath()+".json");
                });
            }
        },importbt.pos("ur").adds(UI.scale(5,0)));
        exportbt.settip(get("area.btn.export"));

        haven.Button syncLogBtn = add(new haven.Button(UI.scale(70), "Sync log") {
            @Override
            public void click() {
                super.click();
                nurgling.widgets.NAreaSyncHistoryWidget.open();
            }
        }, exportbt.pos("ur").adds(UI.scale(10, 0)));
        syncLogBtn.settip("Recent area sync events (auto-merges, conflicts, deletes)");

        haven.Button presetsBtn = add(new haven.Button(UI.scale(70), get("area.preset.btn")) {
            @Override
            public void click() {
                super.click();
                NAreaPresetsWindow.open((NGameUI) ui.gui);
            }
        }, syncLogBtn.pos("ur").adds(UI.scale(5, 0)));
        presetsBtn.settip(get("area.preset.btn_tip"));
//        // Export to Database button
//        haven.Button exportDbBtn;
//        add(exportDbBtn = new haven.Button(UI.scale(80), "Export to DB") {
//            @Override
//            public void click() {
//                super.click();
//                exportAreasToDatabase();
//            }
//        }, exportbt.pos("ur").adds(UI.scale(10, 0)));
//        exportDbBtn.settip("Export all areas to database for sharing");

        TextEntry searchField;
        prev = add(searchField = new TextEntry(UI.scale(580), "") {
            @Override
            public boolean keydown(KeyDownEvent ev) {
                boolean result = super.keydown(ev);
                /* Only on a real change: rebuilding the list re-picks the selection, which
                 * must not happen when Ctrl or Shift is pressed for a multi-select click. */
                String query = text().toLowerCase();
                if(!query.equals(searchQuery)) {
                    searchQuery = query;
                    marked.clear();
                    al.cancelDrag();
                    updateFilteredList();
                }
                return result;
            }
        }, createNewFolder.pos("bl").adds(0, 10));
        searchField.settip(get("area.search.placeholder"));

        pathBar = add(new PathBar(), searchField.pos("bl").adds(0, 6));
        prev = add(al = new AreaList(UI.scale(new Coord(400,289))), searchField.pos("bl").adds(0, 25));
        pathBar.resize(new Coord(al.sz.x, UI.scale(16)));
        Widget lab = add(new Label(get("area.label.specialisation"),NStyle.areastitle), prev.pos("bl").add(UI.scale(0,5)));
        int listWidth = AreasWindowCollapseState.alignedPanelWidth(al.sz.x);

        add(csl = new CurrentSpecialisationList(new Coord(listWidth, UI.scale(71))),lab.pos("bl").add(UI.scale(0,5)));
        add(new IButton(NStyle.add[0].back,NStyle.add[1].back,NStyle.add[2].back){
            @Override
            public void click()
            {
                super.click();
                if(al.sel!=null)
                    Specialisation.selectSpecialisation(al.sel.area);
            }
        },prev.pos("br").sub(UI.scale(40,-5)));

        add(new IButton(NStyle.remove[0].back,NStyle.remove[1].back,NStyle.remove[2].back){
            @Override
            public void click()
            {
                super.click();
                if(al.sel!=null && csl.sel!=null)
                {
                    for(NArea.Specialisation s: al.sel.area.spec)
                    {
                        if(csl.sel.item!=null && s.name.equals(csl.sel.item.name)) {
                            al.sel.area.spec.remove(s);
                            break;
                        }
                    }
                    for(SpecialisationItem item : specItems)
                    {
                        if(csl.sel.item!=null && item.item.name.equals(csl.sel.item.name))
                        {
                            specItems.remove(item);
                            break;
                        }
                    }
                    if (al.sel != null && al.sel.area != null) {
                        al.sel.area.markDirty(nurgling.areas.AreaFieldGroup.ROUTING);
                    }
                    NConfig.needAreasUpdate();
                }
            }
        },prev.pos("br").sub(UI.scale(17,-5)));

        prev = add(Frame.with(in_items = new IngredientContainer("in"),true), prev.pos("ur").add(UI.scale(5,-5)));
        detailWidgets.add(prev);
        detailWidgets.add(add(new Label(get("area.label.take"),NStyle.areastitle),prev.pos("ul").sub(UI.scale(-5,20))));
        detailWidgets.add(add(new IngredientContainer.RuleButton(in_items ),prev.pos("ur").sub(UI.scale(30,20))));
        prev = add(Frame.with(out_items = new IngredientContainer("out"),true), prev.pos("ur").adds(UI.scale(5, 0)));
        detailWidgets.add(prev);
        detailWidgets.add(add(new Label(get("area.label.put"),NStyle.areastitle),prev.pos("ul").sub(UI.scale(-5,20))));
        detailWidgets.add(add(new IngredientContainer.RuleButton(out_items ),prev.pos("ur").sub(UI.scale(30,20))));
        searchField.resize(Coord.of(listWidth, searchField.sz.y));
        pack();

        int tabWidth = UI.scale(18);
        int compactWidth = AreasWindowCollapseState.compactWidth(al.c.x + al.sz.x, tabWidth);
        collapseState = new AreasWindowCollapseState(csz(), compactWidth, listWidth);
        collapseTab = add(new CollapseTab(), Coord.z);
        applyCollapseState();
    }

    private void toggleCollapsed() {
        collapseState.toggle();
        applyCollapseState();
    }

    private void collapse() {
        collapseState.collapse();
        applyCollapseState();
    }

    private void applyCollapseState() {
        Coord target = collapseState.size();
        for(Widget detail : detailWidgets)
            detail.show(collapseState.detailsVisible());
        collapseTab.c = Coord.of(target.x - collapseTab.sz.x, (target.y - collapseTab.sz.y) / 2);
        collapseTab.settip(get(collapseState.expanded()
                ? "area.btn.collapse_details"
                : "area.btn.expand_details"));
        resize(target);
    }

    private class CollapseTab extends Widget {
        private CollapseTab() {
            super(UI.scale(18, 54));
        }

        @Override
        public void draw(GOut g) {
            g.chcolor(COLLAPSE_TAB_BG);
            g.frect(Coord.z, sz);
            g.chcolor(COLLAPSE_TAB_BORDER);
            g.rect(Coord.z, sz.sub(1, 1));

            int direction = collapseState.direction() == AreasWindowCollapseState.Direction.RIGHT ? 1 : -1;
            int centerX = sz.x / 2;
            int centerY = sz.y / 2;
            int halfWidth = UI.scale(4);
            int halfHeight = UI.scale(7);
            Coord tip = Coord.of(centerX + direction * halfWidth, centerY);
            Coord upper = Coord.of(centerX - direction * halfWidth, centerY - halfHeight);
            Coord lower = Coord.of(centerX - direction * halfWidth, centerY + halfHeight);
            g.chcolor(COLLAPSE_TAB_ARROW);
            g.line(upper, tip, UI.scale(2));
            g.line(tip, lower, UI.scale(2));
            g.chcolor();
        }

        @Override
        public boolean mousedown(MouseDownEvent ev) {
            if(ev.b == 1) {
                toggleCollapsed();
                return true;
            }
            return super.mousedown(ev);
        }
    }

    public void removeArea(int id)
    {
        invalidateMoveUndo();
        if(NUtils.getGameUI()!=null && NUtils.getGameUI().map!=null)
        {
            NOverlay nol = NUtils.getGameUI().map.nols.get(id);
            if (nol != null)
                nol.remove();
            NUtils.getGameUI().map.nols.remove(id);
        }
        showPath(currentPath);
    }

    public void updateAreaName(int areaId, String newName) {
        synchronized (items) {
            for (AreaItem item : items) {
                if (item != null && item.area != null && item.area.id == areaId) {
                    item.text.settext(newName);
                    item.settip(buildAreaTip(newName, item.area), true);
                    break;
                }
            }
        }
    }

    public void updateAreaQuality(int areaId) {
        synchronized (items) {
            for (AreaItem item : items) {
                if (item != null && item.area != null && item.area.id == areaId) {
                    item.settip(buildAreaTip(item.area.name, item.area), true);
                    break;
                }
            }
        }
    }

    @Override
    public void show()
    {
        collapse();
        // Preserve any active search when the window reappears (e.g. after "Select area
        // space"). updateFilteredList() falls back to showPath(currentPath) when the query
        // is empty, so the no-search case is unchanged.
        updateFilteredList();
        super.show();
    }

    private static List<NArea> allAreas() {
        Map<Integer, NArea> areas = ((NMapView) NUtils.getGameUI().map).glob.map.areas;
        synchronized (areas) {
            return new ArrayList<>(areas.values());
        }
    }

    private List<String> knownPaths() {
        List<String> paths = new ArrayList<>(emptyFolders);
        for (NArea area : allAreas())
            paths.add(area.path);
        return paths;
    }

    private void reparentEmptyFolders(String from, String to) {
        Set<String> moved = new HashSet<>();
        for (Iterator<String> it = emptyFolders.iterator(); it.hasNext(); ) {
            String folder = it.next();
            if (AreaPaths.isUnder(folder, from)) {
                it.remove();
                moved.add(AreaPaths.reparent(folder, from, to));
            }
        }
        emptyFolders.addAll(moved);
    }

    public void changePath(String newpath, String path) {
        if (newpath.equals(path))
            return;
        invalidateMoveUndo();
        for(NArea area : allAreas())
        {
            if(AreaPaths.isUnder(area.path, path))
            {
                area.path = AreaPaths.reparent(area.path, path, newpath);
                area.markDirty(nurgling.areas.AreaFieldGroup.IDENTITY);
            }
        }
        reparentEmptyFolders(path, newpath);
    }

    /** "Areas › Base › Farm", for messages. */
    String displayPath(String path) {
        StringBuilder sb = new StringBuilder(get("area.path.root"));
        for (String crumb : AreaPaths.crumbs(path)) {
            if (!crumb.isEmpty())
                sb.append(" › ").append(AreaPaths.name(crumb));
        }
        return sb.toString();
    }

    /**
     * Moves areas, and folders with everything inside them, into {@code dest}. Drag and drop,
     * the "Move to..." picker and folder merges all come through here; Ctrl+Z undoes it.
     */
    void moveInto(Collection<NArea> areas, Collection<String> folders, String dest) {
        for (String folder : folders) {
            if (AreaPaths.isUnder(dest, folder))
                return;
        }
        List<NArea> all = allAreas();
        List<Moved> log = new ArrayList<>();
        Set<Integer> done = new HashSet<>();
        Set<String> foldersBefore = new HashSet<>(emptyFolders);
        String merged = null;
        int movedFolders = 0;
        for (String folder : folders) {
            if (!AreaPaths.canMoveFolder(folder, dest))
                continue;
            String to = AreaPaths.join(dest, AreaPaths.name(folder));
            for (String known : knownPaths()) {
                if (AreaPaths.isUnder(known, to))
                    merged = to;
            }
            for (NArea area : all) {
                if (AreaPaths.isUnder(area.path, folder) && done.add(area.id))
                    log.add(relocate(area, AreaPaths.reparent(area.path, folder, to)));
            }
            reparentEmptyFolders(folder, to);
            keepFolder(AreaPaths.parent(folder));
            movedFolders++;
        }
        for (NArea area : areas) {
            if (!area.path.equals(dest) && done.add(area.id)) {
                keepFolder(area.path);
                log.add(relocate(area, dest));
            }
        }
        if (log.isEmpty() && (movedFolders == 0))
            return;
        lastMove = log;
        lastMoveFolders = foldersBefore;
        if (!log.isEmpty())
            NConfig.needAreasUpdate();
        refresh();
        /* Point at where things went when that folder is on screen. */
        for (AreaItem item : al.items()) {
            if (dest.equals(item.folder)) {
                al.sel = item;
                select();
                break;
            }
        }
        String what = (movedFolders == 1 && folders.size() == 1 && areas.isEmpty())
                ? get("area.move.folder", AreaPaths.name(folders.iterator().next()))
                : get("area.move.count", log.size());
        String msg = get("area.move.done", what, displayPath(dest));
        if (merged != null)
            msg += " " + get("area.move.merged", AreaPaths.name(merged));
        NUtils.getGameUI().msg(msg);
    }

    /* A folder a move empties stays listed, so things can go back into it. */
    private void keepFolder(String folder) {
        if (!folder.isEmpty())
            emptyFolders.add(folder);
    }

    private static Moved relocate(NArea area, String to) {
        Moved m = new Moved(area.id, area.path, to);
        area.path = to;
        area.markDirty(nurgling.areas.AreaFieldGroup.IDENTITY);
        return m;
    }

    /** Puts back the last move. Areas whose folder has changed since (e.g. a villager's sync) stay put. */
    void undoMove() {
        if (lastMove == null) {
            NUtils.getGameUI().msg(get("area.move.nothing_to_undo"));
            return;
        }
        int n = 0;
        for (Moved m : lastMove) {
            NArea area = NUtils.getArea(m.id);
            if (area != null && area.path.equals(m.to)) {
                area.path = m.from;
                area.markDirty(nurgling.areas.AreaFieldGroup.IDENTITY);
                n++;
            }
        }
        emptyFolders.clear();
        emptyFolders.addAll(lastMoveFolders);
        lastMove = null;
        lastMoveFolders = null;
        if (n > 0)
            NConfig.needAreasUpdate();
        refresh();
        NUtils.getGameUI().msg(get("area.move.undone", n));
    }

    /** A later folder edit makes the saved empty-folder snapshot unsafe to restore. */
    void invalidateMoveUndo() {
        lastMove = null;
        lastMoveFolders = null;
    }

    /** Rebuilds the list where it is, keeping the search and the selected area. */
    private void refresh() {
        if (!searchQuery.isEmpty()) {
            updateFilteredList();
            return;
        }
        showPath(currentPath, (al.sel != null && al.sel.area != null) ? al.sel.area.id : -1);
    }

    /** Opens "Move to..." for the marked rows if {@code row} is one of them, else for {@code row} alone. */
    void openMoveDialog(AreaItem row) {
        List<NArea> areas = new ArrayList<>();
        List<String> folders = new ArrayList<>();
        payloadOf(row, areas, folders);
        if (areas.isEmpty() && folders.isEmpty())
            return;
        if (moveDialog != null)
            moveDialog.reqdestroy();
        moveDialog = new NAreaMoveDialog(this, areas, folders);
        parent.add(moveDialog, c.add((sz.x - moveDialog.sz.x) / 2, (sz.y - moveDialog.sz.y) / 2));
        moveDialog.raise();
    }

    void moveDialogClosed(NAreaMoveDialog dialog) {
        if (moveDialog == dialog)
            moveDialog = null;
    }

    /** "Save as preset...": the row, or every marked row if it is marked, saved as one preset. */
    void savePreset(AreaItem row) {
        List<NArea> areas = new ArrayList<>();
        List<String> folders = new ArrayList<>();
        payloadOf(row, areas, folders);
        if (areas.isEmpty() && folders.isEmpty())
            return;
        String suggested = (areas.size() + folders.size() == 1)
                ? (areas.isEmpty() ? AreaPaths.name(folders.get(0)) : areas.get(0).name)
                : "";
        NAreaPresetNameDialog.open(ui, get("area.preset.save_title"), suggested, name -> {
            NGameUI gui = (NGameUI) ui.gui;
            try {
                AreaPreset preset = AreaPreset.capture(name, areas, folders, allAreas(), gui.map.glob.map);
                AreaPresetStore.saveNew(preset);
                gui.msg(get("area.preset.saved", preset.name, preset.areas.size()));
                NAreaPresetsWindow.open(gui).load(preset);
            } catch (AreaPreset.CaptureException e) {
                gui.error(e.getMessage());
            } catch (java.io.IOException e) {
                gui.error(get("area.preset.save_failed", e.getMessage()));
            }
        });
    }

    /** What a drag or "Move to..." started on {@code row} carries. */
    private void payloadOf(AreaItem row, List<NArea> areas, List<String> folders) {
        Object key = row.key();
        if (key == null)
            return;
        Collection<Object> keys = marked.contains(key) ? new ArrayList<>(marked) : Collections.singletonList(key);
        for (Object k : keys) {
            if (k instanceof Integer) {
                NArea area = NUtils.getArea((Integer) k);
                if (area != null)
                    areas.add(area);
            } else {
                folders.add((String) k);
            }
        }
    }

    public void showPath(String path) {
        Integer cur = (al != null && al.sel != null && al.sel.area != null) ? al.sel.area.id : null;
        showPath(path, AreaListSelection.implicitRebuildId(cur));
    }

    public void showPath(String path, int selectAreaId) {
        if (!path.equals(currentPath) && !al.dragging())
            marked.clear();
        synchronized (items) {
            items.clear();
            currentPath = path;
            ArrayList<AreaItem> areas = new ArrayList<>();
            List<String> paths = new ArrayList<>(emptyFolders);
            for (NArea area : allAreas()) {
                paths.add(area.path);
                if (area.path.equals(path))
                    areas.add(new AreaItem(area.name, area));
            }

            if (!currentPath.isEmpty())
                items.add(new AreaItem(AreaPaths.parent(currentPath)));

            for (String folder : AreaPaths.childFolders(paths, path))
                items.add(new AreaItem(folder, true));
            items.addAll(areas);
        }
            if(!items.isEmpty()) {
                ArrayList<Integer> present = new ArrayList<>();
                for (AreaItem item : items) {
                    if (item != null && item.area != null)
                        present.add(item.area.id);
                }
                Integer keep = AreaListSelection.keptAreaId(selectAreaId, present);
                AreaItem selectedItem = null;
                if (keep != null) {
                    for (AreaItem item : items) {
                        if (item.area != null && item.area.id == keep) {
                            selectedItem = item;
                            break;
                        }
                    }
                }
                if (selectedItem == null) {
                    selectedItem = items.get(items.size() - 1);
                }
                al.sel = selectedItem;
                if (al.sel.area != null) {
                    select(al.sel.area.id);
                }
                else
                {
                    select();
                }
            }

    }

    /** Same flower menu as RMB on the list row, even if the editor is closed or the list is stale. */
    public void openAreaOptsAt(NArea area, Coord rootPos) {
        if (area == null)
            return;
        String path = area.path == null ? "" : area.path;
        showPath(path, area.id);
        AreaItem found = null;
        synchronized (items) {
            for (AreaItem ai : items) {
                if (ai.area != null && ai.area.id == area.id) {
                    found = ai;
                    break;
                }
            }
        }
        if (found == null)
            found = new AreaItem(area.name, area);
        found.optsAt(rootPos);
    }
    
    public void selectAreaById(int areaId) {
        for(AreaItem item : items) {
            if(item.area != null && item.area.id == areaId) {
                al.sel = item;
                select(areaId);
                return;
            }
        }
    }

    private void updateFilteredList() {
        if (searchQuery.isEmpty()) {
            showPath(currentPath);
            return;
        }

        synchronized (items) {
            items.clear();
            ArrayList<AreaItem> filteredAreas = new ArrayList<>();
            
            for (NArea area : allAreas()) {
                if (matchesSearch(area)) {
                    /* Results come from every folder, so say which one. */
                    AreaItem item = new AreaItem(area.name, area);
                    item.settip(buildAreaTip(area.name, area) + "\n" + get("area.tooltip.in", displayPath(area.path)));
                    filteredAreas.add(item);
                }
            }
            
            items.addAll(filteredAreas);
        }
        
        if (!items.isEmpty()) {
            al.sel = items.get(0);
            if (al.sel.area != null) {
                select(al.sel.area.id);
            }
        } else {
            select();
        }
    }

    private boolean matchesSearch(NArea area) {
        String query = searchQuery.toLowerCase();
        
        if (area.name.toLowerCase().contains(query)) {
            return true;
        }
        
        for (NArea.Specialisation spec : area.spec) {
            if (spec.name.toLowerCase().contains(query)) {
                return true;
            }
            if (spec.subtype != null && spec.subtype.toLowerCase().contains(query)) {
                return true;
            }
            // Search by pretty name
            Specialisation.SpecialisationItem specItem = findSpecialisation(spec.name);
            if (specItem != null && specItem.prettyName.toLowerCase().contains(query)) {
                return true;
            }
        }
        
        for (int i = 0; i < area.jin.length(); i++) {
            String itemName = (String) ((JSONObject) area.jin.get(i)).get("name");
            if (itemName.toLowerCase().contains(query)) {
                return true;
            }
        }
        
        for (int i = 0; i < area.jout.length(); i++) {
            String itemName = (String) ((JSONObject) area.jout.get(i)).get("name");
            if (itemName.toLowerCase().contains(query)) {
                return true;
            }
        }
        
        return false;
    }

    /** Render an area tooltip including last-edited-by presence info (Phase 5). */
    private static String buildAreaTip(String text, NArea area) {
        String tip = RichText.Parser.quote(text);
        if (area == null)
            return tip;
        if (area.showsQuality() && area.maxQuality >= 0)
            tip += "\n$size[10]{Q" + area.maxQuality + "}";
        if (area.lastTouchedBy == null || area.lastTouchedBy.isEmpty())
            return tip;
        String when = "recently";
        if (area.lastTouchedAt > 0) {
            long ago = (System.currentTimeMillis() - area.lastTouchedAt) / 1000L;
            if (ago < 60) when = ago + "s ago";
            else if (ago < 3600) when = (ago / 60) + "m ago";
            else if (ago < 86400) when = (ago / 3600) + "h ago";
            else when = (ago / 86400) + "d ago";
        }
        return tip + "\n" + RichText.Parser.quote("Last edited by " + area.lastTouchedBy + " " + when);
    }

    public class AreaItem extends Widget{
        Label text;
        IButton remove;
        CheckBox hide;
        /* Long names are cut with "..." before the checkbox and trash icons. The label keeps
         * the full name (it is looked up by name) and is hidden; this draws in its place. */
        private Text.Line fitted;
        private String fittedFor;
        private int fittedW;

        private Tex fitted(String name, int w) {
            if((fitted == null) || !name.equals(fittedFor) || (w != fittedW)) {
                if(fitted != null)
                    fitted.dispose();
                fitted = text.f.ellipsize(name, Math.max(0, w));
                fittedFor = name;
                fittedW = w;
            }
            return(fitted.tex());
        }

        @Override
        public void dispose() {
            if(fitted != null)
                fitted.dispose();
            super.dispose();
        }

        public NArea area;

        boolean isDir = false;
        /** Full path of the folder a folder row stands for. */
        String folder = null;
        /** Where the ".." row leads. */
        private String rootPath = null;
        final ArrayList<String> opt;

        /** Identity for marking and dragging; the ".." row has none. */
        Object key() {
            if(area != null)
                return area.id;
            return folder;
        }
        /* A long (hidden) name label must not widen the row past the list: the trash
         * icon sits at the row's right edge. */
        private void fitList() {
            resize(new Coord(al.sz.x, sz.y));
        }

        @Override
        public void resize(Coord sz) {
            if(remove!=null) {
                remove.move(new Coord(sz.x - NStyle.removei[0].sz().x - UI.scale(5), remove.c.y));
            }
            super.resize(sz);
        }

        public AreaItem(String text, NArea area){
            this.text = add(new Label(text));
            this.area = area;
            this.settip(buildAreaTip(text, area), true);
            hide = add(new CheckBox(""){
                @Override
                public void changed(boolean val) {
                    area.hide = val;
                    area.markDirty(nurgling.areas.AreaFieldGroup.COSMETIC);
                    NConfig.needAreasUpdate();
                    al.sel = AreaItem.this;
                    super.changed(val);
                }
            },new Coord(al.sz.x - 2*NStyle.removei[0].sz().x-UI.scale(2), 0).sub(UI.scale(5),0 ));
            hide.a = area.hide;
            hide.settip(get("area.btn.disable"));
            remove = add(new IButton(NStyle.removei[0].back,NStyle.removei[1].back,NStyle.removei[2].back){
                @Override
                public void click() {
                    ((NMapView)NUtils.getGameUI().map).removeAreaById(AreaItem.this.area.id);
                    NConfig.needAreasUpdate();
                }
            },new Coord(al.sz.x - NStyle.removei[0].sz().x, 0).sub(UI.scale(5),UI.scale(1) ));
            remove.settip(get("area.btn.remove"));
            opt = new ArrayList<String>(){
                {
                    add(L10n.get("area.menu.navigate"));
                    add(L10n.get("area.menu.select_space"));
                    add(L10n.get("area.menu.set_color"));
                    add(L10n.get("area.menu.edit_name"));
                    add(L10n.get("area.menu.duplicate"));
                    add(L10n.get("area.menu.fill_direction"));
                    add(L10n.get("area.menu.move_to"));
                    add(L10n.get("area.menu.save_preset"));
                    add(L10n.get("area.menu.scan"));
                    add(L10n.get("area.menu.delete"));
                }
            };

            pack();
            fitList();
        }

        public AreaItem(String folder, boolean isDir){
            this.text = add(new Label(AreaPaths.name(folder)));
            this.area = null;
            this.isDir = isDir;
            this.folder = folder;
            opt = new ArrayList<String>(){
                {
                    add(L10n.get("area.menu.edit_folder"));
                    add(L10n.get("area.menu.move_to"));
                    add(L10n.get("area.menu.save_preset"));
                    add(L10n.get("area.menu.remove_content"));
                }
            };
            pack();
            fitList();
        }

        public AreaItem(String rootPath) {
            this.text = add(new Label(".."));
            this.area = null;
            this.isDir = false;
            this.rootPath = rootPath;
            opt = new ArrayList<>();
            pack();
            fitList();
        }

        @Override
        public void draw(GOut g) {
            if (area == null) {
                g.image((rootPath != null) ? openfolderIcon : folderIcon, Coord.z, UI.scale(16,16));
                g.image(fitted(text.text(), sz.x - UI.scale(25)), new Coord(UI.scale(21), 0)); // Text next to icon
            } else {
                int right = ((hide != null) ? hide.c.x : remove.c.x) - UI.scale(4);
                text.visible = false;
                super.draw(g);
                g.image(fitted(text.text(), right - text.c.x), text.c);
            }
        }

        @Override
        public boolean mousedown(MouseDownEvent ev) {
            if (ev.b == 3)
            {
                opts(c);
                return true;
            }
            else if (ev.b == 1) {
                /* The checkbox and trash icon keep their own clicks; anywhere else on the row
                 * is a press the list turns into a click or a drag. */
                if (ev.propagate(this))
                    return true;
                al.press(this);
                return true;
            }
            return false;
        }


        NFlowerMenu menu;

        private void ensureMenu() {
            if(menu == null) {
                menu = new NFlowerMenu(opt.toArray(new String[0])) {
                    @Override
                    public boolean mousedown(MouseDownEvent ev) {
                        if(super.mousedown(ev))
                            nchoose(null);
                        return true;
                    }

                    public void destroy() {
                        menu = null;
                        super.destroy();
                    }

                    @Override
                    public void nchoose(NPetal option)
                    {
                        if(option!=null)
                        {
                            if (option.name.equals(get("area.menu.navigate")))
                            {
                                final NArea navArea = area;
                                BotExecutor.runTracked("AreaNavigator", (gui) -> {
                                    ChunkNavManager chunkNav = ((NMapView)gui.map).getChunkNavManager();
                                    if (chunkNav != null && chunkNav.isInitialized())
                                    {
                                        ChunkPath path = chunkNav.planToArea(navArea);
                                        if (path != null)
                                        {
                                            try
                                            {
                                                chunkNav.navigateToArea(navArea, gui);
                                            } catch (InterruptedException ignored)
                                            {
                                            }
                                        }
                                    }
                                });
                            }
                            else if (option.name.equals(get("area.menu.select_space")))
                            {
                                ((NMapView)NUtils.getGameUI().map).changeArea(area.id);
                            }
                            else if (option.name.equals(get("area.menu.set_color")))
                            {
                                JColorChooser colorChooser = new JColorChooser();
                                final AbstractColorChooserPanel[] panels = colorChooser.getChooserPanels();
                                for (final AbstractColorChooserPanel accp : panels) {
                                    if (!accp.getDisplayName().equals("RGB")) {
                                        colorChooser.removeChooserPanel(accp);
                                    }
                                }
                                colorChooser.setPreviewPanel(new JPanel());

                                colorChooser.setColor(area.color);
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {

                                        float old = NUtils.getUI().gprefs.bghz.val;
                                        NUtils.getUI().gprefs.bghz.val = NUtils.getUI().gprefs.hz.val;
                                        final int areaId = area.id;
                                        JDialog chooser = JColorChooser.createDialog(null, "SelectColor", true, colorChooser, new AbstractAction() {
                                            @Override
                                            public void actionPerformed(ActionEvent e) {
                                                NArea theArea = NUtils.getArea(areaId);
                                                if(theArea != null) {
                                                    theArea.color = colorChooser.getColor();
                                                    theArea.markDirty(nurgling.areas.AreaFieldGroup.COSMETIC);
                                                    if(NUtils.getGameUI()!=null && NUtils.getGameUI().map!=null)
                                                    {
                                                        NOverlay nol = NUtils.getGameUI().map.nols.get(areaId);
                                                        if(nol != null) {
                                                            nol.remove();
                                                            NUtils.getGameUI().map.nols.remove(areaId);
                                                        }
                                                    }
                                                    NConfig.needAreasUpdate();
                                                    // Retain selection on this area
                                                    NUtils.getGameUI().areas.showPath(NUtils.getGameUI().areas.currentPath, areaId);
                                                }
                                            }
                                        }, new ActionListener() {
                                            @Override
                                            public void actionPerformed(ActionEvent e) {

                                            }
                                        });
                                        chooser.setVisible(true);
                                        NUtils.getUI().gprefs.bghz.val= old;
                                    }
                                }).start();
                            }
                            else if (option.name.equals(get("area.menu.edit_name")))
                            {
                                NEditAreaName.changeName(area, AreaItem.this);
                            }
                            else if (option.name.equals(get("area.menu.duplicate")))
                            {
                                int duplicatedId = ((NMapView) NUtils.getGameUI().map).duplicateArea(area.id);
                                if (duplicatedId != -1) {
                                    NAreasWidget.this.showPath(currentPath, duplicatedId);
                                    ((NMapView) NUtils.getGameUI().map).changeArea(duplicatedId);
                                }
                            }
                            else if (option.name.equals(get("area.menu.fill_direction")))
                            {
                                NAreaDirectionMenu popup = new NAreaDirectionMenu(area);
                                ui.root.add(popup, ui.mc);
                            }
                            else if (option.name.equals(get("area.menu.scan")))
                            {
                                Scaner.startScan(area);
                            }
                            else if (option.name.equals(get("area.menu.delete")))
                            {
                                ((NMapView)NUtils.getGameUI().map).removeAreaById(area.id);
                                NConfig.needAreasUpdate();
                            }
                            else if (option.name.equals(get("area.menu.move_to")))
                            {
                                openMoveDialog(AreaItem.this);
                            }
                            else if (option.name.equals(get("area.menu.save_preset")))
                            {
                                savePreset(AreaItem.this);
                            }
                            else if (option.name.equals(get("area.menu.edit_folder")))
                            {
                                NEditFolderName.changeName(AreaPaths.parent(folder), AreaPaths.name(folder));
                            }
                            else if (option.name.equals(get("area.menu.remove_content")))
                            {
                                NAreasWidget.this.invalidateMoveUndo();
                                ArrayList<Integer> forRemove = new ArrayList<>();
                                for (NArea area : allAreas()) {
                                    if(AreaPaths.isUnder(area.path, folder)) {
                                        forRemove.add(area.id);
                                    }
                                }
                                emptyFolders.removeIf(f -> AreaPaths.isUnder(f, folder));
                                NMapView mapView = (NMapView) NUtils.getGameUI().map;
                                for (Integer key : forRemove)
                                    mapView.removeAreaById(key);
                                NConfig.needAreasUpdate();
                                NAreasWidget.this.showPath(NAreasWidget.this.currentPath);
                            }
                        }
                        uimsg("cancel");
                    }

                };
            }
        }

        // Open the context menu anchored relative to this list row.
        public void opts( Coord c ) {
            ensureMenu();
            Widget par = parent;
            Coord pos = c;
            while(par!=null && !(par instanceof GameUI))
            {
                pos = pos.add(par.c);
                par = par.parent;
            }
            ui.root.add(menu, pos.add(UI.scale(25,38)));
        }

        // Open the same context menu at an absolute root-space coordinate
        // (used when right-clicking the area's name label in the world). The list
        // row this is invoked on may be detached (its own ui is null), so anchor
        // on the GameUI's root explicitly.
        public void optsAt( Coord rootPos ) {
            ensureMenu();
            NUtils.getGameUI().ui.root.add(menu, rootPos);
        }

        @Override
        public void draw(GOut g, boolean strict) {
            super.draw(g, strict);
        }
    }

    public void select(int id)
    {
        in_items.load(id);
        out_items.load(id);
        loadSpec(id);
    }

    public void select()
    {
        in_items.items.clear();
        out_items.items.clear();
        specItems.clear();
    }

    public void set(int id)
    {
        select(id);
    }

    public void loadSpec(int id)
    {
        if(NUtils.getArea(id)!=null) {
            specItems.clear();
            for (NArea.Specialisation spec : NUtils.getArea(id).spec) {
                specItems.add(new SpecialisationItem(spec));
            }
        }
    }
//    private ConcurrentHashMap<Integer, AreaItem> areas = new ConcurrentHashMap<>();

    private final ArrayList<AreaItem> items = new ArrayList<>();

    /** Where a drag would land: a folder or ".." row, a path bar part, or the open folder itself. */
    static class Target
    {
        static final int ROW = 0, CRUMB = 1, LIST = 2;
        final int kind;
        final String dest;
        final boolean valid;

        Target(int kind, String dest, boolean valid) {
            this.kind = kind;
            this.dest = dest;
            this.valid = valid;
        }

        boolean same(Target o) {
            return (o != null) && (o.kind == kind) && o.dest.equals(dest);
        }
    }

    /** Orange frame and tint on a drop target, with the bar that fills until a hovered folder opens. */
    static void drawHot(GOut g, Coord ul, Coord sz, double progress) {
        g.chcolor(233, 156, 84, 50);
        g.frect(ul, sz);
        g.chcolor(NStyle.border);
        nurgling.widgets.login.NLoginTheme.outline(g, ul, sz);
        if(progress > 0)
            g.frect(ul.add(0, sz.y - UI.scale(2)), Coord.of((int)(sz.x * Math.min(1.0, progress)), UI.scale(2)));
        g.chcolor();
    }

    public class AreaList extends SListBox<AreaItem, Widget> {
        /** How long a drag has to rest on a folder before it opens. */
        static final double SPRING = 0.7;
        private AreaItem pressed;
        private Coord pressc;
        private boolean modpress;
        private UI.Grab grab;
        /* Set once the press has moved far enough to be a drag. */
        private List<NArea> dragAreas;
        private List<String> dragFolders;
        private Set<Object> dragKeys;
        Target hover;
        double hovered;
        private double edge;
        private Text.Line ghost, ghostCount;

        AreaList(Coord sz) {
            super(sz, UI.scale(15));
        }

        public List<AreaItem> items() {
            synchronized (items) {
                return items;
            }
        }

        @Override
        public void resize(Coord sz) {
            super.resize(new Coord(UI.scale(170)-UI.scale(6), sz.y));
        }

        protected Widget makeitem(AreaItem item, int idx, Coord sz) {
            return(new ItemWidget<AreaItem>(this, sz.add(UI.scale(0,5)), item) {
                {
                    add(item);
                }
            });
        }

        boolean dragging() {
            return dragKeys != null;
        }

        boolean pressing() {
            return pressed != null;
        }

        /* The grab starts on press so the list keeps the moves and the release even outside the
         * window; the press only turns into a drag once the pointer has moved a few pixels. */
        void press(AreaItem item) {
            cancelDrag();
            pick(item);
            pressed = item;
            pressc = ui.mc;
            modpress = InputNavigation.toggleSelection(ui) || InputNavigation.rangeSelection(ui);
            grab = ui.grabmouse(this);
        }

        /** Selection on press: Ctrl toggles a row, Shift marks a range, and an area row loads its panels. */
        private void pick(AreaItem item) {
            Object key = item.key();
            List<AreaItem> rows = items();
            if(key != null && InputNavigation.toggleSelection(ui)) {
                /* The row selected before the first Ctrl+click counts as picked too. */
                if(marked.isEmpty() && sel != null && sel != item && sel.key() != null && rows.contains(sel))
                    marked.add(sel.key());
                if(!marked.remove(key))
                    marked.add(key);
                anchor = key;
            } else if(key != null && InputNavigation.rangeSelection(ui)) {
                int b = rows.indexOf(item);
                int a = indexOf(rows, (anchor != null) ? anchor : ((sel != null) ? sel.key() : null));
                if(a < 0)
                    a = b;
                marked.clear();
                for(int i = Math.min(a, b); i <= Math.max(a, b); i++) {
                    Object k = rows.get(i).key();
                    if(k != null)
                        marked.add(k);
                }
            } else {
                anchor = key;
            }
            if(item.area != null) {
                sel = item;
                NAreasWidget.this.select(item.area.id);
            }
        }

        private int indexOf(List<AreaItem> rows, Object key) {
            if(key == null)
                return -1;
            for(int i = 0; i < rows.size(); i++) {
                if(key.equals(rows.get(i).key()))
                    return i;
            }
            return -1;
        }

        /** A press and release without a drag: opens a folder or "..", and drops the marks. */
        private void clicked(AreaItem item) {
            marked.clear();
            if(item.folder != null)
                showPath(item.folder);
            else if(item.rootPath != null)
                showPath(item.rootPath);
        }

        private void startDrag() {
            List<NArea> areas = new ArrayList<>();
            List<String> folders = new ArrayList<>();
            payloadOf(pressed, areas, folders);
            if(areas.isEmpty() && folders.isEmpty())
                return;
            dragAreas = areas;
            dragFolders = folders;
            dragKeys = new HashSet<>(folders);
            List<String> names = new ArrayList<>();
            for(String folder : folders)
                names.add(AreaPaths.name(folder));
            for(NArea area : areas) {
                dragKeys.add(area.id);
                names.add(area.name);
            }
            ghost = Text.std.ellipsize(String.join(", ", names), UI.scale(160));
            if(names.size() > 1)
                ghostCount = Text.std.render(String.valueOf(names.size()));
        }

        void cancelDrag() {
            if(grab != null) {
                grab.remove();
                grab = null;
            }
            pressed = null;
            dragAreas = null;
            dragFolders = null;
            dragKeys = null;
            hover = null;
            hovered = 0;
            if(ghost != null) {
                ghost.dispose();
                ghost = null;
            }
            if(ghostCount != null) {
                ghostCount.dispose();
                ghostCount = null;
            }
        }

        private Target targetAt() {
            int kind = -1;
            String dest = null;
            Coord lc = ui.mc.sub(rootpos());
            if(lc.isect(Coord.z, sz)) {
                int slot = slotat(lc);
                List<AreaItem> rows = items();
                AreaItem row = (slot >= 0 && slot < rows.size()) ? rows.get(slot) : null;
                if(row != null && row.folder != null && !dragKeys.contains(row.folder)) {
                    kind = Target.ROW;
                    dest = row.folder;
                } else if(row != null && row.rootPath != null) {
                    kind = Target.ROW;
                    dest = row.rootPath;
                } else {
                    /* Anywhere else in the list means the folder that is open. */
                    kind = Target.LIST;
                    dest = currentPath;
                }
            } else {
                dest = pathBar.crumbAt(ui.mc.sub(pathBar.rootpos()));
                if(dest != null)
                    kind = Target.CRUMB;
            }
            if(kind < 0)
                return null;
            List<String> paths = new ArrayList<>();
            for(NArea area : dragAreas)
                paths.add(area.path);
            return new Target(kind, dest, AreaPaths.canDrop(paths, dragFolders, dest));
        }

        private boolean springs(Target t) {
            return (t != null) && t.valid && (t.kind != Target.LIST) && !t.dest.equals(currentPath);
        }

        double springProgress(Target t) {
            return (springs(t) && t.same(hover)) ? hovered / SPRING : 0;
        }

        @Override
        public void tick(double dt) {
            super.tick(dt);
            if(dragKeys == null)
                return;
            /* Resting near the top or bottom edge scrolls, so far rows can be reached. */
            Coord lc = ui.mc.sub(rootpos());
            int dir = 0;
            if(lc.x >= 0 && lc.x < sz.x) {
                if(lc.y >= 0 && lc.y < itemh)
                    dir = -1;
                else if(lc.y < sz.y && lc.y >= sz.y - itemh)
                    dir = 1;
            }
            if(dir != 0 && scrollmax() > 0) {
                edge += dir * dt * itemh * 8;
                int step = (int)edge;
                edge -= step;
                scrollval(Math.max(0, Math.min(scrollmax(), scrollval() + step)));
            } else {
                edge = 0;
            }

            Target t = targetAt();
            if(t == null || !t.same(hover))
                hovered = 0;
            hover = t;
            if(springs(hover)) {
                hovered += dt;
                if(hovered >= SPRING) {
                    String dest = hover.dest;
                    hover = null;
                    hovered = 0;
                    showPath(dest);
                }
            }
        }

        @Override
        public void mousemove(MouseMoveEvent ev) {
            if(pressed != null && dragKeys == null && searchQuery.isEmpty() && pressed.key() != null
                    && ui.mc.dist(pressc) > UI.scale(4))
                startDrag();
            super.mousemove(ev);
        }

        @Override
        public boolean mousedown(MouseDownEvent ev) {
            if(pressed != null) {
                if(ev.b == 3)
                    cancelDrag();
                return true;
            }
            return super.mousedown(ev);
        }

        @Override
        public boolean mouseup(MouseUpEvent ev) {
            if(ev.b != 1 || pressed == null)
                return super.mouseup(ev);
            AreaItem item = pressed;
            boolean click = (dragKeys == null);
            boolean mod = modpress;
            Target t = click ? null : targetAt();
            List<NArea> areas = dragAreas;
            List<String> folders = dragFolders;
            Set<Object> keys = dragKeys;
            cancelDrag();
            if(click) {
                if(!mod)
                    clicked(item);
            } else if(t != null && t.valid) {
                marked.removeAll(keys);
                moveInto(areas, folders, t.dest);
            }
            return true;
        }

        @Override
        protected void drawslot(GOut g, AreaItem item, int idx, Area area) {
            super.drawslot(g, item, idx, area);
            Object key = item.key();
            if(key != null && marked.contains(key)) {
                g.chcolor(233, 156, 84, 70);
                g.frect2(area.ul, area.br);
                g.chcolor(NStyle.border);
                g.frect(area.ul, Coord.of(UI.scale(2), area.sz().y));
                g.chcolor();
            }
        }

        Color bg = new Color(30,40,40,160);
        @Override
        public void draw(GOut g)
        {
            g.chcolor(bg);
            g.frect(Coord.z, g.sz());
            super.draw(g);
            if(dragKeys == null)
                return;
            List<AreaItem> rows = items();
            int w = sz.x - ((sb != null && sb.vis()) ? sb.sz.x : 0);
            int first = Math.max(0, scrollval() / (itemh + marg));
            for(int i = first; i < rows.size(); i++) {
                int y = i * (itemh + marg) - scrollval();
                if(y >= sz.y)
                    break;
                AreaItem row = rows.get(i);
                Object key = row.key();
                if(key != null && dragKeys.contains(key)) {
                    /* What is being carried fades. */
                    g.chcolor(0, 0, 0, 130);
                    g.frect(Coord.of(0, y), Coord.of(w, itemh));
                    g.chcolor();
                }
                String dest = (row.folder != null) ? row.folder : row.rootPath;
                if(hover != null && hover.valid && hover.kind == Target.ROW && hover.dest.equals(dest))
                    drawHot(g, Coord.of(0, y), Coord.of(w, itemh), springProgress(hover));
            }
            if(hover != null && hover.valid && hover.kind == Target.LIST)
                drawHot(g, Coord.z, sz, 0);
            ui.drawafter(this::drawGhost);
        }

        /** The carried names under the pointer, with a count when there are several. */
        private void drawGhost(GOut g) {
            if(ghost == null)
                return;
            int pad = UI.scale(3);
            Coord c = ui.mc.add(UI.scale(14, 12));
            int x = c.x + pad;
            int th = ghost.sz().y;
            int bw = 0;
            if(ghostCount != null)
                bw = ghostCount.sz().x + UI.scale(6) + pad;
            Coord sz = Coord.of(bw + ghost.sz().x + pad * 2, th + pad * 2);
            g.chcolor(NStyle.rowEven.getRed(), NStyle.rowEven.getGreen(), NStyle.rowEven.getBlue(), 235);
            g.frect(c, sz);
            g.chcolor(NStyle.border);
            nurgling.widgets.login.NLoginTheme.outline(g, c, sz);
            if(ghostCount != null) {
                g.frect(Coord.of(x, c.y + pad), Coord.of(ghostCount.sz().x + UI.scale(6), th));
                g.chcolor(NStyle.rowEven);
                g.image(ghostCount.tex(), Coord.of(x + UI.scale(3), c.y + pad));
                x += bw;
            }
            g.chcolor();
            g.image(ghost.tex(), Coord.of(x, c.y + pad));
        }

        @Override
        public void dispose() {
            cancelDrag();
            super.dispose();
        }
    }

    /**
     * "Areas › Base › Farm" above the list: shows where the list is, a click goes to that folder,
     * and while dragging each part is a drop target. Long paths lose their leading parts to "…",
     * always keeping the open folder and its parent.
     */
    class PathBar extends Widget
    {
        private class Crumb
        {
            final String dest;
            final Text.Line text;
            int x;

            Crumb(String dest, Text.Line text) {
                this.dest = dest;
                this.text = text;
            }
        }

        private final List<Crumb> crumbs = new ArrayList<>();
        private Text.Line sep;
        private String laidFor;
        private boolean cut;
        private final Color dim = new Color(143, 163, 164);

        PathBar() {
            super(Coord.z);
        }

        private int width(List<String> labels) {
            int w = 0;
            for(String label : labels)
                w += Text.std.strsize(label).x;
            return w + sep.sz().x * Math.max(0, labels.size() - 1);
        }

        private void layout() {
            String key = (searchQuery.isEmpty() ? currentPath : "\0search") + "\0" + sz.x;
            if(key.equals(laidFor))
                return;
            laidFor = key;
            for(Crumb crumb : crumbs)
                crumb.text.dispose();
            crumbs.clear();
            if(sep == null)
                sep = Text.std.render(" \u203a ");
            cut = false;
            if(!searchQuery.isEmpty()) {
                crumbs.add(new Crumb(null, Text.std.ellipsize(get("area.path.search"), sz.x)));
                return;
            }
            List<String> path = AreaPaths.crumbs(currentPath);
            List<String> dests = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for(int first = 0; ; first++) {
                dests.clear();
                labels.clear();
                if(first > 0) {
                    dests.add(path.get(first - 1));
                    labels.add("\u2026");
                }
                for(int i = first; i < path.size(); i++) {
                    dests.add(path.get(i));
                    labels.add(path.get(i).isEmpty() ? get("area.path.root") : AreaPaths.name(path.get(i)));
                }
                if(width(labels) <= sz.x || first >= path.size() - 2)
                    break;
                cut = true;
            }
            int max = Integer.MAX_VALUE;
            if(width(labels) > sz.x) {
                cut = true;
                max = Math.max(UI.scale(10), (sz.x - sep.sz().x * (labels.size() - 1)) / labels.size());
            }
            int x = 0;
            for(int i = 0; i < labels.size(); i++) {
                Crumb crumb = new Crumb(dests.get(i), Text.std.ellipsize(labels.get(i), max));
                crumb.x = x;
                x += crumb.text.sz().x + sep.sz().x;
                crumbs.add(crumb);
            }
        }

        /** The folder under {@code c}, or null. */
        String crumbAt(Coord c) {
            layout();
            if(c.y < 0 || c.y >= sz.y)
                return null;
            for(Crumb crumb : crumbs) {
                if(crumb.dest != null && c.x >= crumb.x && c.x < crumb.x + crumb.text.sz().x)
                    return crumb.dest;
            }
            return null;
        }

        @Override
        public void draw(GOut g) {
            layout();
            String over = al.dragging() ? null : crumbAt(ui.mc.sub(rootpos()));
            Target hover = al.hover;
            for(int i = 0; i < crumbs.size(); i++) {
                Crumb crumb = crumbs.get(i);
                Coord tsz = crumb.text.sz();
                int y = (sz.y - tsz.y) / 2;
                boolean last = (i == crumbs.size() - 1);
                boolean hot = hover != null && hover.valid && hover.kind == Target.CRUMB && hover.dest.equals(crumb.dest);
                if(hot)
                    drawHot(g, Coord.of(crumb.x - UI.scale(2), 0), Coord.of(tsz.x + UI.scale(4), sz.y), al.springProgress(hover));
                g.chcolor((last || hot || (crumb.dest != null && crumb.dest.equals(over))) ? Color.WHITE : dim);
                g.image(crumb.text.tex(), Coord.of(crumb.x, y));
                if(!last) {
                    g.chcolor(dim);
                    g.image(sep.tex(), Coord.of(crumb.x + tsz.x, y));
                }
            }
            g.chcolor();
        }

        @Override
        public boolean mousedown(MouseDownEvent ev) {
            if(ev.b != 1)
                return false;
            String dest = crumbAt(ev.c);
            if(dest != null && !dest.equals(currentPath))
                showPath(dest);
            return true;
        }

        @Override
        public Object tooltip(Coord c, Widget prev) {
            return (cut && searchQuery.isEmpty()) ? displayPath(currentPath) : null;
        }

        @Override
        public void dispose() {
            for(Crumb crumb : crumbs)
                crumb.text.dispose();
            if(sep != null)
                sep.dispose();
            super.dispose();
        }
    }

    List<SpecialisationItem> specItems = new ArrayList<>();
    @Override
    public void wdgmsg(Widget sender, String msg, Object... args)
    {
        if(msg.equals("close"))
            hide();
        else
        {
            super.wdgmsg(sender, msg, args);
        }
    }

    public class CurrentSpecialisationList extends SListBox<SpecialisationItem, Widget> {
        private static final long QUALITY_DOUBLE_CLICK_NS = 500_000_000L;
        private SpecialisationItem lastQualityRow;
        private NArea lastQualityArea;
        private long lastQualityClickAt;

        CurrentSpecialisationList(Coord sz) {
            super(sz, UI.scale(24));
        }

        private void clearQualityClick() {
            lastQualityRow = null;
            lastQualityArea = null;
            lastQualityClickAt = 0;
        }

        private void qualityRowClicked(SpecialisationItem row) {
            NArea area = al.sel == null ? null : al.sel.area;
            if (area == null || !area.spec.contains(row.item)) {
                clearQualityClick();
                return;
            }
            long now = System.nanoTime();
            if (lastQualityRow == row && lastQualityArea == area
                    && now - lastQualityClickAt > 0
                    && now - lastQualityClickAt <= QUALITY_DOUBLE_CLICK_NS) {
                clearQualityClick();
                ((NMapView) NUtils.getGameUI().map).resetAreaQuality(area.id);
            } else {
                lastQualityRow = row;
                lastQualityArea = area;
                lastQualityClickAt = now;
            }
        }

        @Override
        public void change(SpecialisationItem item)
        {
            super.change(item);
        }

        protected List<SpecialisationItem> items() {return specItems;}

        @Override
        public void resize(Coord sz) {
            super.resize(new Coord(sz.x, sz.y));
        }

        protected Widget makeitem(SpecialisationItem item, int idx, Coord sz) {
            return(new ItemWidget<SpecialisationItem>(this, sz, item) {
                {
                    add(item);
                    item.positionSettingsButtons(sz.x);
                }

                @Override
                public boolean mousedown(MouseDownEvent ev) {
                    if (ev.b != 1 || !NArea.SHOW_QUALITY_SPEC.equals(item.item.name))
                        clearQualityClick();
                    return super.mousedown(ev);
                }

                @Override
                protected boolean clicked(MouseDownEvent ev) {
                    boolean handled = super.clicked(ev);
                    if (ev.b == 1 && NArea.SHOW_QUALITY_SPEC.equals(item.item.name))
                        qualityRowClicked(item);
                    return handled;
                }

            });
        }

        @Override
        public void wdgmsg(String msg, Object... args)
        {
            super.wdgmsg(msg, args);
        }

        Color bg = new Color(30,40,40,160);

        @Override
        public void draw(GOut g)
        {
            g.chcolor(bg);
            g.frect(Coord.z, g.sz());
            super.draw(g);
        }


    }



    public class SpecialisationItem extends Widget
    {
        Label text;
        NArea.Specialisation item;
        IButton spec = null;
        IButton rankPresetBtn = null;
        final ArrayList<IButton> settingsButtons = new ArrayList<>();
        NFlowerMenu menu;
        NFlowerMenu rankMenu;
        TexI icon;
        
        // Check if specialisation is for animals
        private boolean isAnimalSpec(String name) {
            return name.equals("cows") || name.equals("goats") || name.equals("sheeps") || 
                   name.equals("pigs") || name.equals("horses") || name.equals("deer");
        }
        
        // Get list of presets for animal type
        private java.util.HashSet<String> getPresetNames(String specName) {
            switch(specName) {
                case "cows": return nurgling.conf.CowsHerd.getKeySet();
                case "goats": return nurgling.conf.GoatsHerd.getKeySet();
                case "sheeps": return nurgling.conf.SheepsHerd.getKeySet();
                case "pigs": return nurgling.conf.PigsHerd.getKeySet();
                case "horses": return nurgling.conf.HorseHerd.getKeySet();
                case "deer": return nurgling.conf.TeimDeerHerd.getKeySet();
                default: return new java.util.HashSet<>();
            }
        }
        
        // Get current preset for area and animal type
        private String getCurrentPreset(NArea area, String specName) {
            if(area == null) return null;
            return NConfig.getAreaRankPreset(area.id, specName);
        }
        
        // Set preset for area and animal type
        private void setPreset(NArea area, String specName, String presetName) {
            if(area == null) return;
            NConfig.setAreaRankPreset(area.id, specName, presetName);
        }
        
        public SpecialisationItem(NArea.Specialisation item)
        {
            this.item = item;
            Specialisation.SpecialisationItem specialisationItem = findSpecialisation(item.name);
            if(item.subtype == null) {
                this.text = add(new Label(specialisationItem == null ? "???" + item.name + "???":specialisationItem.prettyName), new Coord(UI.scale(30,4)));
            }
            else
            {
                this.text = add(new Label((specialisationItem == null ? "???" + item.name + "???":specialisationItem.prettyName) + "(" + item.subtype + ")"), new Coord(UI.scale(30,4)));
            }
            if(specialisationItem != null) {
                icon = new TexI(specialisationItem.image);
            }
            if (NArea.SHOW_QUALITY_SPEC.equals(item.name))
                settip(get("area.tooltip.show_quality"));
            
            if(SpecialisationData.data.get(item.name)!=null)
            {
                add(spec = new IButton("nurgling/hud/buttons/settingsnf/","u","d","h"){
                    @Override
                    public void click() {
                        super.click();
                        menu = new NFlowerMenu(SpecialisationData.data.get(item.name)) {

                            @Override
                            public boolean mousedown(MouseDownEvent ev) {
                                if(super.mousedown(ev))
                                    nchoose(null);
                                return true;
                            }

                            public void destroy() {
                                menu = null;
                                super.destroy();
                            }

                            @Override
                            public void nchoose(NPetal option)
                            {
                                if(option!=null)
                                {
                                    Specialisation.SpecialisationItem specItem = findSpecialisation(item.name);
                                    String prettyName = specItem != null ? specItem.prettyName : item.name;
                                    SpecialisationItem.this.text.settext(prettyName + "(" + option.name + ")");
                                    item.subtype = option.name;
                                    if (al.sel != null && al.sel.area != null) {
                                        al.sel.area.markDirty(nurgling.areas.AreaFieldGroup.ROUTING);
                                    }

                                    // Auto-rename area if its name matches the specialisation prettyName
                                    if(al.sel != null && al.sel.area != null) {
                                        NArea area = al.sel.area;
                                        if(area.name.equals(prettyName)) {
                                            String newName = prettyName + "(" + option.name + ")";
                                            ((NMapView)NUtils.getGameUI().map).changeAreaName(area.id, newName);
                                            al.sel.text.settext(newName);
                                            al.sel.settip(buildAreaTip(newName, area), true);
                                            // Update area label on map
                                            Gob dummy = ((NMapView) NUtils.getGameUI().map).dummys.get(area.gid);
                                            if(dummy != null) {
                                                Gob.Overlay ol = dummy.findol(nurgling.overlays.NAreaLabel.class);
                                                if(ol != null && ol.spr instanceof nurgling.overlays.NAreaLabel) {
                                                    nurgling.overlays.NAreaLabel tl = (nurgling.overlays.NAreaLabel) ol.spr;
                                                    tl.update();
                                                }
                                            }
                                        }
                                    }
                                    
                                    NConfig.needAreasUpdate();
                                }
                                uimsg("cancel");
                            }

                        };
                        Widget par = parent;
                        Coord pos = c.add(UI.scale(32,43));
                        while(par!=null && !(par instanceof GameUI))
                        {
                            pos = pos.add(par.c);
                            par = par.parent;
                        }
                        ui.root.add(menu, pos);
                    }
                },UI.scale(new Coord(0,4)));
                settingsButtons.add(spec);
            }
            
            // Add rank preset selection button for animal specialisations
            if(isAnimalSpec(item.name))
            {
                add(rankPresetBtn = new IButton("nurgling/hud/buttons/settingsnf/","u","d","h"){
                    @Override
                    public void click() {
                        super.click();
                        java.util.HashSet<String> presets = getPresetNames(item.name);
                        if(presets.isEmpty()) {
                            NUtils.getGameUI().msg(get("area.msg.no_presets", item.name));
                            return;
                        }
                        
                        // Add reset option
                        String noneOption = get("area.preset.none");
                        String[] options = new String[presets.size() + 1];
                        options[0] = noneOption;
                        int i = 1;
                        for(String preset : presets) {
                            options[i++] = preset;
                        }
                        
                        rankMenu = new NFlowerMenu(options) {
                            @Override
                            public boolean mousedown(MouseDownEvent ev) {
                                if(super.mousedown(ev))
                                    nchoose(null);
                                return true;
                            }

                            public void destroy() {
                                rankMenu = null;
                                super.destroy();
                            }

                            @Override
                            public void nchoose(NPetal option) {
                                if(option != null && al.sel != null && al.sel.area != null) {
                                    String presetName = option.name.equals(noneOption) ? null : option.name;
                                    setPreset(al.sel.area, item.name, presetName);
                                    NConfig.needAreasUpdate();
                                    if(presetName != null) {
                                        NUtils.getGameUI().msg(get("area.msg.preset_set", presetName));
                                    } else {
                                        NUtils.getGameUI().msg(get("area.msg.preset_cleared"));
                                    }
                                }
                                uimsg("cancel");
                            }
                        };
                        Widget par = parent;
                        Coord pos = c.add(UI.scale(32,43));
                        while(par!=null && !(par instanceof GameUI)) {
                            pos = pos.add(par.c);
                            par = par.parent;
                        }
                        ui.root.add(rankMenu, pos);
                    }
                    
                    @Override
                    public Object tooltip(Coord c, Widget prev) {
                        if(al.sel != null && al.sel.area != null) {
                            String current = getCurrentPreset(al.sel.area, item.name);
                            return get("area.tooltip.rank_preset", current != null ? current : get("area.tooltip.default"));
                        }
                        return get("area.tooltip.select_preset");
                    }
                },UI.scale(new Coord(0, 4)));
                settingsButtons.add(rankPresetBtn);
            }
            pack();
            sz.y = UI.scale(24);
        }

        private void positionSettingsButtons(int rowWidth) {
            int[] widths = new int[settingsButtons.size()];
            for (int i = 0; i < settingsButtons.size(); i++)
                widths[i] = settingsButtons.get(i).sz.x;
            int[] positions = SpecialisationButtonLayout.rightAlignedPositions(rowWidth,
                    UI.scale(4), UI.scale(1), widths);
            for (int i = 0; i < settingsButtons.size(); i++)
                settingsButtons.get(i).c = Coord.of(positions[i], UI.scale(4));
            resize(Coord.of(rowWidth, UI.scale(24)));
        }

        @Override
        public void draw(GOut g) {
            super.draw(g);
            g.image(icon,Coord.z,UI.scale(24,24));
        }
    }

    @Override
    public void tick(double dt)
    {
        super.tick(dt);
        if(al.sel == null)
        {
            NAreasWidget.this.in_items.load(-1);
            NAreasWidget.this.out_items.load(-1);
        }
    }

    @Override
    public boolean keydown(KeyDownEvent ev) {
        if(InputNavigation.cancel(ev.code) && al.pressing()) {
            al.cancelDrag();
            return true;
        }
        if(InputNavigation.undoWithoutShift(ev.code, ev.mods)) {
            undoMove();
            return true;
        }
        return super.keydown(ev);
    }

    @Override
    public void hide() {
        if(al != null)
            al.cancelDrag();
        if(moveDialog != null) {
            moveDialog.reqdestroy();
            moveDialog = null;
        }
        super.hide();
        if(NUtils.getGameUI()!=null && NUtils.getGameUI().map!=null && !createMode
                && !AreaLabelSync.toggleOn(NConfig.get(NConfig.Key.showAllZonesAlways)))
            ((NMapView)NUtils.getGameUI().map).destroyDummys();
    }

    @Override
    public boolean show(boolean show) {
        if(show)
        {
            ((NMapView)NUtils.getGameUI().map).initDummys();
        }
        return super.show(show);
    }

    /**
     * Export all areas to database for sharing with other clients.
     * Reads from the old areas file and imports into database.
     */
    private void exportAreasToDatabase() {
        if (nurgling.NCore.databaseManager == null) {
            NUtils.getGameUI().msg("Database is not connected");
            return;
        }

        if (!nurgling.NCore.databaseManager.isReady()) {
            NUtils.getGameUI().msg("Database is not ready");
            return;
        }

        // Get current profile/genus
        String profile = "global";
        if (NUtils.getGameUI() != null) {
            String genus = NUtils.getGameUI().getGenus();
            if (genus != null && !genus.isEmpty()) {
                profile = genus;
            }
        }

        final String finalProfile = profile;

        // First try to load areas from the old file
        java.util.Map<Integer, NArea> areasToExport = new java.util.HashMap<>();
        
        // Read from file
        String areasPath = NUtils.getUI().core.config.getAreasPath();
        java.io.File areasFile = new java.io.File(areasPath);
        
        if (areasFile.exists()) {
            try {
                StringBuilder contentBuilder = new StringBuilder();
                java.nio.file.Files.lines(java.nio.file.Paths.get(areasPath), java.nio.charset.StandardCharsets.UTF_8)
                    .forEach(s -> contentBuilder.append(s).append("\n"));
                
                String content = contentBuilder.toString().trim();
                if (!content.isEmpty() && content.startsWith("{")) {
                    org.json.JSONObject main = new org.json.JSONObject(content);
                    org.json.JSONArray array = main.getJSONArray("areas");
                    for (int i = 0; i < array.length(); i++) {
                        NArea area = new NArea(array.getJSONObject(i));
                        areasToExport.put(area.id, area);
                    }
                }
            } catch (Exception e) {
                NUtils.getGameUI().error("Failed to read areas file: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // If no areas from file, use current cache
        if (areasToExport.isEmpty()) {
            areasToExport = NUtils.getGameUI().map.glob.map.areas;
        }

        if (areasToExport == null || areasToExport.isEmpty()) {
            NUtils.getGameUI().msg("No areas to export");
            return;
        }

        final java.util.Map<Integer, NArea> finalAreas = areasToExport;
        NUtils.getGameUI().msg("Exporting " + finalAreas.size() + " areas to database...");

        // Export asynchronously
        nurgling.NCore.databaseManager.getAreaService().exportAreasToDatabaseAsync(finalAreas, finalProfile)
            .thenAccept(count -> {
                NUtils.getGameUI().msg("Exported " + count + " areas to database");
            })
            .exceptionally(e -> {
                NUtils.getGameUI().error("Failed to export areas: " + e.getMessage());
                e.printStackTrace();
                return null;
            });
    }
}
