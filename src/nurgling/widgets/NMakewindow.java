package nurgling.widgets;

import haven.Button;
import haven.*;
import haven.Frame;
import haven.Label;
import static haven.Inventory.*;

import haven.render.Render;
import haven.res.lib.itemtex.*;
import static haven.PType.*;
import nurgling.*;
import nurgling.actions.bots.*;
import nurgling.areas.*;
import nurgling.conf.FontSettings;
import nurgling.conf.ItemQualityOverlaySettings;
import nurgling.craftatlas.CraftAtlasObservation;
import nurgling.craftatlas.CraftAtlasObservationStore;
import nurgling.craftatlas.CraftAtlasRecipeProbe;
import nurgling.i18n.L10n;
import nurgling.sessions.BotExecutor;
import nurgling.tools.*;
import org.json.*;

import java.awt.*;
import java.awt.image.*;
import java.util.List;
import java.util.*;

public class NMakewindow extends Widget implements DTarget {
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
    private int xoff = UI.scale(45), qmy = UI.scale(38 + CraftSlotQuality.LINE), outy = UI.scale(65 + CraftSlotQuality.LINE);
    private final Map<String, Tex> avgQTex = new HashMap<>();
    public static final Text.Foundry nmf = new Text.Foundry(Text.serif, 20).aa(true);
    private static double softcap = 0;
    private static Tex softTex = null;
    private static Tex softTexLabel = null;
    public CheckBox noTransfer = null;
    public boolean autoMode = false;
    public boolean searchMode = false;
    private ICheckBox autoChk = null;
    private ICheckBox searchChk = null;
    private Button searchBtn = null;
    private CraftIngredientSearchPanel searchPanel = null;
    private boolean searchRan = false;
    private boolean searching = false;
    private final Map<Integer, CraftIngredientStock.Totals> searchTotals = new HashMap<>();
    private final Map<Integer, Set<String>> searchFoundNames = new HashMap<>();
    private final Map<String, Tex> stockOverlayTex = new HashMap<>();
    private IButton savePresetBtn = null;
    private boolean recipesPersisted = false;
    private boolean craftAtlasObservationDirty = true;
    private boolean craftAtlasProbe;
    private boolean craftAtlasProbeClosing;
    private CraftAtlasRecipeProbe craftAtlasRecipeProbe;
    private long craftAtlasProbeDeadline;
    private long craftAtlasLastServerUpdate;

    private static final OwnerContext.ClassResolver<NMakewindow> ctxr = new OwnerContext.ClassResolver<NMakewindow>()
            .add(NMakewindow.class, wdg -> wdg)
            .add(Glob.class, wdg -> wdg.ui.sess.glob)
            .add(Session.class, wdg -> wdg.ui.sess);
    public class Spec implements GSprite.Owner, ItemInfo.SpriteOwner {
        public Indir<Resource> res;
        public MessageBuf sdt;
        public ResData constraint = null;
        public Tex num;
        public String name;
        public int count;
        public GSprite spr;
        private Object[] rawinfo;
        private List<ItemInfo> info;

        public Ingredient ing = null;
        public boolean categories = false;
        public boolean useCategory = false;
        public NArea selectedZone = null;
        public boolean isSubCraft = false;
        public boolean isLocalZone = false;
        public boolean isInventory = false;
        public int selectedZoneId = -1;
        public Map<String, NArea> subIngredientZones = new java.util.HashMap<>();

        public int using = 0;
        public List<MenuGrid.Pagina> rpag = null;
        public Coord rcc = null;

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

        private ResData display() {
            if(constraint != null)
                return(constraint);
            return(new ResData(res, sdt));
        }

        public GSprite sprite() {
            if(spr == null) {
                ResData d = display();
                spr = GSprite.create(this, d.res.get(), d.sdt.clone());
            }
            return(spr);
        }

        public void draw(GOut g) {
            try {
                if(ing==null || !(autoMode || searchMode))
                {
                    sprite().draw(g);
                }
                else
                {
                    Tex icon = ing.iconTex();
                    if (icon != null) {
                        g.image(icon, Coord.z, invsq.sz());
                    } else {
                        g.image(new TexI(ing.img), Coord.z, invsq.sz());
                    }
                }
            } catch(Loading e) {}
            if(num != null)
                g.aimage(num, Inventory.sqsz, 1.0, 1.0);
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
        public Resource getres() {return(display().res.get());}
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
            if((NMakewindow.this.autoMode || NMakewindow.this.searchMode) && name!=null)
            {
                if(NMakewindow.this.autoMode)
                {
                    logisticin = (NContext.findIn(name) != null);
                    if(!logisticin)
                    {
                        categories = (VSpec.categories.get(name)!=null);
                    }
                    logisticout = (NContext.findOut(name,1) != null);
                    if(!logisticout)
                    {
                        categories = (VSpec.categories.get(name)!=null);
                    }
                    for(Spec s : inputs) {
                        if(s.categories && s.ing!=null)
                        {
                            s.ing.logistic = (NContext.findIn(s.ing.name) != null);
                        }
                    }
                    for(Spec s : outputs) {
                        if(s.categories && s.ing!=null)
                        {
                            s.ing.logistic = (NContext.findOut(s.ing.name, 1) != null);
                        }
                    }
                }
                else
                {
                    categories = (VSpec.categories.get(name)!=null);
                }
            }
        }

