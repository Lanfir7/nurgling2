package nurgling;

import haven.Coord;
import haven.GOut;
import haven.Loading;
import haven.Resource;
import haven.Tex;
import haven.TexI;
import haven.Text;
import haven.UI;
import nurgling.i18n.L10n;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Transient, click-through presentation for positive item gains. Owned by one GameUI session. */
public final class LootNoticeOverlay {
    private static final double LIFETIME = 3.65;
    private static final double MERGE_WINDOW = 3.15;
    private static final int MAX_ROWS = 4;
    private static final int WIDTH = UI.scale(300);
    private static final int HEIGHT = UI.scale(57);
    private static final int GAP = UI.scale(7);
    private static final Text.Foundry LABEL_FONT = new Text.Foundry(new Font("SansSerif", Font.BOLD, 10));
    private static final Text.Foundry NAME_FONT = new Text.Foundry(new Font("SansSerif", Font.BOLD, 16));
    private static final Text.Foundry COUNT_FONT = new Text.Foundry(new Font("SansSerif", Font.BOLD, 18));

    private static final class Row {
        final String resource;
        final Resource icon;
        final Tex name;
        Tex count;
        int amount;
        double age;
        double entranceAge;
        double visualSlot;

        Row(String resource, String displayName, Resource icon, int amount, int slot) {
            this.resource = resource;
            this.icon = icon;
            this.visualSlot = slot;
            this.name = NAME_FONT.render(displayName, new Color(248, 241, 220)).tex();
            setAmount(amount);
        }

        void setAmount(int amount) {
            if (count != null)
                count.dispose();
            this.amount = amount;
            count = COUNT_FONT.render("+" + amount, new Color(236, 197, 118)).tex();
        }

        void dispose() {
            name.dispose();
            count.dispose();
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final Tex panel = new TexI(panelImage(WIDTH, HEIGHT));
    private Tex label;
    private String labelText;

    public static BufferedImage panelImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int radius = Math.max(12, height / 4);
        RoundRectangle2D.Float shape = new RoundRectangle2D.Float(1, 1, width - 2, height - 2, radius, radius);
        g.setPaint(new GradientPaint(0, 0, new Color(38, 36, 34, 236), 0, height, new Color(12, 17, 21, 228)));
        g.fill(shape);
        g.setStroke(new BasicStroke(Math.max(1f, height / 57f)));
        g.setColor(new Color(224, 184, 111, 176));
        g.draw(shape);
        g.setColor(new Color(255, 237, 178, 42));
        g.drawLine(radius, 3, width - radius, 3);
        g.setPaint(new GradientPaint(0, 0, new Color(239, 199, 124, 245), 0, height, new Color(133, 86, 43, 170)));
        g.fillRoundRect(2, height / 5, Math.max(2, width / 110), height * 3 / 5, 3, 3);
        g.dispose();
        return image;
    }

    public void add(String resource, String name, Resource icon, int amount) {
        if (amount <= 0 || name == null || name.isBlank())
            return;
        for (Row row : rows) {
            if (row.resource.equals(resource) && row.age < MERGE_WINDOW) {
                row.setAmount(row.amount + amount);
                row.age = 0;
                return;
            }
        }
        if (rows.size() == MAX_ROWS)
            rows.remove(0).dispose();
        rows.add(new Row(resource, name, icon, amount, rows.size()));
    }

    public void tick(double dt) {
        for (Iterator<Row> it = rows.iterator(); it.hasNext();) {
            Row row = it.next();
            row.age += Math.max(0, dt);
            row.entranceAge += Math.max(0, dt);
            if (row.age >= LIFETIME) {
                row.dispose();
                it.remove();
            }
        }
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            row.visualSlot += (i - row.visualSlot) * Math.min(1, dt * 11);
        }
    }

    public void clear() {
        for (Row row : rows)
            row.dispose();
        rows.clear();
    }

