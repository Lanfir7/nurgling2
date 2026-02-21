package nurgling.widgets.bots;

import haven.Button;
import haven.Coord;
import haven.Label;
import haven.TextEntry;
import haven.UI;
import haven.Utils;
import haven.Widget;
import haven.Window;
import nurgling.NGameUI;
import nurgling.actions.bots.QuickBarrageBotRunner;

import java.awt.Color;

public class QuickBarrageBotWnd extends Window {
    private static final String PREF_THRESHOLD = "quickbarrage-threshold";

    private final NGameUI gui;
    private final TextEntry thresholdEntry;
    private final Button startStopButton;
    private volatile boolean isRunning = false;
    private volatile int threshold;
    private Thread botThread = null;
    private QuickBarrageBotRunner botRunner = null;

    public QuickBarrageBotWnd(NGameUI gui) {
        super(UI.scale(250, 100), "Quick Barrage Bot");
        this.gui = gui;
        this.threshold = Utils.getprefi(PREF_THRESHOLD, 90);

        Coord pad = UI.scale(8, 6);
        Coord cur = pad;

        add(new Label("Cornered threshold (50-100):"), cur);
        cur = cur.add(0, UI.scale(20));

        thresholdEntry = add(new TextEntry(UI.scale(80), String.valueOf(threshold)) {
            @Override
            protected void changed() {
                try {
                    int value = Integer.parseInt(this.buf.line());
                    if (value >= 50 && value <= 100) {
                        threshold = value;
                        Utils.setprefi(PREF_THRESHOLD, value);
                    }
                } catch (NumberFormatException e) {
                }
            }
        }, cur);
        cur = thresholdEntry.pos("bl").add(0, UI.scale(8));

        startStopButton = add(new Button(UI.scale(100), "Stop") {
            @Override
            public void click() {
                if (isRunning) {
                    stopBot();
                } else {
                    startBot();
                }
            }
        }, cur);

        pack();
    }

    public void startBot() {
        try {
            int value = Integer.parseInt(thresholdEntry.buf.line());
            if (value >= 50 && value <= 100) {
                threshold = value;
                Utils.setprefi(PREF_THRESHOLD, value);
            } else {
                gui.msg("Threshold must be between 50 and 100", Color.RED);
                return;
            }
        } catch (NumberFormatException e) {
            gui.msg("Invalid threshold value", Color.RED);
            return;
        }

        isRunning = true;
        startStopButton.change("Stop");
        botRunner = new QuickBarrageBotRunner(gui, threshold, this);
        botThread = new Thread(botRunner, "QuickBarrageBot");
        botThread.start();
        gui.biw.addObserve(botThread);
    }

    public void stopBot() {
        isRunning = false;
        startStopButton.change("Start");
        if (botRunner != null) {
            botRunner.stop();
        }
        if (botThread != null && botThread.isAlive()) {
            botThread.interrupt();
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    public int getThreshold() {
        return threshold;
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if ((sender == this) && (msg.equals("close"))) {
            if (isRunning) {
                stopBot();
            }
            reqdestroy();
        } else {
            super.wdgmsg(sender, msg, args);
        }
    }

    public void setRunning(boolean running) {
        isRunning = running;
        if (startStopButton != null) {
            startStopButton.change(running ? "Stop" : "Start");
        }
    }
}
