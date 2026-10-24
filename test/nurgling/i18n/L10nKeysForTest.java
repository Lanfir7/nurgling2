package nurgling.i18n;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class L10nKeysForTest {
    @Test
    void reverseLookupUsesOnlyActualTranslationsAndTracksLocale() {
        Locale original = L10n.getLocale();
        try {
            L10n.setLanguage("ru");
            String russian = L10n.get("qol.night_vision");
            assertTrue(L10n.keysFor(russian).contains("qol.night_vision"));
            assertTrue(L10n.keysFor("a setting name absent from every translation").isEmpty());

            L10n.setLanguage("en");
            String english = L10n.get("qol.night_vision");
            assertTrue(L10n.keysFor(english).contains("qol.night_vision"));
            if(!russian.equals(english))
                assertFalse(L10n.keysFor(russian).contains("qol.night_vision"));
        } finally {
            L10n.setLocale(original);
        }
    }
}
