package nurgling.overlays;

import haven.*;
import haven.res.lib.tree.TreeScale;
import nurgling.tools.NAlias;
import nurgling.tools.NParser;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.NConfig;
import nurgling.TreeLocation;
import nurgling.widgets.TreeFinderNotificationWindow;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class NTreeScaleOl extends NObjectTexLabel {
    public static Text.Furnace fnd = new PUtils.BlurFurn(new Text.Foundry(Text.sans.deriveFont(Font.BOLD), 16).aa(true), UI.scale(1), UI.scale(1), Color.BLACK);
    private static TexI qIcon = new TexI(Resource.loadsimg("nurgling/hud/growth"));
    
    // Track trees that have already been checked to avoid duplicate processing
    // Use location-based ID (gob.id + coordinates) to handle tree recreation
    private static final Set<String> checkedTreeIds = ConcurrentHashMap.newKeySet();
    
    public NTreeScaleOl(Gob target) {
        super(target);
        gob = (Gob) target;
        pos = new Coord3f(0, 0, 3);
        TreeScale ts = gob.getattr(TreeScale.class);
        long scale = 0;
        if (NParser.checkName(gob.ngob.name, new NAlias("bushes"))) {
            scale = Math.round(100 * (ts.scale - 0.3) / 0.7);

        } else {
            scale = Math.round(100 * (ts.scale - 0.1) / 0.9);
        }
        this.img = qIcon;
        BufferedImage retlabel =fnd.render(String.format("%d%%",scale)).img;
        BufferedImage ret = TexI.mkbuf(new Coord(UI.scale(1)+img.sz().x+retlabel.getWidth(), Math.max(img.sz().y,retlabel.getHeight())));
        Graphics g = ret.getGraphics();
        g.drawImage(img.back, 0, ret.getHeight()/2-img.sz().y/2, null);
        g.drawImage(retlabel,UI.scale(1)+img.sz().x,ret.getHeight()/2-retlabel.getHeight()/2,null);
        g.dispose();
        this.label = new TexI(ret);
        
        // Check Tree Finder on overlay creation (delayed to ensure GUI is ready)
        checkTreeFinderDelayed(scale);
    }
    
    private void checkTreeFinderDelayed(long growthPercent) {
        // Use delayed task to ensure GUI is fully initialized
        UI ui = UI.getInstance();
        if (ui != null && ui.loader != null) {
            ui.loader.defer(() -> {
                checkTreeFinder(growthPercent);
            }, null);
        }
    }
    
    private void checkTreeFinder(long growthPercent) {
        try {
            // Check if Tree Finder is enabled
            Boolean enabled = (Boolean) NConfig.get(NConfig.Key.treeFinderEnabled);
            if (enabled == null || !enabled) {
                System.err.println("Tree Finder: Disabled");
                return;
            }
            
            NGameUI gui = NUtils.getGameUI();
            if (gui == null || gui.treeLocationService == null) {
                System.err.println("Tree Finder: GUI or service not available");
                return;
            }
            
            //System.err.println("Tree Finder: Checking tree with growth " + growthPercent + "%");
            
            // Get tree name and resource for location ID generation
            String treeResource = gob.ngob.name;
            String treeName = gui.treeLocationService.getTreeName(treeResource);
            
            // Generate location-based ID (same as TreeLocationService uses)
            // This ensures we track trees by location, not just by gob.id
            // Use the same method as TreeLocationService to get segmentId
            long segmentId = 0;
            Coord segmentCoord = gob.rc.floor();
            try {
                MCache mcache = gui.map.glob.map;
                Coord tc = gob.rc.floor(MCache.tilesz);
                Coord gridCoord = tc.div(MCache.cmaps);
                MCache.Grid grid = mcache.getgrid(gridCoord);
                
                MapFile mapFile = gui.mmap.file;
                MapFile.GridInfo info = null;
                boolean lockAcquired = false;
                try {
                    lockAcquired = mapFile.lock.readLock().tryLock(100, TimeUnit.MILLISECONDS);
                    if (lockAcquired) {
                        info = mapFile.gridinfo.get(grid.id);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    if (lockAcquired) {
                        mapFile.lock.readLock().unlock();
                    }
                }
                
                if (info != null) {
                    segmentId = info.seg;
                    segmentCoord = tc.add(info.sc.sub(grid.gc).mul(MCache.cmaps));
                }
            } catch (Exception e) {
                // If we can't get segment info, use 0 as fallback
                System.err.println("Error getting segment info: " + e.getMessage());
            }
            
            String locationId = TreeLocation.generateLocationId(segmentId, segmentCoord, treeName);
            
            // Get settings
            Boolean saveToMap = (Boolean) NConfig.get(NConfig.Key.treeFinderSaveToMap);
            Boolean showNotification = (Boolean) NConfig.get(NConfig.Key.treeFinderShowNotification);
            
            // Check if tree is already saved (for saving operation only)
            // Use try-catch to handle any lock issues gracefully
            boolean alreadySaved = false;
            try {
                alreadySaved = gui.treeLocationService.treeLocationExists(gob);
            } catch (Exception e) {
                // If check fails, assume not saved to be safe
                System.err.println("Error checking tree location: " + e.getMessage());
            }
            
            // Check if notification was already shown for this location
            String notificationKey = locationId + "_notification";
            boolean notificationShown = checkedTreeIds.contains(notificationKey);
            
            // Save to map if enabled and meets threshold
            if (saveToMap != null && saveToMap) {
                Object saveToMapMinGrowthObj = NConfig.get(NConfig.Key.treeFinderSaveToMapMinGrowth);
                int saveToMapMinGrowth = (saveToMapMinGrowthObj instanceof Integer) ? (Integer) saveToMapMinGrowthObj : 100;
                
                if (growthPercent >= saveToMapMinGrowth && !alreadySaved) {
                    try {
                        gui.treeLocationService.saveTreeLocation(gob, (int)growthPercent);
                        alreadySaved = true; // Update flag after saving
                    } catch (Exception e) {
                        // If save fails, don't mark as checked to allow retry
                        System.err.println("Error saving tree location: " + e.getMessage());
                    }
                }
            }
            
            // Show notification if enabled and meets threshold
            // Show for all trees above threshold, but only once per location
            if (showNotification != null && showNotification && !notificationShown) {
                Object showNotificationMinGrowthObj = NConfig.get(NConfig.Key.treeFinderShowNotificationMinGrowth);
                int showNotificationMinGrowth = (showNotificationMinGrowthObj instanceof Integer) ? (Integer) showNotificationMinGrowthObj : 100;
                
                //System.err.println("Tree Finder: showNotification=" + showNotification + ", notificationShown=" + notificationShown + ", growthPercent=" + growthPercent + ", minGrowth=" + showNotificationMinGrowth);
                
                if (growthPercent >= showNotificationMinGrowth) {
                    try {
                        //System.err.println("Tree Finder: Showing notification for " + treeName + " (" + growthPercent + "%)");
                        // Show popup notification window instead of chat message
                        TreeFinderNotificationWindow notifWindow = new TreeFinderNotificationWindow(treeName, (int)growthPercent, showNotificationMinGrowth);
                        gui.add(notifWindow, new Coord(UI.scale(200), UI.scale(200)));
                        notifWindow.show();
                        notifWindow.raise();
                        // Mark notification as shown for this location
                        checkedTreeIds.add(notificationKey);
                        //System.err.println("Tree Finder: Notification shown and marked");
                    } catch (Exception e) {
                        System.err.println("Error showing notification: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    //System.err.println("Tree Finder: Growth " + growthPercent + "% is below threshold " + showNotificationMinGrowth + "%");
                }
            } else {
                if (showNotification == null || !showNotification) {
                    //System.err.println("Tree Finder: Notifications disabled");
                } else if (notificationShown) {
                    //System.err.println("Tree Finder: Notification already shown for this location");
                }
            }
            
        } catch (Exception e) {
            // Silently ignore errors to avoid disrupting game flow
            System.err.println("Error in Tree Finder: " + e.getMessage());
            e.printStackTrace();
        }
    }


    Gob gob;

    @Override
    public boolean tick(double dt) {
        return gob.getattr(TreeScale.class) == null;
    }
}
