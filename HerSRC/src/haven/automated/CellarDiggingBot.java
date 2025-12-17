package haven.automated;

import haven.*;
import haven.Button;
import haven.Window;

import java.util.Objects;

import static haven.OCache.posres;
import static java.lang.Thread.sleep;

public class CellarDiggingBot extends Window implements Runnable {
    private final GameUI gui;
    private boolean stop;
    private boolean active;
    private final Button activeButton;

    public CellarDiggingBot(GameUI gui) {
        super(UI.scale(150, 70), "Cellar Digging Bot");
        this.gui = gui;
        this.stop = false;
        this.active = false;

        activeButton = new Button(UI.scale(150), "Start") {
            @Override
            public void click() {
                active = !active;
                if (active) {
                    this.change("Stop");
                } else {
                    haltActions();
                    this.change("Start");
                }
            }
        };
        add(activeButton, UI.scale(0, 10));
        pack();
    }

    @Override
    public void run() {
        try {
            while (!stop) {
                if (!checkVitals()) { sleep(200); continue; }
                if (active) {
                    if (gui.getmeter("stam", 0).a < 0.40) {
                        try { AUtils.drinkTillFull(gui, 0.99, 0.99); } catch (InterruptedException ignored) {}
                        sleep(200);
                        continue;
                    }

                    Gob cellar = findCellarGob();
                    if (cellar == null) {
                        gui.error("Cellar Digging Bot: No cellar door present! Stopping.");
                        active = false;
                        activeButton.change("Start");
                        continue;
                    }

                    chipAllBoulders();
                    if (!active || stop) continue;

                    tryEnterCellar(cellar);
                    while (isMiningOrRunning()) {
                        if (!checkVitals()) break;
                        sleep(100);
                    }
                }
                sleep(200);
            }
        } catch (InterruptedException e) {
        }
    }

    private boolean checkVitals() {
        try {
            double hp = gui.getmeters("hp").get(1).a;
            if (hp < 0.02) {
                System.out.println("HP IS " + hp + " .. PORTING HOME!");
                gui.act("travel", "hearth");
                try { Thread.sleep(8000); } catch (InterruptedException ignored) {}
                active = false;
                activeButton.change("Start");
                return false;
            }
            double nrj = ui.gui.getmeter("nrj", 0).a;
            if (nrj < 0.25) {
                gui.error("Cellar Digging Bot: Low on energy! Stopping.");
                active = false;
                activeButton.change("Start");
                return false;
            }
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    private Gob findCellarGob() {
        Gob found = null;
        synchronized (gui.map.glob.oc) {
            for (Gob g : gui.map.glob.oc) {
                try {
                    Resource r = g.getres();
                    if (r == null) continue;
                    if ("gfx/terobjs/arch/cellardoor".equals(r.name)) {
                        found = g;
                        break;
                    }
                } catch (Loading | NullPointerException ignored) {}
            }
        }
        return found;
    }

    private void tryEnterCellar(Gob cell) throws InterruptedException {
        gui.map.pfLeftClick(cell.rc.floor().add(12, 0), null);
        if (!AUtils.waitPf(gui)) AUtils.unstuck(gui);
        clearhand();
        AUtils.rightClickGobAndSelectOption(gui, cell, 0);
        gui.map.wdgmsg("click", Coord.z, cell.rc.floor(posres), 3, 0, 0, (int) cell.id, cell.rc.floor(posres), 0, -1);
        Coord playerCoord = ui.gui.map.player().rc.floor(posres);
        ui.gui.map.wdgmsg("click", Coord.z, playerCoord, 3, 0);
    }

    private void chipAllBoulders() throws InterruptedException {
        while (active && !stop) {
            if (!checkVitals()) return;
            Gob g = closestBumling();
            if (g == null) return;
            chipBoulderOnce(g);
            if (!active || stop) return;
            sleep(150);
        }
    }

    private void chipBoulderOnce(Gob bumling) throws InterruptedException {
        if (!bumlingExists(bumling) || !active || stop) return;

        if (bumling.rc.dist(gui.map.player().rc) > 11 * 5) {
            gui.map.pfLeftClick(bumling.rc.floor().add(10, 0), null);
            if (!AUtils.waitPf(gui)) AUtils.unstuck(gui);
        }
        if (!bumlingExists(bumling) || !active || stop) return;

        clearhand();
        AUtils.rightClickGobAndSelectOption(gui, bumling, 0);
        gui.map.wdgmsg("click", Coord.z, bumling.rc.floor(posres), 3, 0, 0,
                (int) bumling.id, bumling.rc.floor(posres), 0, -1);

        int idleTicks = 0;
        while (active && !stop && bumlingExists(bumling)) {
            if (!checkVitals()) break;
            if (gui.getmeter("stam", 0).a < 0.40) {
                try { AUtils.drinkTillFull(gui, 0.99, 0.99); } catch (InterruptedException ignored) {}
            }
            if (isMiningOrRunning()) {
                idleTicks = 0;
            } else {
                idleTicks++;
                if (idleTicks >= 3) break;
            }
            sleep(100);
        }
    }

    private Gob closestBumling() {
        Gob best = null;
        Coord2d me = gui.map.player().rc;
        synchronized (gui.map.glob.oc) {
            for (Gob g : gui.map.glob.oc) {
                try {
                    Resource r = g.getres();
                    if (r == null || !r.name.contains("/bumlings/")) continue;
                    if (best == null || g.rc.dist(me) < best.rc.dist(me)) best = g;
                } catch (Loading | NullPointerException ignored) {}
            }
        }
        return best;
    }

    private boolean isMiningOrRunning() {
        try {
            boolean miningPose = gui.map.player().getPoses().contains("pickan");
            boolean pathing = (ui.gui.map.pfthread != null && ui.gui.map.pfthread.isAlive());
            boolean workingBar = (gui.prog != null && gui.prog.prog != -1);
            return miningPose || pathing || workingBar;
        } catch (Exception ignored) {}
        return false;
    }

    private boolean bumlingExists(Gob b) {
        if (b == null) return false;
        synchronized (gui.map.glob.oc) {
            for (Gob g : gui.map.glob.oc)
                if (g.id == b.id) return true;
        }
        return false;
    }

    private void clearhand() {
        if (gui.vhand != null) gui.vhand.item.wdgmsg("drop", Coord.z);
        AUtils.rightClick(gui);
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if ((sender == this) && (Objects.equals(msg, "close"))) {
            stop = true;
            stop();
            reqdestroy();
            gui.cellarDiggingBot = null;
            gui.cellarDiggingThread = null;
        } else {
            super.wdgmsg(sender, msg, args);
        }
    }

    public void stop() {
        haltActions();
        this.destroy();
    }

    private void haltActions() {
        ui.gui.map.wdgmsg("click", Coord.z, ui.gui.map.player().rc.floor(posres), 1, 0);
        if (ui.gui.map.pfthread != null) ui.gui.map.pfthread.interrupt();
    }

    @Override
    public void reqdestroy() {
        Utils.setprefc("wndc-cellarDiggingBotWindow", this.c);
        super.reqdestroy();
    }
}