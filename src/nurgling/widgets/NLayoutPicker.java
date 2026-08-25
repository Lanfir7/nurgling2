package nurgling.widgets;

import haven.*;
import haven.Window;
import nurgling.NConfig;
import nurgling.NUtils;
import nurgling.conf.NDefaultLayout;
import nurgling.i18n.L10n;

import java.awt.Color;

/**
 * One-time layout picker shown on a fresh install.
 *
 * Replaces the old behaviour of dropping a first-time player straight into HUD
 * edit mode with twenty labelled widgets to arrange. Choosing between three
 * schematic previews is a single decision a newcomer can actually make, and it
 * costs one click to skip.
 */
public class NLayoutPicker extends Window {
    private static final Coord psz = UI.scale(196, 110);
    private static final int gap = UI.scale(10);
    private static final Text.Foundry descf =
        new Text.Foundry(Text.sans, 10, new Color(190, 190, 190)).aa(true);

    public NLayoutPicker() {
        super(Coord.z, L10n.get("layout.picker.title"));
        Widget prev = add(new Label(L10n.get("layout.picker.intro")), Coord.z);
        int y = prev.pos("bl").adds(0, gap).y;
        int x = 0;
        for (NDefaultLayout.Preset preset : NDefaultLayout.Preset.values()) {
            Widget pv = add(new PresetPreview(psz, preset), new Coord(x, y));
            Widget cap = add(new Label(preset.label()), pv.pos("bl").adds(0, UI.scale(3)));
            add(new Label(preset.desc(), descf), cap.pos("bl").adds(0, UI.scale(2)));
            add(new Button(psz.x, L10n.get("layout.picker.apply")) {
                @Override
                public void click() {
                    super.click();
                    apply(preset);
                }
            }, new Coord(x, cap.pos("bl").adds(0, UI.scale(22)).y));
            x += psz.x + gap;
        }
        pack();
        add(new Button(UI.scale(160), L10n.get("layout.picker.keep")) {
            @Override
            public void click() {
                super.click();
                dismiss();
            }
        }, new Coord((sz.x - UI.scale(160)) / 2, sz.y + gap));
        pack();
    }

    private void apply(NDefaultLayout.Preset preset) {
        NConfig.set(NConfig.Key.layoutPreset, preset.name());
        if (NUtils.getGameUI() != null)
            NDraggableWidget.resetLayout(NUtils.getGameUI());
        dismiss();
    }

    private void dismiss() {
        NConfig.set(NConfig.Key.layoutPresetChosen, true);
        if (NUtils.getUI() != null && NUtils.getUI().core.config.isUpdated())
            NUtils.getUI().core.config.write();
        destroy();
    }

    @Override
    public void wdgmsg(String msg, Object... args) {
        if (msg.equals("close"))
            dismiss();
        else
            super.wdgmsg(msg, args);
    }

    /**
     * Schematic thumbnail of a preset: every visible widget drawn as a block on
     * a miniature screen. Cheap to maintain (no screenshots to re-shoot when the
     * layout changes) and it reads at a glance, which is all it needs to do.
     */
    private static class PresetPreview extends Widget {
        private final NDefaultLayout.Preset preset;

        PresetPreview(Coord sz, NDefaultLayout.Preset preset) {
            super(sz);
            this.preset = preset;
        }

        @Override
        public void draw(GOut g) {
            g.chcolor(24, 28, 32, 255);
            g.frect(Coord.z, sz);
            int belts = 3;
            Object nb = NConfig.get(NConfig.Key.numbelts);
            if (nb instanceof Integer)
                belts = (Integer) nb;
            for (NDefaultLayout.Preview p : NDefaultLayout.preview(preset, sz, belts)) {
                g.chcolor(96, 148, 108, 190);
                g.frect(p.c, p.sz);
                g.chcolor(188, 228, 196, 255);
                g.rect(p.c, p.sz);
            }
            g.chcolor(140, 150, 130, 255);
            g.rect(Coord.z, sz);
            g.chcolor();
        }
    }
}
