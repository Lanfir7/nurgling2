package haven;

import nurgling.i18n.L10n;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IMeterTipTest {
    private String prevLang;

    @BeforeEach
    void saveLang() {
        prevLang = L10n.getLanguage();
    }

    @AfterEach
    void restoreLang() {
        L10n.setLanguage(prevLang);
    }

    @Test
    void parsesEnglishHealthKey() {
        assertEquals("Health", IMeter.tipKey("Health: 80/100"));
        assertEquals(" 80/100", IMeter.tipValue("Health: 80/100"));
        assertTrue(IMeter.meterName("Health", "Health", "widget.hp"));
    }

    @Test
    void parsesRussianHealthKey() {
        L10n.setLanguage("ru");
        assertEquals("Здоровье", IMeter.tipKey("Здоровье: 80/100"));
        assertTrue(IMeter.meterName("Здоровье", "Health", "widget.hp"));
        assertTrue(IMeter.meterName("Выносливость", "Stamina", "widget.stam"));
        assertTrue(IMeter.meterName("Энергия", "Energy", "widget.energy"));
    }

    @Test
    void characterWindowTitleIsLocalized() {
        L10n.setLanguage("ru");
        assertEquals("Лист персонажа", L10n.get("char.window_title"));
        assertEquals("Здоровье и раны", L10n.get("char.tab.wound"));
        assertEquals("Здоровье", L10n.get("widget.hp"));
    }

    @Test
    void missingColonDoesNotThrow() {
        assertNull(IMeter.tipKey("Health 80/100"));
        assertNull(IMeter.tipValue(null));
        assertFalse(IMeter.meterName(null, "Health", "widget.hp"));
    }
}
