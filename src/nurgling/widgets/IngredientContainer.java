package nurgling.widgets;

import haven.*;
import haven.Button;
import haven.Window;
import haven.res.lib.itemtex.*;
import nurgling.*;
import nurgling.areas.*;
import nurgling.i18n.L10n;
import nurgling.overlays.NAreaLabel;
import org.json.*;

import java.awt.*;
import java.awt.image.*;
import java.util.*;

public class IngredientContainer extends BaseIngredientContainer {
    protected Integer id = -1;
    public IngredientContainer(String type) {
        super(type);
    }

    public static class RuleButton extends Button {
        NFlowerMenu menu;
        final IngredientContainer ic;
        
        // Localization keys for menu options
        private static final String OPT_SET_THRESHOLDS = "ingredient.set_thresholds";
        private static final String OPT_DELETE_THRESHOLDS = "ingredient.delete_thresholds";
        private static final String OPT_CLEAR = "ingredient.clear";

        public RuleButton(IngredientContainer ing) {
            super(UI.scale(30), Resource.loadsimg("nurgling/hud/buttons/settings/u"));
            this.ic = ing;
        }

        @Override
        public void click() {
            super.click();
            opts(this.c);
        }

        final ArrayList<String> opt = new ArrayList<String>(){{
            add(L10n.get(OPT_SET_THRESHOLDS));
            add(L10n.get(OPT_DELETE_THRESHOLDS));
            add(L10n.get(OPT_CLEAR));
        }};

        public void draw(BufferedImage img) {
            Graphics g = img.getGraphics();
            Coord tc = sz.sub(Utils.imgsz(cont)).div(2);
            g.drawImage(cont, tc.x, tc.y, null);
            g.dispose();
        }

        class SetThreshold extends Window {
            public SetThreshold(int val) {
                super(UI.scale(140,25), L10n.get("ingredient.threshold"));
                TextEntry te;
                prev = add(te = new TextEntry(UI.scale(80),String.valueOf(val)));
                add(new Button(UI.scale(50), L10n.get("ingredient.btn_set")){
                    @Override
                    public void click() {
                        super.click();
                        try {
                            int val = Integer.parseInt(te.text());
                            for (IconItem item : ic.icons) {
                                item.isThreshold = true;
                                item.val = val;
                                item.q = new TexI(NStyle.iiqual.render(te.text()).img);
                                ic.setThreshold(item.name, item.val);
                            }
                        } catch (NumberFormatException ignored) {
                        }
                        ui.destroy(SetThreshold.this);
                    }
                },prev.pos("ur").add(5,-5));
            }

            @Override
            public void wdgmsg(String msg, Object... args) {
                if(msg.equals("close")) {
                    destroy();
                } else {
                    super.wdgmsg(msg, args);
                }
            }
        }

        public void opts(Coord c) {
            if(menu == null) {
                menu = new NFlowerMenu(opt.toArray(new String[0])) {
                    public boolean mousedown(MouseDownEvent ev) {
                        if(super.mousedown(ev))
                            nchoose(null);
                        return(true);
                    }

                    public void destroy() {
                        menu = null;
                        super.destroy();
                    }

                    @Override
                    public void nchoose(NPetal option) {
                        if(option != null) {
                            if (option.name.equals(L10n.get(OPT_SET_THRESHOLDS))) {
                                SetThreshold st = new SetThreshold(0);
                                ui.root.add(st, c);
                            } else if(option.name.equals(L10n.get(OPT_DELETE_THRESHOLDS))) {
                                for (IconItem item : ic.icons) {
                                    item.isThreshold = false;
                                    item.val = 1;
                                    item.q = null;
                                    ic.delThreshold(item.name);
                                }
                            } else if(option.name.equals(L10n.get(OPT_CLEAR))) {
                                ic.deleteAll();
                            }
                        }
                        uimsg("cancel");
                    }
                };
                Widget par = parent;
                Coord pos = c;
                while(par != null && !(par instanceof GameUI)) {
                    pos = c.add(par.c);
                    par = par.parent;
                }
                ui.root.add(menu, pos);
            }
        }
    }

