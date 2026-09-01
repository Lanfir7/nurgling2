package nurgling.tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimLandTest {
    @Test
    void acceptsPersonalClaimAndVillageTags() {
        assertTrue(ClaimLand.isClaimOrVillageTag("cplot"));
        assertTrue(ClaimLand.isClaimOrVillageTag("vlg"));
        assertTrue(ClaimLand.hasClaimOrVillage(List.of("foo", "vlg")));
        assertTrue(ClaimLand.hasClaimOrVillage(List.of("cplot")));
    }

    @Test
    void rejectsWildernessAndRealm() {
        assertFalse(ClaimLand.isClaimOrVillageTag("prov"));
        assertFalse(ClaimLand.isClaimOrVillageTag("realm"));
        assertFalse(ClaimLand.hasClaimOrVillage(List.of("prov", "realm")));
        assertFalse(ClaimLand.hasClaimOrVillage(List.of()));
        assertFalse(ClaimLand.hasClaimOrVillage(null));
    }

    @Test
    void mutesIconNotifySoundOnClaimAndAllowsWilderness() {
        assertFalse(ClaimLand.shouldPlayIconNotify(true));
        assertTrue(ClaimLand.shouldPlayIconNotify(false));
    }

    @Test
    void skipsAnimalMarkerOnClaimAndPlacesInWilderness() {
        assertFalse(ClaimLand.shouldPlaceAnimalMarker(true));
        assertTrue(ClaimLand.shouldPlaceAnimalMarker(false));
    }
}
