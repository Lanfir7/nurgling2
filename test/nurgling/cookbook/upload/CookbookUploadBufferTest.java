package nurgling.cookbook.upload;

import nurgling.cookbook.Recipe;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CookbookUploadBufferTest {
    @Test
    void deduplicatesRecipesAndKeepsTheNewestEntriesWithinCapacity() {
        CookbookUploadBuffer buffer = new CookbookUploadBuffer(2);

        buffer.offer(record("one"));
        buffer.offer(record("two"));
        buffer.offer(record("two"));
        buffer.offer(record("three"));

        List<CookbookUploadRecord> batch = buffer.drain(10);
        assertEquals(2, batch.size());
        assertEquals("two", batch.get(0).key());
        assertEquals("three", batch.get(1).key());
    }

    @Test
    void failedBatchCanBeRestoredWithoutOverwritingNewerQueuedRecipes() {
        CookbookUploadBuffer buffer = new CookbookUploadBuffer(3);
        buffer.offer(record("one"));
        buffer.offer(record("two"));
        List<CookbookUploadRecord> failed = buffer.drain(2);
        buffer.offer(record("three"));

        buffer.restore(failed);

        List<CookbookUploadRecord> retried = buffer.drain(3);
        assertEquals(3, retried.size());
        assertEquals("one", retried.get(0).key());
        assertEquals("two", retried.get(1).key());
        assertEquals("three", retried.get(2).key());
    }

    private static CookbookUploadRecord record(String hash) {
        Recipe recipe = new Recipe(hash, hash, "gfx/invobjs/" + hash,
                1.0, 50, Collections.emptyMap(), Collections.emptyMap());
        return CookbookUploadRecord.from(recipe, "w17");
    }
}
