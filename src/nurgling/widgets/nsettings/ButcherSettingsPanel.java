package nurgling.widgets.nsettings;

import haven.CheckBox;
import haven.Coord;
import haven.UI;
import nurgling.NConfig;
import nurgling.actions.bots.ButcherKnifePolicy;
import nurgling.i18n.L10n;

public class ButcherSettingsPanel extends Panel {
    private CheckBox useKnife;
    private CheckBox alwaysKnife;

    public ButcherSettingsPanel() {
        super(L10n.get("butcher.settings.title"));
        int margin = UI.scale(10);
        int y = UI.scale(36);

        useKnife = new CheckBox(L10n.get("butcher.settings.use_knife")) {
            @Override
            public void set(boolean val) {
                a = val;
                if (!val && alwaysKnife != null)
                    alwaysKnife.a = false;
            }
        };
        add(useKnife, new Coord(margin, y));
        y += UI.scale(28);

        alwaysKnife = new CheckBox(L10n.get("butcher.settings.always_knife")) {
            @Override
            public void set(boolean val) {
                a = val;
                if (val)
                    useKnife.a = true;
            }
        };
        add(alwaysKnife, new Coord(margin, y));
    }

    @Override
    public void load() {
        useKnife.a = ButcherKnifePolicy.useKnifeEnabled(NConfig.get(NConfig.Key.butcherUseKnife));
        alwaysKnife.a = ButcherKnifePolicy.alwaysKnifeEnabled(NConfig.get(NConfig.Key.butcherKnifeAlways));
        if (alwaysKnife.a)
            useKnife.a = true;
    }

    @Override
    public void save() {
        boolean always = alwaysKnife.a;
        NConfig.set(NConfig.Key.butcherUseKnife, useKnife.a || always);
        NConfig.set(NConfig.Key.butcherKnifeAlways, always);
        NConfig.needUpdate();
    }
}
