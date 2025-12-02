package nurgling.actions.bots;

import haven.Coord;
import haven.Coord2d;
import haven.Gob;
import haven.Resource;
import haven.UI;
import nurgling.NGameUI;
import nurgling.NMapView;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.Build;
import nurgling.actions.Results;
import nurgling.conf.NPrepBoardsProp;
import nurgling.overlays.BuildGhostPreview;
import nurgling.overlays.NCustomBauble;
import nurgling.tasks.WaitCheckable;
import nurgling.tools.NAlias;

import java.util.ArrayList;

public class BuildFromLogs implements Action {
    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        try {
            // Получаем конфигурацию для пиления досок
            nurgling.widgets.bots.PrepareBoards w = null;
            NPrepBoardsProp prop = null;
            try {
                NUtils.getUI().core.addTask(new WaitCheckable(NUtils.getGameUI().add((w = new nurgling.widgets.bots.PrepareBoards()), UI.scale(200, 200))));
                prop = w.prop;
            } catch (InterruptedException e) {
                throw e;
            } finally {
                if (w != null)
                    w.destroy();
            }
            if (prop == null) {
                return Results.ERROR("No config for sawing boards");
            }

            Build.Command command = new Build.Command();
            command.name = "Crate";

            NUtils.getGameUI().msg("Please, select build area");
            SelectAreaWithLiveGhosts buildarea = new SelectAreaWithLiveGhosts(Resource.loadsimg("baubles/buildArea"), "Crate");
            buildarea.run(NUtils.getGameUI());

            NUtils.getGameUI().msg("Please, select area with logs");
            SelectArea logsArea = new SelectArea(Resource.loadsimg("baubles/prepLogs"));
            logsArea.run(NUtils.getGameUI());

            // Создаем Ingredient с specialWay для пиления досок из бревен
            // Build.java будет вызывать sawAction когда нужно пополнить инвентарь досками
            Build.Ingredient boardsIngredient = new Build.Ingredient(new Coord(4, 1), null, new NAlias("Board"), 4);
            SawBoardsFromLogsAction sawAction = new SawBoardsFromLogsAction(logsArea.getRCArea(), boardsIngredient, prop);
            boardsIngredient.specialWay = sawAction;
            command.ingredients.add(boardsIngredient);

            // Get ghost positions from BuildGhostPreview if available
            ArrayList<Coord2d> ghostPositions = null;
            BuildGhostPreview ghostPreview = null;
            Gob player = NUtils.player();
            if (player != null) {
                ghostPreview = player.getattr(BuildGhostPreview.class);
                if (ghostPreview != null) {
                    ghostPositions = new ArrayList<>(ghostPreview.getGhostPositions());
                }
            }

            new Build(command, buildarea.getRCArea(), buildarea.getRotationCount(), ghostPositions, ghostPreview).run(gui);
            return Results.SUCCESS();
        } finally {
            // Always clean up ghost preview when bot finishes or is interrupted
            Gob player = NUtils.player();
            if (player != null) {
                BuildGhostPreview ghostPreview = player.getattr(BuildGhostPreview.class);
                if (ghostPreview != null) {
                    ghostPreview.dispose();
                    player.delattr(BuildGhostPreview.class);
                }
                // Remove custom bauble overlay
                Gob.Overlay baubleOverlay = player.findol(NCustomBauble.class);
                if (baubleOverlay != null) {
                    baubleOverlay.remove();
                }

                // Clean up area selection mode
                if (NUtils.getGameUI() != null && NUtils.getGameUI().map != null) {
                    ((NMapView) NUtils.getGameUI().map).isAreaSelectionMode.set(false);
                }
            }
        }
    }
}

