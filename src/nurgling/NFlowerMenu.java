package nurgling;

import haven.*;
import nurgling.actions.AutoDrink;
import nurgling.actions.ChopAndRemoveStump;
import nurgling.actions.RemoveStump;
import nurgling.actions.bots.*;
import nurgling.areas.NContext;
import nurgling.i18n.L10n;
import nurgling.sessions.BotExecutor;
import nurgling.widgets.NProspecting;

import java.util.*;

public class NFlowerMenu extends FlowerMenu
{
    public static final Tex bl = Resource.loadtex("nurgling/hud/flower/left");
    public static final Tex bm = Resource.loadtex("nurgling/hud/flower/mid");
    public static final Tex br = Resource.loadtex("nurgling/hud/flower/right");

    public static final Tex bhl = Resource.loadtex("nurgling/hud/flower/hleft");
    public static final Tex bhm = Resource.loadtex("nurgling/hud/flower/hmid");
    public static final Tex bhr = Resource.loadtex("nurgling/hud/flower/hright");

    // Localization keys for custom options
    public static final String KEY_SAVE_TREE = "flower.save_tree";
    public static final String KEY_SAVE_BUSH = "flower.save_bush";
    public static final String KEY_REMOVE_STUMP = "flower.remove_stump";
    public static final String KEY_CHOP_STUMP = "flower.chop_stump";
    
    public NPetal[] nopts;

    private static final int MAX_VISIBLE_ITEMS = 10;
    private Scrollbar sb;
    private int itemHeight;

    int len = 0;
    public boolean shiftMode = false;

    // Constructor called by FlowerMenu Factory - includes tree/bush detection
    public NFlowerMenu(String[] opts, UI ui)
    {
        this(processOptions(opts, ui));
    }

    // Constructor for custom menus - no tree/bush detection
    public NFlowerMenu(String[] opts)
    {
        super();
        shiftMode = ((NMapView)NUtils.getGameUI().map).shiftPressed;
        nopts = new NPetal[opts.length];
        itemHeight = bl.sz().y + UI.scale(2);
        int y = 0;

        for(int i = 0; i < opts.length; i++)
        {
            add(nopts[i] = new NPetal(opts[i], i + 1), new Coord(0,y));
            nopts[i].num = i;
            y += itemHeight;
            len = Math.max(nopts[i].sz.x,len);
        }
        for(int i = 0; i < opts.length; i++)
        {
            nopts[i].resize(len, bl.sz().y);
        }
        if(opts.length > MAX_VISIBLE_ITEMS)
        {
            int visibleHeight = MAX_VISIBLE_ITEMS * itemHeight;
            sb = add(new Scrollbar(visibleHeight, 0, opts.length - MAX_VISIBLE_ITEMS), new Coord(len, 0));
            resize(len + sb.sz.x, visibleHeight);
        }
    }

    /**
     * Process options to add tree/bush save option if right-clicked on a tree or bush
     */
    private static String[] processOptions(String[] opts, UI ui) {
        try {
            NCore.LastActions lastActions = ui.core.getLastActions();
            if(lastActions != null && lastActions.gob != null) {
                if(!lastActions.altClick) {
                    return opts;
                }
                Gob gob = lastActions.gob;
                if(gob.ngob != null && gob.ngob.name != null) {
                    if(isTreeStump(gob)) {
                        return appendOption(opts, L10n.get(KEY_REMOVE_STUMP));
                    }
                    if (isTree(gob)) {
                        String[] withChopStump = insertAfterAddMarkers(opts, L10n.get(KEY_CHOP_STUMP));
                        return appendOption(withChopStump, L10n.get(KEY_SAVE_TREE));
                    } else if(gob.ngob.name.startsWith("gfx/terobjs/bushes/")) {
                        return appendOption(opts, L10n.get(KEY_SAVE_BUSH));
                    }
                }
            }
        } catch(Exception e) {
            // Ignore errors - just don't add the option
        }
        return opts;
    }

    private static boolean isTreeStump(Gob gob) {
        String name = gob.ngob.name;
        return name.startsWith("gfx/terobjs/trees/") && name.contains("stump");
    }

    private static boolean isTree(Gob gob) {
        String name = gob.ngob.name;
        return name.startsWith("gfx/terobjs/trees/") && !name.contains("log") && !name.contains("trunk") && !name.contains("stump");
    }

    private static String[] appendOption(String[] opts, String option) {
        String[] newOpts = new String[opts.length + 1];
        System.arraycopy(opts, 0, newOpts, 0, opts.length);
        newOpts[opts.length] = option;
        return newOpts;
    }

