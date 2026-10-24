package nurgling.actions.bots.blueprint;

import nurgling.planning.PlanningGhost;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuildPreflightScenarioTest {
    private static BuildPreflight.TypeRow row(String res, boolean available) {
        BuildPreflight.TypeRow row = new BuildPreflight.TypeRow(res);
        row.ghosts.add(new PlanningGhost(res, res, null, -4, 22, -11, 0));
        row.enabled = available;
        if (!available) row.problem = "build button unavailable";
        return row;
    }

    @Test void scenarioNeverDispatchesAllMissingOrMixedMissingButtons() {
        BuildPreflight preflight = new BuildPreflight();
        preflight.types.put("smelter", row("smelter", false));
        assertNull(preflight.scenarioGhosts(), "an all-unavailable scenario must fail before opening UI or starting a batch");

        preflight.types.put("kiln", row("kiln", true));
        assertNull(preflight.scenarioGhosts(), "a ready building must not hide another unavailable type");

        preflight.types.get("smelter").enabled = true;
        preflight.types.get("smelter").problem = null;
        List<PlanningGhost> ready = preflight.scenarioGhosts();
        assertNotNull(ready);
        assertEquals(2, ready.size());
    }

    @Test void emptyScenarioDoesNotReportACompletedBuild() {
        assertNull(new BuildPreflight().scenarioGhosts());
    }
}
