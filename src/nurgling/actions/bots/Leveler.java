package nurgling.actions.bots;

import haven.Button;
import haven.ChatUI;
import haven.Coord;
import haven.Coord2d;
import haven.Gob;
import haven.Label;
import haven.MCache;
import haven.Pair;
import haven.WItem;
import haven.Widget;
import haven.Window;
import haven.res.ui.stackinv.ItemStack;
import haven.res.ui.surv.LandSurvey;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.PathFinder;
import nurgling.actions.RestoreResources;
import nurgling.actions.Results;
import nurgling.actions.TakeItems2;
import nurgling.actions.TransferToPiles;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.areas.NGlobalCoord;
import nurgling.tasks.NTask;
import nurgling.tasks.WaitFreeHand;
import nurgling.tasks.WaitItems;
import nurgling.tasks.WaitWindow;
import nurgling.tasks.WindowIsClosed;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.tools.StackSupporter;
import nurgling.widgets.Specialisation;
import nurgling.widgets.bots.LevelerWnd;

import java.util.ArrayList;
import java.util.HashSet;

public class Leveler implements Action
{
    private static final Coord SOIL_SIZE = new Coord(1, 1);
    private static final int MIN_FREE_SLOTS = 1;
    private static final String SOIL_ITEM = "Soil";
    private static final String WORM_ITEM = "Earthworm";
    private static final String TUBER_ITEM = "Odd Tuber";
    private static final NAlias SURVOBJ = new NAlias("survobj");
    /* Only what a survey actually counts as fill - earthworms are spoil, not soil. */
    private static final NAlias SOIL_ONLY = new NAlias("Soil");
    private static final NAlias SOIL = new NAlias("Soil");
    private static final NAlias EARTHWORM = new NAlias("Earthworm");
    private static final NAlias ODD_TUBER = new NAlias("Odd Tuber");
    private static final String CANNOT_LEVEL_MSG = "cannot be further leveled";
    private static final String NEED_SOIL_MSG = "need soil";

    private final HashSet<Coord> done = new HashSet<>();
    private final HashSet<Coord> skipped = new HashSet<>();
    private Coord resumeTile = null;
    private NGlobalCoord resumeAt = null;
    private LevelerStats stats = null;
    private LevelerWnd infoWnd = null;
    private NContext context;

    @Override
    public Results run(NGameUI gui) throws InterruptedException
    {
        done.clear();
        skipped.clear();
        resumeTile = null;
        resumeAt = null;
        stats = new LevelerStats();
        context = new NContext(gui);
        infoWnd = NUtils.addCentered(gui, new LevelerWnd());
        try {
            while (true) {
                Results rr = new RestoreResources().run(gui);
                if (!rr.IsSuccess()) {
                    return Results.ERROR("Leveler: failed to restore resources");
                }

                if (resumePending() && findSurveyByTile(resumeTile) == null && resumeAt != null) {
                    if (!goToResumeFlag(gui)) {
                        return Results.ERROR("Leveler: cannot return to survey flag");
                    }
                }

                Gob target = pickSurvey();
                if (target == null) {
                    gui.msg("Leveler: finished. Completed=" + done.size() + " skipped=" + skipped.size());
                    return Results.SUCCESS();
                }

                Results sr = handleSurvey(gui, target);
                if (!sr.IsSuccess()) {
                    return sr;
                }
            }
        } finally {
            if (infoWnd != null) {
                try { infoWnd.destroy(); } catch (Exception ignored) {}
                infoWnd = null;
            }
        }
    }

    static Coord chooseSurveyTile(Coord resume, Coord nearest, boolean resumeStillPending) {
        if (resume != null && resumeStillPending) return resume;
        return nearest;
    }

    static boolean shouldKeepResume(boolean tilePending, boolean gobLoaded, boolean hasBookmark) {
        return tilePending && (gobLoaded || hasBookmark);
    }

    private boolean resumePending() {
        return resumeTile != null && !done.contains(resumeTile) && !skipped.contains(resumeTile);
    }

    private Gob pickSurvey()
    {
        if (resumePending()) {
            Gob g = findSurveyByTile(resumeTile);
            if (g != null)
                return g;
        }
        resumeTile = null;
        resumeAt = null;
        return pickNearestPendingSurvey();
    }

