package nurgling.widgets;

import haven.*;
import nurgling.*;
import nurgling.areas.*;
import nurgling.i18n.L10n;
import org.json.JSONObject;

import java.awt.image.*;
import java.util.*;

public class IconItem extends Widget
{
    // Menu option keys - used for comparison (language-independent)
    private static final String KEY_THRESHOLD = "iconitem.threshold";
    private static final String KEY_DELETE = "iconitem.delete";
    private static final String KEY_MARK_BARTER = "iconitem.mark_barter";
    private static final String KEY_MARK_BARREL = "iconitem.mark_barrel";
    private static final String KEY_UNMARK = "iconitem.unmark";
    private static final String KEY_BY_TYPE = "iconitem.by_type";
    public static final TexI frame = new TexI(Resource.loadimg("nurgling/hud/iconframe"));
    public static final TexI framet = new TexI(Resource.loadimg("nurgling/hud/iconframet"));
    public static final TexI bm = new TexI(Resource.loadimg("nurgling/hud/bartermark"));
    public static final TexI barm = new TexI(Resource.loadimg("nurgling/hud/barrelmark"));
    public JSONObject src;
    TexI tex = null;

    TexI tip;
    TexI q;
    boolean noOpts = false;
    boolean isThreshold = false;

    Coord basec = null;
    NArea.Ingredient.Type type = NArea.Ingredient.Type.CONTAINER;

    int val;

    String name;

    // Сохраняем ссылку на IngredientContainer для доступа из обработчика меню
    private IngredientContainer ingredientContainer = null;
    
    public IconItem(String name, BufferedImage img, Widget parent)
    {
        this.parent = parent;
        this.name = name;
        tip = new TexI(RichText.render(name).img);

        tex = new TexI(img);
        this.sz = UI.scale(new Coord(32, 42));
        
        // Сохраняем ссылку на IngredientContainer, если parent является им
        if(parent instanceof IngredientContainer) {
            this.ingredientContainer = (IngredientContainer) parent;
        } else {
            // Ищем IngredientContainer в иерархии родителей
            Widget current = parent;
            while(current != null) {
                if(current instanceof IngredientContainer) {
                    this.ingredientContainer = (IngredientContainer) current;
                    break;
                }
                current = current.parent;
            }
        }
    }

    public IconItem(String name, TexI img)
    {
        this.name = name;
        tip = new TexI(RichText.render(name).img);

        tex = img;
        this.sz = UI.scale(new Coord(32, 42));
    }

    void update(String name, BufferedImage img)
    {
        this.name = name;
        tip = new TexI(RichText.render(name).img);
        tex = new TexI(img);
    }

    public IconItem()
    {
        this.sz = UI.scale(new Coord(32, 42));
    }

    @Override
    public void draw(GOut g)
    {
        if (tex != null)
        {
            if(isThreshold)
            {
                g.image(framet, Coord.z, UI.scale(32, 42));
                g.image(q, new Coord(UI.scale(16)-q.sz().x/2,UI.scale(28)));
            }
            else
            {
                g.image(frame, Coord.z, UI.scale(32, 32));
            }
            g.image(tex, Coord.z, UI.scale(32,32));
            if(type == NArea.Ingredient.Type.BARTER)
            {
                g.image(bm, UI.scale(16,16), UI.scale(16, 16));
            }
            if(type == NArea.Ingredient.Type.BARREL)
            {
                g.image(barm, UI.scale(16,16), UI.scale(16, 16));
            }
        }
    }

    @Override
    public Object tooltip(Coord c, Widget prev)
    {
        // Если это категория, показываем более информативный tooltip
        if(parent instanceof IngredientContainer) {
            IngredientContainer ic = (IngredientContainer) parent;
            JSONObject itemData = ic.getItemData(name);
            if(itemData != null && itemData.has("isCategory") && itemData.getBoolean("isCategory")) {
                if(itemData.has("originalName")) {
                    return new TexI(RichText.render(name + " (was: " + itemData.getString("originalName") + ")").img);
                } else {
                    return new TexI(RichText.render(name + " (category)").img);
                }
            }
        }
        return tip;
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
        if(ev.b==3)
        {
            if(!noOpts)
                opts(c);
            return true;
        }
        else
        {
            return super.mousedown(ev);
        }

    }

    NFlowerMenu menu;
    
