package nurgling.conf;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NSavedAccountsTest {
    @Test
    void tokenAccountWinsDisplayWithoutDeletingLegacyPasswordFallback() {
        ArrayList<NLoginData> legacy = new ArrayList<>();
        legacy.add(new NLoginData("denis", "secret"));

        List<NSavedAccounts.Account> accounts = NSavedAccounts.collect(
                legacy, Arrays.asList("denis"), name -> new byte[32], name -> 123L);

        assertEquals(1, accounts.size());
        assertEquals("denis", accounts.get(0).name);
        assertTrue(accounts.get(0).token);
        assertEquals("secret", legacy.get(0).pass);
        assertFalse(legacy.get(0).isTokenUsed);
    }

    @Test
    void savingPasswordAndTokenKeepsLegacyCredentialCompatibility() {
        ArrayList<NLoginData> legacy = new ArrayList<>();
        NSavedAccounts.putPassword(legacy, "denis", "first");
        NSavedAccounts.putPassword(legacy, "denis", "second");

        assertEquals(1, legacy.size());
        assertEquals("second", legacy.get(0).pass);
        assertFalse(legacy.get(0).isTokenUsed);

        byte[] token = new byte[32];
        token[0] = 7;
        NSavedAccounts.putToken(legacy, "denis", token);

        assertEquals(1, legacy.size());
        assertTrue(legacy.get(0).isTokenUsed);
        assertArrayEquals(token, legacy.get(0).token);
    }

    @Test
    void rejectedLegacyTokenIsRemovedWithoutDeletingPasswordFallback() {
        ArrayList<NLoginData> legacy = new ArrayList<>();
        legacy.add(new NLoginData("denis", "fallback"));
        legacy.add(new NLoginData("denis", new byte[32]));

        assertTrue(NSavedAccounts.invalidateToken(legacy, "denis"));
        assertEquals(1, legacy.size());
        assertEquals("denis", legacy.get(0).name);
        assertEquals("fallback", legacy.get(0).pass);
        assertFalse(NSavedAccounts.invalidateToken(legacy, "missing"));
    }
}
