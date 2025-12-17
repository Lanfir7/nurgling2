package haven.automated;

import haven.*;

import java.util.Objects;

public class GrubGrubBot extends Window implements Runnable {
    public static boolean transferTicks = false;
    private final GameUI gui;
    private boolean stop = false;
    private boolean active = false;

    public GrubGrubBot(GameUI gui) {
        super(UI.scale(UI.scale(254, 96)), "Grub-Grub Bot");
        this.gui = gui;

        add(new Label(""), UI.scale(243, 0)); // ND: Label to fix horizontal size
        add(new Button(UI.scale(160), "Start"){
            @Override
            public void click() {
                active = !active;
                if (active){
                    GrubGrubBot.transferTicks = true;
                    this.change("Stop");
                } else {
                    GrubGrubBot.transferTicks = false;
                    this.change("Start");
                }
            }
        }, UI.scale(32, 10));
        pack();
    }

    @Override
    public void run() {
        try {
            while (!stop) {
                if (!active) {
                    Thread.sleep(200);
                    continue;
                }
                int totalTicks = gui.maininv.getItemsPartial("Tick").size();
                if (totalTicks >= 2 ) {
                    if(gui.makewnd != null && gui.makewnd.makeWidget != null){
                        if (gui.makewnd.cap.equals("Grub-Grub"))
                            gui.makewnd.makeWidget.wdgmsg("make",0);
                        else
                            gui.ui.error("Grub Grub Bot: Crafting Window is not set to craft Grub-Grub!");
                    } else {
                        gui.ui.error("Grub Grub Bot: Couldn't find Grub-Grub Crafting Window!");
                    }
                }
                sleep(200);

                if(gui.prog != null) {
                    sleep(2000);
                }

                for (haven.WItem witem : gui.maininv.getItemsPartial("Grub-Grub")) {
                    witem.item.wdgmsg("transfer", Coord.z);
                }
                sleep(200);

                GrubGrubBot.transferTicks = gui.maininv.getFreeSpace() > 1;

                sleep(1000);
            }
        } catch (InterruptedException ignored) {
        }
    }

    public void stop() {
        stop = true;
        GrubGrubBot.transferTicks = false;
        if (gui.grubGrubThread != null) {
            gui.grubGrubThread.interrupt();
            gui.grubGrubThread = null;
        }
        this.destroy();
    }

    @Override
    public void reqdestroy() {
        Utils.setprefc("wndc-grubGrubBotWindow", this.c);
        super.reqdestroy();
    }

    private void sleep(int duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if((sender == this) && (Objects.equals(msg, "close"))) {
            stop();
            reqdestroy();
            gui.grubGrubBot = null;
        } else {
            super.wdgmsg(sender, msg, args);
        }
    }

}
