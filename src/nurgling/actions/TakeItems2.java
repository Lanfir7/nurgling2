package nurgling.actions;

import haven.Coord;
import haven.Gob;
import haven.UI;
import haven.WItem;
import haven.Widget;
import haven.Window;
import haven.res.ui.barterbox.Shopbox;
import nurgling.NGameUI;
import nurgling.NInventory;
import nurgling.NInventory.QualityType;
import nurgling.NUtils;
import nurgling.areas.NContext;
import nurgling.tasks.WaitItems;
import nurgling.tasks.WaitTicks;
import nurgling.tasks.WindowIsClosed;
import nurgling.tools.Container;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.StackSupporter;
import nurgling.widgets.Specialisation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

public class TakeItems2 implements Action
{
    static final int PILE_WITHDRAWAL_SETTLE_TICKS = 15;

    @FunctionalInterface
    interface PileWithdrawalStep {
        void run() throws InterruptedException;
    }

    @FunctionalInterface
    interface PileWithdrawalOpen {
        Results run() throws InterruptedException;
    }

    static Results approachSettleAndOpenPile(PileWithdrawalOpen approach,
                                             PileWithdrawalStep settle,
                                             PileWithdrawalOpen open)
            throws InterruptedException {
        approach.run();
        settle.run();
        return open.run();
    }

    static int pileTransferCount(int requested, int capacity) {
        return Math.max(0, Math.min(requested, capacity));
    }

    static boolean inventoryCannotAcceptItem(int freeCells, boolean partialStackAvailable) {
        return freeCells <= 0 && !partialStackAvailable;
    }

    final NContext cnt;
    String item;
    int count;
    Specialisation.SpecName specName;
    String specSubtype;
    QualityType qualityType;
    public boolean exactMatch = false;
    public ArrayList<Container> fillTargets = null;
    private Coord observedShape = null;

    static int capacityForShape(ArrayList<Container> targets, Coord shape) {
        int capacity = 0;
        for(Container target : targets)
            capacity += target.freeSpace(shape);
        return capacity;
    }

    public Coord getObservedShape() {
        return observedShape;
    }

    private void observeShape(ArrayList<WItem> candidates) {
        if(observedShape != null)
            return;
        for(WItem candidate : candidates) {
            if(candidate.item.spr == null)
                continue;
            observedShape = candidate.item.spr.sz().div(UI.scale(32)).swapXY();
            if(fillTargets != null)
                count = capacityForShape(fillTargets, observedShape);
            return;
        }
    }


    public TakeItems2(NContext context, String item, int count)
    {
        this.cnt = context;
        this.item = item;
        this.count = count;
        this.qualityType = null;
    }

    public TakeItems2(NContext context, String item, int count, Specialisation.SpecName specName)
    {
        this.cnt = context;
        this.item = item;
        this.count = count;
        this.specName = specName;
        this.qualityType = null;
    }

    public TakeItems2(NContext context, String item, int count, QualityType qualityType)
    {
        this.cnt = context;
        this.item = item;
        this.count = count;
        this.qualityType = qualityType;
    }

    public TakeItems2(NContext context, String item, int count, Specialisation.SpecName specName, QualityType qualityType)
    {
        this.cnt = context;
        this.item = item;
        this.count = count;
        this.specName = specName;
        this.qualityType = qualityType;
    }

    public TakeItems2(NContext context, String item, int count, Specialisation.SpecName specName, String specSubtype)
    {
        this.cnt = context;
        this.item = item;
        this.count = count;
        this.specName = specName;
        this.specSubtype = specSubtype;
        this.qualityType = QualityType.High;
    }

    public TakeItems2(NContext context, int count, Specialisation.SpecName specName, QualityType qualityType)
    {
        this.cnt = context;
        this.count = count;
        this.specName = specName;
        this.qualityType = qualityType;
    }

    private boolean noRoomLeft(NGameUI gui) throws InterruptedException
    {
        NInventory inventory = gui.getInventory();
        if(inventory == null)
            return false;
        int freeCells = inventory.getNumberFreeCoord(new Coord(1, 1));
        boolean partialStackAvailable = freeCells <= 0 && item != null
                && inventory.findNotFullStack(item) != null;
        return inventoryCannotAcceptItem(freeCells, partialStackAvailable);
    }

