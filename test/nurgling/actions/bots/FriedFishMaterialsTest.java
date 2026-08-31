package nurgling.actions.bots;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import haven.Coord2d;
import nurgling.tools.NAlias;
import nurgling.tools.VSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FriedFishMaterialsTest {
    @Test
    void emptyInputUsesInventoryFish() {
        assertTrue(FriedFishMaterials.fromInventory(false));
        assertFalse(FriedFishMaterials.fromInventory(true));
    }

    @Test
    void cookedFishStaysInInventoryWhenOutputIsEmpty() {
        assertFalse(FriedFishMaterials.toContainers(false));
        assertTrue(FriedFishMaterials.toContainers(true));
    }

    @Test
    void closestFireplaceIsTheOneNearestThePlayer() {
        Coord2d player = new Coord2d(0, 0);
        Coord2d far = new Coord2d(100, 0);
        Coord2d near = new Coord2d(11, 0);
        Coord2d picked = FriedFishMaterials.closestSpot(player, Arrays.asList(far, near));
        assertEquals(11.0, picked.x, 0.01);
        assertEquals(0.0, picked.y, 0.01);
        assertNull(FriedFishMaterials.closestSpot(player, Collections.emptyList()));
        assertNull(FriedFishMaterials.closestSpot(null, Arrays.asList(near)));
    }

    @Test
    void roastspitFireplaceMustHaveSpitAndNotBeBlocked() {
        assertTrue(FriedFishMaterials.isUsableRoastspitPow(0, true));
        assertTrue(FriedFishMaterials.isUsableRoastspitPow(5, true));
        assertFalse(FriedFishMaterials.isUsableRoastspitPow(0, false));
        assertFalse(FriedFishMaterials.isUsableRoastspitPow(16, true));
        assertFalse(FriedFishMaterials.isUsableRoastspitPow(32, true));
        assertFalse(FriedFishMaterials.isUsableRoastspitPow(48, true));
    }

    @Test
    void spitIsBusyOnlyWhileRawFishIsCookingOnALitFire() {
        assertTrue(FriedFishMaterials.isSpitReadyToWork(null, 5));
        assertTrue(FriedFishMaterials.isSpitReadyToWork("gfx/invobjs/meat-herring", 5));
        assertFalse(FriedFishMaterials.isSpitReadyToWork("gfx/invobjs/meat-herring-raw", 5));
        assertTrue(FriedFishMaterials.isSpitReadyToWork("gfx/invobjs/meat-herring-raw", 0));
        assertFalse(FriedFishMaterials.isSpitReadyToWork("gfx/invobjs/rabbit-clean", 5));
        assertFalse(FriedFishMaterials.isSpitReadyToWork("gfx/invobjs/chicken-cleaned", 5));
        assertTrue(FriedFishMaterials.isSpitReadyToWork("gfx/invobjs/rabbit-clean", 0));
    }

    @Test
    void uncookedSpitContentIncludesFishRawAndCleanCarcassOverlays() {
        assertTrue(FriedFishMaterials.isUncookedSpitContent("gfx/invobjs/meat-herring-raw"));
        assertTrue(FriedFishMaterials.isUncookedSpitContent("gfx/invobjs/rabbit-clean"));
        assertTrue(FriedFishMaterials.isUncookedSpitContent("gfx/invobjs/chicken-cleaned"));
        assertTrue(FriedFishMaterials.isUncookedSpitContent("gfx/invobjs/adder-clean"));
        assertFalse(FriedFishMaterials.isUncookedSpitContent("gfx/invobjs/meat-herring"));
        assertFalse(FriedFishMaterials.isUncookedSpitContent(null));
    }

    @Test
    void keepsWorkingUntilFishAndSpitAreEmpty() {
        assertTrue(FriedFishMaterials.shouldKeepWorking(true, false, true, false));
        assertTrue(FriedFishMaterials.shouldKeepWorking(false, true, false, false));
        assertTrue(FriedFishMaterials.shouldKeepWorking(true, false, false, true));
        assertFalse(FriedFishMaterials.shouldKeepWorking(true, false, false, false));
        assertFalse(FriedFishMaterials.shouldKeepWorking(false, false, true, false));
    }

    @Test
    void cookedSpitroastIsNotRawInventoryFish() {
        assertTrue(FriedFishMaterials.isCookedSpitroast("Spitroast Herring"));
        assertFalse(FriedFishMaterials.isCookedSpitroast("Herring"));
        assertFalse(FriedFishMaterials.isCookedSpitroast(null));
    }

    @Test
    void roastableRawIncludesFishAndBothCleanCarcassCategories() {
        NAlias raw = FriedFishMaterials.roastableRaw();
        assertTrue(raw.matches("Herring"));
        assertTrue(raw.matches("Pike"));
        assertTrue(raw.matches("Clean Rabbit Carcass"));
        assertTrue(raw.matches("Cleaned Chicken"));
        for (String name : VSpec.getCategoryContent("Clean Animal Carcass")) {
            assertTrue(raw.matches(name), name);
        }
        for (String name : VSpec.getCategoryContent("Clean Bird Carcass")) {
            assertTrue(raw.matches(name), name);
        }
    }

    @Test
    void roastableRawExcludesCookedSpitroastFishByproductsAndUncleaned() {
        NAlias raw = FriedFishMaterials.roastableRaw();
        assertFalse(raw.matches("Spitroast Herring"));
        assertFalse(raw.matches("Spitroast Rabbit"));
        assertFalse(raw.matches("Filet of Herring"));
        assertFalse(raw.matches("Herring Roe"));
        assertFalse(raw.matches("Dead Hedgehog"));
        assertFalse(raw.matches("Cat Gold"));
        assertFalse(raw.matches("Branch"));
    }

    @Test
    void friedFishLoadsRoastableRawAndHonestInputPrompt() throws Exception {
        String src = new String(Files.readAllBytes(Paths.get("src/nurgling/actions/bots/FriedFish.java")),
                StandardCharsets.UTF_8);
        assertTrue(src.contains("FriedFishMaterials.roastableRaw()"), src);
        assertTrue(src.contains("FriedFishMaterials.isUncookedSpitContent"), src);
        assertTrue(src.contains("Please select area with raw fish or cleaned carcasses"), src);
        assertFalse(src.contains("VSpec.getAllFish()"), src);
        assertFalse(src.contains("content.contains(\"raw\")"), src);
    }
}