    private Gob pickNearestPendingSurvey()
    {
        Gob player = NUtils.player();
        if (player == null) return null;
        ArrayList<Gob> surveys = Finder.findGobs(SURVOBJ);
        Gob best = null;
        double bestDist = Double.MAX_VALUE;
        for (Gob s : surveys) {
            Coord tile = tileOf(s);
            if (done.contains(tile) || skipped.contains(tile)) continue;
            double d = s.rc.dist(player.rc);
            if (d < bestDist) { bestDist = d; best = s; }
        }
        return best;
    }

    private void markDone(Coord tile)
    {
        done.add(tile);
        if (tile.equals(resumeTile)) {
            resumeTile = null;
            resumeAt = null;
        }
    }

    private void markSkipped(Coord tile)
    {
        skipped.add(tile);
        if (tile.equals(resumeTile)) {
            resumeTile = null;
            resumeAt = null;
        }
    }

    private Results handleSurvey(NGameUI gui, Gob surveyGob) throws InterruptedException
    {
        Coord tile = tileOf(surveyGob);
        resumeTile = tile;
        resumeAt = new NGlobalCoord(surveyGob.rc);

        if (NUtils.getGameUI().getWindow("Land survey") == null) {
            if (!goToResumeFlag(gui)) {
                return Results.ERROR("Leveler: cannot reach survey flag");
            }
            surveyGob = findSurveyByTile(tile);
            if (surveyGob == null) {
                markSkipped(tile);
                return Results.SUCCESS();
            }
            clearCursor(gui);
            NUtils.rclickGob(surveyGob);
            NUtils.addTask(new WaitWindow("Land survey"));
        }
        Window wnd = NUtils.getGameUI().getWindow("Land survey");
        if (!(wnd instanceof LandSurvey)) {
            markSkipped(tile);
            return Results.SUCCESS();
        }
        LandSurvey survey = (LandSurvey) wnd;

        Label wlbl = findWlbl(survey);
        if (wlbl == null) {
            markSkipped(tile);
            closeWindow(survey);
            return Results.SUCCESS();
        }
        waitForLabel(wlbl);
        waitForDigLabel(survey);
        refreshInfo(survey);
        int soilRequired = parseAfter(wlbl.text(), "Units of soil required:");
        if (soilRequired > 0) {
            int have = soilCount(gui);
            /* Capped by what still fits rather than by the outstanding requirement: a pile
             * hands over the whole request in one go, and asking for more than the
             * inventory can hold would leave the transfer waiting on items that can never
             * arrive. Whatever is short of the requirement is picked up on the next pass. */
            int want = Math.min(soilRequired, carryCapacity(gui));
            if (have < want) {
                NGlobalCoord spot = NUtils.bookmarkHere();
                closeWindow(survey);
                fetchSoil(gui, want);
                have = soilCount(gui);
                if (spot != null) {
                    NUtils.navigateTo(spot);
                } else if (!goToResumeFlag(gui)) {
                    return Results.ERROR("Leveler: cannot return to survey flag");
                }
                if (have == 0) {
                    return Results.ERROR("Leveler: no soil available (need " + soilRequired + ")");
                }
                /* Carrying less than the survey asks for is fine: digging spends what we
                 * have, the requirement shrinks, and the next pass restocks. */
                survey = reopenSurvey(gui, tile);
                if (survey == null) {
                    if (findSurveyByTile(tile) == null) {
                        markDone(tile);
                    } else {
                        markSkipped(tile);
                    }
                    return Results.SUCCESS();
                }
            } else if (have == 0) {
                closeWindow(survey);
                return Results.ERROR("Leveler: no room to carry soil (need " + soilRequired + ")");
            }
        }

        return digLoop(gui, tile, survey);
    }