    private boolean noRoomLeftForAlias(NGameUI gui) throws InterruptedException {
        NInventory inventory = gui.getInventory();
        if(inventory == null)
            return false;
        if(observedShape != null && !observedShape.equals(1, 1))
            return inventory.getNumberFreeCoord(observedShape) <= 0;
        return inventory.getFreeSpace() <= 0;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException
    {
        if(item == null)
            return Results.FAIL();
        AtomicInteger left = new AtomicInteger(count);
        ArrayList<NContext.ObjectStorage> inputs;
        if(specName == null) {
            inputs = cnt.getInStorages(item);
        } else {
            inputs = cnt.getSpecStorages(this.specName, this.specSubtype);
        }

        if(inputs == null || inputs.isEmpty())
            return Results.FAIL();
        for(NContext.ObjectStorage input: inputs)
        {
            if(noRoomLeft(gui))
                return Results.SUCCESS();
            if(input instanceof NContext.Barter)
                takeFromBarter(left,gui, (NContext.Barter)input);
            else if (input instanceof NContext.Pile)
            {
                takeFromPile(left, gui,(NContext.Pile) input);
            }
            else if (input instanceof Container)
            {
                takeFromContainer(left, gui, (Container) input);
            }
            if(NUtils.getGameUI().getInventory().getItems(new NAlias(item)).size() >= count) {
                return Results.SUCCESS();
            }
            else
            {
                left.set(count - NUtils.getGameUI().getInventory().getItems(new NAlias(item)).size());
            }
        }
        return Results.SUCCESS();
    }

    /** Collects any item matched by the alias from every storage in a specialised area. */
    public Results takeAny(NAlias itemsAlias, NGameUI gui) throws InterruptedException {
        ArrayList<NContext.ObjectStorage> inputs = specName == null
                ? cnt.getInStorages(itemsAlias.getKeys().get(0))
                : cnt.getSpecStorages(specName, specSubtype);
        if(inputs == null || inputs.isEmpty())
            return Results.FAIL();

        HashSet<String> names = new HashSet<>(itemsAlias.getKeys());
        AtomicInteger left = new AtomicInteger(count);
        for(NContext.ObjectStorage input : inputs) {
            if(noRoomLeftForAlias(gui))
                return Results.SUCCESS();
            if(input instanceof NContext.Pile) {
                while(true) {
                    int before = gui.getInventory().getItems(itemsAlias).size();
                    if(before >= count)
                        break;
                    left.set(count - before);
                    if(!takeAnyFromPile(left, gui, (NContext.Pile) input).IsSuccess())
                        break;
                    ArrayList<WItem> held = gui.getInventory().getItems(itemsAlias);
                    observeShape(held);
                    if(held.size() == before || noRoomLeftForAlias(gui))
                        break;
                }
            } else if(input instanceof Container) {
                Container container = (Container) input;
                Gob gob = Container.pathTo(gui, container);
                if(gob == null || (!"Frame".equals(container.cap) && gob.ngob.isContainerEmpty()))
                    continue;
                new OpenTargetContainer(container).run(gui);
                NInventory inventory = gui.getInventory(container.cap);
                if(inventory != null)
                    observeShape(inventory.getItems(itemsAlias));
                while(true) {
                    int before = gui.getInventory().getItems(itemsAlias).size();
                    if(before >= count)
                        break;
                    TakeItemsFromContainer take = new TakeItemsFromContainer(
                            container, names, itemsAlias, qualityType);
                    take.minSize = count - before;
                    take.exactMatch = exactMatch;
                    take.run(gui);
                    int after = gui.getInventory().getItems(itemsAlias).size();
                    if(after == before || noRoomLeftForAlias(gui))
                        break;
                }
                new CloseTargetContainer(container).run(gui);
            }

            int held = gui.getInventory().getItems(itemsAlias).size();
            if(held >= count)
                return Results.SUCCESS();
            left.set(count - held);
        }
        return Results.SUCCESS();
    }

    public Results takeFromBarter(AtomicInteger left, NGameUI gui, NContext.Barter barter) throws InterruptedException
    {
        if(item == null)
            return Results.FAIL();
        Gob gchest = Finder.findGob(barter.chest);
        Gob gbarter = Finder.findGob(barter.barter);
        if(gbarter==null || gchest==null)
            return Results.FAIL();

        // A single visit can only carry as many "Branch" (the barter currency) as fit in the
        // free inventory slots, so we may not be able to buy everything we need in one pass.
        // Repeat the whole take-currency -> buy cycle until we have enough or we can no longer
        // make progress (chest out of currency, no free inventory space, or stand out of stock).
        while (left.get() > 0)
        {
            // 1. Open the chest and look at how much currency is available.
            new PathFinder(gchest).run(gui);
            new OpenTargetContainer("Chest", gchest).run(gui);
            if(gui.getInventory("Chest") == null)
                break;
            ArrayList<WItem> chestBranches = gui.getInventory("Chest").getItems("Branch");
            if(chestBranches.isEmpty())
                break; // no currency left to buy with

            // 2. How many can we carry this pass: limited by need, chest stock and free slots.
            int freeSlots = gui.getInventory().getNumberFreeCoord(chestBranches.get(0));
            int to_take = Math.min(Math.min(left.get(), chestBranches.size()), freeSlots);
            if(to_take <= 0)
                break; // no room to carry currency -> cannot make progress

            // 3. Move the currency into the inventory and read how many actually arrived
            // (SimpleTransferToContainer silently clamps to free space).
            int branchesBefore = gui.getInventory().getItems("Branch").size();
            new SimpleTransferToContainer(gui.getInventory(), gui.getInventory("Chest").getItems("Branch"), to_take).run(gui);
            int payable = gui.getInventory().getItems("Branch").size() - branchesBefore;
            Window chestWnd = gui.getWindow("Chest");
            if(chestWnd != null)
            {
                chestWnd.wdgmsg("close");
                gui.ui.core.addTask(new WindowIsClosed(chestWnd));
            }
            if(payable <= 0)
                break; // nothing actually transferred -> avoid spinning forever

            // 4. Walk to the stand and buy exactly as many as we can pay for.
            new PathFinder(gbarter).run(gui);
            new OpenTargetContainer("Barter Stand", gbarter).run(gui);

            Window barter_wnd = gui.getWindow("Barter Stand");
            if(barter_wnd==null)
            {
                return Results.ERROR("No Barter window");
            }

            int bought = 0;
            for(Widget ch = barter_wnd.child; ch != null; ch = ch.next)
            {
                if (ch instanceof Shopbox)
                {
                    Shopbox sb = (Shopbox) ch;
                    Shopbox.ShopItem offer = sb.getOffer();
                    if (offer != null)
                    {
                        if (offer.name.equals(item))
                        {
                            // Cap by what the stand still has in stock (leftNum == 0 means unlimited).
                            int to_buy = (sb.leftNum != 0) ? Math.min(payable, sb.leftNum) : payable;
                            int itemBefore = gui.getInventory().getItems(new NAlias(item)).size();
                            for (int i = 0; i < to_buy; i++)
                            {
                                sb.wdgmsg("buy", new Object[0]);
                            }

                            NUtils.getUI().core.addTask(new WaitItems(NUtils.getGameUI().getInventory(), new NAlias(item), itemBefore + to_buy));
                            bought = gui.getInventory().getItems(new NAlias(item)).size() - itemBefore;
                            break;
                        }
                    }
                }
            }

            if(bought <= 0)
                break; // matching offer missing or stand could not deliver -> stop

            left.set(left.get() - bought);
        }
        return Results.SUCCESS();
    }

    private Results takeAnyFromPile(AtomicInteger left, NGameUI gui, NContext.Pile pile)
            throws InterruptedException {
        Gob gob = pile == null || pile.pile == null ? null : Finder.findGob(pile.pile.id);
        if(gob == null || !PathFinder.isAvailable(gob))
            return Results.FAIL();

        Results opened = approachSettleAndOpenPile(
                () -> new PathFinder(gob).run(gui),
                () -> NUtils.addTask(new WaitTicks(PILE_WITHDRAWAL_SETTLE_TICKS)),
                () -> new OpenTargetContainer("Stockpile", gob).run(gui));
        if(!opened.IsSuccess() || gui.getStockpile() == null)
            return Results.FAIL();

        Coord shape = observedShape == null ? new Coord(1, 1) : observedShape;
        int capacity = gui.getInventory().getNumberFreeCoord(shape);
        int transferCount = pileTransferCount(left.get(), capacity);
        if(transferCount > 0)
            new TakeItemsFromPile(gob, gui.getStockpile(), transferCount).run(gui);

        Window window = gui.getWindow("Stockpile");
        if(window != null)
            new CloseTargetWindow(window).run(gui);
        return Results.SUCCESS();
    }

    public Results takeFromPile(AtomicInteger left, NGameUI gui, NContext.Pile pile) throws InterruptedException
    {
        Gob gpile = pile == null || pile.pile == null ? null : Finder.findGob(pile.pile.id);
        if(gpile == null || !PathFinder.isAvailable(gpile))
            return Results.FAIL();

        Results opened = approachSettleAndOpenPile(
                    () -> new PathFinder(gpile).run(gui),
                    () -> NUtils.addTask(new WaitTicks(PILE_WITHDRAWAL_SETTLE_TICKS)),
                    () -> new OpenTargetContainer("Stockpile", gpile).run(gui));
        if(!opened.IsSuccess() || gui.getStockpile() == null)
            return Results.FAIL();

        int capacity = StackSupporter.getOptimalItemCapacity(
                gui.getInventory(), item, new Coord(1, 1), left.get());
        int transferCount = pileTransferCount(left.get(), capacity);
        if(transferCount > 0)
            new TakeItemsFromPile(gpile, gui.getStockpile(), transferCount).run(gui);

        Window stockpileWindow = NUtils.getGameUI().getWindow("Stockpile");
        if(stockpileWindow != null)
            new CloseTargetWindow(stockpileWindow).run(gui);
        return Results.SUCCESS();
    }

    public Results takeFromContainer(AtomicInteger left, NGameUI gui, Container cont) throws InterruptedException
    {
        Gob contgob = Finder.findGob(cont.gobHash);
        if(contgob == null)
            return Results.FAIL();
        // Skip empty containers using visual flag (except dframes)
        if(!"Frame".equals(cont.cap) && contgob.ngob.isContainerEmpty())
            return Results.SUCCESS();
        new PathFinder(contgob).run(gui);
        new OpenTargetContainer(cont).run(gui);
        TakeItemsFromContainer tifc = new TakeItemsFromContainer(cont,new HashSet<>(Arrays.asList(item)), null, qualityType);
        tifc.minSize = left.get();
        tifc.exactMatch = this.exactMatch;
        tifc.run(gui);
        new CloseTargetContainer(cont).run(gui);
        return Results.SUCCESS();
    }
}