    private static String[] insertAfterAddMarkers(String[] opts, String option) {
        int markerIdx = -1;
        for (int i = 0; i < opts.length; i++) {
            String lower = opts[i].toLowerCase(Locale.ROOT);
            if (lower.contains("add marker")) {
                markerIdx = i;
                break;
            }
        }
        if (markerIdx < 0) {
            return appendOption(opts, option);
        }
        String[] newOpts = new String[opts.length + 1];
        System.arraycopy(opts, 0, newOpts, 0, markerIdx + 1);
        newOpts[markerIdx + 1] = option;
        System.arraycopy(opts, markerIdx + 1, newOpts, markerIdx + 2, opts.length - markerIdx - 1);
        return newOpts;
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        // Never auto-choose on stump menu: user must explicitly press Remove Stump.
        if (hasOpt(L10n.get(KEY_REMOVE_STUMP))) {
            return;
        }
        if(!ui.modshift && (Boolean) NConfig.get(NConfig.Key.asenable) && !NContext.waitBot.get()) {
            if ((Boolean) NConfig.get(NConfig.Key.singlePetal) && nopts.length == 1 && (NUtils.getUI().core.getLastActions()==null || NUtils.getUI().core.getLastActions().item == null)) {
                nchoose(nopts[0]);
            } else {
                ArrayList<String> autoPetal = NUtils.getPetals();
                for (NPetal opt : nopts) {
                    if (autoPetal.contains(opt.name)) {
                        nchoose(opt);
                        break;
                    }
                }
            }
        }
    }

    public NFlowerMenu(ArrayList<String> opts)
    {
        this(opts.toArray(new String[0]));
    }

    public void nchoose(NPetal option)
    {
        if (option == null)
        {
            wdgmsg("cl", -1);
            NUtils.getUI().core.setLastAction();
            if (isLocalStumpMenu()) {
                ui.destroy(this);
            }
        }
        else
        {
            // Handle custom "Save Tree Location" or "Save Bush Location" options
            // Compare against localized strings
            String saveTreeText = L10n.get(KEY_SAVE_TREE);
            String saveBushText = L10n.get(KEY_SAVE_BUSH);
            String removeStumpText = L10n.get(KEY_REMOVE_STUMP);
            String chopStumpText = L10n.get(KEY_CHOP_STUMP);
            if(option.name.equals(saveTreeText) || option.name.equals(saveBushText)) {
                NCore.LastActions actions = NUtils.getUI().core.getLastActions();
                if(actions != null && actions.gob != null) {
                    NGameUI gui = (NGameUI) NUtils.getGameUI();
                    if(gui != null && gui.treeLocationService != null) {
                        gui.treeLocationService.saveTreeLocation(actions.gob);
                    }
                }
                wdgmsg("cl", -1); // Close menu without sending to server
                NUtils.getUI().core.setLastAction();
                return;
            }
            if(option.name.equals(removeStumpText)) {
                NCore.LastActions actions = NUtils.getUI().core.getLastActions();
                if(actions != null && actions.gob != null) {
                    BotExecutor.runAsync("RemoveStump", new RemoveStump(actions.gob.id));
                }
                wdgmsg("cl", -1); // Close menu without sending to server
                NUtils.getUI().core.setLastAction();
                ui.destroy(this);
                return;
            }
            if(option.name.equals(chopStumpText)) {
                NCore.LastActions actions = NUtils.getUI().core.getLastActions();
                if(actions != null && actions.gob != null) {
                    NPetal chop = findOpt("Chop");
                    if(chop == null) {
                        wdgmsg("cl", -1);
                        NUtils.getUI().core.setLastAction();
                        ui.error("No Chop option");
                        ui.destroy(this);
                        return;
                    }
                    wdgmsg("cl", chop.num, ui.modflags());
                    NUtils.getUI().core.setLastAction("Chop", actions.gob);
                    BotExecutor.runAsync("ChopAndStump", new ChopAndRemoveStump(actions.gob.id, actions.gob.rc));
                    return;
                }
                wdgmsg("cl", -1);
                ui.destroy(this);
                return;
            }

            wdgmsg("cl", option.num, ui.modflags());
            NCore.LastActions actions = NUtils.getUI().core.getLastActions();
            if(actions!=null) {
                if (actions.item != null) {
                    NUtils.getUI().core.setLastAction(option.name, actions.item);
                } else if (actions.gob != null) {
                    NUtils.getUI().core.setLastAction(option.name, actions.gob);
                }
            }
        }
        if(!ui.modshift && !NUtils.getUI().core.isBotmod() && (Boolean)NConfig.get(NConfig.Key.autoFlower))
        {
            if (option != null && NUtils.getUI().core.getLastActions()!=null)
            {
                if (NUtils.getUI().core.getLastActions().item != null && NUtils.getUI().core.getLastActions().item.parent instanceof NInventory && ((NGItem)NUtils.getUI().core.getLastActions().item.item).name()!=null) {
                    if (!option.name.equals("Split") || ((NGItem)NUtils.getUI().core.getLastActions().item.item).name().startsWith("Block") || ((NGItem)NUtils.getUI().core.getLastActions().item.item).name().startsWith("Head of")) {
                        AutoChooser.enable((NInventory) NUtils.getUI().core.getLastActions().item.parent,((NGItem)NUtils.getUI().core.getLastActions().item.item).name(), option.name);
                    }
                }
            }
        }
        if(option != null && NUtils.getUI().core.getLastActions()!=null && NUtils.getUI().core.getLastActions().item!=null && option.name.contains("Prospect")) {
            NProspecting.item(NUtils.getUI().core.getLastActions().item);
        }
        // NOTE: Do NOT call resetLastAction() here!
        // The server hasn't processed the flower menu choice yet.
        // Inventory.$_.create() needs getLastActions().gob to set parentGob
        // for Storage Items DB tracking. Actions will be naturally overridden
        // by the next user interaction (right-click sets new action).
    }

