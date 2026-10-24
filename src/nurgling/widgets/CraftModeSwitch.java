package nurgling.widgets;

import haven.*;
import nurgling.i18n.L10n;
import nurgling.widgets.cookbook.CookbookTheme;

/** Explicit Manual / Autocraft choice using the ordinary checkbox state contract. */
public class CraftModeSwitch extends ACheckBox {
    private final Tex[] lit = new Tex[2], dim = new Tex[2];

    public CraftModeSwitch() {
        String[] labels = {L10n.get("craft.mode_manual"), L10n.get("craft.mode_auto")};
        int width = 0;
        for (int i = 0; i < labels.length; i++) {
            lit[i] = CookbookTheme.render(CookbookTheme.bold, labels[i], CookbookTheme.ink);
            dim[i] = CookbookTheme.render(CookbookTheme.bold, labels[i], CookbookTheme.fg);
            width = Math.max(width, lit[i].sz().x);
        }
        resize(Coord.of(2 * (width + UI.scale(10)), UI.scale(22)));
        settip(L10n.get("craft.mode_tip"));
    }

    @Override public void draw(GOut g) {
        int half = sz.x / 2;
        for (int i = 0; i < 2; i++) {
            boolean selected = state() == (i == 1);
            Coord ul = Coord.of(i * half, 0), box = Coord.of(i == 0 ? half : sz.x - half, sz.y);
            if (selected) CookbookTheme.fill(g, ul, box, CookbookTheme.accent);
            Tex label = selected ? lit[i] : dim[i];
            g.image(label, ul.add((box.x - label.sz().x) / 2, (box.y - label.sz().y) / 2));
        }
        CookbookTheme.frame(g, Coord.z, sz, CookbookTheme.outline);
        CookbookTheme.fill(g, Coord.of(half, 0), Coord.of(Math.max(1, UI.scale(1)), sz.y), CookbookTheme.outline);
    }

    @Override public boolean mousedown(MouseDownEvent ev) {
        if (ev.b != 1 || !ev.c.isect(Coord.z, sz)) return false;
        set(ev.c.x >= sz.x / 2);
        return true;
    }

    @Override public void destroy() {
        for (Tex label : lit) label.dispose();
        for (Tex label : dim) label.dispose();
        super.destroy();
    }
}