    // Map to reverse lookup: localized name -> key
    private Map<String, String> menuKeyMap = new HashMap<>();
    
    private String addMenuOption(ArrayList<String> opts, String key) {
        String localized = L10n.get(key);
        opts.add(localized);
        menuKeyMap.put(localized, key);
        return localized;
    }

    public void opts( Coord c ) {
        System.out.println("IconItem.opts: Called for name='" + name + "', type=" + type);
        if(menu == null) {
            menuKeyMap.clear();
            ArrayList<String> optList = new ArrayList<>();
            
            if(type==NArea.Ingredient.Type.CONTAINER)
            {
                if (parent instanceof IngredientContainer || parent instanceof DropContainer)
                    addMenuOption(optList, KEY_THRESHOLD);
                addMenuOption(optList, KEY_DELETE);
                if (parent instanceof IngredientContainer) {
                    addMenuOption(optList, KEY_MARK_BARTER);
                    addMenuOption(optList, KEY_MARK_BARREL);
                    // Добавляем опцию "By type" для блоков и досок
                    boolean isBlock = isBlockOrBoard(name);
                    System.out.println("IconItem.opts: name='" + name + "', isBlockOrBoard=" + isBlock + ", parent=" + (parent != null ? parent.getClass().getName() : "null"));
                    if (isBlock) {
                        String localized = addMenuOption(optList, KEY_BY_TYPE);
                        System.out.println("IconItem.opts: Added KEY_BY_TYPE as '" + localized + "'");
                    }
                }
            }
            else
            {
                if(parent instanceof IngredientContainer || parent instanceof DropContainer) {
                    addMenuOption(optList, KEY_THRESHOLD);
                }
                addMenuOption(optList, KEY_DELETE);
                if(parent instanceof IngredientContainer) {
                    addMenuOption(optList, KEY_UNMARK);
                    // Добавляем опцию "By type" для блоков и досок
                    boolean isBlock = isBlockOrBoard(name);
                    System.out.println("IconItem.opts: name='" + name + "', isBlockOrBoard=" + isBlock);
                    if (isBlock) {
                        addMenuOption(optList, KEY_BY_TYPE);
                    }
                }
            }
            
            String[] opts = optList.toArray(new String[0]);
            menu = new NFlowerMenu(opts) {

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
                public void nchoose(NPetal option)
                {
                    System.out.println("IconItem.nchoose: Called! option=" + (option != null ? option.name : "null"));
                    System.out.println("IconItem.nchoose: menuKeyMap contents: " + menuKeyMap);
                    
                    // Показываем сообщение в игре для отладки
                    if(NUtils.getGameUI() != null && option != null) {
                        NUtils.getGameUI().msg("Menu: " + option.name, java.awt.Color.YELLOW);
                    }
                    
                    if(option!=null)
                    {
                        // Get the key from the localized name
                        String key = menuKeyMap.get(option.name);
                        System.out.println("IconItem.nchoose: Looking for key for option.name='" + option.name + "', found key='" + key + "'");
                        
                        if (key == null) {
                            key = "";
                            System.err.println("IconItem.nchoose: WARNING - key not found in menuKeyMap!");
                            System.err.println("IconItem.nchoose: menuKeyMap keys: " + menuKeyMap.keySet());
                            if(NUtils.getGameUI() != null) {
                                NUtils.getGameUI().msg("Error: Key not found!", java.awt.Color.RED);
                            }
                        }
                        
                        if(NUtils.getGameUI() != null) {
                            NUtils.getGameUI().msg("Key: " + key, java.awt.Color.CYAN);
                        }
                        
                        if (key.equals(KEY_THRESHOLD))
                        {
                            Widget par = IconItem.this.parent;
                            Coord pos = IconItem.this.c.add(UI.scale(32, 38));
                            while (par != null && !(par instanceof GameUI))
                            {
                                pos = pos.add(par.c);
                                par = par.parent;
                            }
                            SetThreshold st = new SetThreshold(val);
                            ui.root.add(st, pos);

                        }
                        else if(key.equals(KEY_DELETE))
                        {
                            ((BaseIngredientContainer)IconItem.this.parent).delete(IconItem.this.name);
                        }
                        else if(key.equals(KEY_MARK_BARTER))
                        {
                            ((IngredientContainer)IconItem.this.parent).setType(IconItem.this.name, NArea.Ingredient.Type.BARTER);
                        }
                        else if(key.equals(KEY_MARK_BARREL))
                        {
                            ((IngredientContainer)IconItem.this.parent).setType(IconItem.this.name, NArea.Ingredient.Type.BARREL);
                        }
                        else if(key.equals(KEY_UNMARK))
                        {
                            ((IngredientContainer)IconItem.this.parent).setType(IconItem.this.name, NArea.Ingredient.Type.CONTAINER);
                        }
                        else if(key.equals(KEY_BY_TYPE))
                        {
                            // Используем сохраненную ссылку на IngredientContainer
                            if(IconItem.this.ingredientContainer != null) {
                                IngredientContainer ic = IconItem.this.ingredientContainer;
                                
                                // Получаем данные из JSON
                                JSONObject itemData = ic.getItemData(IconItem.this.name);
                                
                                // Если это уже категория (есть originalName), то ничего не делаем
                                if(itemData != null && itemData.has("originalName")) {
                                    if(NUtils.getGameUI() != null) {
                                        NUtils.getGameUI().msg("Already a category!", java.awt.Color.RED);
                                    }
                                    return;
                                }
                                
                                // Используем текущее имя - это конкретный блок/доска
                                if(NUtils.getGameUI() != null) {
                                    NUtils.getGameUI().msg("Converting to category...", java.awt.Color.CYAN);
                                }
                                ic.setCategory(IconItem.this.name);
                            } else {
                                System.err.println("IconItem.KEY_BY_TYPE: ingredientContainer is null!");
                                if(NUtils.getGameUI() != null) {
                                    NUtils.getGameUI().msg("Error: Container not found", java.awt.Color.RED);
                                }
                            }
                        }
                    }
                    uimsg("cancel");
                }

            };
            menu.shiftMode = true;
            Widget par = parent;
            Coord pos = IconItem.this.c.add(UI.scale(60,60));
            while(par!=null && !(par instanceof GameUI))
            {
                pos = pos.add(par.c);
                par = par.parent;
            }
            ui.root.add(menu, pos);
        }
    }

