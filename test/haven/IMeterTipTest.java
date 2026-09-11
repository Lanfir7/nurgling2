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

    @Test
    void healthTipHardHpIsMiddleValueNotSoftOrMeterBar() {
        IMeter.HealthNumbers woundSoft = IMeter.parseHealthNumbers("80/150/150");
        assertEquals(80, woundSoft.soft);
        assertEquals(150, woundSoft.hard);
        assertEquals(150, woundSoft.max);
        assertEquals(1.0, woundSoft.hardFraction(), 0.0001);

        IMeter.HealthNumbers wounded = IMeter.parseHealthNumbers("80/80/150");
        assertEquals(80, wounded.soft);
        assertEquals(80, wounded.hard);
        assertEquals(150, wounded.max);
        assertEquals(80.0 / 150.0, wounded.hardFraction(), 0.0001);

        IMeter.HealthNumbers both = IMeter.parseHealthNumbers("80.0/120.0/150.0");
        assertEquals(80, both.soft);
        assertEquals(120, both.hard);
        assertEquals(150, both.max);
        assertEquals(120.0 / 150.0, both.hardFraction(), 0.0001);
        assertFalse(both.sparring);
    }

    @Test
    void healthTipSparringKeepsHardAsSecondValue() {
        IMeter.HealthNumbers spar = IMeter.parseHealthNumbers("90/110/150/150");
        assertTrue(spar.sparring);
        assertEquals(90, spar.soft);
        assertEquals(110, spar.hard);
        assertEquals(150, spar.max);
        assertEquals(110.0 / 150.0, spar.hardFraction(), 0.0001);
    }

    @Test
    void hardFractionUnavailableWhenUnparsed() {
        assertEquals(-1, IMeter.hardFraction(-1, 150), 0.0001);
        assertEquals(-1, IMeter.hardFraction(80, 0), 0.0001);
        assertNull(IMeter.parseHealthNumbers("80/150"));
    }
}
