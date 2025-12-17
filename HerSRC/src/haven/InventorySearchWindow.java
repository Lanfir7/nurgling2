package haven;

import java.util.Objects;

public class InventorySearchWindow extends Window {
    public static String inventorySearchString = "";
    private final GameUI gui;

    public InventorySearchWindow(GameUI gui) {
        super(UI.scale(140, 35), "Inventory search:", true);
        this.gui = gui;
        inventorySearchString = "";
        TextEntry entry = new TextEntry(UI.scale(150), inventorySearchString) {
            @Override
            protected void changed() {
                setSearchValue(this.buf.line());
            }
        };
        add(entry, UI.scale(0, 10));
        pack();
    }

    public void setSearchValue(String value) {
        inventorySearchString = value;
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if ((sender == this) && (Objects.equals(msg, "close"))) {
            inventorySearchString = "";
            reqdestroy();
            gui.inventorySearchWindow = null;
        } else {
            super.wdgmsg(sender, msg, args);
        }
    }
}
