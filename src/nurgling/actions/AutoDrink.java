package nurgling.actions;

import haven.MenuGrid;
import haven.WItem;
import nurgling.*;
import nurgling.NConfig;
import nurgling.areas.NContext;
import nurgling.tasks.*;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;
import nurgling.widgets.NEquipory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

public class AutoDrink implements Action
{

    public final AtomicBoolean stop = new AtomicBoolean(false);

    public AutoDrink()
    {
        stop.set(false);
    }

    public void requestStop() {
        stop.set(true);
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException
    {
        while(!stop.get())
        {
            // Get threshold from config (make it final for inner class)
            final double threshold;
            {
                double thresholdValue = 0.51; // Default
                Object thresholdObj = NConfig.get(NConfig.Key.autoDrinkThreshold);
                if (thresholdObj instanceof Number) {
                    thresholdValue = ((Number) thresholdObj).doubleValue();
                }
                threshold = thresholdValue;
            }
            
            NUtils.addTask(new NTask() {
                @Override
                public boolean check() {
                    if(NUtils.getGameUI()==null)
                        return false;
                    double stamina = NUtils.getStamina();
                    if(stamina < 0)
                        return false;
                    NGameUI g = NUtils.getGameUI();
                    boolean botRunning = (g != null && g.biw != null && g.biw.waitBot.get()) || NContext.waitBot.get();
                    return (!botRunning && stamina < threshold) || stop.get();
                }
            });
            if(stop.get()) {
                if (gui != null && gui.ui != null && gui.ui.core != null) {
                    gui.ui.core.autoDrink = null;
                }
                return Results.SUCCESS();
            }

            DrinkResult drinkResult = checkDrink();
            if(drinkResult.hasDrink) {
                NUtils.getUI().dropLastError();
                NGameUI gameUI = NUtils.getGameUI();
                if (gameUI == null) {
                    continue;
                }
                
                String actionName = drinkResult.isTea ? "Sip" : "Drink";
                boolean actionUsed = false;
                
                if (drinkResult.isTea && drinkResult.itemToActivate != null) {
                    // For tea, use SelectFlowerAction for instant menu usage (like in other actions)
                    SelectFlowerAction selectAction = new SelectFlowerAction(actionName, drinkResult.itemToActivate);
                    Results result = selectAction.run(gameUI);
                    if (result.IsSuccess()) {
                        actionUsed = true;
                    } else {
                        continue; // Failed to select, try again
                    }
                } else {
                    // For water, use already open menu (like in Drink.java)
                    if (gameUI.menu == null) {
                        continue;
                    }
                    
                    MenuGrid.PagButton foundButton = null;
                    Collection<MenuGrid.Pagina> paginaeCopy;
                    synchronized (gameUI.menu.paginae) {
                        paginaeCopy = new ArrayList<>(gameUI.menu.paginae);
                    }
                    
                    for (MenuGrid.Pagina pag : paginaeCopy) {
                        try {
                            if (pag != null && pag.button() != null && actionName.equals(pag.button().name())) {
                                foundButton = pag.button();
                                break;
                            }
                        } catch (Exception e) {
                            // Continue searching
                        }
                    }
                    
                    if (foundButton == null) {
                        continue; // Button not found, try again
                    }
                    
                    // Use the found button (reset=true to close menu after use)
                    gameUI.menu.use(foundButton, new MenuGrid.Interaction(1, 0), true);
                    actionUsed = true;
                }
                
                // Only wait for animation if action was actually used
                if (actionUsed) {
                    // Wait for drinking animation to start
                    WaitPoseOrMsg wops = new WaitPoseOrMsg(NUtils.player(), "gfx/borka/drinkan", new NAlias("You have nothing on your hotbelt to drink."));
                    NUtils.getUI().core.addTask(wops);
                    
                    // Get timeout from config (in seconds, convert to ticks at ~60fps)
                    double timeoutSeconds = 5.0; // Default
                    Object timeoutObj = NConfig.get(NConfig.Key.autoDrinkTimeout);
                    if (timeoutObj instanceof Number) {
                        timeoutSeconds = ((Number) timeoutObj).doubleValue();
                    }
                    final int maxTimeout = (int)(timeoutSeconds * 60); // Convert seconds to ticks
                    
                    // Wait for animation to finish or timeout, and also wait for stamina to recover
                    NUtils.addTask(new NTask() {
                        private int timeout = 0;
                        
                        @Override
                        public boolean check() {
                            Gob player = NUtils.player();
                            if (player == null) {
                                return true; // Player gone, stop waiting
                            }
                            
                            String pose = player.pose();
                            // Animation finished if not in drinking pose anymore
                            boolean animationFinished = (pose == null || !pose.contains("gfx/borka/drinkan"));
                            
                            // Also check if stamina recovered above threshold to prevent multiple clicks
                            double currentStamina = NUtils.getStamina();
                            boolean staminaRecovered = (currentStamina >= 0 && currentStamina >= threshold);
                            
                            // Wait until animation finishes AND stamina recovers (or timeout)
                            if (animationFinished && staminaRecovered) {
                                return true; // Animation done and stamina recovered
                            }
                            
                            // Timeout after max attempts
                            if (timeout++ >= maxTimeout) {
                                return true; // Timeout, continue anyway
                            }
                            
                            return false;
                        }
                    });
                }
            }
            else
            {
                NUtils.addTask(new NTask(){
                    int count = 0;
                    @Override
                    public boolean check()
                    {
                        return count++>60;
                    }
                });
            }
        }
        if (gui != null && gui.ui != null && gui.ui.core != null) {
            gui.ui.core.autoDrink = null;
        }
        return Results.SUCCESS();
    }

    static class DrinkResult {
        boolean hasDrink = false;
        boolean isTea = false;
        WItem itemToActivate = null; // Item that needs to be activated to open menu
    }

    DrinkResult checkDrink() throws InterruptedException
    {
        DrinkResult result = new DrinkResult();
        
        NEquipory equipment = NUtils.getEquipment();
        if (equipment == null) {
            return result;
        }
        
        // Check waterskins and other containers in belt
        WItem wbelt = equipment.findItem(NEquipory.Slots.BELT.idx);
        if(wbelt != null && wbelt.item.contents != null) {
            ArrayList<WItem> witems = ((NInventory) wbelt.item.contents).getItems(new NAlias("Waterskin", "Glass Jug", "Waterflask", "Kuksa"));
            if (!witems.isEmpty()) {
                for (WItem item : witems) {
                    NGItem ngItem = ((NGItem) item.item);
                    if (!ngItem.content().isEmpty()) {
                        // Check all content items, not just the first one
                        for (NGItem.NContent content : ngItem.content()) {
                            String contentName = content.name();
                            if (contentName != null) {
                                String lowerContentName = contentName.toLowerCase();
                                if (lowerContentName.contains("tea")) {
                                    result.hasDrink = true;
                                    result.isTea = true;
                                    result.itemToActivate = item;
                                    return result;
                                } else if (lowerContentName.contains("water")) {
                                    result.hasDrink = true;
                                    result.isTea = false;
                                    if (result.itemToActivate == null) {
                                        result.itemToActivate = item;
                                    }
                                    // Continue checking for tea (tea has priority)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Check bucket in hands
        WItem bucket = equipment.findBucket("Water");
        if (bucket != null) {
            NGItem ngItem = ((NGItem) bucket.item);
            if (!ngItem.content().isEmpty()) {
                for (NGItem.NContent content : ngItem.content()) {
                    String contentName = content.name();
                    if (contentName != null) {
                        String lowerContentName = contentName.toLowerCase();
                        if (lowerContentName.contains("tea")) {
                            result.hasDrink = true;
                            result.isTea = true;
                            result.itemToActivate = bucket;
                            return result;
                        } else if (lowerContentName.contains("water") && !result.hasDrink) {
                            result.hasDrink = true;
                            result.isTea = false;
                            result.itemToActivate = bucket;
                        }
                    }
                }
            }
        }
        
        // Check for tea in hands (for teapots, cups, etc.)
        WItem leftHand = NUtils.getEquipment().findItem(NEquipory.Slots.HAND_LEFT.idx);
        WItem rightHand = NUtils.getEquipment().findItem(NEquipory.Slots.HAND_RIGHT.idx);
        
        for (WItem handItem : new WItem[]{leftHand, rightHand}) {
            if (handItem != null) {
                NGItem ngItem = ((NGItem) handItem.item);
                if (!ngItem.content().isEmpty()) {
                    for (NGItem.NContent content : ngItem.content()) {
                        String contentName = content.name();
                        if (contentName != null) {
                            String lowerContentName = contentName.toLowerCase();
                            if (lowerContentName.contains("tea")) {
                                result.hasDrink = true;
                                result.isTea = true;
                                result.itemToActivate = handItem;
                                return result;
                            } else if (lowerContentName.contains("water") && !result.hasDrink) {
                                result.hasDrink = true;
                                result.isTea = false;
                                if (result.itemToActivate == null) {
                                    result.itemToActivate = handItem;
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Check foot slots (pockets) for drink containers
        WItem leftFoot = NUtils.getEquipment().findItem(NEquipory.Slots.LFOOT.idx);
        WItem rightFoot = NUtils.getEquipment().findItem(NEquipory.Slots.RFOOT.idx);
        
        for (WItem footItem : new WItem[]{leftFoot, rightFoot}) {
            if (footItem != null && footItem.item instanceof NGItem) {
                NGItem ngItem = ((NGItem) footItem.item);
                String itemName = ngItem.name();
                
                // Check if it's a drink container or bucket
                NAlias drinkContainers = new NAlias("Waterskin", "Glass Jug", "Waterflask", "Kuksa");
                if (itemName != null && (NParser.checkName(itemName, drinkContainers) || 
                    NParser.checkName(itemName, new NAlias("Bucket")))) {
                    if (!ngItem.content().isEmpty()) {
                        for (NGItem.NContent content : ngItem.content()) {
                            String contentName = content.name();
                            if (contentName != null) {
                                String lowerContentName = contentName.toLowerCase();
                                if (lowerContentName.contains("tea")) {
                                    result.hasDrink = true;
                                    result.isTea = true;
                                    result.itemToActivate = footItem;
                                    return result;
                                } else if (lowerContentName.contains("water") && !result.hasDrink) {
                                    result.hasDrink = true;
                                    result.isTea = false;
                                    if (result.itemToActivate == null) {
                                        result.itemToActivate = footItem;
                                    }
                                    // Continue checking for tea (tea has priority)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Check main inventory for drink containers
        NGameUI gameUI = NUtils.getGameUI();
        if (gameUI != null) {
            NInventory mainInv = gameUI.getInventory();
            if (mainInv != null) {
                ArrayList<WItem> invItems = mainInv.getItems(new NAlias("Waterskin", "Glass Jug", "Waterflask", "Kuksa"));
                if (!invItems.isEmpty()) {
                    for (WItem item : invItems) {
                        NGItem ngItem = ((NGItem) item.item);
                        if (!ngItem.content().isEmpty()) {
                            for (NGItem.NContent content : ngItem.content()) {
                                String contentName = content.name();
                                if (contentName != null) {
                                    String lowerContentName = contentName.toLowerCase();
                                    if (lowerContentName.contains("tea")) {
                                        result.hasDrink = true;
                                        result.isTea = true;
                                        result.itemToActivate = item;
                                        return result;
                                    } else if (lowerContentName.contains("water") && !result.hasDrink) {
                                        result.hasDrink = true;
                                        result.isTea = false;
                                        if (result.itemToActivate == null) {
                                            result.itemToActivate = item;
                                        }
                                        // Continue checking for tea (tea has priority)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return result;
    }
}