    @Override
    public void addIcon(JSONObject res) {
        // Если это категория, используем стандартную иконку
        if(res.has("isCategory") && res.getBoolean("isCategory")) {
            String categoryName = res.getString("name");
            System.out.println("addIcon: Processing category '" + categoryName + "'");
            BufferedImage categoryIcon = null;
            
            // Сначала пробуем загрузить из поля static, если есть
            if(res.has("static")) {
                String staticPath = res.getString("static");
                System.out.println("addIcon: Trying to load from static='" + staticPath + "'");
                try {
                    // Используем ItemTex.create для загрузки из static
                    categoryIcon = ItemTex.create(res);
                    System.out.println("addIcon: ItemTex.create result: " + (categoryIcon != null ? "success, size=" + categoryIcon.getWidth() + "x" + categoryIcon.getHeight() : "null"));
                } catch (Exception e) {
                    System.err.println("addIcon: ItemTex.create failed: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            // Если не удалось загрузить из static, используем getCategoryIcon
            if(categoryIcon == null) {
                System.out.println("addIcon: Trying getCategoryIcon for '" + categoryName + "'");
                categoryIcon = getCategoryIcon(categoryName);
                if(categoryIcon != null) {
                    System.out.println("addIcon: Successfully loaded icon from getCategoryIcon");
                } else {
                    System.err.println("addIcon: getCategoryIcon returned null");
                }
            }
            
            if(categoryIcon != null) {
                Ingredient ing = new Ingredient(categoryName, categoryIcon);
                items.add(ing);
                IconItem it = add(new IconItem(ing.name, ing.image, this), UI.scale(new Coord(35*((items.size()-1)%5),51*((items.size()-1)/5))).add(new Coord(5,5)));
                it.basec = new Coord(it.c);
                icons.add(it);
                maxy = UI.scale(51)*((items.size()-1)/5 - 5);
                cury = Math.min(cury, Math.max(maxy, 0));
                
                System.out.println("addIcon: Created IconItem for category '" + categoryName + "' with icon size " + categoryIcon.getWidth() + "x" + categoryIcon.getHeight());
                
                // Устанавливаем тип и порог если есть
                if(res.has("th")) {
                    it.isThreshold = true;
                    it.val = (Integer)res.get("th");
                    it.q = new TexI(NStyle.iiqual.render(String.valueOf(it.val)).img);
                }
                if(res.has("type")) {
                    it.type = NArea.Ingredient.Type.valueOf((String)res.get("type"));
                }
                return;
            } else {
                System.err.println("addIcon: Failed to load icon for category '" + categoryName + "', falling back to super.addIcon");
            }
        }
        
        super.addIcon(res);
        if(res.has("th")) {
            IconItem it = icons.get(icons.size()-1);
            it.isThreshold = true;
            it.val = (Integer)res.get("th");
            it.q = new TexI(NStyle.iiqual.render(String.valueOf(it.val)).img);
        }
        if(res.has("type")) {
            IconItem it = icons.get(icons.size()-1);
            it.type = NArea.Ingredient.Type.valueOf((String)res.get("type"));
        }
    }
    
    /**
     * Получает стандартную иконку для категории блоков или досок
     * Использует ванильные иконки из игры
     */
    private BufferedImage getCategoryIcon(String categoryName) {
        try {
            if("Block of Wood".equals(categoryName)) {
                // Ванильная иконка блока дерева
                return Resource.loadsimg("gfx/invobjs/wblock-oak");
            } else if("Board".equals(categoryName)) {
                // Ванильная иконка доски
                return Resource.loadsimg("gfx/invobjs/board-oak");
            }
        } catch (Exception e) {
            System.err.println("Failed to load category icon for " + categoryName + ": " + e.getMessage());
        }
        return null;
    }

    @Override
    public void addItem(String name, JSONObject res) {
        if(res != null) {
            JSONArray data;
            NArea area = NUtils.getArea(id);
            if(area == null) return;
            if(type.equals("in"))
                data = area.jin;
            else
                data = area.jout;

            boolean find = false;
            for(int i = 0; i < data.length(); i++) {
                if(((JSONObject) data.get(i)).get("name").equals(name)) {
                    find = true;
                    break;
                }
            }
            if(!find) {
                res.put("name", name);
                res.put("type", NArea.Ingredient.Type.CONTAINER.toString());
                addIcon(res);
                data.put(res);
                
                // Auto-rename area if it starts with "New Area" and this is the first item
                if(area.name.startsWith("New Area") && area.jin.length() + area.jout.length() == 1) {
                    renameAreaToItem(area, name);
                }
                
                NConfig.needAreasUpdate();
            }
        }
    }
    
    /**
     * Renames area to the item name if area name starts with "New Area"
     */
    private void renameAreaToItem(NArea area, String itemName) {
        ((NMapView) NUtils.getGameUI().map).changeAreaName(area.id, itemName);
        
        // Update area label on map
        Gob dummy = ((NMapView) NUtils.getGameUI().map).dummys.get(area.gid);
        if(dummy != null) {
            Gob.Overlay ol = dummy.findol(NAreaLabel.class);
            if(ol != null && ol.spr instanceof NAreaLabel) {
                NAreaLabel tl = (NAreaLabel) ol.spr;
                tl.update();
            }
        }
        
        // Update area name in the list item without rebuilding the entire list
        if(NUtils.getGameUI().areas != null) {
            NAreasWidget areasWidget = NUtils.getGameUI().areas;
            areasWidget.updateAreaName(area.id, itemName);
        }
    }

    public void load(Integer id) {
        this.id = id;
        items.clear();
        for(IconItem it : icons) {
            it.destroy();
        }
        icons.clear();
        if(id != -1) {
            NArea area = NUtils.getArea(id);
            if(area == null) return;
            JSONArray data;
            if(type.equals("in"))
                data = area.jin;
            else
                data = area.jout;

            for(int i = 0; i < data.length(); i++) {
                addIcon(((JSONObject) data.get(i)));
            }
        }
    }


    @Override
    public boolean drop(Drop ev) {
        if(id != -1) {
            String name = ((NGItem) ev.src.item).name();
            JSONObject res = ItemTex.save(((NGItem) ev.src.item).spr);
            addItem(name, res);
        }
        return super.drop(ev);
    }

    public void setThreshold(String name, int val) {
        JSONArray data;
        if(NUtils.getArea(id) == null) return;
        if(type.equals("in"))
            data = NUtils.getArea(id).jin;
        else
            data = NUtils.getArea(id).jout;

        for(int i = 0; i < data.length(); i++) {
            if(((JSONObject) data.get(i)).get("name").equals(name)) {
                ((JSONObject) data.get(i)).put("th",val);
                NConfig.needAreasUpdate();
                return;
            }
        }
    }

    public void delThreshold(String name) {
        JSONArray data;
        if(NUtils.getArea(id) == null) return;
        if(type.equals("in"))
            data = NUtils.getArea(id).jin;
        else
            data = NUtils.getArea(id).jout;

        for(int i = 0; i < data.length(); i++) {
            if(((JSONObject) data.get(i)).get("name").equals(name)) {
                ((JSONObject) data.get(i)).remove("th");
                NConfig.needAreasUpdate();
                return;
            }
        }
    }

    public void setType(String name, NArea.Ingredient.Type val) {
        JSONArray data;
        if(NUtils.getArea(id) == null) return;
        if(type.equals("in"))
            data = NUtils.getArea(id).jin;
        else
            data = NUtils.getArea(id).jout;

        for(int i = 0; i < data.length(); i++) {
            if(((JSONObject) data.get(i)).get("name").equals(name)) {
                ((JSONObject) data.get(i)).put("type",val.toString());
                icons.get(i).type = val;
                NConfig.needAreasUpdate();
                return;
            }
        }
    }

    @Override
    public void delete(String name) {
        JSONArray data;
        if(NUtils.getArea(id) == null) return;
        if(type.equals("in"))
            data = NUtils.getArea(id).jin;
        else
            data = NUtils.getArea(id).jout;

        for(int i = 0; i < data.length(); i++) {
            if(((JSONObject) data.get(i)).get("name").equals(name)) {
                data.remove(i);
                NConfig.needAreasUpdate();
                load(id);
                return;
            }
        }
    }

    @Override
    public void deleteAll() {
        JSONArray data;
        if(NUtils.getArea(id) == null) return;
        if(type.equals("in"))
            data = NUtils.getArea(id).jin;
        else
            data = NUtils.getArea(id).jout;

        // Удаляем все элементы по одному (JSONArray не имеет метода clear())
        while(data.length() > 0) {
            data.remove(0);
        }
        NConfig.needAreasUpdate();
        load(id);
    }

    /**
     * Заменяет конкретный блок/доску на категорию
     */
    public void setCategory(String itemName) {
        System.out.println("setCategory: START - itemName='" + itemName + "', id=" + id + ", type=" + type);
        
        if(id == null || id == -1) {
            System.err.println("setCategory: id is null or -1!");
            if(NUtils.getGameUI() != null) {
                NUtils.getGameUI().msg("Error: Zone not selected!", java.awt.Color.RED);
            }
            return;
        }
        
        JSONArray data;
        if(NUtils.getArea(id) == null) {
            System.err.println("setCategory: Area is null for id " + id);
            if(NUtils.getGameUI() != null) {
                NUtils.getGameUI().msg("Error: Area not found for id " + id, java.awt.Color.RED);
            }
            return;
        }
        if(type.equals("in"))
            data = NUtils.getArea(id).jin;
        else
            data = NUtils.getArea(id).jout;

        System.out.println("setCategory: Looking for '" + itemName + "' in " + data.length() + " items");
        
        for(int i = 0; i < data.length(); i++) {
            JSONObject item = (JSONObject) data.get(i);
            String currentName = item.getString("name");
            
            // Проверяем как текущее имя, так и originalName (если уже была категория)
            String checkName = currentName;
            if(item.has("originalName")) {
                checkName = item.getString("originalName");
            }
            
            System.out.println("setCategory: Checking [" + i + "] currentName='" + currentName + "', checkName='" + checkName + "', itemName='" + itemName + "'");
            
            // Проверяем прямое совпадение или совпадение с originalName
            boolean nameMatch = currentName.equals(itemName) || checkName.equals(itemName);
            if(nameMatch) {
                // Определяем категорию на основе оригинального имени (если есть) или текущего
                String nameToCheck = checkName.equals(itemName) ? checkName : currentName;
                String category = getCategoryForItem(nameToCheck);
                
                if(category != null) {
                    // Сохраняем оригинальное имя в поле originalName для отображения
                    item.put("originalName", nameToCheck);
                    // Заменяем name на категорию
                    item.put("name", category);
                    // Добавляем флаг категории
                    item.put("isCategory", true);
                    
                    // Сохраняем статический путь к иконке для категории (используем ванильные иконки)
                    if("Block of Wood".equals(category)) {
                        item.put("static", "gfx/invobjs/wblock-oak");
                    } else if("Board".equals(category)) {
                        item.put("static", "gfx/invobjs/board-oak");
                    }
                    
                    NConfig.needAreasUpdate();
                    
                    // Перезагружаем контейнер для обновления иконки
                    load(id);
                    
                    // Принудительно обновляем отображение
                    if(parent != null) {
                        parent.pack();
                        pack();
                    }
                    
                    // Показываем сообщение об успехе
                    if(NUtils.getGameUI() != null) {
                        NUtils.getGameUI().msg("Converted to category: " + category, java.awt.Color.GREEN);
                    }
                    
                    return;
                }
            }
        }
        System.err.println("setCategory: Item '" + itemName + "' not found!");
    }

    /**
     * Получает категорию для конкретного блока или доски
     */
    private String getCategoryForItem(String itemName) {
        if(itemName == null) {
            System.err.println("getCategoryForItem: itemName is null");
            return null;
        }
        
        System.out.println("getCategoryForItem: Checking '" + itemName + "'");
        
        // Если уже категория, возвращаем null
        if("Block of Wood".equals(itemName) || "Board".equals(itemName)) {
            System.out.println("getCategoryForItem: Already a category, returning null");
            return null;
        }
        
        // Проверяем начало строки для блоков и досок
        if(itemName.startsWith("Block of ")) {
            System.out.println("getCategoryForItem: Matches Block of, returning 'Block of Wood'");
            return "Block of Wood";
        } else if(itemName.startsWith("Board of ")) {
            System.out.println("getCategoryForItem: Matches Board of, returning 'Board'");
            return "Board";
        }
        
        System.out.println("getCategoryForItem: No match, returning null");
        return null;
    }
    
    /**
     * Получает данные предмета из JSON
     */
    public JSONObject getItemData(String itemName) {
        JSONArray data;
        if(NUtils.getArea(id) == null) {
            System.err.println("getItemData: Area is null for id " + id);
            return null;
        }
        if(type.equals("in"))
            data = NUtils.getArea(id).jin;
        else
            data = NUtils.getArea(id).jout;

        System.out.println("getItemData: Looking for '" + itemName + "' in " + data.length() + " items");
        for(int i = 0; i < data.length(); i++) {
            JSONObject item = (JSONObject) data.get(i);
            String itemNameInJson = item.getString("name");
            String checkName = itemNameInJson;
            // Также проверяем originalName, если есть
            if(item.has("originalName")) {
                checkName = item.getString("originalName");
            }
            System.out.println("getItemData: [" + i + "] name='" + itemNameInJson + "', checkName='" + checkName + "'");
            if(itemNameInJson.equals(itemName) || checkName.equals(itemName)) {
                System.out.println("getItemData: Found match!");
                return item;
            }
        }
        System.err.println("getItemData: Item '" + itemName + "' not found!");
        return null;
    }
}