    public boolean hasOpt(String action) {
        for(NPetal petal: nopts)
        {
            if(petal.name.equals(action))
            {
                return true;
            }
        }
        return false;
    }

    private NPetal findOpt(String action) {
        for(NPetal petal: nopts) {
            if(petal.name.equals(action)) {
                return petal;
            }
        }
        return null;
    }

    public class NPetal extends Widget {
        public String name;
        public int num;
        private Text text;
        private Text textnum;

        public NPetal(String name, int num) {
            super(Coord.z);
            this.name = name;
            this.num = num;
            text = NStyle.flower.render(name);
            textnum = NStyle.flower.render(String.valueOf(num));
            resize(text.sz().x + bl.sz().x + br.sz().x + UI.scale(30), ph);
        }

        public void draw(GOut g)
        {
            g.image((isHighligted) ? bhl : bl, new Coord(0, 0));

            Coord pos = new Coord(0, 0);
            for (pos.x = bl.sz().x; pos.x + bm.sz().x <= len - br.sz().x; pos.x += bm.sz().x)
            {
                g.image((isHighligted) ? bhm : bm, pos);
            }
            g.image((isHighligted) ? bhm : bm, pos, new Coord(sz.x - pos.x - br.sz().x, br.sz().y));
            g.image(textnum.tex(), new Coord(bl.sz().x/2 - textnum.tex().sz().x/2 - UI.scale(1), br.sz().y / 2 - textnum.tex().sz().y / 2));
            g.image(text.tex(), new Coord(br.sz().x + bl.sz().x + UI.scale(10), br.sz().y / 2 - text.tex().sz().y / 2));
            g.image((isHighligted) ? bhr : br, new Coord(len - br.sz().x, 0));
        }

        @Override
        public boolean mousedown(MouseDownEvent ev) {
            nchoose(this);
            return(true);
        }

        @Override
        public void mousemove(MouseMoveEvent ev)
        {
            isHighligted = ev.c.isect(Coord.z, sz);
            super.mousemove(ev);
        }

        boolean isHighligted = false;
    }

    protected void added()
    {
        if (c.equals(-1, -1))
            c = parent.ui.lcc;
        mg = ui.grabmouse(this);
        kg = ui.grabkeys(this);
    }

    @Override
    public void draw(GOut g) {
        if(sb != null) {
            sb.max = nopts.length - MAX_VISIBLE_ITEMS;
            for(int i = 0; i < nopts.length; i++) {
                nopts[i].c = new Coord(0, (i - sb.val) * itemHeight);
            }
            super.draw(g, true);
        } else {
            super.draw(g, false);
        }
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
        if(isLocalStumpMenu() && !ev.propagate(this)) {
            nchoose(null);
            return true;
        }
        if(sb != null && sb.vis()) {
            Coord sc = ev.c.sub(sb.c);
            if(sc.isect(Coord.z, sb.sz)) {
                sb.mousedown(ev.derive(sc));
                return false;
            }
        }
        return super.mousedown(ev);
    }

    private boolean isLocalStumpMenu() {
        return (nopts != null) && (nopts.length == 1) && hasOpt(L10n.get(KEY_REMOVE_STUMP));
    }

    @Override
    public boolean mousewheel(MouseWheelEvent ev) {
        if(sb != null) {
            sb.ch(ev.a);
            return true;
        }
        return super.mousewheel(ev);
    }

    public void uimsg(String msg, Object... args)
    {

        if (msg.equals("cancel") || msg.equals("act"))
        {
            ui.destroy(NFlowerMenu.this);
        }
    }


    @Override
    public void destroy() {
        mg.remove();
        kg.remove();
        super.destroy();
    }

    public boolean keydown(KeyDownEvent ev) {
        char key = ev.c;
        if((key >= '0') && (key <= '9')) {
            int opt = (key == '0')?10:(key - '1');
            if(opt < nopts.length) {
                nchoose(nopts[opt]);
                kg.remove();
            }
            return(true);
        } else if(key_esc.match(ev)) {
            nchoose(null);
            kg.remove();
            return(true);
        }
        return(false);
    }

    public boolean chooseOpt(String value)
    {
        for(NPetal petal: nopts)
        {
            if(petal.name.equals(value))
            {
                nchoose(petal);
                return true;
            }
        }
        wdgmsg("cl", -1);
        return false;
    }
}
