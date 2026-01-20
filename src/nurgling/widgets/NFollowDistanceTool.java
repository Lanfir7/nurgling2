package nurgling.widgets;

import haven.*;
import nurgling.NGameUI;
import nurgling.NMapView;
import nurgling.NUtils;
import nurgling.actions.bots.SelectGob;
import nurgling.tools.Finder;

import java.awt.Color;
import java.text.DecimalFormat;
import java.util.Objects;

import static haven.OCache.posres;

public class NFollowDistanceTool extends haven.Window implements Runnable {
    private final NGameUI gui;
    private volatile boolean stop;
    private volatile boolean isFollowing;
    private final haven.Label currentDistanceLabel;
    private final haven.Label targetLabel;
    private String distanceValue = "";
    private Thread updateThread;
    private Thread followThread;
    private long targetGobId = -1;
    private double targetDistance = 0.0;

    public NFollowDistanceTool(NGameUI gui) {
        super(UI.scale(240, 100), "Follow Distance Tool", true);
        this.gui = gui;
        this.stop = false;
        this.isFollowing = false;

        Widget prev;

        prev = add(new haven.Label("Target:"), 0, UI.scale(6));

        prev = add(new Button(UI.scale(70), "Select") {
            @Override
            public void click() {
                selectTarget();
                defocus();
            }
        }, prev.pos("ur").adds(5, 0));

        targetLabel = new haven.Label("No target selected");
        add(targetLabel, 0, UI.scale(25));

        prev = add(new haven.Label("Distance:"), 0, UI.scale(45));

        prev = add(new TextEntry(UI.scale(80), distanceValue) {
            @Override
            protected void changed() {
                distanceValue = this.buf.line();
            }
        }, prev.pos("ur").adds(5, 0));

        prev = add(new Button(UI.scale(50), "Start") {
            @Override
            public void click() {
                startFollowing();
                defocus();
            }
        }, prev.pos("ur").adds(5, -2));

        prev = add(new Button(UI.scale(50), "Stop") {
            @Override
            public void click() {
                stopFollowing();
                defocus();
            }
        }, prev.pos("ur").adds(5, 0));

        currentDistanceLabel = new haven.Label("Current dist: No target");
        add(currentDistanceLabel, UI.scale(0, 70));
        pack();
    }

    public void start() {
        stop = false;
        updateThread = new Thread(this, "FollowDistanceTool");
        updateThread.start();
    }

    public void setTarget(Gob gob) {
        if (gob != null) {
            targetGobId = gob.id;
            String gobName = gob.ngob != null && gob.ngob.name != null ? gob.ngob.name : "Unknown";
            targetLabel.settext("Target: " + gobName);
        } else {
            targetGobId = -1;
            targetLabel.settext("No target selected");
        }
    }

    private void selectTarget() {
        if (!(gui.map instanceof NMapView)) {
            gui.msg("Cannot select target", Color.RED);
            return;
        }

        NMapView mapView = (NMapView) gui.map;
        
        // Check if already in selection mode
        if (mapView.isGobSelectionMode.get()) {
            gui.msg("Selection mode already active. Click on an object to select it.", Color.YELLOW);
            return;
        }

        // Start selection mode using SelectGob like in AutoFlowerActionBot
        // Run in separate thread to avoid blocking UI
        new Thread(() -> {
            try {
                SelectGob selgob = new SelectGob(Resource.loadsimg("baubles/selectobject"));
                gui.msg("Please select target object", Color.WHITE);
                selgob.run(gui);
                Gob targetGob = selgob.getResult();
                
                if (targetGob != null) {
                    setTarget(targetGob);
                    gui.msg("Target selected: " + (targetGob.ngob != null && targetGob.ngob.name != null ? targetGob.ngob.name : "Unknown"), Color.GREEN);
                } else {
                    gui.msg("No target selected", Color.YELLOW);
                }
            } catch (InterruptedException e) {
                gui.msg("Target selection interrupted", Color.YELLOW);
            } catch (Exception e) {
                gui.msg("Error selecting target: " + e.getMessage(), Color.RED);
            }
        }, "SelectTargetThread").start();
    }

