package nurgling.actions.bots;

import haven.Area;
import haven.Button;
import haven.ChatUI;
import haven.Coord;
import haven.Coord2d;
import haven.Gob;
import haven.Label;
import haven.MCache;
import haven.Pair;
import haven.UI;
import haven.WItem;
import haven.Widget;
import haven.Window;
import haven.res.ui.surv.LandSurvey;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.CloseTargetContainer;
import nurgling.actions.OpenTargetContainer;
import nurgling.actions.PathFinder;
import nurgling.actions.RestoreResources;
import nurgling.actions.Results;
import nurgling.actions.TakeItemsFromPile;
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
    private static final NAlias STOCKPILE = new NAlias("stockpile");
    private static final NAlias SOIL_PILE = new NAlias("gfx/terobjs/stockpile-soil");
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

    @Override
    public Results run(NGameUI gui) throws InterruptedException
    {
        done.clear();
        skipped.clear();
        resumeTile = null;
        resumeAt = null;
        stats = new LevelerStats();
        infoWnd = gui.add(new LevelerWnd(), UI.scale(200, 200));
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
        if (shouldPullSoil(soilRequired, gui.getInventory().getItems(SOIL).size())) {
            closeWindow(survey);
            Results dr = disposeIfNeeded(gui, false);
            if (!dr.IsSuccess()) {
                return dr;
            }
            int free = gui.getInventory().getNumberFreeCoord(SOIL_SIZE);
            int trip = tripSize(soilRequired, 0, free, StackSupporter.getFullStackSize(SOIL_ITEM));
            if (trip <= 0) {
                return Results.ERROR("Leveler: no inventory space for soil");
            }
            Results pr = pullSoilFromTake(gui, trip);
            if (!pr.IsSuccess()) {
                return Results.ERROR("Leveler: no soil in TAKE area");
            }
            return Results.SUCCESS();
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

            if (shouldFetchMoreSoil(parseAfter(curLabel, "Units of soil required:") > 0,
                    gui.getInventory().getItems(SOIL).isEmpty())) {
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

            if (shouldFetchMoreSoil(filling, gui.getInventory().getItems(SOIL).isEmpty())
                    || syslogContainsSince(gui, sysBefore, NEED_SOIL_MSG)
                    || isNeedSoilError(lastErr)) {
                return Results.SUCCESS();
            }

            if (NUtils.getStamina() < 0.25 || NUtils.getEnergy() < 0.3) {
                stopDig(gui);
                return Results.SUCCESS();
            }

            int free = gui.getInventory().getNumberFreeCoord(SOIL_SIZE);
            if (shouldDumpForFreeSpace(filling, free)) {
                closeWindow(survey);
                Results dr = disposeIfNeeded(gui, false);
                if (!dr.IsSuccess()) {
                    return dr;
                }
                if (!goToResumeFlag(gui)) {
                    return Results.ERROR("Leveler: cannot return to survey flag");
                }
                Gob sg = findSurveyByTile(tile);
                if (sg == null) {
                    markDone(tile);
                    return Results.SUCCESS();
                }
                NUtils.rclickGob(sg);
                NUtils.addTask(new WaitWindow("Land survey"));
                Window nw = NUtils.getGameUI().getWindow("Land survey");
                if (!(nw instanceof LandSurvey)) {
                    return Results.SUCCESS();
                }
                survey = (LandSurvey) nw;
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

    private Results pullSoilFromTake(NGameUI gui, int need) throws InterruptedException
    {
        NArea take = NContext.findIn(SOIL_ITEM);
        if (take == null) take = NContext.findInGlobal(SOIL_ITEM);
        if (take == null) return Results.FAIL();
        if (!NUtils.navigateToArea(take, true))
            return Results.FAIL();

        int stack = Math.max(1, StackSupporter.getFullStackSize(SOIL_ITEM));
        ArrayList<Gob> piles = Finder.findGobs(take, SOIL_PILE);
        piles.sort(NUtils.d_comp);

        for (Gob pile : piles) {
            while (Finder.findGob(pile.id) != null) {
                int freeSlots = gui.getInventory().getNumberFreeCoord(SOIL_SIZE);
                if (freeSlots <= 0) {
                    return Results.SUCCESS();
                }
                int invSoil = gui.getInventory().getItems(SOIL).size();
                int toTake = tripSize(need, 0, freeSlots, stack);
                if (toTake <= 0) {
                    return invSoil > 0 ? Results.SUCCESS() : Results.FAIL();
                }

                PathFinder pf = new PathFinder(pile);
                pf.isHardMode = true;
                pf.run(gui);
                new OpenTargetContainer("Stockpile", pile).run(gui);
                if (gui.getStockpile() == null) {
                    break;
                }
                int pileCount = gui.getStockpile().calcCount();
                if (pileCount <= 0) {
                    new CloseTargetContainer("Stockpile").run(gui);
                    break;
                }
                TakeItemsFromPile takeAct = new TakeItemsFromPile(pile, gui.getStockpile(), Math.min(toTake, pileCount));
                takeAct.run(gui);
                new CloseTargetContainer("Stockpile").run(gui);
                if (takeAct.getResult() <= 0) {
                    break;
                }
            }
            if (gui.getInventory().getNumberFreeCoord(SOIL_SIZE) <= 0) {
                return Results.SUCCESS();
            }
        }
        return gui.getInventory().getItems(SOIL).isEmpty() ? Results.FAIL() : Results.SUCCESS();
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

        if (count(gui, SOIL) > 0) {
            NContext ctx = new NContext(gui);
            NArea dump = ctx.goToArea(Specialisation.SpecName.soilDump);
            if (dump != null && NUtils.navigateToArea(dump, true)) {
                Pair<Coord2d, Coord2d> rca = dump.getRCArea();
                if (rca != null) {
                    Coord2d center = rca.b.sub(rca.a).div(2).add(rca.a);
                    new PathFinder(center).run(gui);
                    clearCursor(gui);
                    dropItems(gui, SOIL);
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

    private static Area varea(LandSurvey survey)
    {
        try {
            return survey.data != null ? survey.data.varea : null;
        } catch (NullPointerException e) {
            return null;
        }
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
