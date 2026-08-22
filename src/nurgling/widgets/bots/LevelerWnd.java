package nurgling.widgets.bots;

import haven.Coord;
import haven.Label;
import haven.UI;
import haven.Window;
import nurgling.i18n.L10n;

public class LevelerWnd extends Window {
    private volatile boolean closed = false;
    private final Label speedLbl;
    private final Label remainLbl;
    private final Label etaLbl;

    public LevelerWnd() {
        super(UI.scale(new Coord(280, 80)), L10n.get("bot.leveler.wnd_title"));
        speedLbl = add(new Label(L10n.get("bot.leveler.speed", "-")));
        remainLbl = add(new Label(L10n.get("bot.leveler.remaining", "-")), speedLbl.pos("bl").adds(0, 4));
        etaLbl = add(new Label(L10n.get("bot.leveler.eta", "-")), remainLbl.pos("bl").adds(0, 4));
        pack();
    }

    public boolean isClosed() {
        return closed;
    }

    public void update(String speed, String remaining, String eta) {
        if (closed)
            return;
        speedLbl.settext(L10n.get("bot.leveler.speed", speed));
        remainLbl.settext(L10n.get("bot.leveler.remaining", remaining));
        etaLbl.settext(L10n.get("bot.leveler.eta", eta));
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
