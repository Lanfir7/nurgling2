package nurgling.actions;

import haven.*;
import nurgling.*;
import nurgling.actions.bots.SelectArea;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.tasks.HandIsFree;
import nurgling.tasks.NTask;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;
import nurgling.widgets.NEquipory;
import nurgling.widgets.Specialisation;

import java.util.ArrayList;

public class EmptyTeapots implements Action {
    boolean oz;
    public EmptyTeapots(boolean only_area){oz = only_area;}
    public EmptyTeapots(){oz = false;}

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        Pair<Coord2d,Coord2d> area = null;
        NArea nArea = NContext.findSpec(Specialisation.SpecName.water.toString());
        if(nArea==null)
        {
            nArea = NContext.findSpecGlobal(Specialisation.SpecName.water.toString());
            if(nArea!=null)
            {
                NUtils.navigateToArea(nArea);
                area = nArea.getRCArea();
            }
            else
            {
                SelectArea insa;
                NUtils.getGameUI().msg("Please, select area with barrels");
                (insa = new SelectArea(Resource.loadsimg("baubles/waterRefiller"))).run(gui);
                area = insa.getRCArea();
            }
        }
        else
        {
            area = nArea.getRCArea();
        }

        if(area==null)
        {
            return Results.ERROR("no area selected");
        }

        // Find barrels in area
        ArrayList<Gob> barrels = Finder.findGobs(area, new NAlias("barrel"));
        if(barrels.isEmpty())
        {
            return Results.ERROR("No barrels in area");
        }

        // Collect all teapots with tea
        ArrayList<WItem> teapots = new ArrayList<>();
        
        // Check main inventory
        ArrayList<WItem> inventoryItems = gui.getInventory().getItems(new NAlias("Teapot"));
        for(WItem item : inventoryItems)
        {
            NGItem ngItem = ((NGItem)item.item);
            if(!ngItem.content().isEmpty())
            {
                String contentName = ngItem.content().get(0).name();
                // Check if it's tea (Piping Hot Tea or Tea)
                if(contentName.contains("Tea") || contentName.contains("tea"))
                {
                    teapots.add(item);
                }
            }
        }
        
        // Check belt
        WItem wbelt = NUtils.getEquipment().findItem(NEquipory.Slots.BELT.idx);
        if(wbelt != null && wbelt.item.contents instanceof NInventory)
        {
            ArrayList<WItem> beltItems = ((NInventory) wbelt.item.contents).getItems(new NAlias("Teapot"));
            for(WItem item : beltItems)
            {
                NGItem ngItem = ((NGItem)item.item);
                if(!ngItem.content().isEmpty())
                {
                    String contentName = ngItem.content().get(0).name();
                    // Check if it's tea (Piping Hot Tea or Tea)
                    if(contentName.contains("Tea") || contentName.contains("tea"))
                    {
                        teapots.add(item);
                    }
                }
            }
        }

        // Check equipment slots
        checkTeapotInSlot(teapots, NUtils.getEquipment().findItem(NEquipory.Slots.LFOOT.idx));
        checkTeapotInSlot(teapots, NUtils.getEquipment().findItem(NEquipory.Slots.RFOOT.idx));
        checkTeapotInSlot(teapots, NUtils.getEquipment().findItem(NEquipory.Slots.HAND_LEFT.idx));
        checkTeapotInSlot(teapots, NUtils.getEquipment().findItem(NEquipory.Slots.HAND_RIGHT.idx));

        if(teapots.isEmpty())
        {
            return Results.SUCCESS(); // No teapots with tea
        }

        // Navigate to first barrel
        Gob targetBarrel = barrels.get(0);
        new PathFinder(targetBarrel).run(gui);