        String name()
        {
            return name;
        }

        public boolean logisticin = false;
        public boolean logisticout = false;


    }

    public void tick(double dt) {
        for(Spec s : inputs) {
            if(s.spr != null)
                s.spr.tick(dt);
            s.tick(dt);
            if((s.rcc != null) && (s.rpag != null)) {
                if(!s.rpag.isEmpty()) {
                    SListMenu.of(UI.scale(250, 120), s.rpag,
                                 pag -> pag.button().name(), pag -> pag.button().img(),
                                 pag -> pag.button().use(new MenuGrid.Interaction(1, ui.modflags())))
                        .addat(this, s.rcc.add(UI.scale(5, 5))).tick(dt);
                }
                s.rcc = null;
            }
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

        if (savePresetBtn != null) {
            savePresetBtn.visible = autoMode && allInputsConfigured();
        }
        persistRecipeMappings();
        maybePublishCraftAtlasObservation();
        if(craftAtlasProbe && !craftAtlasProbeClosing && System.nanoTime() >= craftAtlasProbeDeadline)
            closeCraftAtlasProbe(false);
    }

    public void enableCraftAtlasProbe(String recipeResource, CraftAtlasRecipeProbe recipeProbe) {
        this.recipeResource = recipeResource;
        this.craftAtlasRecipeProbe = recipeProbe;
        craftAtlasProbe = true;
        craftAtlasLastServerUpdate = System.nanoTime();
        craftAtlasProbeDeadline = System.nanoTime() + 8_000_000_000L;
        hide();
    }

    @Override public void destroy() {
        if(craftAtlasProbe && !craftAtlasProbeClosing && craftAtlasRecipeProbe != null)
            craftAtlasRecipeProbe.fail(recipeResource);
        super.destroy();
    }

    private void maybePublishCraftAtlasObservation() {
        if(!craftAtlasObservationDirty || ui == null) return;
        if(craftAtlasProbe && !CraftAtlasRecipeProbe.readyToPublish(
                System.nanoTime(), craftAtlasLastServerUpdate)) return;
        String recipe = resolveRecipeResource();
        if(recipe == null || rcpnm == null || outputs.isEmpty()) return;
        try {
            List<CraftAtlasObservation.Item> observedInputs = new ArrayList<>();
            for(Spec spec : inputs) observedInputs.add(observedItem(spec));
            List<CraftAtlasObservation.Item> observedOutputs = new ArrayList<>();
            for(Spec spec : outputs) observedOutputs.add(observedItem(spec));
            List<CraftAtlasObservation.RequirementResource> observedRequirements = new ArrayList<>();
            for(Indir<Resource> indir : tools) {
                Resource resource = indir.get();
                observedRequirements.add(new CraftAtlasObservation.RequirementResource(resource.name, resourceName(resource)));
            }
            List<CraftAtlasObservation.BonusResource> observedBonuses = new ArrayList<>();
            List<CraftAtlasObservation.AttributeResource> observedQualityModifiers = new ArrayList<>();
            for(Indir<Resource> indir : qmod) {
                Resource resource = indir.get();
                observedQualityModifiers.add(new CraftAtlasObservation.AttributeResource(resource.name, resourceName(resource)));
            }
            CraftAtlasObservationStore.current().record(new CraftAtlasObservation(recipe, rcpnm,
                    observedInputs, observedOutputs, observedRequirements, observedBonuses, observedQualityModifiers));
            craftAtlasObservationDirty = false;
            if(craftAtlasProbe) closeCraftAtlasProbe(true);
        } catch(Loading ignored) {
            // Retry on a later UI tick; never block the render thread waiting for resources.
        }
    }

    private void closeCraftAtlasProbe(boolean completed) {
        if(craftAtlasProbeClosing) return;
        craftAtlasProbeClosing = true;
        if(craftAtlasRecipeProbe != null) {
            if(completed) craftAtlasRecipeProbe.complete(recipeResource);
            else craftAtlasRecipeProbe.fail(recipeResource);
        }
        wdgmsg("close");
    }

    private CraftAtlasObservation.Item observedItem(Spec spec) {
        Resource resource = spec.res.get();
        String name = spec.name != null ? spec.name : resourceName(resource);
        boolean optional = spec.opt();
        return new CraftAtlasObservation.Item(resource.name, name, spec.count, optional);
    }

    private String resourceName(Resource resource) {
        Resource.Tooltip tooltip = resource.layer(Resource.tooltip);
        if(tooltip != null && tooltip.text() != null && !tooltip.text().trim().isEmpty()) return tooltip.text();
        int slash = resource.name.lastIndexOf('/');
        return slash < 0 ? resource.name : resource.name.substring(slash + 1);
    }

    private String resolveRecipeResource() {
        if(recipeResource != null)
            return recipeResource;
        if(MenuGrid.lastPagina != null) {
            try {
                recipeResource = MenuGrid.lastPagina.res().name;
                return recipeResource;
            } catch(Loading l) {
            }
        }
        if(parent instanceof NCraftWindow) {
            NTabStrip.Button<MenuGrid.Pagina> sel =
                    ((NCraftWindow)parent).tabStrip.getSelectedButton();
            if(sel != null && sel.tag != null) {
                try {
                    recipeResource = sel.tag.res().name;
                } catch(Loading l) {
                }
            }
        }
        return recipeResource;
    }

    private void persistRecipeMappings() {
        if(recipesPersisted)
            return;
        String pagina = resolveRecipeResource();
        if(pagina == null || rcpnm == null)
            return;
        List<String> inNames = new ArrayList<>();
        List<RecipeIngredientCache.IngredientSpec> specs = new ArrayList<>();
        for(Spec s : inputs) {
            if(s.name == null) {
                try {
                    String rn = s.res.get().name;
                    if(rn != null && !rn.contains("coin"))
                        return;
                } catch(Loading l) {
                    return;
                }
            } else {
                inNames.add(s.name);
                specs.add(new RecipeIngredientCache.IngredientSpec(s.name, s.count));
            }
        }
        List<String> outNames = new ArrayList<>();
        for(Spec s : outputs) {
            if(s.name == null) {
                try {
                    String rn = s.res.get().name;
                    if(rn != null && !rn.contains("coin"))
                        return;
                } catch(Loading l) {
                    return;
                }
            } else {
                outNames.add(s.name);
            }
        }
        if(inNames.isEmpty() && outNames.isEmpty())
            return;
        RecipeIngredientCache.addInputsAndPersist(inNames, pagina, rcpnm);
        RecipeIngredientCache.addOutputsAndPersist(outNames, pagina, rcpnm);
        RecipeIngredientCache.setRecipeSpecs(pagina, specs);
        recipesPersisted = true;
    }

    /**
     * Check if all category inputs have their ingredient selected.
     */
    private boolean allInputsConfigured() {
        for (Spec s : inputs) {
            // If it's a category input, it needs to have an ingredient selected (or be ignored)
            if (s.categories && s.ing == null) {
                return false;
            }
        }
        return true;
    }

    private int inputIndexAt(Coord c) {
        Coord sc = new Coord(xoff, 0);
        boolean popt = false;
        int idx = 0;
        for(Spec s : inputs) {
            boolean opt = s.opt();
            if(opt != popt)
                sc = sc.add(UI.scale(10), 0);
            if(c.isect(sc, Inventory.sqsz))
                return idx;
            sc = sc.add(Inventory.sqsz.x, 0);
            popt = opt;
            idx++;
        }
        return -1;
    }

    private Spec specAt(Coord c) {
        int idx = inputIndexAt(c);
        if(idx >= 0)
            return inputs.get(idx);
        Coord sc = new Coord(xoff, outy);
        for(Spec s : outputs) {
            if(c.isect(sc, Inventory.sqsz))
                return s;
            sc = sc.add(Inventory.sqsz.x, 0);
        }
        return null;
    }

    private boolean itemactAt(Coord c) {
        if(autoMode || searchMode)
            return false;
        int idx = inputIndexAt(c);
        if(idx < 0)
            return false;
        wdgmsg("itemact", idx, ui.modflags());
        return true;
    }

    @Override
    public boolean drop(Coord cc, Coord ul) {
        return itemactAt(cc);
    }

    @Override
    public boolean iteminteract(Coord cc, Coord ul) {
        return itemactAt(cc);
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
        if(ev.b == 3 && ui.modmeta && !ui.modshift && !ui.modctrl) {
            Spec s = specAt(ev.c);
            if(s != null && s.name != null && CraftRecipeLookup.show(this, ev.c, s.name))
                return true;
        }
        if(autoMode || searchMode)
        {
            Coord sc = new Coord(xoff, 0);
            boolean popt = false;
            if (clickForCategories(inputs, popt, sc, ev.c)) return true;
            if (autoMode) {
                sc = new Coord(xoff, outy);
                if (clickForCategories(outputs, popt, sc, ev.c)) return true;
            }
        }
        else
        {
            int idx = inputIndexAt(ev.c);
            if(idx >= 0) {
                Spec s = inputs.get(idx);
                if(ev.b == 1) {
                    wdgmsg("choose", idx, ui.modflags());
                    return true;
                } else if(ev.b == 3) {
                    if(s.rpag == null)
                        wdgmsg("findrcps", idx);
                    s.rcc = ev.c;
                    return true;
                }
            }
        }
        return super.mousedown(ev);
    }

    private boolean clickForCategories(List<Spec> outputs, boolean popt, Coord sc, Coord c) {
        for (Spec s : outputs) {
            boolean opt = s.opt();
            if (opt != popt)
                sc = sc.add(UI.scale(10), 0);
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
        int inputW = add(new Label(L10n.get("craft.input")), new Coord(0, UI.scale(8))).sz.x;
        int resultW = add(new Label(L10n.get("craft.result")), new Coord(0, outy + UI.scale(8))).sz.x;
        xoff = Math.max(inputW, resultW) + UI.scale(10);

        add(new Button(UI.scale(85), L10n.get("craft.craft")), UI.scale(new Coord(230, 75 + CraftSlotQuality.LINE))).action(() -> craft()).setgkey(kb_make);
        add(craft_num = new TextEntry(UI.scale(55), ""), UI.scale(new Coord(165, 82 + CraftSlotQuality.LINE)));
        add(new Button(UI.scale(85), L10n.get("craft.craft_all")), UI.scale(new Coord(325, 75 + CraftSlotQuality.LINE))).action(() -> craftAll()).setgkey(kb_makeall);
        searchChk = add(new ICheckBox(NStyle.search[0], NStyle.search[1], NStyle.search[2], NStyle.search[3]){
            @Override
            public void changed(boolean val)
            {
                super.changed(val);
                searchMode = val;
                if (val && autoChk != null && autoChk.a) {
                    autoChk.set(false);
                }
                applyModeUi();
            }
        }, UI.scale(new Coord(335, 5)));
        autoChk = add(new ICheckBox(NStyle.auto[0],NStyle.auto[1],NStyle.auto[2],NStyle.auto[3]){
            @Override
            public void changed(boolean val)
            {
                super.changed(val);
                autoMode = val;
                if (val && searchChk != null && searchChk.a) {
                    searchChk.set(false);
                }
                applyModeUi();
            }
        }, UI.scale(new Coord(365, 5)));

        add(noTransfer = new CheckBox(L10n.get("craft.no_transfer"))
        {
            @Override
            public void changed(boolean val) {
                super.changed(val);
            }
        }, UI.scale(new Coord(325, 38 + CraftSlotQuality.LINE)));
        noTransfer.visible = false;
        searchBtn = add(new Button(UI.scale(85), L10n.get("craft.search")) {
            @Override
            public void click() {
                runIngredientSearch();
            }
        }, UI.scale(new Coord(325, 38 + CraftSlotQuality.LINE)));
        searchBtn.visible = false;

        // Save Preset button - only visible in auto mode when all inputs are configured
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
        }, UI.scale(new Coord(305, 5)));
        savePresetBtn.visible = false;

        pack();
        searchPanel = add(new CraftIngredientSearchPanel(), new Coord(sz.x + UI.scale(8), 0));
        searchPanel.visible = false;
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

    private void applyModeUi() {
        if (noTransfer != null) {
            noTransfer.visible = autoMode;
        }
        if (searchBtn != null) {
            searchBtn.visible = searchMode;
        }
        if (searchPanel != null) {
            searchPanel.visible = searchMode;
            if (searchMode) {
                searchPanel.syncTabs(inputs);
            }
        }
        if (savePresetBtn != null) {
            savePresetBtn.visible = autoMode && allInputsConfigured();
        }
        if (!searchMode) {
            searchRan = false;
            searchTotals.clear();
            searchFoundNames.clear();
        }
        packCraftWindow();
    }

    private void packCraftWindow() {
        pack();
        if (parent != null) {
            parent.pack();
        }
    }

    @Override
    public void pack() {
        Coord csz = contentsz();
        int line = UI.scale(CraftSlotQuality.LINE);
        resize(new Coord(csz.x, CraftSlotQuality.packedHeight(csz.y, sz.y, line)));
    }

    void runIngredientSearch() {
        if (searching) {
            return;
        }
        NGameUI gui = NUtils.getGameUI();
        if (gui == null) {
            return;
        }
        if (!(Boolean) NConfig.get(NConfig.Key.ndbenable)
                || NCore.databaseManager == null
                || !NCore.databaseManager.isReady()) {
            gui.msg(L10n.get("storage.db_not_ready"), Color.RED);
            return;
        }
        searching = true;
        final List<Spec> snapshot = new ArrayList<>(inputs);
        Thread loader = new Thread(() -> {
            try {
                Map<Integer, List<NStorageItemsWidget.GroupedItem>> byInput = new LinkedHashMap<>();
                Map<Integer, CraftIngredientStock.Totals> totals = new HashMap<>();
                Map<Integer, Set<String>> foundNames = new HashMap<>();
                for (int i = 0; i < snapshot.size(); i++) {
                    Spec s = snapshot.get(i);
                    if (s.ing != null && s.ing.isIgnored) {
                        continue;
                    }
                    boolean category = s.categories || (s.name != null && VSpec.categories.containsKey(s.name));
                    String picked = (s.ing != null && !s.ing.isIgnored) ? s.ing.name : null;
                    List<String> names = CraftIngredientStock.namesFor(s.name, category, picked);
                    List<NStorageItemsWidget.GroupedItem> found = CraftIngredientStock.search(names);
                    byInput.put(i, found);
                    totals.put(i, CraftIngredientStock.totals(found));
                    Set<String> nset = new HashSet<>();
                    for (NStorageItemsWidget.GroupedItem gi : found) {
                        nset.add(gi.name);
                    }
                    foundNames.put(i, nset);
                }
                UI ui = NMakewindow.this.ui;
                if (ui != null) {
                    synchronized (ui) {
                        searchRan = true;
                        searchTotals.clear();
                        searchTotals.putAll(totals);
                        searchFoundNames.clear();
                        searchFoundNames.putAll(foundNames);
                        if (searchPanel != null) {
                            searchPanel.syncTabs(inputs);
                            searchPanel.setResults(byInput);
                        }
                    }
                }
            } finally {
                searching = false;
            }
        }, "CraftIngredientSearch");
        loader.setDaemon(true);
        loader.start();
    }

    private void openSavePresetDialog() {
        NUtils.addCentered(new SaveCraftPresetDialog(this));
    }

    // Parse one make-window spec (protocol v31: modular message format).
    private Spec parsespec(Object[] desc) {
        int a = 0;
        Indir<Resource> res = ui.sess.getresv(desc[a++]);
        Message sdt = BYTES.is(desc, a) ? new MessageBuf(BYTES.of(desc, a++)) : MessageBuf.nil;
        int num = INT.of(desc, a++);
        Object[] info = OBJS.is(desc, a) ? OBJS.of(desc, a++) : new Object[0];
        Spec ret = new Spec(res, sdt, num, info);
        while(a < desc.length) {
            Object[] arg = OBJS.of(desc[a++]);
            switch(STR.of(arg[0])) {
            case "constraint":
                ResData cst = new ResData(ui.sess.getresv(arg[1]), Message.nil);
                if(BYTES.is(arg, 2))
                    cst.sdt = new MessageBuf(BYTES.of(arg, 2));
                ret.constraint = cst;
                break;
            }
        }
        return ret;
    }

    public void uimsg(String msg, Object... args) {
        if(msg == "inpop") {
            craftAtlasLastServerUpdate = System.nanoTime();
            List<Spec> inputs;
            if(INT.is(args, 0)) {
                // Indexed update: reuse existing specs, replace by index.
                inputs = new ArrayList<>(this.inputs);
                for(int i = 0; i < args.length; i += 2)
                    inputs.set(INT.of(args, i), parsespec(OBJS.of(args, i + 1)));
            } else {
                inputs = new ArrayList<>();
                for(int i = 0; i < args.length; i++)
                    inputs.add(parsespec(OBJS.of(args[i])));
            }
            this.inputs = inputs;
            craftAtlasObservationDirty = true;
            if (searchMode && searchPanel != null) {
                searchPanel.syncTabs(this.inputs);
            }
        } else if(msg == "opop") {
            craftAtlasLastServerUpdate = System.nanoTime();
            List<Spec> outputs;
            if(INT.is(args, 0)) {
                outputs = new ArrayList<>(this.outputs);
                for(int i = 0; i < args.length; i += 2)
                    outputs.set(INT.of(args, i), parsespec(OBJS.of(args, i + 1)));
            } else {
                outputs = new ArrayList<>();
                for(int i = 0; i < args.length; i++)
                    outputs.add(parsespec(OBJS.of(args[i])));
            }
            this.outputs = outputs;
            craftAtlasObservationDirty = true;
        } else if(msg == "qmod") {
            craftAtlasLastServerUpdate = System.nanoTime();
            List<Indir<Resource>> qmod = new ArrayList<Indir<Resource>>();
            for(Object arg : args)
                qmod.add(ui.sess.getresv(arg));
            this.qmod = qmod;
            craftAtlasObservationDirty = true;
        } else if(msg == "tool") {
            craftAtlasLastServerUpdate = System.nanoTime();
            tools.add(ui.sess.getresv(args[0]));
            craftAtlasObservationDirty = true;
        } else if(msg == "use") {
            inputs.get(Utils.iv(args[0])).using = Utils.iv(args[1]);
        } else if(msg == "inprcps") {
            int idx = Utils.iv(args[0]);
            List<MenuGrid.Pagina> rcps = new ArrayList<>();
            GameUI gui = getparent(GameUI.class);
            if((gui != null) && (gui.menu != null)) {
                for(int a = 1; a < args.length; a++)
                    rcps.add(gui.menu.paginafor(ui.sess.getresv(args[a])));
            }
            inputs.get(idx).rpag = rcps;
        } else {
            super.uimsg(msg, args);
        }
    }

    public static final Coord qmodsz = UI.scale(20, 20);
    private static final WeakHashMap<Indir<Resource>, Tex> qmicons = new WeakHashMap<>();
    private Tex qmicon(Indir<Resource> qm) {
        synchronized (qmicons) {
            return qmicons.computeIfAbsent(qm, NMakewindow.this::buildQTex);
        }
    }

    public void draw(GOut g) {
        List<Double> slotAvgs = ingredientAverages();
        Double resultAvg = CraftSlotQuality.meanOfSlotAverages(slotAvgs);
        Coord c = new Coord(xoff, 0);
        boolean popt = false;
        int inIdx = 0;
        for(Spec s : inputs) {
            boolean opt = s.opt();
            if(opt != popt)
                c = c.add(UI.scale(10), 0);
            GOut sg = g.reclip(c, invsq.sz());
            if(opt) {
                sg.chcolor(0, 255, 0, 255);
                sg.image(invsq, Coord.z);
                sg.chcolor();
            } else {
                sg.image(invsq, Coord.z);
            }
            s.draw(sg);
            if(!autoMode && !searchMode && !opt && (s.count > 0) && (s.using < s.count)) {
                sg.chcolor(255, 0, 0, 64);
                sg.frect2(Coord.of(0, (invsq.sz().y * s.using) / s.count), invsq.sz());
                sg.chcolor();
            }
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
            else if(searchMode)
            {
                drawSearchSlotOverlay(sg, s, inIdx);
            }
            drawAvgQuality(g, c, inIdx < slotAvgs.size() ? slotAvgs.get(inIdx) : null);
            c = c.add(Inventory.sqsz.x, 0);
            inIdx++;
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
            drawAvgQuality(g, c, resultAvg);
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

    private void drawSearchSlotOverlay(GOut sg, Spec s, int idx) {
        if (s.ing != null && s.ing.isIgnored) {
            sg.image(ignoreOverlay, Coord.z);
            return;
        }
        if (!searchRan) {
            if (s.categories && s.ing == null) {
                sg.image(categories, Coord.z);
            }
            return;
        }
        CraftIngredientStock.Totals totals = searchTotals.get(idx);
        if (totals != null && totals.count > 0) {
            sg.image(aready, Coord.z);
            ItemQualityOverlaySettings qs = qualityOverlaySettings();
            Tex countTex = overlayChip(String.valueOf(totals.count), qs.defaultColor);
            Tex qTex = overlayChip(fmtQuality(totals.maxQuality, qs), qs.getColorForQuality(totals.maxQuality));
            sg.image(countTex, Coord.z);
            sg.aimage(qTex, new Coord(sg.sz().x, 0), 1, 0);
        } else {
            sg.image(anotfound, Coord.z);
        }
    }

    private static String fmtQuality(double q, ItemQualityOverlaySettings qs) {
        if (qs.showDecimal) {
            return String.format("%.1f", q);
        }
        return Integer.toString((int) Math.round(q));
    }

    private ItemQualityOverlaySettings qualityOverlaySettings() {
        Object settings = NConfig.get(NConfig.Key.itemQualityOverlay);
        if (settings instanceof ItemQualityOverlaySettings) {
            return (ItemQualityOverlaySettings) settings;
        }
        return new ItemQualityOverlaySettings();
    }

    private Tex overlayChip(String text, Color color) {
        String key = text + "|" + color.getRGB();
        Tex cached = stockOverlayTex.get(key);
        if (cached != null) {
            return cached;
        }
        ItemQualityOverlaySettings qs = qualityOverlaySettings();
        int fontPx = Math.max(UI.scale(7), UI.scale(Math.max(7, qs.fontSize - 2)));
        Font font = new Font("SansSerif", Font.BOLD, fontPx);
        FontSettings fontSettings = (FontSettings) NConfig.get(NConfig.Key.fonts);
        if (fontSettings != null) {
            Font fromCfg = fontSettings.getFont(qs.fontFamily);
            if (fromCfg != null) {
                font = fromCfg.deriveFont(Font.BOLD, (float) fontPx);
            }
        }
        Text.Foundry fnd = new Text.Foundry(font, color).aa(true);
        BufferedImage textImg = fnd.render(text, color).img;
        if (qs.showOutline) {
            textImg = Utils.outline2(textImg, qs.outlineColor);
        }
        if (qs.showBackground) {
            BufferedImage bi = new BufferedImage(textImg.getWidth(), textImg.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = bi.createGraphics();
            g.setColor(qs.backgroundColor);
            g.fillRect(0, 0, bi.getWidth(), bi.getHeight());
            g.drawImage(textImg, 0, 0, null);
            g.dispose();
            textImg = bi;
        }
        Tex tex = new TexI(textImg);
        stockOverlayTex.put(key, tex);
        return tex;
    }

    private boolean searchHasIngredient(Spec spec, String name) {
        int idx = inputs.indexOf(spec);
        if (idx < 0 || name == null) {
            return false;
        }
        Set<String> names = searchFoundNames.get(idx);
        return names != null && names.contains(name);
    }

    private static final class InvSample {
        final String name;
        final boolean makePrep;
        final Double quality;

        InvSample(String name, boolean makePrep, Double quality) {
            this.name = name;
            this.makePrep = makePrep;
            this.quality = quality;
        }
    }

    private void drawAvgQuality(GOut g, Coord slot, Double avg) {
        if (avg == null) {
            return;
        }
        g.aimage(avgQualityTex(avg.doubleValue()), slot.add(invsq.sz().x / 2, invsq.sz().y), 0.5, 0);
    }

    private Tex avgQualityTex(double q) {
        String text = String.format(java.util.Locale.US, "%.1f", q);
        Tex cached = avgQTex.get(text);
        if (cached != null) {
            return cached;
        }
        Tex tex = new TexI(fnd.render(text).img);
        avgQTex.put(text, tex);
        return tex;
    }

    /**
     * Per-slot average quality of highlighted player-inventory items (MakePrep),
     * unweighted by recipe count. Empty slots stay blank.
     */
    private List<Double> ingredientAverages() {
        List<InvSample> samples = playerInvSamples();
        boolean anyPrep = false;
        for (InvSample s : samples) {
            if (s.makePrep) {
                anyPrep = true;
                break;
            }
        }
        List<Double> avgs = new ArrayList<>(inputs.size());
        for (Spec spec : inputs) {
            if (spec.ing != null && spec.ing.isIgnored) {
                avgs.add(null);
                continue;
            }
            String picked = ((autoMode || searchMode) && spec.ing != null) ? spec.ing.name : null;
            boolean category = spec.categories || (spec.name != null && VSpec.categories.containsKey(spec.name));
            List<String> want = CraftIngredientStock.namesFor(spec.name, category, picked);
            List<Double> qs = new ArrayList<>();
            for (InvSample s : samples) {
                if (!CraftSlotQuality.includeItem(s.makePrep, anyPrep, s.name, want)) {
                    continue;
                }
                if (s.quality != null) {
                    qs.add(s.quality);
                }
            }
            avgs.add(CraftSlotQuality.average(qs));
        }
        return avgs;
    }

    /** Player inventory only — not chests/containers. */
    private List<InvSample> playerInvSamples() {
        List<InvSample> out = new ArrayList<>();
        NGameUI gui = NUtils.getGameUI();
        if (gui == null) {
            return out;
        }
        NInventory inv = gui.getInventory();
        if (inv == null) {
            return out;
        }
        try {
            for (WItem w : inv.getTopLevelItems()) {
                collectSample(w, out);
            }
        } catch (Loading l) {
        } catch (Exception ignored) {
        }
        return out;
    }

    private void collectSample(WItem w, List<InvSample> out) {
        if (w == null || !(w.item instanceof NGItem)) {
            return;
        }
        NGItem ng = (NGItem) w.item;
        boolean prep = hasMakePrep(ng);
        out.add(new InvSample(ng.name(), prep, readItemQuality(ng)));
        if (!prep && ng.contents != null) {
            for (Widget ch = ng.contents.child; ch != null; ch = ch.next) {
                if (ch instanceof WItem) {
                    collectSample((WItem) ch, out);
                }
            }
        }
    }

    private static boolean hasMakePrep(NGItem ng) {
        try {
            for (ItemInfo inf : ng.info()) {
                if (inf instanceof MakePrep || CraftSlotQuality.isMakePrepClass(inf.getClass().getName())) {
                    return true;
                }
            }
        } catch (Loading l) {
        }
        return false;
    }

    private static Double readItemQuality(NGItem item) {
        Double q = CraftSlotQuality.qualityOf(item.quality);
        if (q != null) {
            return q;
        }
        try {
            haven.res.ui.tt.stackn.Stack stack = item.getInfo(haven.res.ui.tt.stackn.Stack.class);
            if (stack != null && stack.quality > 0) {
                return Double.valueOf(stack.quality);
            }
        } catch (Exception ignored) {
        }
        if (item.contents != null) {
            List<Double> inner = new ArrayList<>();
            for (Widget ch : item.contents.children()) {
                if (ch instanceof NGItem) {
                    Double cq = CraftSlotQuality.qualityOf(((NGItem) ch).quality);
                    if (cq != null) {
                        inner.add(cq);
                    }
                }
            }
            return CraftSlotQuality.average(inner);
        }
        return null;
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
            if(ui.modctrl)
                craftAll();
            else
                craft();
            return(true);
        }
        return(super.globtype(ev));
    }

    /**
     * Current text of the target-quantity field, or "" if unavailable. Used to
     * carry the requested craft count across a recipe re-open (which builds a fresh
     * widget with an empty field).
     */
    public String getCraftCount()
    {
        return (craft_num != null) ? craft_num.text() : "";
    }

    /**
     * Restore the target-quantity field text (see {@link #getCraftCount()}).
     */
    public void setCraftCount(String value)
    {
        if (craft_num != null && value != null)
            craft_num.settext(value);
    }

    void craft()
    {
        final NGameUI gui = NUtils.getGameUI();
        Integer parsed = CraftTarget.parse(getCraftCount());
        if (parsed == null) {
            if (gui != null)
                gui.error(L10n.get("craft.incorrect_target_num"));
            return;
        }
        int num = parsed.intValue();
        if(!autoMode)
        {
            if (num == 1)
                wdgmsg("make", 0);
            else
                BotExecutor.runAsync("Craft", new RepeatMake(NMakewindow.this, num));
        }
        else
        {
            if (gui == null) return;
            BotExecutor.runAsync("Auto craft(BOT)", new Craft(NMakewindow.this, num));
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
            BotExecutor.runAsync("Auto craft(BOT)", new Craft(NMakewindow.this, CraftTarget.ALL));
        }
    }



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
        private Tex scaledIcon = null;

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

        Tex iconTex() {
            if (scaledIcon == null && img != null) {
                Coord tsz = invsq.sz();
                if (img.getWidth() != tsz.x || img.getHeight() != tsz.y) {
                    scaledIcon = new TexI(PUtils.convolvedown(img, tsz, CharWnd.iconfilter));
                } else {
                    scaledIcon = new TexI(img);
                }
            }
            return scaledIcon;
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
    
    public class Categories extends Widget
    {

        Color bg = new Color(30,40,40,160);
        ArrayList<Ingredient> data = new ArrayList<>();
        Frame fr;


        Spec s;
        boolean isOptional;
        
        public Categories(ArrayList<JSONObject> objs, Spec s, boolean isOptional)
        {
            super(calculateSize(objs.size() + (isOptional ? 1 : 0)));
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
                Tex icon = ing.iconTex();
                if (icon != null) {
                    sg.image(icon, Coord.z);
                }
                boolean inStock;
                if (searchMode && searchRan && !ing.isIgnored) {
                    inStock = searchHasIngredient(s, ing.name);
                } else {
                    inStock = ing.logistic;
                }
                if(ing.isIgnored)
                {
                    sg.image(ignoreOverlay, Coord.z);
                }
                else if(inStock)
                {
                    sg.image(aready, Coord.z);
                }
                else
                {
                    sg.image(anotfound, Coord.z);
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
