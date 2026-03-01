package nurgling.widgets;

import haven.Button;
import haven.*;
import haven.Frame;
import haven.Label;
import static haven.Inventory.*;

import haven.render.Render;
import haven.res.lib.itemtex.*;
import nurgling.*;
import nurgling.actions.bots.*;
import nurgling.areas.*;
import nurgling.i18n.L10n;
import nurgling.sessions.BotExecutor;
import nurgling.tools.*;
import nurgling.actions.bots.SubRecipeResolver;
import org.json.*;

import java.awt.*;
import java.awt.image.*;
import java.util.List;
import java.util.*;

public class NMakewindow extends Widget {
//    public static final Text.Foundry fnd = new Text.Foundry(Text.sans, 12);

    public static Text.Furnace fnd = new PUtils.BlurFurn(new Text.Foundry(Text.sans.deriveFont(java.awt.Font.BOLD), 12).aa(true), UI.scale(1), UI.scale(1), Color.BLACK);
    public static Text.Furnace fnd2 = new Text.Foundry(Text.sans, 12).aa(true);
    public static Text qmodl = null;
    private static Text getQmodl() {
        if (qmodl == null) qmodl = fnd.render(L10n.get("craft.quality"));
        return qmodl;
    }
    public static final TexI aready = new TexI(Resource.loadsimg("nurgling/hud/autocraft/ready"));
    public static final TexI anotfound = new TexI(Resource.loadsimg("nurgling/hud/autocraft/notfound"));
    public static final TexI categories = new TexI(Resource.loadsimg("nurgling/hud/autocraft/spec"));
    public static final TexI ignoreOverlay = new TexI(Resource.loadsimg("nurgling/hud/autocraft/ignore"));
    public static Text tooll = null;
    private static Text getTooll() {
        if (tooll == null) tooll = fnd.render(L10n.get("craft.tools"));
        return tooll;
    }
    public static final Coord boff = UI.scale(new Coord(7, 9));
    public String rcpnm;
    public String recipeResource;
    public List<Spec> inputs = Collections.emptyList();
    public List<Spec> outputs = Collections.emptyList();
    public List<Indir<Resource>> qmod = Collections.emptyList();
    public List<Indir<Resource>> tools = new ArrayList<>();;
    private int xoff = UI.scale(45), qmy = UI.scale(38), outy = UI.scale(65);
    public static final Text.Foundry nmf = new Text.Foundry(Text.serif, 20).aa(true);
    private static double softcap = 0;
    private static Tex softTex = null;
    private static Tex softTexLabel = null;
    public CheckBox noTransfer = null;
    public boolean autoMode = false;
    private IButton savePresetBtn = null;
    private int pendingRecipeIdx = -1;
    private boolean recipeCached = false;

    static final NArea CRAFT_SENTINEL = new NArea("[Craft]");
    static final NArea LOCAL_SENTINEL = new NArea("[Local Zone]");
    static final NArea INVENTORY_SENTINEL = new NArea("[Leave in Inventory]");
    private List<AutoSpecRow> autoInputRows = null;
    private List<AutoSpecRow> autoOutputRows = null;
    private Label inputLabel, resultLabel;
    private Widget craftBtn, craftAllBtn;
    private static final int AUTO_ROW_H = 42;
    private static final int AUTO_SUB_ROW_H = 30;
    private static final int AUTO_DROPBOX_W = 180;
    private static final int AUTO_DROPBOX_X = 230;
    private static final int AUTO_SUB_INDENT = 30;

    private class AutoSubRow {
        String name;
        int count;
        Dropbox<NArea> zoneDropbox;
        List<NArea> zones = new ArrayList<>();
        NArea selectedZone;
        int yPos;
        Spec parentSpec;

        AutoSubRow(String name, int count, int y, Spec parentSpec) {
            this.name = name;
            this.count = count;
            this.yPos = y;
            this.parentSpec = parentSpec;
            final AutoSubRow sub = this;
            zoneDropbox = new Dropbox<NArea>(UI.scale(AUTO_DROPBOX_W), 8, UI.scale(16)) {
                @Override
                protected NArea listitem(int i) {
                    return i < sub.zones.size() ? sub.zones.get(i) : null;
                }
                @Override
                protected int listitems() {
                    return Math.max(1, sub.zones.size());
                }
                @Override
                protected void drawitem(GOut g, NArea item, int i) {
                    if(item != null) {
                        g.text(item.name != null ? item.name : "#" + item.id, Coord.z);
                    } else {
                        g.text("---", Coord.z);
                    }
                }
                @Override
                public void change(NArea item) {
                    super.change(item);
                    sub.selectedZone = item;
                    if(sub.parentSpec != null && item != null) {
                        sub.parentSpec.subIngredientZones.put(sub.name, item);
                    }
                }
            };
            NMakewindow.this.add(zoneDropbox, new Coord(UI.scale(AUTO_DROPBOX_X), y));
            refreshZones();
        }

        void refreshZones() {
            zones.clear();
            Set<Integer> seenIds = new HashSet<>();
            for(NArea a : NContext.findAllIn(name)) {
                if(seenIds.add(a.id)) zones.add(a);
            }
            ArrayList<JSONObject> members = VSpec.categories.get(name);
            if(members != null) {
                for(JSONObject obj : members) {
                    String memberName = obj.optString("name");
                    if(memberName != null) {
                        for(NArea a : NContext.findAllIn(memberName)) {
                            if(seenIds.add(a.id)) zones.add(a);
                        }
                    }
                }
            }
            if(!zones.isEmpty()) {
                zoneDropbox.change(zones.get(0));
            }
        }

        void destroy() {
            zoneDropbox.destroy();
        }

        void setY(int y) {
            this.yPos = y;
            zoneDropbox.c = new Coord(UI.scale(AUTO_DROPBOX_X), y);
        }
    }

    private class AutoSpecRow {
        Spec spec;
        Dropbox<NArea> zoneDropbox;
        List<NArea> zones = new ArrayList<>();
        boolean isOutput;
        boolean zonesLoaded = false;
        boolean canSubCraft = false;
        int yPos;
        List<AutoSubRow> subRows = null;

        private int extraCount() {
            int n = 0;
            if(!isOutput) {
                if(canSubCraft) n++;
                n++; // [Local Zone]
            } else {
                n++; // [Leave in Inventory]
                n++; // [Local Zone]
            }
            return n;
        }

        AutoSpecRow(Spec spec, boolean isOutput, int y) {
            this.spec = spec;
            this.isOutput = isOutput;
            this.yPos = y;
            final AutoSpecRow row = this;
            zoneDropbox = new Dropbox<NArea>(UI.scale(AUTO_DROPBOX_W), 8, UI.scale(16)) {
                @Override
                protected NArea listitem(int i) {
                    if(i < row.zones.size()) return row.zones.get(i);
                    int extra = i - row.zones.size();
                    if(!row.isOutput) {
                        if(row.canSubCraft && extra == 0) return CRAFT_SENTINEL;
                        int localIdx = row.canSubCraft ? 1 : 0;
                        if(extra == localIdx) return LOCAL_SENTINEL;
                    } else {
                        if(extra == 0) return INVENTORY_SENTINEL;
                        if(extra == 1) return LOCAL_SENTINEL;
                    }
                    return null;
                }
                @Override
                protected int listitems() {
                    return row.zones.size() + row.extraCount();
                }
                @Override
                protected void drawitem(GOut g, NArea item, int i) {
                    if(item == CRAFT_SENTINEL) {
                        g.chcolor(255, 165, 0, 255);
                        g.text("[Craft]", Coord.z);
                        g.chcolor();
                    } else if(item == INVENTORY_SENTINEL) {
                        g.chcolor(180, 255, 180, 255);
                        g.text("[Leave in Inventory]", Coord.z);
                        g.chcolor();
                    } else if(item == LOCAL_SENTINEL) {
                        g.chcolor(100, 200, 255, 255);
                        g.text("[Local Zone]", Coord.z);
                        g.chcolor();
                    } else if(item != null) {
                        String label = getZoneDisplayLabel(item, row.spec, row.isOutput);
                        g.text(label, Coord.z);
                    } else {
                        g.text("---", Coord.z);
                    }
                }
                @Override
                public void change(NArea item) {
                    super.change(item);
                    if(item == CRAFT_SENTINEL) {
                        row.spec.selectedZone = null;
                        row.spec.isSubCraft = true;
                        row.spec.isLocalZone = false;
                        row.spec.isInventory = false;
                        expandSubCraft();
                    } else if(item == INVENTORY_SENTINEL) {
                        row.spec.selectedZone = null;
                        row.spec.isSubCraft = false;
                        row.spec.isLocalZone = false;
                        row.spec.isInventory = true;
                        collapseSubCraft();
                    } else if(item == LOCAL_SENTINEL) {
                        row.spec.selectedZone = null;
                        row.spec.isSubCraft = false;
                        row.spec.isLocalZone = true;
                        row.spec.isInventory = false;
                        collapseSubCraft();
                    } else {
                        row.spec.selectedZone = item;
                        row.spec.isSubCraft = false;
                        row.spec.isLocalZone = false;
                        row.spec.isInventory = false;
                        collapseSubCraft();
                    }
                    autoSaveZoneSelections();
                }
            };
            int dropboxCenterY = y + (invsq.sz().y - zoneDropbox.sz.y) / 2;
            NMakewindow.this.add(zoneDropbox, new Coord(UI.scale(AUTO_DROPBOX_X), dropboxCenterY));
        }

