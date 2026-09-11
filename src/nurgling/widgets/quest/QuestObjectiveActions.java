package nurgling.widgets.quest;

import haven.GameUI;
import haven.Widget;
import nurgling.widgets.MapToolsWindow;
import nurgling.widgets.craftatlas.CraftAtlasWindow;

/** Availability checks and UI dispatch for quest-objective action buttons. */
public final class QuestObjectiveActions {
    private static final QuestObjectiveActionResolver RESOLVER = new QuestObjectiveActionResolver();

    private QuestObjectiveActions() {
    }

    public static QuestObjectiveAction available(Widget origin, QCond cond) {
        QuestObjectiveAction action = RESOLVER.resolve(cond);
        if(action == null)
            return null;
        if(action.kind == QuestObjectiveAction.Kind.CRAFT && !hasAtlasRecipe(origin, action.targets.get(0)))
            return null;
        return action;
    }

    public static boolean execute(Widget origin, QuestObjectiveAction action) {
        if(origin == null || action == null)
            return false;
        switch(action.kind) {
            case FORAGE_TERRAIN:
            case TREE_TERRAIN:
                MapToolsWindow.openTerrainSearch(action.targets);
                return true;
            case ROCK_TERRAIN:
                MapToolsWindow.openTerrainResources(action.targets);
                return true;
            case CRAFT:
                CraftAtlasWindow atlas = craftAtlas(origin);
                return atlas != null && atlas.openRecipe(null, action.targets.get(0));
            default:
                return false;
        }
    }

    public static String tooltip(QuestObjectiveAction action) {
        return action != null && action.kind == QuestObjectiveAction.Kind.CRAFT
                ? "Open crafting recipe" : "Show gathering terrain";
    }

    private static boolean hasAtlasRecipe(Widget origin, String target) {
        CraftAtlasWindow atlas = craftAtlas(origin);
        return atlas != null && atlas.controller().hasUniqueExactName(target);
    }

    private static CraftAtlasWindow craftAtlas(Widget origin) {
        GameUI gui = gui(origin);
        return gui == null ? null : gui.craftAtlas;
    }

    private static GameUI gui(Widget origin) {
        return origin == null ? null : origin.getparent(GameUI.class);
    }
}