    private void startFollowing() {
        if (targetGobId == -1) {
            // Try to get selected gob from map
            if (gui.map instanceof NMapView) {
                Gob selectedGob = ((NMapView) gui.map).selectedGob;
                if (selectedGob != null) {
                    setTarget(selectedGob);
                }
            }
            
            if (targetGobId == -1) {
                gui.msg("Please select a target first (click on object)", Color.YELLOW);
                return;
            }
        }

        try {
            targetDistance = Double.parseDouble(distanceValue);
            if (targetDistance <= 0) {
                gui.error("Distance must be greater than 0");
                return;
            }
        } catch (NumberFormatException e) {
            gui.error("Invalid distance format. Use numbers like 25.5");
            return;
        }

        if (!isFollowing) {
            isFollowing = true;
            followThread = new Thread(this::followLoop, "FollowDistanceLoop");
            followThread.start();
            gui.msg("Started following at distance: " + targetDistance, Color.GREEN);
        }
    }

    private void stopFollowing() {
        if (isFollowing) {
            isFollowing = false;
            if (followThread != null) {
                followThread.interrupt();
            }
            gui.msg("Stopped following", Color.WHITE);
        }
    }

    private void followLoop() {
        while (isFollowing && !stop) {
            try {
                Gob target = Finder.findGob(targetGobId);
                Gob player = NUtils.player();
                
                if (target == null || player == null) {
                    gui.msg("Target lost", Color.YELLOW);
                    stopFollowing();
                    break;
                }

                double currentDist = target.rc.dist(player.rc);
                double tolerance = 1.0; // Allow 1 unit tolerance

                // If we're too far or too close, adjust position
                if (Math.abs(currentDist - targetDistance) > tolerance) {
                    // Calculate angle from target to player, then place player at targetDistance from target
                    double angle = target.rc.angle(player.rc);
                    Coord2d targetPos = getNewCoord(target, targetDistance, angle);
                    gui.map.wdgmsg("click", Coord.z, targetPos.floor(posres), 1, 0);
                }

                Thread.sleep(500); // Check every 500ms
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                // Ignore errors and continue
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
    }

    @Override
    public void run() {
        DecimalFormat df = new DecimalFormat("#.##");
        while (!stop) {
            try {
                if (targetGobId != -1) {
                    double dist = getDistance(targetGobId);
                    if (dist < 0) {
                        currentDistanceLabel.settext("Current dist: Target lost");
                    } else {
                        currentDistanceLabel.settext("Current dist: " + df.format(dist) + " units");
                    }
                } else {
                    currentDistanceLabel.settext("Current dist: No target");
                }
                Thread.sleep(500);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private Coord2d getNewCoord(Gob target, double distance, double angle) {
        return new Coord2d(
                target.rc.x + distance * Math.cos(angle),
                target.rc.y + distance * Math.sin(angle)
        );
    }

    private double getDistance(long gobId) {
        Gob target = Finder.findGob(gobId);
        Gob player = NUtils.player();
        if (target != null && player != null) {
            return target.rc.dist(player.rc);
        }
        return -1;
    }

    private void defocus() {
        if (gui.portrait != null) {
            setfocus(gui.portrait);
        }
    }

    public void stopTool() {
        stopFollowing();
        stop = true;
        if (updateThread != null) {
            updateThread.interrupt();
        }
        if (followThread != null) {
            followThread.interrupt();
        }
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if ((sender == this) && (Objects.equals(msg, "close"))) {
            stopTool();
            reqdestroy();
        } else {
            super.wdgmsg(sender, msg, args);
        }
    }

    @Override
    public void destroy() {
        stopTool();
        super.destroy();
    }
}
