package nurgling.actions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FillWaterskinsPolicyTest {
    @Test
    void partialWaterWaterskinIsEmptiedBeforeRefill() {
        assertTrue(FillWaterskins.isPartialDrink("Waterskin", "1.50 l of Water"));
        assertTrue(FillWaterskins.isPartialDrink("Waterskin", "0.5l of Water"));
        assertTrue(FillWaterskins.isPartialDrink("Glass Jug", "1.00 l of Water"));
        assertTrue(FillWaterskins.isPartialDrink("Glass Jug", "3.00 l of Water"));
    }

    @Test
    void fullEmptyAndOtherLiquidsAreLeftAlone() {
        assertFalse(FillWaterskins.isPartialDrink("Waterskin", "3.00 l of Water"));
        assertFalse(FillWaterskins.isPartialDrink("Waterskin", "3 l of Water"));
        assertFalse(FillWaterskins.isPartialDrink("Waterskin", null));
        assertFalse(FillWaterskins.isPartialDrink("Waterskin", "1.00 l of Tea"));
        assertFalse(FillWaterskins.isPartialDrink("Waterskin", "1.00 l of Saltwater"));
        assertFalse(FillWaterskins.isPartialDrink("Glass Jug", "5.00 l of Water"));
        assertFalse(FillWaterskins.isPartialDrink("Glass Jug", "5 l of Water"));
    }

    @Test
    void onlyEmptyDrinkContainersNeedAWaterTrip() {
        assertTrue(FillWaterskins.isEmptyDrink("Waterskin", true));
        assertTrue(FillWaterskins.isEmptyDrink("Glass Jug", true));
        assertFalse(FillWaterskins.isEmptyDrink("Waterskin", false));
        assertFalse(FillWaterskins.isEmptyDrink("Bucket", true));
    }

    @Test
    void bucketTripMatchesExistingRefillRule() {
        assertTrue(FillWaterskins.bucketNeedsRefill("Bucket", true, null));
        assertTrue(FillWaterskins.bucketNeedsRefill("Bucket", false, "5l of Water"));
        assertFalse(FillWaterskins.bucketNeedsRefill("Bucket", false, "10l of Water"));
        assertFalse(FillWaterskins.bucketNeedsRefill("Bucket", false, "3 l of Milk"));
    }
}