    private Results digLoop(NGameUI gui, Coord tile, LandSurvey survey) throws InterruptedException
    {
        String prevLabel = null;
        boolean didDigThisCycle = false;
        while (true) {
            Label wlbl = findWlbl(survey);
            Button digBtn = findButton(survey, "Dig");
            Button removeBtn = findButton(survey, "Remove");
            if (wlbl == null || digBtn == null || removeBtn == null) {
                markSkipped(tile);
                closeWindow(survey);
                return Results.SUCCESS();
            }
            waitForLabel(wlbl);
            waitForDigLabel(survey);
            waitForMapUpdate(survey);
            refreshInfo(survey);
            String curLabel = wlbl.text();
            long diff = surveyDiffUnits(survey);

            /* A fill that runs dry mid-dig leaves the character idle with the survey still
             * asking for soil. Hand back to the caller to restock instead of clicking Dig
             * again and sitting out the idle timer on every pass. */
            if (shouldFetchMoreSoil(parseAfter(curLabel, "Units of soil required:") > 0,
                    soilCount(gui) == 0)) {
                closeWindow(survey);
                return Results.SUCCESS();
            }

            if (didDigThisCycle && prevLabel != null && prevLabel.equals(curLabel) && diff == 0) {
                removeBtn.click();
                NUtils.addTask(new WindowIsClosed(survey));
                markDone(tile);
                disposeIfNeeded(gui, true);
                return Results.SUCCESS();
            }
            prevLabel = curLabel;
            final boolean filling = parseAfter(curLabel, "Units of soil required:") > 0;
            int soilBefore = soilCount(gui);

            NUtils.getUI().dropLastError();
            int sysSizeBefore = syslogSize(gui);
            digBtn.click();
            didDigThisCycle = true;
            final int sysBefore = sysSizeBefore;

            final Gob player = NUtils.player();
            if (player == null) return Results.FAIL();

            NUtils.addTask(new NTask()
            {
                int idleCount = 0;

                @Override
                public boolean check()
                {
                    if (player.pose().contains("idle")) idleCount++;
                    else idleCount = 0;
                    if (filling && idleCount >= 20) return true;
                    if (idleCount >= 360) return true;
                    if (NUtils.getStamina() < 0.25 || NUtils.getEnergy() < 0.3) return true;
                    /* Running out of room is a stop condition for digging only - that is the
                     * phase that loads the inventory up. A fill empties it, and it starts out
                     * deliberately packed with the soil to spend, so any free-space test here
                     * fires on the very first tick and kills the fill before it places a
                     * single unit. A fill that jams instead falls out through the idle count. */
                    if (shouldDumpForFreeSpace(filling, gui.getInventory().calcFreeSpace())) return true;
                    if (syslogContainsSince(gui, sysBefore, CANNOT_LEVEL_MSG)) return true;
                    if (syslogContainsSince(gui, sysBefore, NEED_SOIL_MSG)) return true;
                    String err = NUtils.getUI().getLastError();
                    return (err != null && err.contains(CANNOT_LEVEL_MSG)) || isNeedSoilError(err);
                }
            });

            refreshInfo(survey);

            String lastErr = NUtils.getUI().getLastError();
            if ((lastErr != null && lastErr.contains(CANNOT_LEVEL_MSG))
                    || syslogContainsSince(gui, sysBefore, CANNOT_LEVEL_MSG)) {
                removeBtn.click();
                NUtils.addTask(new WindowIsClosed(survey));
                markDone(tile);
                disposeIfNeeded(gui, true);
                return Results.SUCCESS();
            }

            if (shouldFetchMoreSoil(filling, soilCount(gui) == 0)
                    || syslogContainsSince(gui, sysBefore, NEED_SOIL_MSG)
                    || isNeedSoilError(lastErr)) {
                return Results.SUCCESS();
            }

            if (NUtils.getStamina() < 0.25 || NUtils.getEnergy() < 0.3) {
                stopDig(gui);
                return Results.SUCCESS();
            }

            int free = gui.getInventory().getNumberFreeCoord(SOIL_SIZE);
            /* A fill that is still eating into the load is working as intended - what is
             * in the inventory is the fill material, and hauling it to the dump only to
             * fetch it again is the round trip this whole path exists to avoid. Dump only
             * once the dig has stopped spending it. */
            int soilNow = soilCount(gui);
            if (filling && soilNow < soilBefore) {
                continue;
            }
            if (shouldDumpForFreeSpace(filling, free)) {
                closeWindow(survey);
                Results dr = disposeIfNeeded(gui, false);
                if (!dr.IsSuccess()) {
                    return dr;
                }
                if (!goToResumeFlag(gui)) {
                    return Results.ERROR("Leveler: cannot return to survey flag");
                }
                LandSurvey nw = reopenSurvey(gui, tile);
                if (nw == null) {
                    if (findSurveyByTile(tile) == null) {
                        markDone(tile);
                    }
                    return Results.SUCCESS();
                }
                survey = nw;
                prevLabel = null;
                didDigThisCycle = false;
            }
        }
    }

