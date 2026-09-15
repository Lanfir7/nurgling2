package nurgling.actions.bots;

import haven.Button;
import haven.ChatUI;
import haven.Coord;
import haven.ICheckBox;
import haven.Coord2d;
import haven.Gob;
import haven.Label;
import haven.MCache;
import haven.Pair;
import haven.WItem;
import haven.Widget;
import haven.Window;
import haven.res.ui.surv.LandSurvey;
import nurgling.NGItem;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.Equip;
import nurgling.actions.PathFinder;
import nurgling.actions.RestoreResources;
import nurgling.actions.Results;
import nurgling.actions.TransferToPiles;
import nurgling.areas.NArea;
import nurgling.areas.NContext;
import nurgling.areas.NGlobalCoord;
import nurgling.tasks.NTask;
import nurgling.tasks.WaitFreeHand;
import nurgling.tasks.WaitWindow;
import nurgling.tools.Finder;
import nurgling.tools.NAlias;
import nurgling.widgets.bots.WormFarmWnd;

import java.util.ArrayList;
import java.util.Map;

public class WormFarmer implements Action {
    private static final Coord SOIL_SIZE = new Coord(1, 1);
    private static final NAlias SURVOBJ = new NAlias("survobj");
    private static final NAlias SOIL = new NAlias("Soil");
    private static final NAlias EARTHWORM = new NAlias("Earthworm");
    private static final NAlias ODD_TUBER = new NAlias("Odd Tuber");
    private static final String NEED_SOIL_MSG = "need soil";

    private Coord flagTile = null;
    private NGlobalCoord flagAt = null;
    private Integer planeZ = null;
    private WormFarmStats stats = null;
    private WormFarmWnd infoWnd = null;

    public WormFarmer() {}

    public WormFarmer(Map<String, Object> settings) {
        this();
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        if (!new Equip(new NAlias("Shovel")).run(gui).IsSuccess()) {
            return Results.ERROR("Worm Farm: no shovel");
        }
        Gob first = pickNearestSurvey();
        if (first == null) {
            return Results.ERROR("Worm Farm: no survey flag found");
        }
        flagTile = tileOf(first);
        flagAt = new NGlobalCoord(first.rc);
        stats = new WormFarmStats();
        infoWnd = NUtils.addCentered(gui, new WormFarmWnd());
        refreshStats(gui);

        try {
        while (true) {
            Results rr = drinkAndEquip(gui);
            if (!rr.IsSuccess()) return finishWith(gui, rr);
            LandSurvey survey = openSurvey(gui);
            if (survey == null) {
                return finishWith(gui, Results.ERROR("Worm Farm: survey flag gone"));
            }
            if (planeZ == null) {
                Results remembered = rememberGroundPlane(survey);
                if (!remembered.IsSuccess()) return finishWith(gui, remembered);
            }
            pushPlane(survey, WormFarmLogic.deepHeight(planeZ));

            Results dug = digUntilFull(gui, survey, false);
            if (!dug.IsSuccess()) return finishWith(gui, dug);
            dropJunk(gui);
            Results restored = restorePlaneAtFlag(gui);
            if (!restored.IsSuccess()) return finishWith(gui, restored);

            Results drank = drinkAndEquip(gui);
            if (!drank.IsSuccess()) return finishWith(gui, drank);
            Results dumped = dumpWormsAndTubers(gui);
            if (!dumped.IsSuccess()) return finishWith(gui, dumped);
            refreshStats(gui);
            survey = openSurvey(gui);
            if (survey == null) {
                return finishWith(gui, Results.ERROR("Worm Farm: survey flag gone"));
            }
            if (count(gui, SOIL) > 0) {
                pushPlane(survey, planeZ);
                waitFillMode(survey);
                Results filled = digUntilFull(gui, survey, true);
                if (!filled.IsSuccess()) return finishWith(gui, filled);
            }
            dropJunk(gui);
        }
        } finally {
            try { restorePlaneAtFlag(gui); } catch (Exception ignored) {}
            if (infoWnd != null) {
                try { infoWnd.destroy(); } catch (Exception ignored) {}
                infoWnd = null;
            }
        }
    }

