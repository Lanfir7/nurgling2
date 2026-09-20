package nurgling.conf;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NCharTagsAccountTest {
    @TempDir
    Path temp;

    @AfterEach
    void restoreDefaultStore() {
        NCharTags.usePathForTests(null);
    }

    @Test
    void loadsLegacyCharacterDataAndKeepsItWhenSavingAccountAnnotations() throws Exception {
        Path file = temp.resolve("character-tags.json");
        Files.writeString(file, "{\"version\":1,\"palette\":{\"farmer\":2},\"chars\":{\"alice\":{\"Ava\":{\"tags\":[\"farmer\"],\"note\":\"legacy\"}}}}");
        NCharTags.usePathForTests(file);

        assertEquals(Arrays.asList("farmer"), NCharTags.tags("alice", "Ava"));
        assertEquals("legacy", NCharTags.note("alice", "Ava"));

        NCharTags.addAccTag("main", 4);
        NCharTags.setAcc("alice", Arrays.asList("main"), "account note");

        JSONObject saved = new JSONObject(Files.readString(file));
        assertEquals("legacy", saved.getJSONObject("chars").getJSONObject("alice").getJSONObject("Ava").getString("note"));
        assertEquals("main", saved.getJSONObject("accounts").getJSONObject("alice").getJSONArray("tags").getString(0));
    }

    @Test
    void forgetUsedRemovesAccountAnnotationsWithoutChangingCharacterDataOrPalette() {
        NCharTags.usePathForTests(temp.resolve("character-tags.json"));
        NCharTags.addtag("character", 1);
        NCharTags.addAccTag("account", 5);
        NCharTags.set("alice", "Ava", Arrays.asList("character"), "character note");
        NCharTags.setAcc("alice", Arrays.asList("account"), "account note");

        assertEquals(Arrays.asList("character"), NCharTags.alltags());
        assertEquals(Arrays.asList("account"), NCharTags.allAccTags());
        assertTrue(NCharTags.hasAccNote("alice"));

        NCharTags.forgetUsed("alice");

        assertFalse(NCharTags.hasAccNote("alice"));
        assertTrue(NCharTags.accTags("alice").isEmpty());
        assertEquals(Arrays.asList("character"), NCharTags.tags("alice", "Ava"));
        assertEquals("character note", NCharTags.note("alice", "Ava"));
        assertEquals(Arrays.asList("character"), NCharTags.alltags());
    }
}
