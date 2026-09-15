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
    private int[] originalHeights = null;

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

        while (true) {
            Results rr = new RestoreResources().run(gui);
            if (!rr.IsSuccess()) {
                return Results.ERROR("Worm Farm: failed to restore resources");
            }
            LandSurvey survey = openSurvey(gui);
            if (survey == null) {
                return Results.ERROR("Worm Farm: survey flag gone");
            }
            if (originalHeights == null) {
                snapshotSurface(survey);
                originalHeights = WormFarmLogic.copyHeights(survey.data.dz);
                sendSurvey(survey);
            }
            unlock(survey);
            int min = originalHeights[0];
            for (int z : originalHeights) min = Math.min(min, z);
            WormFarmLogic.applyUniform(survey.data.wz, survey.data.dz, WormFarmLogic.deepHeight(min));
            survey.data.seq++;
            sendSurvey(survey);
            waitTicks(15);

            Results dug = digUntilFull(gui, survey, false);
            if (!dug.IsSuccess()) return dug;
            dropJunk(gui);
            Results dumped = dumpWormsAndTubers(gui);
            if (!dumped.IsSuccess()) return dumped;

            survey = openSurvey(gui);
            if (survey == null) {
                return Results.ERROR("Worm Farm: survey flag gone");
            }
            unlock(survey);
            WormFarmLogic.restoreHeights(survey.data.wz, survey.data.dz, originalHeights);
            survey.data.seq++;
            sendSurvey(survey);
            waitTicks(15);

            if (!WormFarmLogic.shouldSkipFill(count(gui, SOIL))) {
                Results filled = digUntilFull(gui, survey, true);
                if (!filled.IsSuccess()) return filled;
            }
            dropJunk(gui);
        }
    }

    private Results digUntilFull(NGameUI gui, LandSurvey survey, boolean filling) throws InterruptedException {
        while (true) {
            dropJunk(gui);
            if (WormFarmLogic.shouldRestoreNeeds(NUtils.getStamina(), NUtils.getEnergy())) {
                stopDig(gui);
                return Results.SUCCESS();
            }
            int soil = count(gui, SOIL);
            int free = gui.getInventory().getNumberFreeCoord(SOIL_SIZE);
            Label wlbl = findWlbl(survey);
            int required = 0;
            if (wlbl != null) {
                waitForLabel(wlbl);
                required = parseAfter(wlbl.text(), "Units of soil required:");
            }
            if (filling) {
                if (WormFarmLogic.shouldStopFill(soil, required)) {
                    stopDig(gui);
                    return Results.SUCCESS();
                }
            } else if (WormFarmLogic.shouldStopDig(free)) {
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
            NUtils.addTask(new NTask() {
                int idleCount = 0;
                @Override
                public boolean check() {
                    if (player.pose().contains("idle")) idleCount++;
                    else idleCount = 0;
                    if (idleCount >= 20) return true;
                    if (WormFarmLogic.shouldRestoreNeeds(NUtils.getStamina(), NUtils.getEnergy())) return true;
                    if (!fillWait && WormFarmLogic.shouldStopDig(freeSoilSlotsSafe(gui))) return true;
                    if (fillWait && countSafe(gui, SOIL) == 0) return true;
                    String err = NUtils.getUI().getLastError();
                    return isNeedSoil(err) || syslogContainsSince(gui, sysBefore, NEED_SOIL_MSG);
                }
            });
            dropJunk(gui);
            if (filling && (count(gui, SOIL) == 0
                    || syslogContainsSince(gui, sysBefore, NEED_SOIL_MSG)
                    || isNeedSoil(NUtils.getUI().getLastError()))) {
                stopDig(gui);
                return Results.SUCCESS();
            }
        }
    }

    private static int countSafe(NGameUI gui, NAlias alias) {
        try {
            return count(gui, alias);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        }
    }

    private static int freeSoilSlotsSafe(NGameUI gui) {
        try {
            return gui.getInventory().getNumberFreeCoord(SOIL_SIZE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        }
    }

    private static boolean isNeedSoil(String err) {
        return err != null && err.toLowerCase().contains(NEED_SOIL_MSG);
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

    private static void snapshotSurface(LandSurvey survey) {
        MCache map = NUtils.getGameUI().map.glob.map;
        for (Coord vc : survey.data.varea) {
            int i = survey.data.varea.ridx(vc);
            int z = (int) Math.round(map.getfz(vc) * survey.data.gran);
            survey.data.wz[i] = survey.data.dz[i] = z;
        }
        survey.data.seq++;
    }

    private static void unlock(LandSurvey survey) {
        survey.wdgmsg("lock", 1);
    }

    private static void sendSurvey(LandSurvey survey) {
        survey.wdgmsg("data", survey.data.encode());
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

    private static Label findWlbl(LandSurvey survey) {
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
            @Override
            public boolean check() {
                return !label.text().equals("...");
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
