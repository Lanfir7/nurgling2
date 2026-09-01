package nurgling.actions;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectTrackerAnimalMarkerClaimGateTest {
    @Test
    void saveAnimalMarkerPathGatesOnPlayerClaimAtCreateTime() throws Exception {
        String src = Files.readString(Path.of("src/nurgling/actions/ObjectTracker.java"));
        assertTrue(src.contains("ClaimLand.shouldPlaceAnimalMarker"),
                "saveAnimalMarkerToDb must gate via ClaimLand.shouldPlaceAnimalMarker");
        assertTrue(src.contains("ClaimLand.isOnClaimOrVillage"),
                "create-time gate must check player claim/village overlay");
        assertTrue(src.contains("NUtils.player()"),
                "gate must use player position, not the animal tile");
        int gateAt = src.indexOf("shouldPlaceAnimalMarker");
        int addLocalAt = src.indexOf("addAnimalMarkerLocal");
        int insertAt = src.indexOf("animalMarkerService.insert");
        assertTrue(gateAt >= 0 && addLocalAt >= 0 && gateAt < addLocalAt,
                "claim gate must run before addAnimalMarkerLocal");
        assertTrue(gateAt < insertAt,
                "claim gate must run before Postgres insert");
        assertFalse(src.contains("SettingsWindow"),
                "animal marker claim gate must be always-on, no settings checkbox");
    }
}
