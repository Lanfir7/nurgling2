package nurgling.widgets;

import haven.Coord;
import haven.GOut;
import haven.Widget;

/**
 * Overlay widget for database statistics (toggle with F11 when ndbenable is true).
 * Stub implementation — extend with actual DB stats display as needed.
 */
public class DbStatsOverlay extends Widget {

    private static final Coord DEFAULT_SZ = new Coord(280, 120);

    public DbStatsOverlay() {
        super(DEFAULT_SZ);
    }

    @Override
    public void draw(GOut g) {
        // Empty overlay; override to draw DB stats
        super.draw(g);
    }
}
