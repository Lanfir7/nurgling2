package nurgling.tools;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimAreaHashTest {
    @Test
    void tileHashMatchesPreviousObjectsHashForLongAndNegativeCoordinates() {
        for(ClaimArea.Tile tile : Arrays.asList(
                new ClaimArea.Tile(0L, 0, 0),
                new ClaimArea.Tile(Long.MIN_VALUE, -1, Integer.MAX_VALUE),
                new ClaimArea.Tile(Long.MAX_VALUE, Integer.MIN_VALUE, -17))) {
            assertEquals(Objects.hash(tile.gridId, tile.x, tile.y), tile.hashCode());
        }
    }

    @Test
    void areaHashMatchesPreviousValueAndStaysStableAfterSourceMutation() {
        ClaimArea.Tile anchor = new ClaimArea.Tile(42L, 7, 9);
        ClaimArea.Tile other = new ClaimArea.Tile(-42L, -3, 11);
        List<ClaimArea.Tile> source = new ArrayList<>(Arrays.asList(other, null, anchor, other));
        ClaimArea area = new ClaimArea(anchor, source);
        int previousHash = Objects.hash(area.anchor, area.tiles());

        source.clear();
        source.add(new ClaimArea.Tile(500L, 1, 1));
        assertEquals(previousHash, area.hashCode());
        assertThrows(UnsupportedOperationException.class, () -> area.tiles().add(other));

        ClaimArea equal = new ClaimArea(new ClaimArea.Tile(42L, 7, 9),
                new LinkedHashSet<>(Arrays.asList(anchor, other, null)));
        assertEquals(area, equal);
        assertEquals(area.hashCode(), equal.hashCode());
        assertTrue(new HashSet<>(Arrays.asList(area)).contains(equal));
    }
}
