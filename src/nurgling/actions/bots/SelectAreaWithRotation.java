package nurgling.actions.bots;

import haven.*;
import nurgling.*;
import nurgling.actions.Action;
import nurgling.actions.Results;
import nurgling.areas.NArea;
import nurgling.overlays.NCustomBauble;
import nurgling.widgets.TrellisDirectionDialog;

import java.awt.image.BufferedImage;

public class SelectAreaWithRotation implements Action {

    public SelectAreaWithRotation(BufferedImage image, NHitBox hitBox) {
        this.image = image;
        this.trellisHitBox = hitBox;
    }

    BufferedImage image = null;
    BufferedImage spr = null;
    NHitBox trellisHitBox = null;
    public NArea.Space result;
    public int orientation = 0; // 0=NS-East, 1=NS-West, 2=EW-North, 3=EW-South, 4=NS-Center, 5=EW-Center
    private TrellisDirectionDialog dirDialog = null;

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        if (gui == null || gui.map == null || gui.ui == null) {
            return Results.FAIL();
        }
        NMapView map = (NMapView) gui.map;

        if (!map.isAreaSelectionMode.get()) {
            Gob player = NUtils.player();
            map.isAreaSelectionMode.set(true);

            // Add direction dialog to the UI
            dirDialog = new TrellisDirectionDialog();
            NUtils.addCentered(gui, dirDialog);

            if(image!=null && player!=null) {
                player.addcustomol(new NCustomBauble(player,image, spr, map.isAreaSelectionMode));
            }

            // Use appropriate task based on whether we have a hitbox (for ghost previews)
            if (trellisHitBox != null) {
                // Create orientation reference array that can be updated by the dialog
                int[] orientationRef = new int[] { orientation };
                boolean[] confirmRef = new boolean[] { false };
                dirDialog.setReferences(orientationRef, confirmRef);
                dirDialog.show();
                dirDialog.raise();

                nurgling.tasks.SelectAreaWithGhosts sa;
                NUtils.getUI().core.addTask(sa = new nurgling.tasks.SelectAreaWithGhosts(trellisHitBox, orientationRef, confirmRef));
                if (sa.getResult() != null) {
                    result = sa.getResult();
                    orientation = orientationRef[0];
                }
            } else {
                nurgling.tasks.SelectArea sa;
                gui.ui.core.addTask(sa = new nurgling.tasks.SelectArea(gui));
                if (sa.getResult() != null) {
                    result = sa.getResult();
                    orientation = 0;
                }
            }

            // Clean up dialog
            if(dirDialog != null) {
                dirDialog.reqdestroy();
                dirDialog = null;
            }
        }
        else {
            return Results.FAIL();
        }
        return Results.SUCCESS();
    }

    public Pair<Coord2d,Coord2d> getRCArea() {
        NGameUI gui = NUtils.getGameUI();
        if (result == null || gui == null || gui.map == null) {
            return null;
        }
        Coord begin = null;
        Coord end = null;
        for (Long id : result.space.keySet()) {
            MCache.Grid grid = gui.map.glob.map.findGrid(id);
            if (grid == null) {
                continue;
            }
            haven.Area area = result.space.get(id).area;
            Coord b = area.ul.add(grid.ul);
            Coord e = area.br.add(grid.ul);
            begin = (begin != null) ? new Coord(Math.min(begin.x, b.x), Math.min(begin.y, b.y)) : b;
            end = (end != null) ? new Coord(Math.max(end.x, e.x), Math.max(end.y, e.y)) : e;
        }
        if (begin != null)
            return new Pair<Coord2d, Coord2d>(begin.mul(MCache.tilesz), end.sub(1, 1).mul(MCache.tilesz).add(MCache.tilesz));
        return null;
    }

    public boolean getRotation() {
        // For backward compatibility: orientation 2 and 3 are East-West
        return orientation >= 2;
    }
}
