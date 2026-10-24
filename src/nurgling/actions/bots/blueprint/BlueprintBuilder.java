package nurgling.actions.bots.blueprint;

import haven.Coord;
import haven.Coord2d;
import haven.UI;
import nurgling.NGameUI;
import nurgling.NMapView;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.Results;
import nurgling.i18n.L10n;
import nurgling.navigation.ChunkNavManager;
import nurgling.planning.PlanningGhost;
import nurgling.planning.PlanningLayerManager;
import nurgling.tasks.WaitCheckable;
import nurgling.widgets.bots.BlueprintBuilderReport;
import nurgling.widgets.bots.BlueprintBuilderWindow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds Base planner ghosts: a whole folder or layer, a dragged region, or any parts picked in
 * its window. Resolves each ghost to a build button, walks to it, fetches confirmed materials one
 * unit at a time, places and finishes the sign, and removes the
 * ghost from the plan once the building stands. Ghosts it can't build stay in the plan and are
 * listed with the reason in the report, so running it again resumes where it stopped.
 */
public class BlueprintBuilder implements Action
{
    /** Scenario setting: id of the planner folder or layer to build. */
    public static final String SETTING_NODE = "nodeId";

    private final List<String> presetGhostIds;
    private final String scenarioNodeId;
    private final boolean scenario;

    /** Bot menu: pick the plan parts in the window. */
    public BlueprintBuilder()
    {
        this(Collections.emptyMap());
    }

    /** Scenario step (with {@link #SETTING_NODE}), or the bot menu (empty settings). */
    public BlueprintBuilder(Map<String, Object> settings)
    {
        this(settings, false);
    }

    public BlueprintBuilder(Map<String, Object> settings, boolean scenario)
    {
        this.scenario = scenario;
        Object node = settings == null ? null : settings.get(SETTING_NODE);
        this.scenarioNodeId = (node instanceof String && !((String) node).isEmpty()) ? (String) node : null;
        this.presetGhostIds = null;
    }