    int visibleCount() {
        return rows.size();
    }

    int amountFor(String resource) {
        for (Row row : rows) {
            if (row.resource.equals(resource))
                return row.amount;
        }
        return 0;
    }

    double entranceAgeFor(String resource) {
        for (Row row : rows) {
            if (row.resource.equals(resource))
                return row.entranceAge;
        }
        return -1;
    }

    public void dispose() {
        clear();
        panel.dispose();
        if (label != null)
            label.dispose();
    }

    private Tex label() {
        String text = L10n.get("loot_notice.obtained");
        if (!text.equals(labelText)) {
            if (label != null)
                label.dispose();
            labelText = text;
            label = LABEL_FONT.render(text, new Color(201, 176, 131)).tex();
        }
        return label;
    }

    public void draw(GOut g, Coord screen, LootNoticeLayout layout, Integer compassBottom) {
        if (rows.isEmpty())
            return;
        int shown = LootNoticeLayout.visibleRows(screen.y, HEIGHT, GAP, rows.size());
        if (shown == 0)
            return;
        int first = rows.size() - shown;
        Coord origin = layout.origin(screen, new Coord(WIDTH, HEIGHT), GAP, shown, compassBottom,
                UI.scale(12), UI.scale(110));
        int x = origin.x;
        for (int i = first; i < rows.size(); i++) {
            Row row = rows.get(i);
            double entering = Math.min(1, row.entranceAge / 0.24);
            double leaving = Math.min(1, (LIFETIME - row.age) / 0.58);
            int alpha = (int)(255 * Math.max(0, Math.min(entering, leaving)));
            int y = origin.y + (int)((row.visualSlot - first) * (HEIGHT + GAP))
                    + (int)(UI.scale(10) * (1 - entering));
            y = Math.max(0, Math.min(Math.max(0, screen.y - HEIGHT), y));
            g.chcolor(255, 255, 255, alpha * layout.backgroundOpacity / 100);
            g.image(panel, new Coord(x, y));
            g.chcolor(255, 255, 255, alpha);
            Coord iconPos = new Coord(x + UI.scale(15), y + UI.scale(12));
            int iconSize = UI.scale(33);
            boolean iconDrawn = false;
            try {
                Tex icon = row.icon == null ? null : row.icon.layer(Resource.imgc).tex();
                if (icon != null) {
                    g.image(icon, iconPos, new Coord(iconSize, iconSize));
                    iconDrawn = true;
                }
            } catch (Loading | NullPointerException ignored) {
                // A resource can still be loading when its inventory widget first appears.
            }
            if (!iconDrawn)
                drawFallbackIcon(g, iconPos, iconSize, alpha, i);
            g.chcolor(255, 255, 255, alpha);
            int textX = x + UI.scale(61);
            g.image(label(), new Coord(textX, y + UI.scale(8)));
            Coord namePos = new Coord(textX, y + UI.scale(25));
            Coord nameArea = new Coord(WIDTH - UI.scale(129), HEIGHT - UI.scale(26));
            g.reclip(namePos, nameArea).image(row.name, Coord.z);
            g.image(row.count, new Coord(x + WIDTH - UI.scale(16) - row.count.sz().x, y + UI.scale(25)));
            g.chcolor();
        }
    }

    private static void drawFallbackIcon(GOut g, Coord pos, int size, int alpha, int variant) {
        Coord center = pos.add(size / 2, size / 2);
        g.chcolor(9, 16, 20, alpha);
        g.fellipse(center, new Coord(size / 2, size / 2));
        if (variant % 2 == 0)
            g.chcolor(216, 181, 102, alpha);
        else
            g.chcolor(139, 176, 180, alpha);
        g.fellipse(center, new Coord(size / 3, size / 3));
        g.chcolor(255, 248, 210, alpha / 2);
        g.line(center.add(-size / 4, -size / 4), center.add(size / 4, size / 4), Math.max(1, size / 14));
    }
}
