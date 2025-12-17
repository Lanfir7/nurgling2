package haven;

import java.util.Objects;

public class ObjectSearchWindow extends Window {
    public static String objectSearchString = "";
    private final GameUI gui;

    public ObjectSearchWindow(GameUI gui) {
        super(UI.scale(250, 55), "Object Search:", true);
        this.gui = gui;
        objectSearchString = "";
        TextEntry entry = new TextEntry(UI.scale(244), objectSearchString) {
            @Override
            protected void changed() {
                setSearchValue(this.buf.line());
                updateOverlays();
            }
        };
        add(entry, UI.scale(0, 8));
        add(new Label("(Start with \"@\" to search for items on Barter Stands)"), UI.scale(0, 36));
        pack();
    }

    public void updateOverlays() {
        if (ui != null && ui.gui != null) {
            ui.sess.glob.oc.gobAction(Gob::setGobSearchOverlay);
        }
    }

    public void setSearchValue(String value) {
        objectSearchString = value;
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if ((sender == this) && (Objects.equals(msg, "close"))) {
            objectSearchString = "";
            updateOverlays();
            reqdestroy();
            gui.objectSearchWindow = null;
        } else {
            super.wdgmsg(sender, msg, args);
        }
    }
}
