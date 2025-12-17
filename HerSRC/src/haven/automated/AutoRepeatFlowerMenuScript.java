package haven.automated;

import haven.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AutoRepeatFlowerMenuScript implements Runnable{
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    public static String option;
    private GameUI gui;
    private boolean stop = false;
    private int ping;
    private final String name;
    private List<GItem> items;

    public AutoRepeatFlowerMenuScript(GameUI gui, String name) {
        scheduler.schedule(() -> {
            stop = true;
            }, 2000, TimeUnit.MILLISECONDS);
        this.gui = gui;
        Integer ping = GameUI.getPingValue();
        this.ping = ping != null ? ping + 10 : 200;
        option = "";
        this.name = name;
        items = iterateThroughItems(name);
    }

    public List<GItem> iterateThroughItems(String name) {
        List<GItem> items = new ArrayList<>();
        if (OptWnd.alsoUseContainersWithRepeaterCheckBox.a) {
            for (Inventory inventory : gui.getAllInventories()){
                for (WItem item : inventory.getAllItems()) {
                    try {
                        if (item.item != null && item.item.getres() != null && item.item.getres().name.equals(name)) {
                            items.add(item.item);
                        }
                    } catch (Loading ignored) {
                    }
                }
            }
        } else {
            for (WItem item : gui.maininv.getAllItems()) {
                try {
                    if (item.item != null && item.item.getres() != null && item.item.getres().name.equals(name)) {
                        items.add(item.item);
                    }
                } catch (Loading ignored) {
                }
            }
        }
        return items;
    }

    @Override
    public void run() {
        while(!stop){
            if(option != null && !option.equals("")){
                scheduler.shutdown();
                if (option.equals("Study")){
                    gui.error("Auto-Repeat Flower-Menu Script: Stopped script. The \"Study\" option disabled for this script, cause you can only study one at a time.");
                    stop = true;
                } else if (option.equals("Eat")){
                    gui.error("Auto-Repeat Flower-Menu Script: Stopped script. The \"Eat\" option is disabled for this script, to avoid user error.");
                    stop = true;
                } else {
                    int counter = 0;
                    while(!items.isEmpty() && counter <= 10){
                        counter++;
                        for(GItem item : items){
                            FlowerMenu.setNextSelection(option);
                            item.wdgmsg("iact", Coord.z, 3);
                            try {
                                Thread.sleep(ping);
                            } catch (InterruptedException ignored) {
                                FlowerMenu.setNextSelection(null);
                                return;
                            }
                        }
                        items = iterateThroughItems(name);
                        Integer pingValue = GameUI.getPingValue();
                        ping = pingValue != null ? pingValue + 10 : 200;
                    }
                    stop = true;
                }
            }
            try {
                Thread.sleep(ping);
            } catch (InterruptedException ignored) {
                FlowerMenu.setNextSelection(null);
                return;
            }

        }
        option = null;
        FlowerMenu.setNextSelection(null);
        if (gui.autoRepeatFlowerMenuScriptThread != null) {
            gui.autoRepeatFlowerMenuScriptThread.interrupt();
            gui.autoRepeatFlowerMenuScriptThread = null;
        }
    }
}
