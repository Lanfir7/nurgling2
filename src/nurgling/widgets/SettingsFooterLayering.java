package nurgling.widgets;

import haven.Widget;

final class SettingsFooterLayering {
    private static final int MASK_Z = 10;
    private static final int ACTION_Z = 20;

    private SettingsFooterLayering() {
    }

    static void arrange(Widget mask, Widget... actions) {
        mask.z(MASK_Z);
        for(Widget action : actions) {
            if(action != null)
                action.z(ACTION_Z);
        }
    }
}
