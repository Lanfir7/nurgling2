package nurgling.contextmenu;

import haven.Coord;
import haven.Gob;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.hotkeys.Hotkeys;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class GobContextRegistry {
    private static final List<GobContextAction> actions = new ArrayList<>();

    public static void register(GobContextAction action) {
        actions.add(action);
    }

    public static List<GobContextAction> getActionsFor(Gob gob) {
        if (gob == null || gob.ngob == null || gob.ngob.name == null)
            return List.of();
        List<GobContextAction> result = new ArrayList<>();
        for (GobContextAction action : actions) {
            if (action.appliesTo(gob))
                result.add(action);
        }
        return result;
    }

    public static void openMenu(Gob gob) {
        List<GobContextAction> available = getActionsFor(gob);
        NGameUI gui = NUtils.getGameUI();
        if (gui != null && !available.isEmpty())
            gui.add(new NGobContextMenu(gob, available), new Coord(-1, -1));
    }

    public static boolean routeMinimapIconClick(Gob gob, int button, int mods, boolean press,
                                                 Runnable serverClick) {
        return routeMinimapIconClick(gob, button, mods, press,
                GobContextRegistry::openMenu, serverClick);
    }

    static boolean routeMinimapIconClick(Gob gob, int button, int mods, boolean press,
                                          Consumer<Gob> menuOpener, Runnable serverClick) {
        if (!press)
            return false;
        if (Hotkeys.action(Hotkeys.WORLD_CONTEXT_MENU).current().matchesMouse(button, mods))
            menuOpener.accept(gob);
        else
            serverClick.run();
        return true;
    }

    static {
        register(new FillEmptyContainersAction());
        register(new ChopAndRemoveStumpAction());
        register(new RemoveStumpAction());
        register(new FillTroughWithSwillAction());
        register(new FillBarrelsFromVehicleAction());
        register(new EmptyBarrelsIntoCisternAction());
        register(new LoadVehicleAction());
        register(new UnloadVehicleAction());
        register(new UnloadAnimalsAction());
        register(new UnloadCarryoutAction());
        register(new CarryManyAction());
        register(new SaveTreeLocationAction());
        register(new SaveBushLocationAction());
        register(new CutDownAreaAction());
        register(new ChipStoneAreaAction());
        register(new ShearWoolAreaAction());
        register(new LightAction());
        register(new RecordMilestoneAction());
        register(new FuelKilnsAction());
        register(new FuelOvensAction());
        register(new FuelSmeltersAction());
        register(new KilnFuelAction());
        register(new HTableTimesAction());
        register(new TakeDecalAction());
        register(new BoughBeeAction());
        register(new BoughPyreTimerAction());
        register(new FeedCloverAction());
        register(new ButcherAction());
        register(new ChopBlocksAction());
        register(new SawBoardsAction());
        register(new DryHidesContextAction());
        register(new DryFishContextAction());
        register(new SpitRoastContextAction());
        // Registered last so the generic entry sits below the object-specific ones.
        register(new ConfigureGobAction());
    }
}
