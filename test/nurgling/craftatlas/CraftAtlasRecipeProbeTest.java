package nurgling.craftatlas;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CraftAtlasRecipeProbeTest {
    @Test
    void claimsRequestedRecipeOnceAndDoesNotProbeItAgainThisSession() {
        CraftAtlasRecipeProbe probe = new CraftAtlasRecipeProbe();
        AtomicInteger requests = new AtomicInteger();

        assertTrue(probe.request("paginae/craft/glue", "Bone Glue", requests::incrementAndGet));
        assertEquals(1, requests.get());
        assertEquals("paginae/craft/glue", probe.claim("Bone Glue").recipeResource);
        assertNull(probe.claim("Bone Glue"));
        probe.complete("paginae/craft/glue");
        assertFalse(probe.request("paginae/craft/glue", "Bone Glue", requests::incrementAndGet));
    }

    @Test
    void failedRequestCanBeRetried() {
        CraftAtlasRecipeProbe probe = new CraftAtlasRecipeProbe();
        assertThrows(IllegalStateException.class,
                () -> probe.request("paginae/craft/test", "Test", () -> { throw new IllegalStateException("nope"); }));

        assertTrue(probe.request("paginae/craft/test", "Test", () -> { }));
    }

    @Test
    void waitsForServerMessagesToSettleBeforeCapturingTools() {
        assertFalse(CraftAtlasRecipeProbe.readyToPublish(1_100_000_000L, 1_000_000_000L));
        assertTrue(CraftAtlasRecipeProbe.readyToPublish(1_200_000_000L, 1_000_000_000L));
    }

    @Test
    void cancelledRequestCanBeRetried() {
        CraftAtlasRecipeProbe probe = new CraftAtlasRecipeProbe();
        assertTrue(probe.request("paginae/craft/old", "Old", () -> { }));
        probe.cancel("paginae/craft/old");

        assertTrue(probe.request("paginae/craft/old", "Old", () -> { }));
        assertEquals("paginae/craft/old", probe.claim("Old").recipeResource);
    }

    @Test
    void unrelatedCraftWindowDoesNotConsumePendingProbe() {
        CraftAtlasRecipeProbe probe = new CraftAtlasRecipeProbe();
        assertTrue(probe.request("paginae/craft/glue", "Bone Glue", () -> { }));

        assertNull(probe.claim("Stone Axe"));
        assertEquals("paginae/craft/glue", probe.claim("Bone Glue").recipeResource);
    }

    @Test
    void completedRecipesAreScopedToOneAtlasSession() {
        CraftAtlasRecipeProbe first = new CraftAtlasRecipeProbe();
        assertTrue(first.request("paginae/craft/glue", "Bone Glue", () -> { }));
        first.claim("Bone Glue");
        first.complete("paginae/craft/glue");
        assertFalse(first.request("paginae/craft/glue", "Bone Glue", () -> { }));

        CraftAtlasRecipeProbe second = new CraftAtlasRecipeProbe();
        assertTrue(second.request("paginae/craft/glue", "Bone Glue", () -> { }));
    }
}
