package haven;

import nurgling.NConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuffTimeLeftTest {
    private static NConfig previousConfig;

    @BeforeAll
    static void fonts() {
        previousConfig = NConfig.current;
        if(NConfig.current == null)
            NConfig.current = new NConfig();
    }

    @AfterAll
    static void restoreConfig() {
        NConfig.current = previousConfig;
    }

    @Test
    void formatsTheLargestCompletedRussianUnit() {
        assertEquals("23 дня", BuffTimeLeft.format(23 * 86400));
        assertEquals("21 час", BuffTimeLeft.format(21 * 3600));
        assertEquals("5 мин", BuffTimeLeft.format(5 * 60));
        assertEquals("5 сек", BuffTimeLeft.format(5));
    }

    @Test
    void roundsAVisibleSubSecondRemainderUpAndHidesExpiredTimers() {
        assertEquals("1 сек", BuffTimeLeft.format(0.01));
        assertEquals("1 мин", BuffTimeLeft.format(59.01));
        assertEquals(null, BuffTimeLeft.format(0));
        assertEquals(null, BuffTimeLeft.format(-2));
    }

    @Test
    void convertsGameClockDurationsToRealTimeBeforeFormatting() {
        assertEquals("20 сек", BuffTimeLeft.formatForGameTime(300, 240, 3));
        assertEquals("1 мин", BuffTimeLeft.formatForGameTime(600, 420, 3));
        assertEquals(null, BuffTimeLeft.formatForGameTime(300, 240, 0));
    }

    @Test
    void usesRussianDayAndHourPluralForms() {
        assertEquals("1 день", BuffTimeLeft.format(86400));
        assertEquals("2 дня", BuffTimeLeft.format(2 * 86400));
        assertEquals("5 дней", BuffTimeLeft.format(5 * 86400));
        assertEquals("22 часа", BuffTimeLeft.format(22 * 3600));
        assertEquals("5 часов", BuffTimeLeft.format(5 * 3600));
    }

    @Test
    void renderedPlateFitsTheBuffIconForRepresentativeLabels() {
        assertPlateFits("11 часов");
        assertPlateFits("23 дня");
	assertPlateFits("123456 дней");
    }

    private static void assertPlateFits(String label) {
        assertTrue(BuffTimeLeft.renderPlate(label, 32).getWidth() <= 32, label);
    }
}
