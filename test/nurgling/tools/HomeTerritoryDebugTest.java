package nurgling.tools;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeTerritoryDebugTest {
    @Test
    void describesCurrentVillageClaimAndCombinedHomeStatusFromVisibleSettings() {
        ClaimArea claimArea = new ClaimArea(new ClaimArea.Tile(7L, 10, 20), Arrays.asList(
                new ClaimArea.Tile(7L, 10, 20),
                new ClaimArea.Tile(7L, 11, 20)));
        HomeTerritories.Entry village = new HomeTerritories.Entry(
                HomeTerritories.Type.VILLAGE, "Oakvale");
        HomeTerritories.Entry claim = new HomeTerritories.Entry(
                HomeTerritories.Type.CLAIM, "Lanfir", claimArea);

        HomeTerritoryDebug.Snapshot snapshot = HomeTerritoryDebug.inspect(
                Collections.singletonList(claim), Arrays.asList(village, claim), claimArea, false);

        assertEquals("Oakvale", snapshot.village);
        assertEquals("Lanfir's Claim", snapshot.claim);
        assertFalse(snapshot.villageHome);
        assertTrue(snapshot.claimHome);
        assertTrue(snapshot.home);
        assertFalse(snapshot.loading);
        assertEquals(2, snapshot.claimTiles);
    }
}
