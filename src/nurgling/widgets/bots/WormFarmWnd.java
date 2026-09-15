package nurgling.widgets.bots;

import haven.Coord;
import haven.Label;
import haven.UI;
import haven.Window;
import nurgling.i18n.L10n;

public class WormFarmWnd extends Window {
    private volatile boolean closed = false;
    private final Label totalLbl;
    private final Label rateLbl;
    private final Label timeLbl;
    private final Label stamLbl;

    public WormFarmWnd() {
        super(UI.scale(new Coord(360, 100)), L10n.get("bot.wormfarm.wnd_title"));
        totalLbl = add(new Label(L10n.get("bot.wormfarm.total", "0")));
        rateLbl = add(new Label(L10n.get("bot.wormfarm.rate", "-")), totalLbl.pos("bl").adds(0, 4));
        timeLbl = add(new Label(L10n.get("bot.wormfarm.time", "-")), rateLbl.pos("bl").adds(0, 4));
        stamLbl = add(new Label(L10n.get("bot.wormfarm.stamina", "-")), timeLbl.pos("bl").adds(0, 4));
        pack();
    }

    public boolean isClosed() {
        return closed;
    }

    public void update(int total, String rate, String time, String stamina) {
        if (closed)
            return;
        totalLbl.settext(L10n.get("bot.wormfarm.total", total));
        rateLbl.settext(L10n.get("bot.wormfarm.rate", rate));
        timeLbl.settext(L10n.get("bot.wormfarm.time", time));
        stamLbl.settext(L10n.get("bot.wormfarm.stamina", stamina));
        pack();
    }

    @Override
    public void wdgmsg(String msg, Object... args) {
        if ("close".equals(msg)) {
            closed = true;
            hide();
        }
        super.wdgmsg(msg, args);
    }
}