    private static int syslogSize(NGameUI gui)
    {
        try {
            ChatUI.Channel ch = gui.syslog;
            if (ch == null) return 0;
            synchronized (ch.rmsgs) {
                return ch.rmsgs.size();
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean syslogContainsSince(NGameUI gui, int startIdx, String needle)
    {
        try {
            ChatUI.Channel ch = gui.syslog;
            if (ch == null) return false;
            synchronized (ch.rmsgs) {
                for (int i = Math.max(0, startIdx); i < ch.rmsgs.size(); i++) {
                    ChatUI.Channel.Message m = ch.rmsgs.get(i).msg;
                    if (m instanceof ChatUI.Channel.SimpleMessage) {
                        String t = ((ChatUI.Channel.SimpleMessage) m).text;
                        if (t != null && t.contains(needle)) return true;
                    }
                }
            }
        } catch (Exception e) {
        }
        return false;
    }

    private static long surveyDiffUnits(LandSurvey survey)
    {
        try {
            haven.res.ui.surv.Data d = survey.data;
            if (d == null || d.dz == null) return -1;
            haven.MCache map = NUtils.getGameUI().map.glob.map;
            long total = 0;
            for (Coord vc : d.varea) {
                int vz = Math.round((float) map.getfz(vc) * d.gran);
                int tz = d.dz[d.varea.ridx(vc)];
                total += Math.abs(tz - vz);
            }
            return total;
        } catch (Exception e) {
            return -1;
        }
    }

    private static void waitForMapUpdate(LandSurvey survey) throws InterruptedException
    {
        final int startSeq = survey.data != null ? survey.data.seq : -1;
        NUtils.addTask(new NTask()
        {
            int ticks = 0;
            @Override
            public boolean check()
            {
                ticks++;
                if (ticks > 40) return true;
                return survey.data != null && survey.data.seq != startSeq;
            }
        });
    }

    static boolean shouldDumpForFreeSpace(boolean filling, int freeSlots) {
        return shouldDumpForFreeSpace(filling, freeSlots, MIN_FREE_SLOTS);
    }

    static boolean shouldDumpForFreeSpace(boolean filling, int freeSlots, int minFree) {
        return !filling && freeSlots >= 0 && freeSlots < minFree;
    }

    static String[] excavationDumpOrder() {
        return new String[] { WORM_ITEM, SOIL_ITEM, TUBER_ITEM };
    }

    static boolean shouldPullSoil(int soilRequired, int invSoil) {
        return soilRequired > 0 && invSoil <= 0;
    }

    static boolean shouldFetchMoreSoil(boolean filling, boolean soilEmpty) {
        return filling && soilEmpty;
    }

    static boolean isNeedSoilError(String err) {
        return err != null && err.toLowerCase().contains(NEED_SOIL_MSG);
    }

    static int tripSize(int soilRequired, int invSoil, int freeSlots, int stackSize) {
        int need = Math.max(0, soilRequired - Math.max(0, invSoil));
        int cap = Math.max(0, freeSlots) * Math.max(1, stackSize);
        return Math.min(need, cap);
    }

    /**
     * Bring back up to {@code want} soil from wherever the Take zones keep it.
     *
     * TakeItems2 handles the storage as a whole - piles, containers, barter - asks each
     * source for everything still wanted rather than for a slot count, and skips a pile
     * it cannot path to instead of clicking at a stockpile walled in by its neighbours
     * and then waiting forever for a window that never opens.
     */
    private Results fetchSoil(NGameUI gui, int want) throws InterruptedException
    {
        if (want <= 0) {
            return Results.SUCCESS();
        }
        context.addInItem(SOIL_ITEM, null);
        return new TakeItems2(context, SOIL_ITEM, want).run(gui);
    }

    /**
     * How much more soil the inventory can still take.
     *
     * Soil stacks, so capacity is free cells times the stack depth, not the free cell
     * count: a take of N soil gives back all but N/stack of the cells it filled, and
     * budgeting by cells alone meant every visited pile was left half full while the bot
     * walked to the next one with an ever smaller request.
     */
    private static int carryCapacity(NGameUI gui) throws InterruptedException
    {
        int cells = gui.getInventory().getNumberFreeCoord(SOIL_SIZE);
        if (cells <= 0) {
            return 0;
        }
        return cells * Math.max(1, StackSupporter.getFullStackSize(SOIL_ITEM));
    }

    private static int soilCount(NGameUI gui) throws InterruptedException
    {
        return gui.getInventory().getItems(SOIL_ONLY).size();
    }

    /** Walk back to the survey marker on {@code tile} and reopen its window. */
    private static LandSurvey reopenSurvey(NGameUI gui, Coord tile) throws InterruptedException
    {
        Gob sg = findSurveyByTile(tile);
        if (sg == null) {
            return null;
        }
        if (nurgling.actions.PathFinder.isAvailable(sg.rc))
            new PathFinder(sg.rc).run(gui);
        clearCursor(gui);
        NUtils.rclickGob(sg);
        NUtils.addTask(new WaitWindow("Land survey"));
        Window nw = NUtils.getGameUI().getWindow("Land survey");
        return (nw instanceof LandSurvey) ? (LandSurvey) nw : null;
    }

    private Results disposeIfNeeded(NGameUI gui, boolean bestEffort) throws InterruptedException
    {
        if (soilDisposalComplete(count(gui, SOIL), count(gui, EARTHWORM), count(gui, ODD_TUBER)))
            return Results.SUCCESS();

        for (String itemName : excavationDumpOrder()) {
            transferToPut(gui, itemName);
        }

        if (soilDisposalComplete(count(gui, SOIL), count(gui, EARTHWORM), count(gui, ODD_TUBER)))
            return Results.SUCCESS();

        if (count(gui, SOIL) > 0 || hasTopLevelStacks(gui)) {
            NArea dump = context.goToArea(Specialisation.SpecName.soilDump);
            if (dump != null && NUtils.navigateToArea(dump, true)) {
                Pair<Coord2d, Coord2d> rca = dump.getRCArea();
                if (rca != null) {
                    Coord2d center = rca.b.sub(rca.a).div(2).add(rca.a);
                    new PathFinder(center).run(gui);
                    clearCursor(gui);
                    dropItems(gui, SOIL);
                    dropTopLevelStacks(gui);
                }
            }
        }

        int remainingSoil = count(gui, SOIL);
        int remainingWorms = count(gui, EARTHWORM);
        int remainingTubers = count(gui, ODD_TUBER);
        if (soilDisposalComplete(remainingSoil, remainingWorms, remainingTubers) || bestEffort)
            return Results.SUCCESS();
        String err = disposalError(remainingSoil, remainingWorms, remainingTubers);
        return err != null ? Results.ERROR(err) : Results.FAIL();
    }

    static boolean soilDisposalComplete(int remainingSoil, int remainingWorms, int remainingTubers) {
        return remainingSoil == 0 && remainingWorms == 0 && remainingTubers == 0;
    }

    static String disposalError(int remainingSoil, int remainingWorms, int remainingTubers) {
        if (remainingSoil > 0)
            return "Leveler: no soil disposal route available";
        if (remainingWorms > 0)
            return "Leveler: no earthworm PUT area available";
        if (remainingTubers > 0)
            return "Leveler: no Odd Tuber PUT area available";
        return null;
    }

    private static int count(NGameUI gui, NAlias alias) throws InterruptedException
    {
        return gui.getInventory().getItems(alias).size();
    }

    static boolean readyToUseRemoteArea(boolean navigated, boolean hasRcArea) {
        return navigated && hasRcArea;
    }

    private void transferToPut(NGameUI gui, String itemName) throws InterruptedException
    {
        if (count(gui, new NAlias(itemName)) <= 0)
            return;
        NArea put = NContext.findOut(itemName, 1);
        if (put == null) put = NContext.findOutGlobal(itemName, 1, gui);
        if (put == null)
            return;
        if (!NUtils.navigateToArea(put, true))
            return;
        Pair<Coord2d, Coord2d> rc = put.getRCArea();
        if (!readyToUseRemoteArea(true, rc != null))
            return;
        clearCursor(gui);
        new TransferToPiles(rc, itemName, 1).run(gui);
    }

    private static void dropItems(NGameUI gui, NAlias alias) throws InterruptedException
    {
        ArrayList<WItem> items = gui.getInventory().getItems(alias);
        for (WItem item : items) {
            NUtils.drop(item);
        }
        if (!items.isEmpty()) {
            NUtils.addTask(new WaitItems(gui.getInventory(), alias, 0));
        }
    }

    private static boolean hasTopLevelStacks(NGameUI gui)
    {
        for (Widget w = gui.getInventory().child; w != null; w = w.next) {
            if (w instanceof ItemStack) return true;
        }
        return false;
    }

    private static void dropTopLevelStacks(NGameUI gui) throws InterruptedException
    {
        ArrayList<Widget> toDrop = new ArrayList<>();
        for (Widget w = gui.getInventory().child; w != null; w = w.next) {
            if (w instanceof ItemStack) toDrop.add(w);
        }
        for (Widget w : toDrop) {
            w.wdgmsg("drop");
        }
    }

    private static Coord tileOf(Gob g)
    {
        return g.rc.floor(MCache.tilesz);
    }

    private boolean goToResumeFlag(NGameUI gui) throws InterruptedException
    {
        if (resumeAt != null && !NUtils.navigateTo(resumeAt))
            return false;
        final Coord tile = resumeTile;
        NUtils.addTask(new NTask()
        {
            int ticks = 0;
            @Override
            public boolean check()
            {
                ticks++;
                return findSurveyByTile(tile) != null || ticks > 80;
            }
        });
        Gob sg = findSurveyByTile(resumeTile);
        if (sg == null)
            return false;
        if (nurgling.actions.PathFinder.isAvailable(sg.rc))
            new PathFinder(sg.rc).run(gui);
        return true;
    }

    private static Gob findSurveyByTile(Coord tile)
    {
        if (tile == null) return null;
        for (Gob g : Finder.findGobs(SURVOBJ)) {
            if (tileOf(g).equals(tile)) return g;
        }
        return null;
    }

    private static void clearCursor(NGameUI gui) throws InterruptedException
    {
        if (gui.vhand != null) {
            NUtils.drop(gui.vhand);
            NUtils.addTask(new WaitFreeHand());
        }
    }

    private static void stopDig(NGameUI gui) throws InterruptedException
    {
        final Gob player = NUtils.player();
        if (player == null) return;
        NUtils.lclick(player.rc);
        NUtils.addTask(new NTask()
        {
            int idleCount = 0;
            int totalTicks = 0;

            @Override
            public boolean check()
            {
                totalTicks++;
                if (totalTicks > 100) return true;
                if (player.pose().contains("idle")) {
                    idleCount++;
                    return idleCount >= 3;
                }
                idleCount = 0;
                return false;
            }
        });
    }

    private static Label findWlbl(LandSurvey survey)
    {
        for (Widget child : survey.children()) {
            if (child instanceof Label) {
                String t = ((Label) child).text();
                if (t.contains("Units of soil left") || t.contains("Units of soil req")) {
                    return (Label) child;
                }
            }
        }
        return null;
    }

    private static Label findDlbl(LandSurvey survey)
    {
        for (Widget child : survey.children()) {
            if (child instanceof Label) {
                String t = ((Label) child).text();
                if (t.contains("Units of soil to dig")) {
                    return (Label) child;
                }
            }
        }
        return null;
    }

    private void waitForDigLabel(LandSurvey survey) throws InterruptedException
    {
        Label dlbl = findDlbl(survey);
        if (dlbl != null)
            waitForLabel(dlbl);
    }

    private void refreshInfo(LandSurvey survey)
    {
        if (stats == null)
            return;
        int required = 0;
        int toDig = 0;
        Label w = findWlbl(survey);
        Label d = findDlbl(survey);
        if (w != null)
            required = parseAfter(w.text(), "Units of soil required:");
        if (d != null)
            toDig = parseAfter(d.text(), "Units of soil to dig:");
        stats.noteRemaining(LevelerStats.remainingWork(required, toDig));
        if (infoWnd == null || infoWnd.isClosed())
            return;
        long now = System.currentTimeMillis();
        String remain = stats.lastRemaining() < 0 ? "-" : String.valueOf(stats.lastRemaining());
        infoWnd.update(
                LevelerStats.formatRate(stats.unitsPerMinute(now)),
                remain,
                LevelerStats.formatDuration(stats.etaMs(now)));
    }

    private static Button findButton(LandSurvey survey, String label)
    {
        for (Widget child : survey.children()) {
            if (child instanceof Button) {
                Button b = (Button) child;
                if (b.text != null && b.text.text != null && b.text.text.equals(label)) return b;
            }
        }
        return null;
    }

    private static void waitForLabel(Label label) throws InterruptedException
    {
        final Label fl = label;
        NUtils.addTask(new NTask()
        {
            @Override
            public boolean check()
            {
                return !fl.text().equals("...");
            }
        });
    }

    private static int parseAfter(String label, String prefix)
    {
        int idx = label.indexOf(prefix);
        if (idx < 0) return 0;
        String rem = label.substring(idx + prefix.length()).trim();
        int end = 0;
        while (end < rem.length() && Character.isDigit(rem.charAt(end))) end++;
        if (end == 0) return 0;
        try {
            return Integer.parseInt(rem.substring(0, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void closeWindow(Window wnd) throws InterruptedException
    {
        if (wnd == null || !NUtils.getGameUI().isWindowExist(wnd)) return;
        wnd.wdgmsg("close");
        NUtils.addTask(new WindowIsClosed(wnd));
    }
}