    /**
     * Проверяет, является ли предмет блоком или доской (но не категорией)
     */
    private boolean isBlockOrBoard(String itemName) {
        if (itemName == null) {
            System.out.println("isBlockOrBoard: itemName is null");
            return false;
        }
        // Исключаем категории
        if("Block of Wood".equals(itemName) || "Board".equals(itemName)) {
            System.out.println("isBlockOrBoard: '" + itemName + "' is a category, returning false");
            return false;
        }
        // Проверяем, начинается ли название с "Block of " или "Board of " (с пробелом в конце)
        boolean result = itemName.startsWith("Block of ") || itemName.startsWith("Board of ");
        System.out.println("isBlockOrBoard: '" + itemName + "' -> " + result);
        return result;
    }

    public JSONObject toJson() {
        return src;
    }


    class SetThreshold extends Window
    {
        public SetThreshold(int val)
        {
            super(UI.scale(140,25), L10n.get("iconitem.threshold"));
            TextEntry te;
            prev = add(te = new TextEntry(UI.scale(80),String.valueOf(val)));
            add(new Button(UI.scale(50), L10n.get("iconitem.btn_set")){
                @Override
                public void click()
                {
                    super.click();
                    try
                    {
                        IconItem.this.isThreshold = true;
                        IconItem.this.val = Integer.valueOf(te.text());
                        IconItem.this.q = new TexI(NStyle.iiqual.render(te.text()).img);
                        if(IconItem.this.parent instanceof IngredientContainer)
                            ((IngredientContainer)IconItem.this.parent).setThreshold(IconItem.this.name,IconItem.this.val);
                        else
                            ((DropContainer)IconItem.this.parent).setThreshold(IconItem.this.name,IconItem.this.val);
                    }
                    catch (NumberFormatException e)
                    {
                        IconItem.this.isThreshold = false;
                        ((IngredientContainer)IconItem.this.parent).setThreshold(IconItem.this.name,-1);
                    }
                    ui.destroy(SetThreshold.this);

                }
            },prev.pos("ur").add(5,-5));
        }

        @Override
        public void wdgmsg(String msg, Object... args)
        {
            if(msg.equals("close"))
            {
                destroy();
            }
            else
            {
                super.wdgmsg(msg, args);
            }
        }
    }
}