    private Results digUntilFull(NGameUI gui, LandSurvey survey, boolean filling) throws InterruptedException {
        while (true) {
            dropJunk(gui);
            refreshStats(gui);
            if (WormFarmLogic.shouldRestoreNeeds(NUtils.getStamina(), NUtils.getEnergy())) {
                stopDig(gui);
                if (!filling) {
                    Results restored = restorePlaneAtFlag(gui);
                    if (!restored.IsSuccess()) return restored;
                }
                Results drank = drinkAndEquip(gui);
                if (!drank.IsSuccess()) return drank;
                survey = openSurvey(gui);
                if (survey == null) {
                    return Results.ERROR("Worm Farm: survey flag gone");
                }
                if (!filling && planeZ != null) {
                    pushPlane(survey, WormFarmLogic.deepHeight(planeZ));
                }
                continue;
            }
            int soil = count(gui, SOIL);
            int free = gui.getInventory().getNumberFreeCoord(SOIL_SIZE);
            int minFree = WormFarmLogic.minFreeSlots(equippedToolName());
            waitForLabel(survey.wlbl);
            boolean fillMode = WormFarmLogic.isFillMode(survey.wlbl.text());
            int required = fillMode ? parseAfter(survey.wlbl.text(), "Units of soil required:") : 0;
            if (filling) {
                if (WormFarmLogic.shouldStopFill(fillMode, soil, required)) {
                    stopDig(gui);
                    return Results.SUCCESS();
                }
            } else if (WormFarmLogic.shouldStopDig(free, minFree)) {
                stopDig(gui);
                return Results.SUCCESS();
            }

            Button digBtn = findButton(survey, "Dig");
            if (digBtn == null) {
                return Results.ERROR("Worm Farm: survey flag gone");
            }
            NUtils.getUI().dropLastError();
            int sysBefore = syslogSize(gui);
            digBtn.click();
            final Gob player = NUtils.player();
            if (player == null) return Results.FAIL();
            final boolean fillWait = filling;
            final int stopFree = minFree;
            NUtils.addTask(new NTask() {
                int idleCount = 0;
                @Override
                public boolean check() {
                    if (player.pose().contains("idle")) idleCount++;
                    else idleCount = 0;
                    if (WormFarmLogic.waitDigTickDone(fillWait, idleCount,
                            WormFarmLogic.shouldRestoreNeeds(NUtils.getStamina(), NUtils.getEnergy()),
                            gui.getInventory().calcFreeSpace(), stopFree))
                        return true;
                    if (stats != null)
                        stats.noteStamina(NUtils.getStamina());
                    String err = NUtils.getUI().getLastError();
                    return isNeedSoil(err) || syslogContainsSince(gui, sysBefore, NEED_SOIL_MSG);
                }
            });
            dropJunk(gui);
            int soilAfter = count(gui, SOIL);
            boolean fillModeAfter = WormFarmLogic.isFillMode(survey.wlbl.text());
            if (filling && (soilAfter == 0
                    || syslogContainsSince(gui, sysBefore, NEED_SOIL_MSG)
                    || isNeedSoil(NUtils.getUI().getLastError())
                    || WormFarmLogic.fillDidNotUseSoil(fillModeAfter, soil, soilAfter))) {
                stopDig(gui);
                return Results.SUCCESS();
            }
        }
    }

    private static boolean isNeedSoil(String err) {
        return err != null && err.toLowerCase().contains(NEED_SOIL_MSG);
    }