        void expandSubCraft() {
            collapseSubCraft();
            String itemName = getEffectiveItemName(spec);
            if(itemName == null) return;
            Set<RecipeIngredientCache.RecipeEntry> recipes = RecipeIngredientCache.findOutputRecipesForItem(itemName);
            if(recipes.isEmpty()) return;
            RecipeIngredientCache.RecipeEntry recipe = recipes.iterator().next();

            List<RecipeIngredientCache.IngredientSpec> specs = RecipeIngredientCache.getRecipeSpecs(recipe.paginaResource);
            if(specs.isEmpty()) return;
            spec.subIngredientZones.clear();
            subRows = new ArrayList<>();
            int subY = yPos + UI.scale(AUTO_ROW_H);
            for(RecipeIngredientCache.IngredientSpec is : specs) {
                subRows.add(new AutoSubRow(is.name, is.count, subY, spec));
                subY += UI.scale(AUTO_SUB_ROW_H);
            }
            repositionAllRows();
        }

        void collapseSubCraft() {
            if(subRows != null) {
                for(AutoSubRow sr : subRows) sr.destroy();
                subRows = null;
                repositionAllRows();
            }
        }

        int getTotalHeight() {
            int h = UI.scale(AUTO_ROW_H);
            if(subRows != null) {
                h += subRows.size() * UI.scale(AUTO_SUB_ROW_H);
            }
            return h;
        }

        void refreshZones() {
            String itemName = getEffectiveItemName(spec);
            if(itemName == null) return;
            zonesLoaded = true;
            List<NArea> found;
            if(isOutput) {
                found = NContext.findAllOut(itemName);
            } else {
                found = NContext.findAllIn(itemName);
            }
            zones.clear();
            zones.addAll(found);
            canSubCraft = !isOutput && SubRecipeResolver.canSubCraft(itemName);

            NArea prevSel = spec.selectedZone;
            if(spec.selectedZoneId > 0 && prevSel == null) {
                for(NArea a : zones) {
                    if(a.id == spec.selectedZoneId) {
                        spec.selectedZone = a;
                        zoneDropbox.change(a);
                        break;
                    }
                }
            } else if(prevSel != null && zones.contains(prevSel)) {
                zoneDropbox.change(prevSel);
            } else if(spec.isInventory && isOutput) {
                zoneDropbox.change(INVENTORY_SENTINEL);
            } else if(spec.isLocalZone) {
                zoneDropbox.change(LOCAL_SENTINEL);
            } else if(!zones.isEmpty()) {
                zoneDropbox.change(zones.get(0));
            } else if(canSubCraft) {
                zoneDropbox.change(CRAFT_SENTINEL);
            } else if(isOutput) {
                zoneDropbox.change(INVENTORY_SENTINEL);
            }
        }

        void setY(int y) {
            this.yPos = y;
            int dropboxCenterY = y + (invsq.sz().y - zoneDropbox.sz.y) / 2;
            zoneDropbox.c = new Coord(UI.scale(AUTO_DROPBOX_X), dropboxCenterY);
            if(subRows != null) {
                int subY = y + UI.scale(AUTO_ROW_H);
                for(AutoSubRow sr : subRows) {
                    sr.setY(subY);
                    subY += UI.scale(AUTO_SUB_ROW_H);
                }
            }
        }

        void destroy() {
            collapseSubCraft();
            zoneDropbox.destroy();
        }
    }

    private void repositionAllRows() {
        int y = UI.scale(28);
        if(autoInputRows != null) {
            for(AutoSpecRow row : autoInputRows) {
                row.setY(y);
                y += row.getTotalHeight();
            }
        }
        autoQmodY = y + UI.scale(2);
        if(!qmod.isEmpty()) {
            y = autoQmodY + UI.scale(22);
        }
        resultLabel.c = new Coord(0, y + UI.scale(4));
        y += UI.scale(20);
        if(autoOutputRows != null) {
            for(AutoSpecRow row : autoOutputRows) {
                row.setY(y);
                y += row.getTotalHeight();
            }
        }
        int btnY = y + UI.scale(8);
        craftBtn.c = new Coord(UI.scale(230), btnY);
        craft_num.c = new Coord(UI.scale(165), btnY + UI.scale(7));
        craftAllBtn.c = new Coord(UI.scale(325), btnY);
        if(savePresetBtn != null) {
            savePresetBtn.c = UI.scale(new Coord(340, 5));
        }
        pack();
        if(parent != null) parent.pack();
    }

    private String getEffectiveItemName(Spec s) {
        if(s.ing != null && !s.ing.isIgnored) return s.ing.name;
        return s.name;
    }

