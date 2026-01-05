package nurgling.widgets;

import haven.*;
import haven.Frame;
import haven.Label;
import haven.Window;
import haven.render.*;
import nurgling.*;
import nurgling.actions.bots.*;
import nurgling.areas.*;
import nurgling.overlays.map.*;
import nurgling.routes.RoutePoint;
import nurgling.tools.*;
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

public class NAreasWidget extends Window
{
    public IngredientContainer in_items;
    public IngredientContainer out_items;
    CurrentSpecialisationList csl;
    public AreaList al;
    public boolean createMode = false;
    public String currentPath = "";
    public String searchQuery = "";
    final static Tex folderIcon = new TexI(Resource.loadsimg("nurgling/hud/folder/d"));
    final static Tex openfolderIcon = new TexI(Resource.loadsimg("nurgling/hud/folder/u"));
    NCatSelection catSelection;
    static class Folder
    {
        public String name;
        public String rootPath;

        public Folder(String name, String path) {
            this.name = name;
            this.rootPath = path;
        }
    }

    public NAreasWidget()
    {
        super(UI.scale(new Coord(700,500)), "Areas Settings");

        IButton createNewFolder;
        prev = add(createNewFolder = new IButton(NStyle.addfolder[0].back,NStyle.addfolder[1].back,NStyle.addfolder[2].back){
            @Override
            public void click()
            {
                super.click();
                NEditFolderName.createFolder(currentPath);
            }
        },new Coord(0,UI.scale(5)));
        createNewFolder.settip("Create new folder");

        IButton create;
        add(create = new IButton(NStyle.addarea[0].back,NStyle.addarea[1].back,NStyle.addarea[2].back){
            @Override
            public void click()
            {
                super.click();
                NUtils.getGameUI().msg("Please, select area");
                new Thread(new NAreaSelector(NAreaSelector.Mode.CREATE)).start();
            }
        },prev.pos("ur").adds(UI.scale(5,0)));
        create.settip("Create new area");

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
        showCat.settip("Show all categories");

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
        importbt.settip("Import");

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
        exportbt.settip("Export to file");

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

        // Import from Database JSON button
        haven.Button importDbBtn;
        add(importDbBtn = new haven.Button(UI.scale(80), "IMPORT") {
            @Override
            public void click() {
                super.click();
                importAreasFromJsonFile();
            }
        }, exportbt.pos("ur").adds(UI.scale(10, 0)));
        importDbBtn.settip("Import areas from server JSON file to database");

        TextEntry searchField;
        prev = add(searchField = new TextEntry(UI.scale(580), "") {
            @Override
            public boolean keydown(KeyDownEvent ev) {
                boolean result = super.keydown(ev);
                searchQuery = text().toLowerCase();
                updateFilteredList();
                return result;
            }
        }, createNewFolder.pos("bl").adds(0, 10));
        searchField.settip("Search areas by name, category, or items");

        prev = add(al = new AreaList(UI.scale(new Coord(400,270))), searchField.pos("bl").adds(0, 25));
        Widget lab = add(new Label("Specialisation",NStyle.areastitle), prev.pos("bl").add(UI.scale(0,5)));

        add(csl = new CurrentSpecialisationList(UI.scale(164,90)),lab.pos("bl").add(UI.scale(0,5)));
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
                    NConfig.needAreasUpdate();
                }
            }
        },prev.pos("br").sub(UI.scale(17,-5)));

        prev = add(Frame.with(in_items = new IngredientContainer("in"),true), prev.pos("ur").add(UI.scale(5,-5)));
        add(new Label("Take:",NStyle.areastitle),prev.pos("ul").sub(UI.scale(-5,20)));
        add(new IngredientContainer.RuleButton(in_items ),prev.pos("ur").sub(UI.scale(30,20)));
        prev = add(Frame.with(out_items = new IngredientContainer("out"),true), prev.pos("ur").adds(UI.scale(5, 0)));
        add(new Label("Put:",NStyle.areastitle),prev.pos("ul").sub(UI.scale(-5,20)));
        add(new IngredientContainer.RuleButton(out_items ),prev.pos("ur").sub(UI.scale(30,20)));
        pack();
    }

    public void removeArea(int id)
    {
        if(NUtils.getGameUI()!=null && NUtils.getGameUI().map!=null)
        {
            nurgling.NMapView mapView = (nurgling.NMapView) NUtils.getGameUI().map;
            
            // Удаляем overlay (как в старом коде - просто удаляем без проверки на null)
            synchronized (mapView.nols) {
                nurgling.overlays.map.NOverlay nol = mapView.nols.get(id);
                if (nol != null) {
                    nol.remove();
                }
                mapView.nols.remove(id);
            }
        }
        showPath(currentPath);
    }

    @Override
    public void show()
    {
        showPath(currentPath);
        super.show();
    }

    public void changePath(String newpath, String path) {
        for(NArea area : ((NMapView)NUtils.getGameUI().map).glob.map.areas.values())
        {
            if(area.path.startsWith(path))
            {
                area.path = area.path.replace(path,newpath);
            }
        }
    }

    /**
     * Устанавливает hide для всех зон в папке (включая вложенные)
     */
    public void setFolderHide(String folderPath, boolean hide) {
        NMapView mapView = (NMapView) NUtils.getGameUI().map;
        int count = 0;
        for (NArea area : mapView.glob.map.areas.values()) {
            // Проверяем: зона находится в этой папке или в подпапках
            if (area.path.equals(folderPath) || area.path.startsWith(folderPath + "/")) {
                mapView.disableArea(area.name, area.path, hide);
                count++;
            }
        }
        NConfig.needAreasUpdate();
        NUtils.getGameUI().msg((hide ? "Hidden " : "Shown ") + count + " areas in folder");
    }

    /**
     * Возвращает текущее состояние hide для папки
     * true если ВСЕ зоны в папке скрыты, false если хотя бы одна видима
     */
    public boolean getFolderHideState(String folderPath) {
        NMapView mapView = (NMapView) NUtils.getGameUI().map;
        boolean allHidden = true;
        boolean hasAreas = false;
        for (NArea area : mapView.glob.map.areas.values()) {
            if (area.path.equals(folderPath) || area.path.startsWith(folderPath + "/")) {
                hasAreas = true;
                if (!area.hide) {
                    allHidden = false;
                    break;
                }
            }
        }
        return hasAreas && allHidden;
    }

    public void showPath(String path) {
        synchronized (items) {
            items.clear();
            currentPath = path;
            HashMap<String, Folder> folders = new HashMap<>();
            ArrayList<AreaItem> areas = new ArrayList<>();
            for (NArea area : ((NMapView) NUtils.getGameUI().map).glob.map.areas.values()) {
                if (area.path.equals(path)) {
                    areas.add(new AreaItem(area.name, area));
                } else if (area.path.startsWith(path)) {
                    String cand = area.path.substring(path.length());
                    if (cand.startsWith("/")) {
                        String[] parts = cand.split("/");
                        if (parts.length > 1) {
                            String fname = parts[1];
                            folders.put(fname, new Folder(fname, path));
                        }
                    }
                }
            }


            if (!currentPath.isEmpty()) {
                if (currentPath.contains("/")) {
                    String subPath = currentPath.substring(0, currentPath.lastIndexOf("/"));
                    items.add(new AreaItem(subPath));
                } else {
                    items.add(new AreaItem(""));
                }

            }

            for (Folder folder : folders.values()) {
                if (folder.rootPath.equals(path)) {
                    items.add(new AreaItem(folder.name, true));
                }
            }
            items.addAll(areas);
        }
            if(!items.isEmpty()) {
                al.sel = items.get(items.size() - 1);
                if (al.sel.area != null) {
                    select(al.sel.area.id);
                }
                else
                {
                    select();
                }
            }

    }

    /**
     * Обновляет текущий путь без потери выделения
     * Используется при синхронизации зон
     */
    public void refreshCurrentPath() {
        // Сохраняем текущее выделение
        AreaItem currentSel = al.sel;
        int selectedAreaId = -1;
        if (currentSel != null && currentSel.area != null) {
            selectedAreaId = currentSel.area.id;
        }
        
        // Обновляем список
        showPath(currentPath);
        
        // Восстанавливаем выделение, если возможно
        if (selectedAreaId >= 0) {
            for (AreaItem item : items) {
                if (item.area != null && item.area.id == selectedAreaId) {
                    al.sel = item;
                    select(selectedAreaId);
                    return;
                }
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
            
            for (NArea area : ((NMapView) NUtils.getGameUI().map).glob.map.areas.values()) {
                if (matchesSearch(area)) {
                    filteredAreas.add(new AreaItem(area.name, area));
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

    public class AreaItem extends Widget{
        Label text;
        IButton remove;
        CheckBox hide;

        public NArea area;

        boolean isDir = false;
        private String rootPath = null;
        final ArrayList<String> opt;
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
            hide = add(new CheckBox(""){
                @Override
                public void changed(boolean val) {
                    ((NMapView)NUtils.getGameUI().map).disableArea(AreaItem.this.text.text(), area.path, val);
                    al.sel = AreaItem.this;
                    super.changed(val);
                }
            },new Coord(al.sz.x - 2*NStyle.removei[0].sz().x-UI.scale(2), 0).sub(UI.scale(5),0 ));
            hide.a = area.hide;
            hide.settip("Disable this area");
            remove = add(new IButton(NStyle.removei[0].back,NStyle.removei[1].back,NStyle.removei[2].back){
                @Override
                public void click() {
                    ((NMapView)NUtils.getGameUI().map).removeArea(AreaItem.this.text.text());
                    NConfig.needAreasUpdate();
                }
            },new Coord(al.sz.x - NStyle.removei[0].sz().x, 0).sub(UI.scale(5),UI.scale(1) ));
            remove.settip(Resource.remote().loadwait("nurgling/hud/buttons/removeItem/u").flayer(Resource.tooltip).t);
            opt = new ArrayList<String>(){
                {
                    add("Navigate To");
                    add("Select area space");
                    add("Set color");
                    add("Edit name");
                    add("Edit folder");
                    add("Дубликат");
                    add("Scan");
                }
            };

            pack();
        }

        public AreaItem(String text, boolean isDir){
            // Для папок сдвигаем Label вправо, чтобы оставить место для иконки
            this.text = add(new Label(text), new Coord(UI.scale(21), 0));
            this.area = null;
            this.isDir = isDir;
            final String folderPath = currentPath + "/" + text;
            hide = add(new CheckBox(""){
                @Override
                public void changed(boolean val) {
                    // Массово изменяем hide для всех зон в этой папке
                    setFolderHide(folderPath, val);
                    super.changed(val);
                }
            },new Coord(al.sz.x - 2*NStyle.removei[0].sz().x-UI.scale(2), 0).sub(UI.scale(5),0 ));
            hide.a = getFolderHideState(folderPath);
            hide.settip("Hide/Show all areas in this folder");
            remove = add(new IButton(NStyle.removei[0].back,NStyle.removei[1].back,NStyle.removei[2].back){
                @Override
                public void click() {
                }
            },new Coord(al.sz.x - NStyle.removei[0].sz().x, 0).sub(UI.scale(5),UI.scale(1) ));
            opt = new ArrayList<String>(){
                {
                    add("Edit folder name");
                    add("Remove with content");
                    add("Show all in folder");
                    add("Hide all in folder");
                }
            };
            pack();
        }

        public AreaItem(String rootPath) {
            this.text = add(new Label(".."));
            this.area = null;
            this.isDir = false;
            this.rootPath = rootPath;
            remove = add(new IButton(NStyle.removei[0].back,NStyle.removei[1].back,NStyle.removei[2].back){
                @Override
                public void click() {
                }
            },new Coord(al.sz.x - NStyle.removei[0].sz().x, 0).sub(UI.scale(5),UI.scale(1) ));
            opt = new ArrayList<>();
            pack();
        }

        @Override
        public void draw(GOut g) {
            if (rootPath != null) {
                // Кнопка "вверх" (..) - просто иконка и текст (без super.draw)
                g.image(openfolderIcon, Coord.z, UI.scale(16,16));
                g.text(text.text(), new Coord(UI.scale(21), 0));
            } else if (isDir) {
                // Папка - иконка вручную, остальное через super.draw
                g.image(folderIcon, Coord.z, UI.scale(16,16));
                super.draw(g);
            } else if (area != null) {
                // Зона - полная отрисовка
                super.draw(g);
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
                // SHIFT + ЛКМ - автоматическое переименование зоны
                if (ui.modshift && area != null && !isDir) {
                    String newName = getAutoNameForZone(area);
                    if (newName != null && !newName.isEmpty()) {
                        ((NMapView)NUtils.getGameUI().map).changeAreaName(area.id, newName);
                        // Обновляем текст в виджете
                        text.settext(newName);
                        NConfig.needAreasUpdate();
                        return true;
                    }
                }
                
                if (!isDir)
                    if(area != null)
                    {
                        NAreasWidget.this.select(area.id);
                    }
                    else
                    {
                        NAreasWidget.this.showPath(rootPath);
                    }

                else
                    showPath(currentPath + "/" + text.text());
            }
            return super.mousedown(ev);
        }
        
        /**
         * Получает автоматическое название для зоны из INPUT, OUTPUT или специализации
         * Приоритет: INPUT > OUTPUT > специализация
         */
        private String getAutoNameForZone(NArea area) {
            // 1. Проверяем INPUT (jin)
            if (area.jin != null && area.jin.length() > 0) {
                try {
                    JSONObject firstInput = area.jin.getJSONObject(0);
                    if (firstInput.has("name")) {
                        String name = firstInput.getString("name");
                        if (name != null && !name.isEmpty()) {
                            return name;
                        }
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки парсинга
                }
            }
            
            // 2. Проверяем OUTPUT (jout)
            if (area.jout != null && area.jout.length() > 0) {
                try {
                    JSONObject firstOutput = area.jout.getJSONObject(0);
                    if (firstOutput.has("name")) {
                        String name = firstOutput.getString("name");
                        if (name != null && !name.isEmpty()) {
                            return name;
                        }
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки парсинга
                }
            }
            
            // 3. Проверяем специализацию (spec)
            if (area.spec != null && !area.spec.isEmpty()) {
                NArea.Specialisation firstSpec = area.spec.get(0);
                if (firstSpec != null && firstSpec.name != null && !firstSpec.name.isEmpty()) {
                    return firstSpec.name;
                }
            }
            
            return null; // Не найдено подходящее название
        }


        NFlowerMenu menu;

        public void opts( Coord c ) {
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
                            if (option.name.equals("Navigate To"))
                            {
                                Thread t = new Thread(() -> {
                                    try {
                                        RoutePoint targetPoint = ((NMapView)NUtils.getGameUI().map).routeGraphManager.getGraph().findAreaRoutePoint(area);
                                        if(targetPoint == null) {
                                            NUtils.getGameUI().error("No route point found for area: " + area.name);
                                            return;
                                        }
                                        new RoutePointNavigator(targetPoint, area.id).run(NUtils.getGameUI());
                                    } catch (InterruptedException e) {
                                        NUtils.getGameUI().error("Navigation to area interrupted: " + e.getMessage());
                                    }
                                }, "AreaNavigator");
                                t.start();
                                NUtils.getGameUI().biw.addObserve(t);
                            }
                            else if (option.name.equals("Select area space"))
                            {
                                ((NMapView)NUtils.getGameUI().map).changeArea(area.id);
                            }
                            else if (option.name.equals("Set color"))
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
                                        JDialog chooser = JColorChooser.createDialog(null, "SelectColor", true, colorChooser, new AbstractAction() {
                                            @Override
                                            public void actionPerformed(ActionEvent e) {
                                                area.color = colorChooser.getColor();
                                                area.lastLocalChange = System.currentTimeMillis();
                                                if(NUtils.getGameUI()!=null && NUtils.getGameUI().map!=null)
                                                {
                                                    NOverlay nol = NUtils.getGameUI().map.nols.get(area.id);
                                                    nol.remove();
                                                    NUtils.getGameUI().map.nols.remove(area.id);
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
                            else if (option.name.equals("Edit name"))
                            {
                                NEditAreaName.changeName(area, AreaItem.this);
                            }
                            else if (option.name.equals("Edit folder"))
                            {
                                ui.gui.add(new NFolderSelectWindow(area, NAreasWidget.this), ui.mc);
                            }
                            else if (option.name.equals("Дубликат"))
                            {
                                duplicateArea(area);
                            }
                            else if (option.name.equals("Scan"))
                            {
                                Scaner.startScan(area);
                            }
                            else if (option.name.equals("Edit folder name"))
                            {
                                NEditFolderName.changeName(currentPath, AreaItem.this.text.text());
                            }
                            else if (option.name.equals("Remove with content"))
                            {
                                ArrayList<Integer> forRemove = new ArrayList<>();
                                for (NArea area : ((NMapView) NUtils.getGameUI().map).glob.map.areas.values()) {
                                    if(area.path.startsWith(currentPath + "/" + text.text())) {
                                        forRemove.add(area.id);
                                    }
                                }
                                synchronized (((NMapView) NUtils.getGameUI().map).glob.map.areas)
                                {
                                    for(Integer key:forRemove)
                                    {
                                        if(NUtils.getGameUI()!=null && NUtils.getGameUI().map!=null)
                                        {
                                            // Удаляем зону из БД
                                            try {
                                                nurgling.areas.db.AreaDBManager.getInstance().deleteArea(key);
                                            } catch (Exception e) {
                                                System.err.println("Failed to delete area from database: " + e.getMessage());
                                            }
                                            
                                            NOverlay nol = NUtils.getGameUI().map.nols.get(key);
                                            if(nol != null) {
                                                nol.remove();
                                            }
                                            NUtils.getGameUI().map.nols.remove(key);
                                            NArea area = ((NMapView) NUtils.getGameUI().map).glob.map.areas.get(key);
                                            if(area != null) {
                                                Gob dummy = ((NMapView) NUtils.getGameUI().map).dummys.get(area.gid);
                                                if(dummy!=null) {
                                                    NUtils.getGameUI().map.glob.oc.remove(dummy);
                                                    ((NMapView) NUtils.getGameUI().map).dummys.remove(dummy.id);
                                                }
                                            }
                                            ((NMapView) NUtils.getGameUI().map).glob.map.areas.remove(key);
                                            
                                            // Delete from database if enabled
                                            if ((Boolean) nurgling.NConfig.get(nurgling.NConfig.Key.ndbenable) &&
                                                nurgling.NCore.databaseManager != null && 
                                                nurgling.NCore.databaseManager.isReady()) {
                                                String profile = NUtils.getGameUI().getGenus();
                                                if (profile == null || profile.isEmpty()) {
                                                    profile = "global";
                                                }
                                                nurgling.NCore.databaseManager.getAreaService().deleteAreaAsync(key, profile);
                                            }
                                        }
                                    }
                                    NConfig.needAreasUpdate();
                                    NAreasWidget.this.showPath(NAreasWidget.this.currentPath);
                                }
                            }
                            else if (option.name.equals("Show all in folder"))
                            {
                                String folderPath = currentPath + "/" + text.text();
                                setFolderHide(folderPath, false);
                                if (hide != null) hide.a = false;
                            }
                            else if (option.name.equals("Hide all in folder"))
                            {
                                String folderPath = currentPath + "/" + text.text();
                                setFolderHide(folderPath, true);
                                if (hide != null) hide.a = true;
                            }
                        }
                        uimsg("cancel");
                    }

                };
            }
            Widget par = parent;
            Coord pos = c;
            while(par!=null && !(par instanceof GameUI))
            {
                pos = pos.add(par.c);
                par = par.parent;
            }
            ui.root.add(menu, pos.add(UI.scale(25,38)));
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

    /**
     * Выбирает зону по ID и устанавливает на неё фокус
     */
    public void selectAreaById(int areaId) {
        synchronized (items) {
            for (int i = 0; i < items.size(); i++) {
                AreaItem item = items.get(i);
                if (item.area != null && item.area.id == areaId) {
                    al.sel = item;
                    select(areaId);
                    // Прокрутка к выбранному элементу
                    int scrollPos = i * (al.itemh + al.marg);
                    al.scrollval(scrollPos);
                    return;
                }
            }
        }
    }

    /**
     * Обновляет имя зоны в списке без перестроения всего списка
     */
    public void updateAreaName(int areaId, String newName) {
        synchronized (items) {
            for (AreaItem item : items) {
                if (item.area != null && item.area.id == areaId) {
                    item.text.settext(newName);
                    return;
                }
            }
        }
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

    public class AreaList extends SListBox<AreaItem, Widget> {
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
                    //item.resize(new Coord(searchF.sz.x - removei[0].sz().x  + UI.scale(4), item.sz.y));
                    add(item);
                }

                @Override
                public boolean mousedown(MouseDownEvent ev) {
                    boolean psel = sel == item;
                    super.mousedown(ev);
                    if(!psel) {
                        String value = item.text.text();
                    }
                    return super.mousedown(ev);
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

        @Override
        public void change(AreaItem item) {
            if(item != null && !item.isDir && item.area==null && item.rootPath != null) {
                showPath(item.rootPath);
            }
            else
                super.change(item);
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
        CurrentSpecialisationList(Coord sz) {
            super(sz, UI.scale(24));
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
                }

                @Override
                public boolean mousedown(MouseDownEvent ev) {
                    return super.mousedown(ev);
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
        NFlowerMenu menu;
        TexI icon;
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
                                    SpecialisationItem.this.text.settext(item.name + "(" + option.name + ")");
                                    item.subtype = option.name;
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
                },UI.scale(new Coord(135,4)));
            }
            pack();
            sz.y = UI.scale(24);
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
    public void hide() {
        super.hide();
        // ВАЖНО: Не удаляем лейблы зон при закрытии окна, если включен тоггл "показывать все зоны"
        // Лейблы будут скрыты через проверку в NAreaLabel.draw()
        if(NUtils.getGameUI()!=null && NUtils.getGameUI().map!=null && !createMode) {
            NGameUI gui = NUtils.getGameUI();
            boolean showAllZones = false;
            if (gui.mmapw != null && gui.mmapw.miniMap != null && gui.mmapw.miniMap instanceof nurgling.widgets.NMiniMap) {
                showAllZones = ((nurgling.widgets.NMiniMap) gui.mmapw.miniMap).showAllZonesAlways;
            }
            // Удаляем лейблы только если тоггл не включен
            if (!showAllZones) {
                ((NMapView)NUtils.getGameUI().map).destroyDummys();
            }
        }
    }

    @Override
    public boolean show(boolean show) {
        if(show)
        {
            showPath(currentPath);
            ((NMapView)NUtils.getGameUI().map).initDummys();
        }
        return super.show(show);
    }

    /**
     * Import areas from server JSON file to database.
     * Supports old server structure with uuid, zone_sync, last_update fields.
     */
    private void importAreasFromJsonFile() {
        if (nurgling.NCore.databaseManager == null) {
            NUtils.getGameUI().msg("Database is not connected");
            return;
        }

        if (!nurgling.NCore.databaseManager.isReady()) {
            NUtils.getGameUI().msg("Database is not ready");
            return;
        }

        java.awt.EventQueue.invokeLater(() -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("JSON files", "json"));
            if (fc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
                return;

            java.io.File selectedFile = fc.getSelectedFile();
            if (selectedFile == null)
                return;

            // Get current profile/genus
            String profile = "global";
            if (NUtils.getGameUI() != null) {
                String genus = NUtils.getGameUI().getGenus();
                if (genus != null && !genus.isEmpty()) {
                    profile = genus;
                }
            }

            final String finalProfile = profile;
            final java.io.File finalFile = selectedFile;

            // Import asynchronously
            NUtils.getGameUI().msg("Importing areas from " + finalFile.getName() + "...");
            
            nurgling.NCore.databaseManager.getAreaService().importAreasFromServerJsonAsync(finalFile, finalProfile)
                .thenAccept(result -> {
                    NUtils.getGameUI().msg("Imported " + result.getImportedCount() + " areas, " + 
                        result.getSkippedCount() + " skipped, " + result.getErrorCount() + " errors");
                    // Reload areas from database
                    if (NUtils.getGameUI() != null && NUtils.getGameUI().map != null) {
                        try {
                            haven.MCache cache = NUtils.getGameUI().map.glob.map;
                            if (cache != null) {
                                cache.loadAreasIfNeeded();
                            }
                        } catch (Exception e) {
                            System.err.println("Failed to reload areas after import: " + e.getMessage());
                        }
                    }
                })
                .exceptionally(e -> {
                    NUtils.getGameUI().error("Failed to import areas: " + e.getMessage());
                    e.printStackTrace();
                    return null;
                });
        });
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

    /**
     * Дублирует зону со всеми параметрами (инпут, аутпут, специализации, цвет, space и т.д.)
     * Название новой зоны = старое название + " 1"
     */
    private void duplicateArea(NArea sourceArea) {
        if (sourceArea == null) {
            NUtils.getGameUI().error("Cannot duplicate: area is null");
            return;
        }

        NMapView mapView = (NMapView) NUtils.getGameUI().map;
        synchronized (mapView.glob.map.areas) {
            // Находим следующий доступный ID
            int newId = 1;
            for (NArea area : mapView.glob.map.areas.values()) {
                if (area.id >= newId) {
                    newId = area.id + 1;
                }
            }

            // Создаем новое название: старое название + " 1"
            String newName = sourceArea.name + " 1";
            HashSet<String> names = new HashSet<>();
            for (NArea area : mapView.glob.map.areas.values()) {
                names.add(area.name);
            }
            // Если название уже существует, добавляем (2), (3) и т.д.
            int counter = 1;
            String finalName = newName;
            while (names.contains(finalName)) {
                counter++;
                finalName = sourceArea.name + " " + counter;
            }

            // Создаем новую зону
            NArea newArea = new NArea(finalName);
            newArea.id = newId;
            newArea.path = sourceArea.path;
            newArea.color = new Color(sourceArea.color.getRed(), sourceArea.color.getGreen(), 
                                     sourceArea.color.getBlue(), sourceArea.color.getAlpha());
            newArea.hide = sourceArea.hide;
            newArea.lastLocalChange = System.currentTimeMillis();

            // Копируем space (глубокая копия)
            newArea.space = new NArea.Space();
            if (sourceArea.space != null && sourceArea.space.space != null) {
                for (Long gridId : sourceArea.space.space.keySet()) {
                    NArea.VArea vArea = sourceArea.space.space.get(gridId);
                    if (vArea != null && vArea.area != null) {
                        haven.Area sourceAreaObj = vArea.area;
                        newArea.space.space.put(gridId, new NArea.VArea(
                            new haven.Area(sourceAreaObj.ul, sourceAreaObj.br)
                        ));
                    }
                }
            }
            newArea.grids_id.clear();
            newArea.grids_id.addAll(sourceArea.grids_id);

            // Копируем jin (JSONArray) - создаем новый объект
            if (sourceArea.jin != null) {
                try {
                    newArea.jin = new JSONArray(sourceArea.jin.toString());
                } catch (Exception e) {
                    newArea.jin = new JSONArray();
                    for (int i = 0; i < sourceArea.jin.length(); i++) {
                        newArea.jin.put(sourceArea.jin.get(i));
                    }
                }
            } else {
                newArea.jin = new JSONArray();
            }

            // Копируем jout (JSONArray) - создаем новый объект
            if (sourceArea.jout != null) {
                try {
                    newArea.jout = new JSONArray(sourceArea.jout.toString());
                } catch (Exception e) {
                    newArea.jout = new JSONArray();
                    for (int i = 0; i < sourceArea.jout.length(); i++) {
                        newArea.jout.put(sourceArea.jout.get(i));
                    }
                }
            } else {
                newArea.jout = new JSONArray();
            }

            // Копируем spec (ArrayList) - создаем новый список с копиями объектов
            newArea.spec = new ArrayList<>();
            if (sourceArea.spec != null) {
                for (NArea.Specialisation spec : sourceArea.spec) {
                    if (spec.subtype != null) {
                        newArea.spec.add(new NArea.Specialisation(spec.name, spec.subtype));
                    } else {
                        newArea.spec.add(new NArea.Specialisation(spec.name));
                    }
                }
            }

            // Копируем jspec если есть
            if (sourceArea.jspec != null) {
                try {
                    newArea.jspec = new JSONArray(sourceArea.jspec.toString());
                } catch (Exception e) {
                    newArea.jspec = new JSONArray();
                    for (int i = 0; i < sourceArea.jspec.length(); i++) {
                        newArea.jspec.put(sourceArea.jspec.get(i));
                    }
                }
            } else {
                newArea.jspec = new JSONArray();
            }

            // Добавляем зону в карту
            mapView.glob.map.areas.put(newId, newArea);

            // Помечаем зону как созданную локально
            nurgling.areas.AllowedZonesManager.getInstance().markAsLocallyCreated(newId, newArea.uuid);
            newArea.hide = false; // Локально созданные зоны всегда видны

            // Подключаем к графу маршрутов
            mapView.routeGraphManager.getGraph().connectAreaToRoutePoints(newArea);

            // Создаем лейбл зоны
            mapView.createAreaLabel(newId);

            // Обновляем конфигурацию
            NConfig.needAreasUpdate();

            // Обновляем список зон
            showPath(currentPath);

            // Выбираем новую зону
            select(newId);

            NUtils.getGameUI().msg("Зона '" + finalName + "' создана");
        }
    }

}