    private Results finishWith(NGameUI gui, Results r) {
        try {
            restorePlaneAtFlag(gui);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return r;
    }

    private Results restorePlaneAtFlag(NGameUI gui) throws InterruptedException {
        if (planeZ == null) return Results.SUCCESS();
        LandSurvey survey = openSurvey(gui);
        if (survey == null) {
            return Results.ERROR("Worm Farm: survey flag gone");
        }
        pushPlane(survey, planeZ);
        return Results.SUCCESS();
    }

    private Results dumpWormsAndTubers(NGameUI gui) throws InterruptedException {
        transferToPut(gui, "Earthworm");
        transferToPut(gui, "Odd Tuber");
        String err = WormFarmLogic.putError(count(gui, EARTHWORM), count(gui, ODD_TUBER));
        return err != null ? Results.ERROR(err) : Results.SUCCESS();
    }

    private void transferToPut(NGameUI gui, String itemName) throws InterruptedException {
        if (count(gui, new NAlias(itemName)) <= 0) return;
        NArea put = NContext.findOut(itemName, 1);
        if (put == null) put = NContext.findOutGlobal(itemName, 1, gui);
        if (put == null) return;
        if (!NUtils.navigateToArea(put, true)) return;
        Pair<Coord2d, Coord2d> rc = put.getRCArea();
        if (rc == null) return;
        clearCursor(gui);
        new TransferToPiles(rc, itemName, 1).run(gui);
    }

    private static String equippedToolName() throws InterruptedException {
        nurgling.widgets.NEquipory eq = NUtils.getEquipment();
        if (eq == null) return null;
        String left = itemName(eq.findItem(nurgling.widgets.NEquipory.Slots.HAND_LEFT.idx));
        String right = itemName(eq.findItem(nurgling.widgets.NEquipory.Slots.HAND_RIGHT.idx));
        if (WormFarmLogic.isMetalShovel(left)) return left;
        if (WormFarmLogic.isMetalShovel(right)) return right;
        return left != null ? left : right;
    }

    private static String itemName(WItem w) {
        if (w == null || !(w.item instanceof NGItem)) return null;
        return ((NGItem) w.item).name();
    }

    private static void dropJunk(NGameUI gui) throws InterruptedException {
        ArrayList<WItem> items = gui.getInventory().getItems();
        ArrayList<WItem> junk = new ArrayList<>();
        for (WItem item : items) {
            String name = item.item instanceof NGItem ? ((NGItem) item.item).name() : null;
            if (WormFarmLogic.shouldDropJunk(name)) junk.add(item);
        }
        for (WItem item : junk) {
            NUtils.drop(item);
        }
        if (!junk.isEmpty()) {
            NUtils.addTask(new NTask() {
                int ticks = 0;
                @Override
                public boolean check() {
                    ticks++;
                    return ticks > 20;
                }
            });
        }
    }

    private LandSurvey openSurvey(NGameUI gui) throws InterruptedException {
        Window existing = NUtils.getGameUI().getWindow("Land survey");
        if (existing instanceof LandSurvey) return (LandSurvey) existing;
        if (!goToFlag(gui)) return null;
        Gob gob = findSurveyByTile(flagTile);
        if (gob == null) return null;
        clearCursor(gui);
        NUtils.rclickGob(gob);
        NUtils.addTask(new WaitWindow("Land survey"));
        Window wnd = NUtils.getGameUI().getWindow("Land survey");
        return (wnd instanceof LandSurvey) ? (LandSurvey) wnd : null;
    }

    private boolean goToFlag(NGameUI gui) throws InterruptedException {
        if (flagAt != null && !NUtils.navigateTo(flagAt)) return false;
        final Coord tile = flagTile;
        NUtils.addTask(new NTask() {
            int ticks = 0;
            @Override
            public boolean check() {
                ticks++;
                return findSurveyByTile(tile) != null || ticks > 80;
            }
        });
        Gob sg = findSurveyByTile(flagTile);
        if (sg == null) return false;
        if (PathFinder.isAvailable(sg.rc)) new PathFinder(sg.rc).run(gui);
        return true;
    }

    private static Gob pickNearestSurvey() {
        Gob player = NUtils.player();
        if (player == null) return null;
        Gob best = null;
        double bestDist = Double.MAX_VALUE;
        for (Gob s : Finder.findGobs(SURVOBJ)) {
            double d = s.rc.dist(player.rc);
            if (d < bestDist) {
                bestDist = d;
                best = s;
            }
        }
        return best;
    }

    private static Gob findSurveyByTile(Coord tile) {
        if (tile == null) return null;
        for (Gob g : Finder.findGobs(SURVOBJ)) {
            if (tileOf(g).equals(tile)) return g;
        }
        return null;
    }

    private static Coord tileOf(Gob g) {
        return g.rc.floor(MCache.tilesz);
    }

    private Results drinkAndEquip(NGameUI gui) throws InterruptedException {
        Results rr = new RestoreResources().run(gui);
        if (!rr.IsSuccess()) {
            return Results.ERROR("Worm Farm: failed to restore resources");
        }
        if (!new Equip(new NAlias("Shovel")).run(gui).IsSuccess()) {
            return Results.ERROR("Worm Farm: no shovel");
        }
        refreshStats(gui);
        return Results.SUCCESS();
    }

    private void refreshStats(NGameUI gui) throws InterruptedException {
        if (stats == null)
            return;
        stats.noteWorms(count(gui, EARTHWORM));
        stats.noteStamina(NUtils.getStamina());
        if (infoWnd == null || infoWnd.isClosed())
            return;
        long now = System.currentTimeMillis();
        infoWnd.update(
                stats.harvested(),
                LevelerStats.formatRate(stats.wormsPerMinute(now)),
                LevelerStats.formatDuration(stats.elapsedMs(now)),
                WormFarmStats.formatStaminaRate(stats.staminaBarPerMinute(now)));
    }

    private Results rememberGroundPlane(LandSurvey survey) throws InterruptedException {
        unlock(survey);
        waitForLabel(survey.tllbl);
        String label = survey.tllbl.text();
        int z = WormFarmLogic.parseTargetLevel(label);
        if (!WormFarmLogic.canReadTarget(label) || WormFarmLogic.isRangeTarget(label)
                || !WormFarmLogic.isUsablePlaneTarget(z)) {
            Button plane = findButton(survey, "Ground plane");
            if (plane != null)
                plane.click();
            waitTicks(10);
            sendSurvey(survey);
            waitTicks(15);
            waitForLabel(survey.tllbl);
            label = survey.tllbl.text();
            z = WormFarmLogic.parseTargetLevel(label);
        }
        if (!WormFarmLogic.canReadTarget(label) || !WormFarmLogic.isUsablePlaneTarget(z))
            return Results.ERROR("Worm Farm: cannot read Ground plane target");
        planeZ = z;
        return Results.SUCCESS();
    }

    private void pushPlane(LandSurvey survey, int height) throws InterruptedException {
        NUtils.addTask(new NTask() {
            int phase = 0;
            @Override
            public boolean check() {
                if (phase == 0) {
                    phase = 1;
                    return false;
                }
                if (phase == 1) {
                    survey.applyAndSend(height);
                    phase = 2;
                    return false;
                }
                return ++phase > 25;
            }
        });
    }

    private static void waitFillMode(LandSurvey survey) throws InterruptedException {
        NUtils.addTask(new NTask() {
            int ticks = 0;
            @Override
            public boolean check() {
                try {
                    if (++ticks > 40) return true;
                    if (survey == null || survey.wlbl == null) return true;
                    return WormFarmLogic.isFillMode(survey.wlbl.text());
                } catch (Exception e) {
                    return true;
                }
            }
        });
    }

    private static void sendSurvey(LandSurvey survey) {
        Object[] enc = survey.data.encode();
        survey.wdgmsg("data", enc[0], enc[1]);
    }

    private static void unlock(LandSurvey survey) {
        for (Widget child : survey.children()) {
            if (child instanceof ICheckBox) {
                ICheckBox lock = (ICheckBox) child;
                if (lock.a)
                    lock.click();
                return;
            }
        }
        survey.wdgmsg("lock", 1);
    }

    private static void waitTicks(int n) throws InterruptedException {
        NUtils.addTask(new NTask() {
            int ticks = 0;
            @Override
            public boolean check() {
                return ++ticks > n;
            }
        });
    }

    private static void clearCursor(NGameUI gui) throws InterruptedException {
        if (gui.vhand != null) {
            NUtils.drop(gui.vhand);
            NUtils.addTask(new WaitFreeHand());
        }
    }

    private static void stopDig(NGameUI gui) throws InterruptedException {
        Gob player = NUtils.player();
        if (player == null) return;
        NUtils.lclick(player.rc);
        NUtils.addTask(new NTask() {
            int idleCount = 0;
            int totalTicks = 0;
            @Override
            public boolean check() {
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

    private static int count(NGameUI gui, NAlias alias) throws InterruptedException {
        return gui.getInventory().getItems(alias).size();
    }

    private static Button findButton(LandSurvey survey, String label) {
        for (Widget child : survey.children()) {
            if (child instanceof Button) {
                Button b = (Button) child;
                if (b.text != null && b.text.text != null && b.text.text.equals(label)) return b;
            }
        }
        return null;
    }

    private static void waitForLabel(Label label) throws InterruptedException {
        NUtils.addTask(new NTask() {
            int ticks = 0;
            @Override
            public boolean check() {
                return !label.text().equals("...") || ++ticks > 80;
            }
        });
    }

    private static int parseAfter(String label, String prefix) {
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

    private static int syslogSize(NGameUI gui) {
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

    private static boolean syslogContainsSince(NGameUI gui, int startIdx, String needle) {
        try {
            ChatUI.Channel ch = gui.syslog;
            if (ch == null) return false;
            synchronized (ch.rmsgs) {
                for (int i = Math.max(0, startIdx); i < ch.rmsgs.size(); i++) {
                    ChatUI.Channel.Message m = ch.rmsgs.get(i).msg;
                    if (m instanceof ChatUI.Channel.SimpleMessage) {
                        String t = ((ChatUI.Channel.SimpleMessage) m).text;
                        if (t != null && t.toLowerCase().contains(needle)) return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