        // Empty each teapot
        for(WItem teapot : teapots)
        {
            NGItem ngItem = ((NGItem)teapot.item);
            if(ngItem.content().isEmpty())
                continue;

            // Remember where teapot came from before taking it
            boolean fromBelt = (teapot.parent instanceof NInventory && wbelt != null && teapot.parent == wbelt.item.contents);
            boolean fromInventory = (teapot.parent == gui.getInventory());
            
            // Take teapot to hand
            NUtils.takeItemToHand(teapot);
            
            // Find a barrel that can accept tea
            Gob barrel = null;
            for(Gob b : barrels)
            {
                // Check if barrel has content
                if(NUtils.barrelHasContent(b))
                {
                    String barrelContent = NUtils.getContentsOfBarrel(b);
                    // Can empty into barrel with tea
                    if(barrelContent != null && (barrelContent.contains("Tea") || barrelContent.contains("tea")))
                    {
                        barrel = b;
                        break;
                    }
                }
                else
                {
                    // Empty barrel - can accept tea
                    barrel = b;
                    break;
                }
            }

            if(barrel == null)
            {
                // All barrels are full or have different content
                // Try to find any barrel and empty anyway
                barrel = barrels.get(0);
            }

            // Navigate to barrel if needed
            if(barrel != targetBarrel)
            {
                new PathFinder(barrel).run(gui);
                targetBarrel = barrel;
            }

            // Wait for item to be in hand before activating
            NUtils.addTask(new NTask() {
                @Override
                public boolean check() {
                    return NUtils.getGameUI().vhand != null;
                }
            });

            // Activate barrel to empty teapot
            NUtils.activateItem(barrel);
            
            // Wait for teapot to be empty
            NUtils.getUI().core.addTask(new NTask() {
                @Override
                public boolean check() {
                    if(NUtils.getGameUI().vhand == null)
                        return true;
                    NGItem handItem = (NGItem)NUtils.getGameUI().vhand.item;
                    return handItem.content().isEmpty();
                }
            });

            // Return teapot to its original location
            if(fromBelt)
            {
                NUtils.transferToBelt();
                NUtils.getUI().core.addTask(new HandIsFree(((NInventory) wbelt.item.contents)));
            }
            else if(fromInventory)
            {
                // Return to inventory - find free slot and drop
                Coord pos = gui.getInventory().getFreeCoord(NUtils.getGameUI().vhand);
                if(pos != null)
                {
                    gui.getInventory().dropOn(pos, ((NGItem)NUtils.getGameUI().vhand.item).name());
                    NUtils.getUI().core.addTask(new NTask() {
                        @Override
                        public boolean check() {
                            return NUtils.getGameUI().vhand == null;
                        }
                    });
                }
                else
                {
                    // No free space in inventory, try to drop on ground or belt
                    NUtils.drop(NUtils.getGameUI().vhand);
                    NUtils.getUI().core.addTask(new NTask() {
                        @Override
                        public boolean check() {
                            return NUtils.getGameUI().vhand == null;
                        }
                    });
                }
            }
            else
            {
                // Return to equipment slot
                NUtils.getEquipment().wdgmsg("drop", -1);
                NUtils.getUI().core.addTask(new NTask() {
                    @Override
                    public boolean check() {
                        return NUtils.getGameUI().vhand == null;
                    }
                });
            }
            
            // Wait for hand to be free before processing next teapot
            NUtils.getUI().core.addTask(new NTask() {
                @Override
                public boolean check() {
                    return NUtils.getGameUI().vhand == null;
                }
            });
        }

        return Results.SUCCESS();
    }

    void checkTeapotInSlot(ArrayList<WItem> teapots, WItem item)
    {
        if(item != null && item.item instanceof NGItem && NParser.checkName(((NGItem)item.item).name(), "Teapot"))
        {
            NGItem ngItem = ((NGItem) item.item);
            if(!ngItem.content().isEmpty())
            {
                String contentName = ngItem.content().get(0).name();
                if(contentName.contains("Tea") || contentName.contains("tea"))
                {
                    teapots.add(item);
                }
            }
        }
    }
}