    private static String findCategoryFor(String itemName) {
        if(VSpec.categories.containsKey(itemName)) return itemName;
        for(Map.Entry<String, ArrayList<JSONObject>> entry : VSpec.categories.entrySet()) {
            for(JSONObject obj : entry.getValue()) {
                if(itemName.equals(obj.optString("name"))) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private static String getZoneDisplayLabel(NArea area, Spec spec, boolean isOutput) {
        String label = area.name != null ? area.name : "#" + area.id;
        if(isOutput && area.jout != null) {
            String itemName = spec.ing != null ? spec.ing.name : spec.name;
            if(itemName != null) {
                for(int i = 0; i < area.jout.length(); i++) {
                    try {
                        JSONObject item = area.jout.getJSONObject(i);
                        if(itemName.equals(item.optString("name"))) {
                            Object thObj = item.opt("th");
                            if(thObj instanceof Number) {
                                int th = ((Number) thObj).intValue();
                                if(th > 0) label += " (q" + th + "+)";
                            }
                            break;
                        }
                    } catch(Exception ignored) {}
                }
            }
        }
        return label;
    }

    private static final OwnerContext.ClassResolver<NMakewindow> ctxr = new OwnerContext.ClassResolver<NMakewindow>()
            .add(Glob.class, wdg -> wdg.ui.sess.glob)
            .add(Session.class, wdg -> wdg.ui.sess);
    public class Spec implements GSprite.Owner, ItemInfo.SpriteOwner {
        public Indir<Resource> res;
        public MessageBuf sdt;
        public Tex num;
        public String name;
        public int count;
        public GSprite spr;
        private Object[] rawinfo;
        private List<ItemInfo> info;

        public Ingredient ing = null;

        public Spec(Indir<Resource> res, Message sdt, int num, Object[] info) {
            this.res = res;
            this.sdt = new MessageBuf(sdt);
            if(num >= 0)
                this.num = new TexI(Utils.outline2(Text.render(Integer.toString(num), Color.WHITE).img, Utils.contrast(Color.WHITE)));
            else
                this.num = null;
            this.rawinfo = info;
            this.count = num;
        }

        public GSprite sprite() {
            if(spr == null)
                spr = GSprite.create(this, res.get(), sdt.clone());;
            return(spr);
        }

        public void draw(GOut g) {
            try {
                if(ing==null || !autoMode)
                {
                    // Если это категория блоков или досок, показываем стандартную иконку
                    if(name != null && (name.equals("Block of Wood") || name.equals("Board"))) {
                        BufferedImage categoryIcon = getCategoryIcon(name);
                        if(categoryIcon != null) {
                            g.image(new TexI(categoryIcon), Coord.z, UI.scale(32,32));
                        } else {
                            sprite().draw(g);
                        }
                    } else {
                        sprite().draw(g);
                    }
                }
                else
                {
                    // В режиме автокрафта также проверяем категории
                    if(ing != null && ing.name != null && (ing.name.equals("Block of Wood") || ing.name.equals("Board"))) {
                        BufferedImage categoryIcon = getCategoryIcon(ing.name);
                        if(categoryIcon != null) {
                            g.image(new TexI(categoryIcon), Coord.z, UI.scale(32,32));
                        } else {
                            g.image(new TexI(ing.img), Coord.z, UI.scale(32,32));
                        }
                    } else {
                        g.image(new TexI(ing.img), Coord.z, UI.scale(32,32));
                    }
                }
            } catch(Loading e) {}
            if(num != null)
                g.aimage(num, Inventory.sqsz, 1.0, 1.0);
        }
        
        /**
         * Получает стандартную иконку для категории блоков или досок
         * Использует ванильные иконки из игры
         */
        private BufferedImage getCategoryIcon(String categoryName) {
            try {
                if("Block of Wood".equals(categoryName)) {
                    return Resource.loadsimg("gfx/invobjs/wblock-oak");
                } else if("Board".equals(categoryName)) {
                    return Resource.loadsimg("gfx/invobjs/board-oak");
                }
            } catch (Exception e) {
                // Если не удалось загрузить, вернем null
            }
            return null;
        }

        private int opt = 0;
        public boolean opt() {
            if(opt == 0) {
                try {
                    List<ItemInfo> infoList = info();
                    boolean found = false;
                    for (ItemInfo inf : infoList) {
                        String className = inf.getClass().getName();
                        if (className.endsWith("$Optional")) {
                            found = true;
                            break;
                        }
                    }
                    opt = found ? 1 : 2;
                } catch(Loading l) {
                    return(false);
                } catch(Exception e) {
                    e.printStackTrace();
                    return(false);
                }
            }
            return(opt == 1);
        }

        public BufferedImage shorttip() {
            List<ItemInfo> info = info();
            if(info.isEmpty()) {
                Resource.Tooltip tt = res.get().layer(Resource.tooltip);
                if(tt == null)
                    return(null);
                return(Text.render(tt.text()).img);
            }
            return(ItemInfo.shorttip(info()));
        }
        public BufferedImage longtip() {
            List<ItemInfo> info = info();
            BufferedImage img;
            if(info.isEmpty()) {
                Resource.Tooltip tt = res.get().layer(Resource.tooltip);
                if(tt == null)
                    return(null);
                img = Text.render(tt.text()).img;
            } else {
                img = ItemInfo.longtip(info);
            }
            Resource.Pagina pg = res.get().layer(Resource.pagina);
            if(pg != null)
                img = ItemInfo.catimgs(0, img, RichText.render("\n" + pg.text, 200).img);
            return(img);
        }

        private Random rnd = null;
        public Random mkrandoom() {
            if(rnd == null)
                rnd = new Random();
            return(rnd);
        }
        public Resource getres() {return(res.get());}
        public <T> T context(Class<T> cl) {return(ctxr.context(cl, NMakewindow.this));}
        @Deprecated
        public Glob glob() {return(ui.sess.glob);}

        public List<ItemInfo> info() {
            if(info == null)
                info = ItemInfo.buildinfo(this, rawinfo);
            return(info);
        }
        public Resource resource() {return(res.get());}


        void tick(double dt)
        {
            // Load name from sprite, or directly from resource in headless mode
            if (name == null && (spr != null || nurgling.headless.Headless.isHeadless()))
            {
                if (!res.get().name.contains("coin"))
                {
                    if (res.get() != null)
                    {
                        name = ItemInfo.Name.Default.get(this);
                    }
                }
            }
            if(NMakewindow.this.autoMode && name!=null)
            {
                logisticin = (NContext.findIn(name) != null);
                if(!logisticin)
                {
                    categories = (VSpec.categories.get(name)!=null);
                    if (!categories) {
                        subCraftable = SubRecipeResolver.canSubCraft(name);
                    } else {
                        subCraftable = false;
                    }
                }
                else
                {
                    subCraftable = false;
                }
                logisticout = (NContext.findOut(name,1) != null);
                if(!logisticout)
                {
                    categories = (VSpec.categories.get(name)!=null);
                }
                if("Block of Wood".equals(name) || "Board".equals(name)) {
                    categories = true;
                }
                for(Spec s : inputs) {
                    if(s.categories && s.ing!=null)
                    {
                        s.ing.logistic = (NContext.findIn(s.ing.name) != null);
                        if (!s.ing.logistic && !s.ing.isIgnored) {
                            s.ing.subCraftable = SubRecipeResolver.canSubCraft(s.ing.name);
                        } else {
                            s.ing.subCraftable = false;
                        }
                    }
                }
                for(Spec s : outputs) {
                    if(s.categories && s.ing!=null)
                    {
                        s.ing.logistic = (NContext.findOut(s.ing.name, 1) != null);
                    }
                }
            }
        }

        String name()
        {
            return name;
        }

        public boolean logisticin = false;
        public boolean logisticout = false;
        public boolean categories = false;
        public boolean useCategory = false;
        public boolean subCraftable = false;
        public NArea selectedZone = null;
        public boolean isSubCraft = false;
        public boolean isLocalZone = false;
        public boolean isInventory = false;
        public int selectedZoneId = -1;
        public Map<String, NArea> subIngredientZones = new HashMap<>();

    }

    public void tick(double dt) {
        for(Spec s : inputs) {
            if(s.spr != null)
                s.spr.tick(dt);
            s.tick(dt);
        }
        for(Spec s : outputs) {
            if(s.spr != null)
                s.spr.tick(dt);
            s.tick(dt);
        }
        if(cat!=null) {
            cat.raise();
            cat.tick(dt);
        }

        // Update Save Preset button visibility
        if (savePresetBtn != null) {
            savePresetBtn.visible = autoMode && allInputsConfigured();
        }

        // Refresh zone lists for auto mode dropboxes
        if(autoMode) {
            if(autoInputRows != null) {
                for(AutoSpecRow row : autoInputRows) {
                    if(!row.zonesLoaded && getEffectiveItemName(row.spec) != null) {
                        row.refreshZones();
                    }
                }
            }
            if(autoOutputRows != null) {
                for(AutoSpecRow row : autoOutputRows) {
                    if(!row.zonesLoaded && getEffectiveItemName(row.spec) != null) {
                        row.refreshZones();
                    }
                }
            }
        }

        // Cache ingredient/output -> recipe mapping and persist to DB
        // inputCache: item used AS ingredient (for Alt+RMB)
        // outputCache: item PRODUCED by recipe (for Shift+Click "how to make")
        if(!recipeCached && recipeResource != null && !inputs.isEmpty()) {
            boolean allNamed = true;
            for(Spec s : inputs) {
                if(s.name == null) { allNamed = false; break; }
            }
            for(Spec s : outputs) {
                if(s.name == null) { allNamed = false; break; }
            }
            if(allNamed) {
                List<String> ingredientNames = new ArrayList<>();
                List<RecipeIngredientCache.IngredientSpec> specs = new ArrayList<>();
                for(Spec s : inputs) {
                    ingredientNames.add(s.name);
                    specs.add(new RecipeIngredientCache.IngredientSpec(s.name, s.count));
                }
                RecipeIngredientCache.addInputsAndPersist(ingredientNames, recipeResource, rcpnm);
                RecipeIngredientCache.setRecipeSpecs(recipeResource, specs);
                List<String> outputNames = new ArrayList<>();
                for(Spec s : outputs) {
                    outputNames.add(s.name);
                }
                if(!outputNames.isEmpty()) {
                    RecipeIngredientCache.addOutputsAndPersist(outputNames, recipeResource, rcpnm);
                }
                recipeCached = true;
            }
        }
    }

    /**
     * Check if all category inputs have their ingredient selected.
     */
    private boolean allInputsConfigured() {
        for (Spec s : inputs) {
            if (s.categories && s.ing == null) {
                if (s.isSubCraft || s.selectedZone != null || s.isLocalZone || s.useCategory) continue;
                return false;
            }
        }
        return true;
    }

    void autoSaveZoneSelections() {
        if (recipeResource == null || !autoMode) return;
        try {
            nurgling.scenarios.CraftPreset preset = new nurgling.scenarios.CraftPreset();
            preset.setRecipeName(rcpnm);
            preset.setRecipeResource(recipeResource);

            java.util.List<nurgling.scenarios.CraftPreset.InputSpec> pInputs = new java.util.ArrayList<>();
            for (Spec s : inputs) {
                nurgling.scenarios.CraftPreset.InputSpec pi = new nurgling.scenarios.CraftPreset.InputSpec();
                pi.setName(s.name);
                pi.setCategory(s.categories);
                pi.setCount(s.count);
                pi.setSubCraft(s.isSubCraft);
                pi.setLocalZone(s.isLocalZone);
                pi.setUseCategory(s.useCategory);
                if (s.selectedZone != null) {
                    pi.setSelectedZoneId(s.selectedZone.id);
                    pi.setZoneName(s.selectedZone.name);
                }
                if (s.ing != null) {
                    pi.setPreferredIngredient(s.ing.name);
                    pi.setIgnored(s.ing.isIgnored);
                }
                pInputs.add(pi);
            }
            preset.setInputs(pInputs);

            java.util.List<nurgling.scenarios.CraftPreset.OutputSpec> pOutputs = new java.util.ArrayList<>();
            for (Spec s : outputs) {
                nurgling.scenarios.CraftPreset.OutputSpec po = new nurgling.scenarios.CraftPreset.OutputSpec();
                po.setName(s.name);
                po.setCount(s.count);
                po.setInventory(s.isInventory);
                po.setLocalZone(s.isLocalZone);
                if (s.selectedZone != null) {
                    po.setSelectedZoneId(s.selectedZone.id);
                    po.setZoneName(s.selectedZone.name);
                }
                pOutputs.add(po);
            }
            preset.setOutputs(pOutputs);

            nurgling.scenarios.CraftPresetManager.getInstance().saveAutoPreset(recipeResource, preset);
        } catch (Exception e) {
            System.err.println("Auto-save zone selections failed: " + e.getMessage());
        }
    }

    void autoRestoreZoneSelections() {
        if (recipeResource == null) return;
        try {
            nurgling.scenarios.CraftPreset auto =
                    nurgling.scenarios.CraftPresetManager.getInstance().getAutoPreset(recipeResource);
            if (auto == null) return;

            for (nurgling.scenarios.CraftPreset.InputSpec pi : auto.getInputs()) {
                for (Spec s : inputs) {
                    if (s.name != null && s.name.equals(pi.getName())) {
                        s.isSubCraft = pi.isSubCraft();
                        s.isLocalZone = pi.isLocalZone();
                        s.useCategory = pi.isUseCategory();
                        if (pi.getSelectedZoneId() > 0) {
                            s.selectedZoneId = pi.getSelectedZoneId();
                            try {
                                for (java.util.Map.Entry<Integer, nurgling.areas.NArea> entry :
                                        NUtils.getGameUI().map.glob.map.areas.entrySet()) {
                                    if (entry.getKey() == pi.getSelectedZoneId()) {
                                        s.selectedZone = entry.getValue();
                                        break;
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                        break;
                    }
                }
            }
            for (int i = 0; i < auto.getOutputs().size() && i < outputs.size(); i++) {
                nurgling.scenarios.CraftPreset.OutputSpec po = auto.getOutputs().get(i);
                Spec s = outputs.get(i);
                s.isInventory = po.isInventory();
                s.isLocalZone = po.isLocalZone();
                if (po.getSelectedZoneId() > 0) {
                    s.selectedZoneId = po.getSelectedZoneId();
                    try {
                        for (java.util.Map.Entry<Integer, nurgling.areas.NArea> entry :
                                NUtils.getGameUI().map.glob.map.areas.entrySet()) {
                            if (entry.getKey() == po.getSelectedZoneId()) {
                                s.selectedZone = entry.getValue();
                                break;
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            System.err.println("Auto-restore zone selections failed: " + e.getMessage());
        }
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
        // Shift+Click on INPUT: "how to make this ingredient" (server findrcps + outputCache)
        if(ev.b == 1 && (ui.modflags() & UI.MOD_SHIFT) != 0) {
            int idx = findInputIdx(ev.c);
            if(idx >= 0) {
                pendingRecipeIdx = idx;
                wdgmsg("findrcps", idx);
                return true;
            }
            // Shift+Click on OUTPUT: "where is this result used as ingredient" (inputCache)
            int outIdx = findOutputIdx(ev.c);
            if(outIdx >= 0 && outIdx < outputs.size()) {
                String outName = outputs.get(outIdx).name();
                if(outName != null) {
                    showUsageRecipes(outName);
                }
                return true;
            }
        }
        if(autoMode)
        {
            // Проверяем клик по чекбоксу "all" для категорий в inputs
            Coord sc = new Coord(xoff, 0);
            boolean popt = false;
            for(Spec s : inputs) {
                boolean opt = s.opt();
                if (opt != popt)
                    sc = sc.add(10, 0);
                if(s.categories && (s.name != null && (s.name.equals("Board") || s.name.equals("Block of Wood")))) {
                    Coord checkboxPos = sc.add(Inventory.sqsz.x - UI.scale(12), UI.scale(2));
                    if(ev.c.isect(checkboxPos, UI.scale(10, 10))) {
                        s.useCategory = !s.useCategory;
                        return true;
                    }
                }
                sc = sc.add(Inventory.sqsz.x, 0);
                popt = opt;
            }
            
            // Проверяем клик по чекбоксу "all" для категорий в outputs
            sc = new Coord(xoff, outy);
            popt = false;
            for(Spec s : outputs) {
                boolean opt = s.opt();
                if (opt != popt)
                    sc = sc.add(10, 0);
                if(s.categories && (s.name != null && (s.name.equals("Board") || s.name.equals("Block of Wood")))) {
                    Coord checkboxPos = sc.add(Inventory.sqsz.x - UI.scale(12), UI.scale(2));
                    if(ev.c.isect(checkboxPos, UI.scale(10, 10))) {
                        s.useCategory = !s.useCategory;
                        return true;
                    }
                }
                sc = sc.add(Inventory.sqsz.x, 0);
                popt = opt;
            }
            
            sc = new Coord(xoff, 0);
            popt = false;
            if (clickForCategories(inputs, popt, sc, ev.c)) return true;
            sc = new Coord(xoff, outy);
            if (clickForCategories(outputs, popt, sc, ev.c)) return true;
        }
        return super.mousedown(ev);
    }

    private int findInputIdx(Coord c) {
        Coord sc = new Coord(xoff, 0);
        boolean popt = false;
        int idx = 0;
        for(Spec s : inputs) {
            boolean opt = s.opt();
            if(opt != popt)
                sc = sc.add(10, 0);
            if(c.isect(sc, Inventory.sqsz))
                return idx;
            sc = sc.add(Inventory.sqsz.x, 0);
            popt = opt;
            idx++;
        }
        return -1;
    }

    private int findOutputIdx(Coord c) {
        Coord sc = new Coord(xoff, outy);
        for(int idx = 0; idx < outputs.size(); idx++) {
            if(c.isect(sc, Inventory.sqsz))
                return idx;
            sc = sc.add(Inventory.sqsz.x, 0);
        }
        return -1;
    }

    private boolean clickForCategories(List<Spec> outputs, boolean popt, Coord sc, Coord c) {
        for (Spec s : outputs) {
            boolean opt = s.opt();
            if (opt != popt)
                sc = sc.add(10, 0);
            if (s.categories) {
                if (c.isect(sc, Inventory.sqsz)) {
                    boolean isOpt = s.opt();
                    ArrayList<JSONObject> categoryItems = VSpec.categories.get(s.name);
                    if (cat == null) {
                        // If category has only one item and it's not optional, auto-select it
                        if (categoryItems != null && categoryItems.size() == 1 && !isOpt) {
                            s.ing = new Ingredient(categoryItems.get(0));
                            return true;
                        }
                        NUtils.getGameUI().add(cat = new Categories(categoryItems, s, isOpt), sc.add(this.parent.c).add(this.c).add(Inventory.sqsz.x / 2, Inventory.sqsz.y * 2).add(UI.scale(2, 2)));
                        pack();
                        NUtils.getGameUI().craftwnd.lower();
                        cat.raise();
                        return true;
                    }
                }
            }
            sc = sc.add(Inventory.sqsz.x, 0);
            popt = opt;
        }
        return false;
    }


    TextEntry craft_num;
    public static final KeyBinding kb_make = KeyBinding.get("make/one", KeyMatch.forcode(java.awt.event.KeyEvent.VK_ENTER, 0));
    public static final KeyBinding kb_makeall = KeyBinding.get("make/all", KeyMatch.forcode(java.awt.event.KeyEvent.VK_ENTER, KeyMatch.C));
    public NMakewindow(String rcpnm) {
        inputLabel = add(new Label(L10n.get("craft.input")), new Coord(0, UI.scale(8)));
        int inputW = inputLabel.sz.x;
        resultLabel = add(new Label(L10n.get("craft.result")), new Coord(0, outy + UI.scale(8)));
        int resultW = resultLabel.sz.x;
        xoff = Math.max(inputW, resultW) + UI.scale(10);

        craftBtn = add(new Button(UI.scale(85), L10n.get("craft.craft")), UI.scale(new Coord(230, 75)));
        ((Button)craftBtn).action(() -> craft());
        craftBtn.setgkey(kb_make);
        add(craft_num = new TextEntry(UI.scale(55), ""), UI.scale(new Coord(165, 82)));
        craftAllBtn = add(new Button(UI.scale(85), L10n.get("craft.craft_all")), UI.scale(new Coord(325, 75)));
        ((Button)craftAllBtn).action(() -> craftAll());
        craftAllBtn.setgkey(kb_makeall);
        add(new ICheckBox(NStyle.auto[0],NStyle.auto[1],NStyle.auto[2],NStyle.auto[3]){
            @Override
            public void changed(boolean val)
            {
                super.changed(val);
                autoMode = val;
                if(val) {
                    if(!RecipeIngredientCache.isDbLoaded()) {
                        RecipeIngredientCache.loadFromDatabase();
                    }
                    autoRestoreZoneSelections();
                    rebuildAutoLayout();
                } else {
                    clearAutoLayout();
                }
            }
        }, UI.scale(new Coord(365, 5)));

        noTransfer = new CheckBox("");
        noTransfer.a = false;

        // Save Preset button - only visible in auto mode when all inputs are configured
        // Scale icons to 2/3 size and position left of quantity input
        int scaledW = NStyle.savei[0].back.getWidth() * 2 / 3;
        int scaledH = NStyle.savei[0].back.getHeight() * 2 / 3;
        Coord scaledSz = new Coord(scaledW, scaledH);
        BufferedImage scaledUp = PUtils.convolve(NStyle.savei[0].back, scaledSz, CharWnd.iconfilter);
        BufferedImage scaledDown = PUtils.convolve(NStyle.savei[1].back, scaledSz, CharWnd.iconfilter);
        BufferedImage scaledHover = PUtils.convolve(NStyle.savei[2].back, scaledSz, CharWnd.iconfilter);
        savePresetBtn = add(new IButton(scaledUp, scaledDown, scaledHover) {
            @Override
            public void click() {
                openSavePresetDialog();
            }
        }, UI.scale(new Coord(340, 5)));
        savePresetBtn.visible = false;

        pack();
        this.rcpnm = rcpnm;

        // Capture recipe resource from MenuGrid.lastPagina while it's still valid
        if (MenuGrid.lastPagina != null) {
            try {
                this.recipeResource = MenuGrid.lastPagina.res().name;
            } catch (Loading l) {
                // Resource not loaded yet
            }
        }
    }

    private void openSavePresetDialog() {
        NUtils.getGameUI().add(new SaveCraftPresetDialog(this), UI.scale(new Coord(200, 200)));
    }

    public void uimsg(String msg, Object... args) {
        if(msg == "inpop") {
            List<Spec> inputs = new LinkedList<Spec>();
            for(int i = 0; i < args.length;) {
                int resid = (Integer)args[i++];
                Message sdt = (args[i] instanceof byte[])?new MessageBuf((byte[])args[i++]):MessageBuf.nil;
                int num = (Integer)args[i++];
                Object[] info = {};
                if((i < args.length) && (args[i] instanceof Object[]))
                    info = (Object[])args[i++];
                inputs.add(new Spec(ui.sess.getres(resid), sdt, num, info));
            }
            this.inputs = inputs;
            if(autoMode) rebuildAutoLayout();
        } else if(msg == "opop") {
            List<Spec> outputs = new LinkedList<Spec>();
            for(int i = 0; i < args.length;) {
                int resid = (Integer)args[i++];
                Message sdt = (args[i] instanceof byte[])?new MessageBuf((byte[])args[i++]):MessageBuf.nil;
                int num = (Integer)args[i++];
                Object[] info = {};
                if((i < args.length) && (args[i] instanceof Object[]))
                    info = (Object[])args[i++];
                outputs.add(new Spec(ui.sess.getres(resid), sdt, num, info));
            }
            this.outputs = outputs;
            if(autoMode) rebuildAutoLayout();
        } else if(msg == "qmod") {
            List<Indir<Resource>> qmod = new ArrayList<Indir<Resource>>();
            for(Object arg : args)
                qmod.add(ui.sess.getres((Integer)arg));
            this.qmod = qmod;
        } else if(msg == "tool") {
            tools.add(ui.sess.getres((Integer)args[0]));
        } else if(msg == "inprcps") {
            int idx = Utils.iv(args[0]);
            List<MenuGrid.Pagina> rcps = new ArrayList<>();
            GameUI gui = getparent(GameUI.class);
            if(gui != null && gui.menu != null) {
                for(int a = 1; a < args.length; a++)
                    rcps.add(gui.menu.paginafor(ui.sess.getresv(args[a])));
            }
            if(idx == pendingRecipeIdx) {
                // Supplement server results with OUTPUT cache only
                // (recipes that PRODUCE this ingredient — "how to make it")
                if(gui != null && gui.menu != null && idx >= 0 && idx < inputs.size()) {
                    String ingName = inputs.get(idx).name();
                    if(ingName != null) {
                        List<String> searchNames = new ArrayList<>();
                        searchNames.add(ingName);
                        searchNames.addAll(VSpec.getCategory(ingName));
                        // Search output cache: recipes that produce this item
                        Set<RecipeIngredientCache.RecipeEntry> cached =
                            RecipeIngredientCache.findOutputRecipes(searchNames);
                        // DB fallback — search output type only
                        if(cached.isEmpty() && NCore.databaseManager != null
                                && NCore.databaseManager.isReady()
                                && NCore.databaseManager.getCraftRecipeService() != null) {
                            try {
                                cached = NCore.databaseManager.getCraftRecipeService()
                                    .findByProducts(searchNames);
                            } catch(Exception e) {}
                        }
                        // Merge: add cache results not already in server results
                        Set<String> existing = new HashSet<>();
                        for(MenuGrid.Pagina p : rcps) {
                            try { existing.add(p.res().name); } catch(Loading e) {}
                        }
                        Set<String> cacheRes = new HashSet<>();
                        for(RecipeIngredientCache.RecipeEntry entry : cached) {
                            cacheRes.add(entry.paginaResource);
                        }
                        for(MenuGrid.Pagina pag : gui.menu.paginae) {
                            try {
                                String rn = pag.res().name;
                                if(cacheRes.contains(rn) && !existing.contains(rn)) {
                                    rcps.add(pag);
                                    existing.add(rn);
                                }
                            } catch(Loading e) {}
                        }
                    }
                }
                showRecipeMenu(rcps);
                pendingRecipeIdx = -1;
            }
        } else {
            super.uimsg(msg, args);
        }
    }

    /**
     * Show recipes where the given item is used AS INGREDIENT (inputCache).
     * Called on Shift+Click on OUTPUT items.
     */
    private void showUsageRecipes(String itemName) {
        GameUI gui = getparent(GameUI.class);
        if(gui == null || gui.menu == null) return;

        // Trigger DB load on first use
        if(!RecipeIngredientCache.isDbLoaded()) {
            RecipeIngredientCache.loadFromDatabase();
        }

        // Search names: item name + VSpec category groups it belongs to
        List<String> searchNames = new ArrayList<>();
        searchNames.add(itemName);
        searchNames.addAll(VSpec.getCategory(itemName));

        // Search input cache: recipes that CONSUME this item
        Set<RecipeIngredientCache.RecipeEntry> cached =
            RecipeIngredientCache.findInputRecipes(searchNames);

        // DB fallback — input type only
        if(cached.isEmpty() && NCore.databaseManager != null
                && NCore.databaseManager.isReady()
                && NCore.databaseManager.getCraftRecipeService() != null) {
            try {
                cached = NCore.databaseManager.getCraftRecipeService()
                    .findByIngredients(searchNames);
            } catch(Exception e) {}
        }
        if(cached.isEmpty()) return;

        // Match against available paginae
        Set<String> cacheRes = new HashSet<>();
        for(RecipeIngredientCache.RecipeEntry entry : cached) {
            cacheRes.add(entry.paginaResource);
        }
        List<MenuGrid.Pagina> available = new ArrayList<>();
        for(MenuGrid.Pagina pag : gui.menu.paginae) {
            try {
                if(cacheRes.contains(pag.res().name)) {
                    available.add(pag);
                }
            } catch(Loading e) {}
        }

        showRecipeMenu(available);
    }

    private void showRecipeMenu(List<MenuGrid.Pagina> recipes) {
        if(recipes.isEmpty()) return;
        if(recipes.size() == 1) {
            recipes.get(0).button().use(new MenuGrid.Interaction(1, ui.modflags()));
            return;
        }
        recipes.sort((a, b) -> {
            try {
                return a.button().name().compareTo(b.button().name());
            } catch(Loading e) { return 0; }
        });
        SListMenu.of(UI.scale(250, 120), recipes,
                pag -> pag.button().name(),
                pag -> pag.button().img(),
                pag -> pag.button().use(new MenuGrid.Interaction(1, ui.modflags())))
            .addat(this, ui.mc.sub(rootpos(Coord.z)));
    }

    /**
     * Draws an orange indicator for ingredients that can be sub-crafted.
     */
    private void drawSubCraftIndicator(GOut sg) {
        int sz = UI.scale(8);
        Coord pos = new Coord(UI.scale(1), UI.scale(1));
        sg.chcolor(255, 165, 0, 220);
        sg.frect(pos, new Coord(sz, sz));
        sg.chcolor(200, 120, 0, 255);
        sg.rect(pos, new Coord(sz, sz));
        sg.chcolor();
    }

    public static final Coord qmodsz = UI.scale(20, 20);
    private static final WeakHashMap<Indir<Resource>, Tex> qmicons = new WeakHashMap<>();
    private Tex qmicon(Indir<Resource> qm) {
        synchronized (qmicons) {
            return qmicons.computeIfAbsent(qm, NMakewindow.this::buildQTex);
        }
    }

    public void draw(GOut g) {
        if(autoMode && autoInputRows != null && !autoInputRows.isEmpty()) {
            drawAutoMode(g);
            return;
        }
        Coord c = new Coord(xoff, 0);
        boolean popt = false;
        for(Spec s : inputs) {
            boolean opt = s.opt();
            if(opt != popt)
                c = c.add(10, 0);
            GOut sg = g.reclip(c, invsq.sz());
            if(opt) {
                sg.chcolor(0, 255, 0, 255);
                sg.image(invsq, Coord.z);
                sg.chcolor();
            } else {
                sg.image(invsq, Coord.z);
            }
            s.draw(sg);
            
            // Рисуем чекбокс "all" для категорий
            if(autoMode && s.categories && (s.name != null && (s.name.equals("Board") || s.name.equals("Block of Wood")))) {
                Coord checkboxPos = new Coord(Inventory.sqsz.x - UI.scale(12), UI.scale(2));
                // Рисуем простой чекбокс
                if(s.useCategory) {
                    sg.chcolor(0, 255, 0, 255);
                    sg.frect(checkboxPos, UI.scale(10, 10));
                    sg.chcolor();
                    sg.chcolor(255, 255, 255, 255);
                    sg.line(checkboxPos.add(UI.scale(2), UI.scale(5)), checkboxPos.add(UI.scale(4), UI.scale(7)), 1);
                    sg.line(checkboxPos.add(UI.scale(4), UI.scale(7)), checkboxPos.add(UI.scale(8), UI.scale(2)), 1);
                    sg.chcolor();
                } else {
                    sg.chcolor(255, 255, 255, 128);
                    sg.frect(checkboxPos, UI.scale(10, 10));
                    sg.chcolor();
                }
            }
            
            c = c.add(Inventory.sqsz.x, 0);
            popt = opt;
            if(autoMode)
            {
                if(s.logisticin)
                {
                    sg.image(aready, Coord.z);
                }
                else
                {
                    if(s.categories)
                    {
                        if(s.ing==null)
                            sg.image(categories, Coord.z);
                        else
                        {
                            if(s.ing.isIgnored)
                            {
                                sg.image(ignoreOverlay, Coord.z);
                            }
                            else if(s.ing.logistic)
                            {
                                sg.image(aready, Coord.z);
                            }
                            else if(s.ing.subCraftable)
                            {
                                drawSubCraftIndicator(sg);
                            }
                            else
                            {
                                sg.image(anotfound, Coord.z);
                            }
                        }
                    }
                    else if(s.subCraftable)
                    {
                        drawSubCraftIndicator(sg);
                    }
                    else
                    {
                        sg.image(anotfound, Coord.z);
                    }
                }
            }
        }
        {
            int x = 0;
            if(!qmod.isEmpty()) {
                x += getQmodl().sz().x + UI.scale(5);
                x = Math.max(x, xoff);
                qmx = x;
                int count = 0;
                double product = 1.0;
                for(Indir<Resource> qm : qmod) {
                    try {
                        Tex t = buildQTex(qm);
                        g.image(t, new Coord(x, qmy));
                        x += t.sz().x + UI.scale(1);

                        for(BAttrWnd.Attr attr: ui.gui.chrwdg.battr.attrs)
                        {
                            if(attr.attr.nm.equals(qm.get().basename()))
                            {
                                count++;
                                product = product * attr.attr.comp;

                                BufferedImage texVal = fnd2.render(String.valueOf(attr.attr.comp)).img;
                                g.image(texVal,new Coord(x, qmy + UI.scale(1)));
                                x += texVal.getWidth() + UI.scale(1);
                                break;
                            }
                        }
                        for(SAttrWnd.SAttr attr: ui.gui.chrwdg.sattr.attrs)
                        {
                            if(attr.attr.nm.equals(qm.get().basename()))
                            {
                                count++;
                                product = product * attr.attr.comp;
                                BufferedImage texVal = fnd2.render(String.valueOf(attr.attr.comp)).img;
                                g.image(texVal,new Coord(x, qmy + UI.scale(1)));
                                x += texVal.getWidth() + UI.scale(1);
                                break;
                            }
                        }
                    } catch(Loading l) {
                    }
                }
                if(count > 0) {
                    x += drawSoftcap(g, new Coord(x, qmy), product, count);
                }
                x += UI.scale(25);

            }
            if(!tools.isEmpty()) {
                g.aimage(getTooll().tex(), new Coord(x, qmy + (qmodsz.y / 2) - UI.scale(2)), 0, 0.5);
                x += getTooll().sz().x + UI.scale(5);
                x = Math.max(x, xoff);
                toolx = x;
                for(Indir<Resource> tool : tools) {
                    try {
                        Tex t = qmicon(tool);
                        g.image(t, new Coord(x, qmy));
                        x += t.sz().x + UI.scale(1);
                    } catch(Loading l) {
                    }
                }
                x += UI.scale(25);
            }
        }
        c = new Coord(xoff, outy);
        for(Spec s : outputs) {
            GOut sg = g.reclip(c, invsq.sz());
            sg.image(invsq, Coord.z);
            s.draw(sg);
            
            // Рисуем чекбокс "all" для категорий
            if(autoMode && s.categories && (s.name != null && (s.name.equals("Board") || s.name.equals("Block of Wood")))) {
                Coord checkboxPos = new Coord(Inventory.sqsz.x - UI.scale(12), UI.scale(2));
                // Рисуем простой чекбокс
                if(s.useCategory) {
                    sg.chcolor(0, 255, 0, 255);
                    sg.frect(checkboxPos, UI.scale(10, 10));
                    sg.chcolor();
                    sg.chcolor(255, 255, 255, 255);
                    sg.line(checkboxPos.add(UI.scale(2), UI.scale(5)), checkboxPos.add(UI.scale(4), UI.scale(7)), 1);
                    sg.line(checkboxPos.add(UI.scale(4), UI.scale(7)), checkboxPos.add(UI.scale(8), UI.scale(2)), 1);
                    sg.chcolor();
                } else {
                    sg.chcolor(255, 255, 255, 128);
                    sg.frect(checkboxPos, UI.scale(10, 10));
                    sg.chcolor();
                }
            }
            
            c = c.add(Inventory.sqsz.x, 0);
            if(autoMode)
            {
                if(s.logisticout)
                {
                    sg.image(aready, Coord.z);
                }
                else
                {
                    if(s.categories)
                    {
                        if(s.ing==null)
                            sg.image(categories, Coord.z);
                        else
                        {
                            if(s.ing.isIgnored)
                            {
                                sg.image(ignoreOverlay, Coord.z);
                            }
                            else if(s.ing.logistic)
                            {
                                sg.image(aready, Coord.z);
                            }
                            else
                            {
                                sg.image(anotfound, Coord.z);
                            }
                        }
                    }
                    else
                    {
                        sg.image(anotfound, Coord.z);
                    }
                }
            }
        }
        super.draw(g);
    }

    private void drawAutoMode(GOut g) {
        for(AutoSpecRow row : autoInputRows) {
            drawAutoRow(g, row);
            if(row.subRows != null) {
                for(AutoSubRow sub : row.subRows) {
                    drawAutoSubRow(g, sub);
                }
            }
        }
        if(!qmod.isEmpty()) {
            int x = 0;
            x += getQmodl().sz().x + UI.scale(5);
            x = Math.max(x, xoff);
            qmx = x;
            int count = 0;
            double product = 1.0;
            for(Indir<Resource> qm : qmod) {
                try {
                    Tex t = buildQTex(qm);
                    g.image(t, new Coord(x, autoQmodY));
                    x += t.sz().x + UI.scale(1);
                    for(BAttrWnd.Attr attr: ui.gui.chrwdg.battr.attrs) {
                        if(attr.attr.nm.equals(qm.get().basename())) {
                            count++;
                            product = product * attr.attr.comp;
                            BufferedImage texVal = fnd2.render(String.valueOf(attr.attr.comp)).img;
                            g.image(texVal, new Coord(x, autoQmodY + UI.scale(1)));
                            x += texVal.getWidth() + UI.scale(1);
                            break;
                        }
                    }
                    for(SAttrWnd.SAttr attr: ui.gui.chrwdg.sattr.attrs) {
                        if(attr.attr.nm.equals(qm.get().basename())) {
                            count++;
                            product = product * attr.attr.comp;
                            BufferedImage texVal = fnd2.render(String.valueOf(attr.attr.comp)).img;
                            g.image(texVal, new Coord(x, autoQmodY + UI.scale(1)));
                            x += texVal.getWidth() + UI.scale(1);
                            break;
                        }
                    }
                } catch(Loading l) {}
            }
            if(count > 0) {
                drawSoftcap(g, new Coord(x, autoQmodY), product, count);
            }
        }
        if(autoOutputRows != null) {
            for(AutoSpecRow row : autoOutputRows) {
                drawAutoRow(g, row);
            }
        }
        super.draw(g);
    }

    private void drawAutoRow(GOut g, AutoSpecRow row) {
        Spec s = row.spec;
        int y = row.yPos;
        int iconX = UI.scale(5);
        Coord iconSz = invsq.sz();
        GOut sg = g.reclip(new Coord(iconX, y), iconSz);
        sg.image(invsq, Coord.z);
        s.draw(sg);

        if(!row.isOutput) {
            if(s.selectedZone != null) {
                sg.image(aready, Coord.z);
            } else if(s.isSubCraft) {
                drawSubCraftIndicator(sg);
            } else if(s.logisticin || (s.ing != null && s.ing.logistic)) {
                sg.image(aready, Coord.z);
            } else if(row.zones.isEmpty() && !row.canSubCraft) {
                sg.image(anotfound, Coord.z);
            }
        } else {
            if(s.selectedZone != null) {
                sg.image(aready, Coord.z);
            } else if(s.logisticout || (s.ing != null && s.ing.logistic)) {
                sg.image(aready, Coord.z);
            } else {
                sg.image(anotfound, Coord.z);
            }
        }

        String displayName = s.name;
        if(s.ing != null) displayName = s.ing.name;
        if(displayName != null) {
            int craftCount = getCraftCount();
            String text = displayName;
            if(s.count > 0) {
                text += " x" + s.count;
                if(craftCount > 1) {
                    text += " [" + (s.count * craftCount) + "]";
                }
            }
            BufferedImage nameImg = fnd2.render(text).img;
            int nameX = iconX + iconSz.x + UI.scale(5);
            int nameY = y + (iconSz.y - nameImg.getHeight()) / 2;
            g.image(nameImg, new Coord(nameX, nameY));
        }
    }

    private void drawAutoSubRow(GOut g, AutoSubRow sub) {
        int y = sub.yPos;
        int indentX = UI.scale(AUTO_SUB_INDENT);

        g.chcolor(180, 180, 180, 200);
        int midY = y + UI.scale(AUTO_SUB_ROW_H) / 2;
        g.line(new Coord(indentX - UI.scale(14), y - UI.scale(4)), new Coord(indentX - UI.scale(14), midY), 1);
        g.line(new Coord(indentX - UI.scale(14), midY), new Coord(indentX - UI.scale(4), midY), 1);
        g.chcolor();

        if(sub.selectedZone != null) {
            g.chcolor(100, 255, 100, 255);
        } else if(sub.zones.isEmpty()) {
            g.chcolor(255, 100, 100, 255);
        }
        int craftCount = getCraftCount();
        String text = "\u2022 " + sub.name;
        if(sub.count > 0) {
            text += " x" + sub.count;
            if(craftCount > 1) {
                text += " [" + (sub.count * craftCount) + "]";
            }
        }
        BufferedImage nameImg = fnd2.render(text).img;
        int nameY = y + (UI.scale(AUTO_SUB_ROW_H) - nameImg.getHeight()) / 2;
        g.image(nameImg, new Coord(indentX, nameY));
        g.chcolor();
    }

    private int getCraftCount() {
        try {
            String cand = craft_num.text();
            if(!cand.isEmpty()) return Math.max(1, Integer.parseInt(cand));
        } catch(NumberFormatException e) {}
        return 1;
    }

    private int drawSoftcap(GOut g, Coord p, double product, int count) {
        if(count > 0) {
            double current = Math.pow(product, 1.0 / count);
            if (current != softcap || softTex == null) {
                softcap = current;
                String format = String.format("%.1f", softcap);
                if (softTex != null) {
                    softTex.dispose();
                }
                softTexLabel = new TexI(fnd.render(L10n.get("craft.softcap")).img);
                softTex = new TexI(fnd2.render(format).img);
            }
            g.image(softTexLabel, p.add(UI.scale(5), UI.scale(-2)));
            g.image(softTex, p.add(UI.scale(5) + softTexLabel.sz().x + UI.scale(3), UI.scale(1)));
            return softTex.sz().x + softTexLabel.sz().x + UI.scale(9);
        }
        return 0;
    }

    private Tex buildQTex(Indir<Resource> res) {
        BufferedImage result = PUtils.convolve(res.get().layer(Resource.imgc).img, qmodsz, CharWnd.iconfilter);
        try {
//            Glob.CAttr attr = NUtils.getGameUI().chrwdg.findattr(res.get().basename());
//            if(attr != null) {
//                result = ItemInfo.catimgsh(1, result, attr.compline().img);
//            }
        } catch (Exception ignored) {
        }
        return new TexI(result);
    }

    public static void invalidate(String name) {
        synchronized (qmicons) {
            LinkedList<Indir<Resource>> tmp = new LinkedList<>(qmicons.keySet());
            tmp.forEach(res -> {
                if(name.equals(res.get().basename())) {
                    qmicons.remove(res);
                }
            });
        }
    }

    private int qmx, toolx;
    private long hoverstart;
    private Spec lasttip;
    private Indir<Object> stip, ltip;
    public Object tooltip(Coord mc, Widget prev) {
        String name = null;
        Spec tspec = null;
        Coord c;
        if(!qmod.isEmpty()) {
            c = new Coord(qmx, qmy);
            try {
                for(Indir<Resource> qm : qmod) {
                    Tex t = qmicon(qm);
                    Coord sz = t.sz();
                    if(mc.isect(c, sz))
                        return(qm.get().layer(Resource.tooltip).text());
                    c = c.add(sz.x + UI.scale(1), 0);
                }
            } catch(Loading l) {
            }
        }
        if(!tools.isEmpty()) {
            c = new Coord(toolx, qmy);
            try {
                for(Indir<Resource> tool : tools) {
                    Coord tsz = qmicon(tool).sz();
                    if(mc.isect(c, tsz))
                        return(tool.get().layer(Resource.tooltip).text());
                    c = c.add(tsz.x + UI.scale(1), 0);
                }
            } catch(Loading l) {
            }
        }
        find: {
            c = new Coord(xoff, 0);
            boolean popt = false;
            for(Spec s : inputs) {
                boolean opt = s.opt();
                if(opt != popt)
                    c = c.add(UI.scale(10), 0);
                if(mc.isect(c, Inventory.invsq.sz())) {
                    name = getDynamicName(s.spr);
                    if(name == null || name.contains("Raw")){
                        tspec = s;
                    }
                    break find;
                }
                c = c.add(Inventory.sqsz.x, 0);
                popt = opt;
            }
            c = new Coord(xoff, outy);
            for(Spec s : outputs) {
                if(mc.isect(c, invsq.sz())) {
                    tspec = s;
                    break find;
                }
                c = c.add(Inventory.sqsz.x, 0);
            }
        }
        if(lasttip != tspec) {
            lasttip = tspec;
            stip = ltip = null;
        }
        if(tspec == null)
            return(super.tooltip(mc, prev));
        long now = System.currentTimeMillis();
        boolean sh = true;
        if(prev != this)
            hoverstart = now;
        else if(now - hoverstart > 1000)
            sh = false;
        if(sh) {
            if(stip == null) {
                BufferedImage tip = tspec.shorttip();
                if(tip == null) {
                    stip = () -> null;
                } else {
                    Tex tt = new TexI(tip);
                    stip = () -> tt;
                }
            }
            return(stip);
        } else {
            if(ltip == null) {
                BufferedImage tip = tspec.longtip();
                if(tip == null) {
                    ltip = () -> null;
                } else {
                    Tex tt = new TexI(tip);
                    ltip = () -> tt;
                }
            }
            return(ltip);
        }
    }

    private static String getDynamicName(GSprite spr) {
        if(spr != null) {
            if(spr instanceof ItemInfo.Name.Dynamic)
            {
                return ((ItemInfo.Name.Dynamic)spr).name();
            }
        }
        return null;
    }

    public static Class[] interfaces(Class c) {
        try {
            return c.getInterfaces();
        } catch (Exception ignored) {}
        return new Class[0];
    }

    public static boolean hasInterface(String name, Class c) {
        Class[] interfaces = interfaces(c);
        for (Class in : interfaces) {
            if(in.getCanonicalName().equals(name)) {return true; }
        }
        return false;
    }

    public boolean globtype(GlobKeyEvent ev) {
        if(ev.c == '\n') {
            wdgmsg("make", ui.modctrl?1:0);
            return(true);
        }
        return(super.globtype(ev));
    }

    void craft()
    {
        if(!autoMode)
            wdgmsg("make", 0);
        else
        {
            final NGameUI gui = NUtils.getGameUI();
            if (gui == null) return;

            int num = 1;
            try
            {
                String cand = craft_num.text();
                if(!cand.isEmpty())
                    num = Integer.parseInt(cand);
            }
            catch (NumberFormatException e)
            {
                gui.error("Incorrect target num");
            }
            final int craftNum = num;
            BotExecutor.runAsync("Auto craft(BOT)", new Craft(NMakewindow.this, craftNum));
        }
    }

    void craftAll()
    {
        if(!autoMode)
        {
            wdgmsg("make", 1);
        }
        else
        {
            BotExecutor.runAsync("Auto craft(BOT)", new Craft(NMakewindow.this, 9999));
        }
    }

    void rebuildAutoLayout() {
        clearAutoLayout();
        autoInputRows = new ArrayList<>();
        autoOutputRows = new ArrayList<>();
        int y = UI.scale(28);
        for(Spec s : inputs) {
            AutoSpecRow row = new AutoSpecRow(s, false, y);
            autoInputRows.add(row);
            y += UI.scale(AUTO_ROW_H);
        }
        autoQmodY = y + UI.scale(2);
        if(!qmod.isEmpty()) {
            y = autoQmodY + UI.scale(22);
        }
        resultLabel.c = new Coord(0, y + UI.scale(4));
        y += UI.scale(20);
        for(Spec s : outputs) {
            AutoSpecRow row = new AutoSpecRow(s, true, y);
            autoOutputRows.add(row);
            y += UI.scale(AUTO_ROW_H);
        }
        repositionAllRows();
    }

    void clearAutoLayout() {
        if(autoInputRows != null) {
            for(AutoSpecRow row : autoInputRows) row.destroy();
            autoInputRows = null;
        }
        if(autoOutputRows != null) {
            for(AutoSpecRow row : autoOutputRows) row.destroy();
            autoOutputRows = null;
        }
        resultLabel.c = new Coord(0, outy + UI.scale(8));
        craftBtn.c = UI.scale(new Coord(230, 75));
        craft_num.c = UI.scale(new Coord(165, 82));
        craftAllBtn.c = UI.scale(new Coord(325, 75));
        if(savePresetBtn != null) {
            savePresetBtn.c = UI.scale(new Coord(340, 5));
        }
        pack();
        if(parent != null) parent.pack();
    }

    private int autoQmodY = 0;

    public static class Optional extends ItemInfo.Tip {
        public static Text text = null;
        private static Text getText() {
            if (text == null) text = RichText.render(String.format("$i{%s}", L10n.get("craft.optional")), 0);
            return text;
        }
        public Optional(Owner owner) {
            super(owner);
        }

        public BufferedImage tipimg() {
            return(getText().img);
        }

        public Tip shortvar() {return(this);}
    }

    public static class MakePrep extends ItemInfo implements GItem.ColorInfo {
        private final static Color olcol = new Color(0, 255, 0, 64);
        public MakePrep(Owner owner) {
            super(owner);
        }

        public Color olcol() {
            return(olcol);
        }
    }

    Categories cat = null;

    public class Ingredient{
        public BufferedImage img;
        public String name;
        boolean logistic;
        public boolean isIgnored = false;
        public boolean subCraftable = false;

        public Ingredient(JSONObject obj)
        {
            img = ItemTex.create(obj);
            name = (String) obj.get("name");
        }

        public Ingredient(BufferedImage img, String name, boolean isIgnored)
        {
            this.img = img;
            this.name = name;
            this.isIgnored = isIgnored;
        }

        void tick(double dt)
        {
            if (!isIgnored) {
                logistic = (NContext.findIn(name) != null);
            }
        }
    }

    final static Coord catoff = UI.scale(8,8);
    final static Coord catend = UI.scale(15,15);
    static final int width = 9;
    
    private static Coord calculateSize(int totalSize) {
        return new Coord(
            Math.max((Inventory.sqsz.x+UI.scale(1))*((totalSize/width>=1)?width:0),
                    (Inventory.sqsz.x+UI.scale(1))*(totalSize%width))- UI.scale(2),
            (Inventory.sqsz.x+UI.scale(1))*(totalSize/width+(totalSize%width!=0?1:0))
        ).add(UI.scale(20,18));
    }
    
    private static Coord calculateCategoriesSize(int itemCount, boolean isOptional) {
        int totalSize = itemCount + (isOptional ? 1 : 0);
        return calculateSize(totalSize);
    }
    
    public class Categories extends Widget
    {

        Color bg = new Color(30,40,40,160);
        ArrayList<Ingredient> data = new ArrayList<>();
        Frame fr;


        Spec s;
        boolean isOptional;
        
        public Categories(ArrayList<JSONObject> objs, Spec s, boolean isOptional)
        {
            super(calculateCategoriesSize(objs.size(), isOptional));
            this.s = s;
            this.isOptional = isOptional;
            add(fr = new Frame(sz.sub(catend),true));
            
            // Add "ignore" option first if this is optional
            if (isOptional) {
                try {
                    BufferedImage ignoreImg = Resource.loadsimg("nurgling/hud/autocraft/ignore");
                    data.add(new Ingredient(ignoreImg, L10n.get("craft.ignore_ingredient"), true));
                } catch (Exception e) {
                    System.out.println("Failed to load ignore resource: " + e.getMessage());
                }
            }
            
            // Больше не добавляем опцию категории - используем чекбокс "all" вместо этого
            
            for(JSONObject obj: objs)
            {
                data.add(new Ingredient(obj));
            }
            setfocustab(true);
            autofocus =true;
        }

        @Override
        public void draw(GOut g)
        {
            super.draw(g);
            g.chcolor(bg);
            g.frect(UI.scale(4,4), fr.inner());
            Coord pos = new Coord(catoff);
            Coord shift = new Coord(0,0);
            for(Ingredient ing: data)
            {
                GOut sg = g.reclip(pos, invsq.sz());
                sg.image(new TexI(ing.img), Coord.z,UI.scale(32,32));
                if(ing.isIgnored)
                {
                    sg.image(ignoreOverlay, Coord.z,UI.scale(32,32));
                }
                else if(ing.logistic)
                {
                    sg.image(aready, Coord.z,UI.scale(32,32));
                }
                else
                {
                    sg.image(anotfound, Coord.z,UI.scale(32,32));
                }
                if(shift.x<width-1)
                {
                    pos = pos.add(Inventory.sqsz.x + UI.scale(1), 0);
                    shift.x+=1;
                }
                else
                {
                    pos.x = UI.scale(8);
                    shift.x = 0;
                    pos = pos.add(0, Inventory.sqsz.y + UI.scale(1));
                }
            }

        }
        UI.Grab mg;
        @Override
        protected void added()
        {
            mg = NUtils.getUI().grabmouse(this);
        }

        @Override
        public void remove()
        {
            mg.remove();
            super.remove();
        }

        @Override
        public boolean mousedown(MouseDownEvent ev) {
            Coord pos = new Coord(catoff);
            if(!ev.c.isect(pos, sz.sub(catend)))
            {
                destroy();
                cat = null;
                return true;
            }
            else
            {
                Coord shift = new Coord(0,0);
                for(Ingredient ing: data)
                {
                    if(ev.c.isect(pos, invsq.sz()))
                    {
                        s.ing = ing;
                        destroy();
                        cat = null;
                        return true;
                    }
                    if(shift.x<width-1)
                    {
                        pos = pos.add(Inventory.sqsz.x + UI.scale(1), 0);
                        shift.x+=1;
                    }
                    else
                    {
                        pos.x = UI.scale(8);
                        pos = pos.add(0, Inventory.sqsz.y + UI.scale(1));
                        shift.x = 0;
                    }
                }
                return true;
            }
        }


        @Override
        public void tick(double dt)
        {
            for(Ingredient ing: data)
            {
                ing.tick(dt);
            }
            super.tick(dt);
        }

        @Override
        public Object tooltip(Coord c, Widget prev) {
            Coord pos = new Coord(catoff);
            if(!c.isect(pos, sz.sub(catend)))
            {
                return null;
            }
            else
            {
                Coord shift = new Coord(0,0);
                for(Ingredient ing: data)
                {
                    if(c.isect(pos, invsq.sz()))
                    {
                        return ing.name;
                    }
                    if(shift.x<width-1)
                    {
                        pos = pos.add(Inventory.sqsz.x + UI.scale(1), 0);
                        shift.x+=1;
                    }
                    else
                    {
                        pos.x = UI.scale(8);
                        pos = pos.add(0, Inventory.sqsz.y + UI.scale(1));
                        shift.x = 0;
                    }
                }
                return true;
            }
        }
    }
}