    /** Base planner: build exactly these ghosts, after the pre-flight window. */
    public BlueprintBuilder(List<PlanningGhost> ghosts)
    {
        this.scenario = false;
        this.scenarioNodeId = null;
        this.presetGhostIds = new ArrayList<>();
        for (PlanningGhost g : ghosts) presetGhostIds.add(g.id);
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException
    {
        if (scenario && scenarioNodeId == null)
            return Results.ERROR(L10n.get("bbuilder.planner.select"));
        PlanningLayerManager plan = gui.ui.core.planningLayer;
        if (scenarioNodeId != null && plan.getNode(scenarioNodeId) == null)
            return Results.ERROR(L10n.get("bbuilder.planner.select"));
        // Load the wiki catalog off the UI thread before the window asks for estimates.
        BuildRecipes.wikiEstimate("");

        List<PlanningGhost> ghosts;
        boolean removeBuilt = true;
        boolean stopOnProblem = false;
        if (scenarioNodeId != null)
        {
            ghosts = new BuildPreflight(gui, plan, plan.ghostsOf(scenarioNodeId)).scenarioGhosts();
            if (ghosts == null)
                return Results.ERROR(L10n.get("bbuilder.safe.unavailable"));
        } else
        {
            List<PlanningGhost> initial = null;
            if (presetGhostIds != null)
            {
                initial = new ArrayList<>();
                for (String id : presetGhostIds)
                {
                    PlanningGhost g = plan.snapshotOf(id);
                    if (g != null) initial.add(g);
                }
            }
            BlueprintBuilderWindow wnd = new BlueprintBuilderWindow(initial);
            try
            {
                NUtils.getUI().core.addTask(new WaitCheckable(gui.add(wnd, UI.scale(new Coord(200, 120)))));
            } finally
            {
                wnd.destroy();
            }
            if (!wnd.started)
                return Results.SUCCESS();
            ghosts = wnd.chosenGhosts();
            removeBuilt = wnd.removeBuilt;
            stopOnProblem = wnd.stopOnProblem;
        }
        if (ghosts.isEmpty())
        {
            gui.msg(L10n.get("bbuilder.nothing"));
            return Results.SUCCESS();
        }

        List<GhostJob> jobs = new ArrayList<>();
        for (PlanningGhost g : ghosts)
            jobs.add(new GhostJob(g, plan.layerNameOf(g.id)));

        MaterialFetcher fetcher = new MaterialFetcher(new nurgling.areas.NContext(gui));
        GhostConstruction construction = new GhostConstruction(gui, plan, fetcher);
        String fatal;
        try
        {
            List<GhostJob> ordered = new ArrayList<>();
            for (List<GhostJob> group : order(gui, jobs)) ordered.addAll(group);
            BlueprintBatch.Outcome outcome = BlueprintBatch.run(ordered, removeBuilt, stopOnProblem,
                    new BlueprintBatch.Port() {
                        @Override public boolean unchanged(PlanningGhost ghost) { return plan.stillMatches(ghost); }
                        @Override public void build(GhostJob job) throws InterruptedException,
                                GhostConstruction.FatalException { construction.build(job); }
                        @Override public boolean removeIfUnchanged(PlanningGhost ghost) {
                            boolean removed = plan.removeIfUnchanged(ghost);
                            if (removed && gui.basePlanner != null) gui.basePlanner.refresh();
                            return removed;
                        }
                        @Override public void progress(GhostJob job, int index, int total) {
                            gui.msg(L10n.get("bbuilder.progress", index, total, job.typeName()));
                        }
                        @Override public void finished(GhostJob job, int index, int total) {
                            if (job.status == GhostJob.Status.BUILT)
                                gui.msg(L10n.get("bbuilder.done.built", job.typeName(), index, total));
                            else if (job.status == GhostJob.Status.ALREADY_BUILT)
                                gui.msg(L10n.get("bbuilder.done.standing", job.typeName(), index, total));
                            else if (job.status == GhostJob.Status.SKIPPED)
                                gui.msg(L10n.get("bbuilder.done.skipped", job.typeName(), index, total, job.reason));
                        }
                    });
            fatal = outcome.failure;
        } finally {
            construction.closeOwnedUi();
        }

        gui.add(new BlueprintBuilderReport(jobs, fatal), UI.scale(new Coord(200, 120)));
        return (fatal == null) ? Results.SUCCESS() : Results.ERROR(fatal);
    }

    /**
     * Groups to build in order: one per (grid, building type), nearest grid first by chunk
     * hops, and inside a group nearest-neighbour by position.
     */
    private static List<List<GhostJob>> order(NGameUI gui, List<GhostJob> jobs)
    {
        ChunkNavManager nav = (gui.map instanceof NMapView) ? ((NMapView) gui.map).getChunkNavManager() : null;
        Map<Long, Integer> hops = (nav != null) ? nav.chunkHopsFromPlayer() : null;

        Map<Long, List<GhostJob>> byGrid = new LinkedHashMap<>();
        for (GhostJob j : jobs)
            byGrid.computeIfAbsent(j.ghost.gridId, k -> new ArrayList<>()).add(j);
        List<Long> grids = new ArrayList<>(byGrid.keySet());
        grids.sort(Comparator.comparingInt(id -> (hops == null) ? 0 : hops.getOrDefault(id, Integer.MAX_VALUE)));

        List<List<GhostJob>> out = new ArrayList<>();
        for (long grid : grids)
        {
            Map<String, List<GhostJob>> byType = new LinkedHashMap<>();
            for (GhostJob j : byGrid.get(grid))
                byType.computeIfAbsent(j.ghost.resName, k -> new ArrayList<>()).add(j);
            for (List<GhostJob> group : byType.values())
                out.add(nearestFirst(group));
        }
        return out;
    }

    private static List<GhostJob> nearestFirst(List<GhostJob> group)
    {
        List<GhostJob> left = new ArrayList<>(group);
        List<GhostJob> out = new ArrayList<>();
        Coord2d at = left.get(0).ghost.offset();
        while (!left.isEmpty())
        {
            GhostJob best = null;
            for (GhostJob j : left)
                if (best == null || j.ghost.offset().dist(at) < best.ghost.offset().dist(at)) best = j;
            left.remove(best);
            out.add(best);
            at = best.ghost.offset();
        }
        return out;
    }

    /** Ghost ids in a stable order, without duplicates (folder and layer both ticked). */
    public static List<PlanningGhost> dedupe(List<PlanningGhost> ghosts)
    {
        Map<String, PlanningGhost> byId = new LinkedHashMap<>();
        for (PlanningGhost g : ghosts) byId.putIfAbsent(g.id, g);
        return new ArrayList<>(byId.values());
    }
}
