package nurgling;

import haven.*;
import monitoring.StockpileStorageTracker;
import nurgling.db.StockpileStoragePolicy;
import nurgling.tasks.*;

import java.awt.event.*;
import java.util.*;

public class NISBox extends ISBox
{
    public Gob parentGob = null;
    private TextEntry.NumberValue value;
    private TakeButton take;
    private Gob observedPile;

    private int rem;
    /** The piled item's resource; ISBox keeps its own copy private. */
    public final Indir<Resource> itemres;

    public NISBox(Indir<Resource> res, int rem, int av, int bi)
    {
        super(res, rem, av, bi);
        this.rem = rem;
        this.itemres = res;
    }

    public int calcFreeSpace()
    {
        if ( label == null || label.text == null ) {
            return -1;
        }
        int sep = label.text.indexOf ( '/' );
        if ( sep > 0 ) {
            String count = label.text.substring ( 0, sep );
            String capacity = label.text.substring ( sep + 1 );
            try {
                return Integer.parseInt ( capacity ) - Integer.parseInt ( count );
            }
            catch ( NumberFormatException nfe ) {
            }
        }
        return -1;
    }

    public int calcCount()
    {
        if ( label == null || label.text == null ) {
            return -1;
        }
        int sep = label.text.indexOf ( '/' );
        if ( sep > 0 ) {
            String count = label.text.substring ( 0, sep );
            try {
                return Integer.parseInt ( count );
            }
            catch ( NumberFormatException nfe ) {
            }
        }
        return -1;
    }

    public int getFreeSpace() throws InterruptedException
    {
        GetFreeSpace gfs = new GetFreeSpace(this);
        NUtils.getUI().core.addTask(gfs);
        return gfs.result();
    }

    public int total() throws InterruptedException
    {
        GetPileSize gps = new GetPileSize(this);
        NUtils.getUI().core.addTask(gps);
        return gps.getResult();
    }

    public void transfer(int amount)
    {
        beginWithdrawalTracking();
        for (int i = 0; i < amount; i++)
        {
            wdgmsg("xfer2", -1, 1);
        }
    }

    public void put(int amount)
    {
        beginDepositTracking();
        for (int i = 0; i < amount; i++)
        {
            wdgmsg("xfer2", 1, 1);
        }
    }



    private class TakeButton extends Button{
        public TakeButton(int w, String text) {
            super(w, text);
        }

        @Override
        public void click () {
            int amount = 1;
            try {
                amount = Integer.parseInt(value.text());
            } catch (Exception ignored) {
            }
            int available = calcCount();
            if (available < 0) available = rem;
            if (amount > available) {
                amount = available;
            }
            if (amount > 0) {
                transfer(amount);
            }

        }
    }

    protected void added() {
        if(parent instanceof Window) {
            boolean isStockpile = "Stockpile".equals(((Window) parent).cap);
            if(isStockpile) {
                take = new TakeButton(UI.scale(60), "Take");
                value = new TextEntry.NumberValue(UI.scale(40), "");
                parent.add(value, UI.scale(45, 50));
                value.canactivate = true;


                parent.add(take, UI.scale(87, 44));
                take.canactivate = true;
                bindParentPileIfNeeded();
            }
        }
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        bindParentPileIfNeeded();
    }

    private void bindParentPileIfNeeded() {
        if (!(parent instanceof Window) || !"Stockpile".equals(((Window) parent).cap))
            return;
        if (parentGob != null && (parentGob.ngob == null
                || !StockpileStoragePolicy.isStockpileRes(parentGob.ngob.name)))
            parentGob = null;
        if (parentGob == null && ui != null && ui.core != null) {
            parentGob = ui.core.takeClickedGob(NISBox::isStockpileGob);
            if (parentGob == null)
                parentGob = ui.core.takePlacedGob(NISBox::isStockpileGob);
        }
        if (parentGob != null && observedPile != parentGob && ui != null
                && ui.gui == NUtils.getGameUI()) {
            observedPile = parentGob;
            StockpileStorageTracker.onGob(parentGob);
            StockpileStorageTracker.observeOpenPile(parentGob, stockpileItemName(), stockpileCount());
        }
    }

    private static boolean isStockpileGob(Gob gob) {
        return gob != null && gob.ngob != null
                && StockpileStoragePolicy.isStockpileRes(gob.ngob.name);
    }
}
