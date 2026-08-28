package nurgling.cookbook.upload;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeCaptureModeTest {
    @Test
    void capturesWhenEitherLocalCookbookOrSharingIsEnabled() {
        assertFalse(RecipeCaptureMode.shouldCapture(false, false));
        assertTrue(RecipeCaptureMode.shouldCapture(true, false));
        assertTrue(RecipeCaptureMode.shouldCapture(false, true));
    }

    @Test
    void enablingSharingDoesNotReuseLocalOnlyDeduplicationKey() {
        String localOnly = RecipeCaptureMode.cacheKey("Bread|Flour", true, false);
        String localAndShared = RecipeCaptureMode.cacheKey("Bread|Flour", true, true);

        assertNotEquals(localOnly, localAndShared);
    }

    @Test
    void waitsForTooltipAndQualityBeforeCapturing() {
        assertFalse(RecipeCaptureMode.isReady(false, true));
        assertFalse(RecipeCaptureMode.isReady(true, false));
        assertTrue(RecipeCaptureMode.isReady(true, true));
    }

    @Test
    void waitsForWorldIdentifierBeforeRemoteCapture() {
        assertFalse(RecipeCaptureMode.isRemoteReady(true, null));
        assertFalse(RecipeCaptureMode.isRemoteReady(true, "  "));
        assertTrue(RecipeCaptureMode.isRemoteReady(true, "w17"));
        assertFalse(RecipeCaptureMode.isRemoteReady(false, ""));
    }

    @Test
    void identifiesOnlyRemoteDeduplicationKeys() {
        assertTrue(RecipeCaptureMode.isRemoteKey(
                RecipeCaptureMode.cacheKey("Bread|Flour", false, true)));
        assertTrue(RecipeCaptureMode.isRemoteKey(
                RecipeCaptureMode.cacheKey("Bread|Flour", true, true)));
        assertFalse(RecipeCaptureMode.isRemoteKey(
                RecipeCaptureMode.cacheKey("Bread|Flour", true, false)));
    }

    @Test
    void resetsPerItemStateWhenSharingGenerationChanges() {
        assertFalse(RecipeCaptureMode.isNewSharingGeneration(7, 7));
        assertTrue(RecipeCaptureMode.isNewSharingGeneration(7, 8));
    }
}
