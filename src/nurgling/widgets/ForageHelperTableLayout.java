package nurgling.widgets;

import haven.UI;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Resource-free table geometry shared by rendering, hit testing, and tests. */
final class ForageHelperTableLayout {
    private static final int[] COLUMNS = {
            32, 190, 245, 300, 355, 410, 465, 520, 580
    };
    private static final List<String> HEADERS = Collections.unmodifiableList(Arrays.asList(
            "Item", "First", "Base", "All", "Spring", "Summer", "Autumn", "Winter", "Terrain"));

    private ForageHelperTableLayout() {
    }

    static List<String> columnHeaders() {
        return HEADERS;
    }

    static int columnCount() {
        return COLUMNS.length;
    }

    static int columnX(int column) {
        return UI.scale(COLUMNS[column]);
    }

    static int terrainColumnStart() {
        return columnX(8);
    }

    static boolean isTerrainColumn(int x, int rowWidth) {
        return x >= terrainColumnStart() && x < rowWidth;
    }
}
