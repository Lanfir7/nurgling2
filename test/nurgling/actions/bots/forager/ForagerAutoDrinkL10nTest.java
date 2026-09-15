package nurgling.actions.bots.forager;

import nurgling.i18n.L10n;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForagerAutoDrinkL10nTest {
    private String previousLanguage;

    @BeforeEach
    void saveLanguage() {
        previousLanguage = L10n.getLanguage();
    }

    @AfterEach
    void restoreLanguage() {
        L10n.setLanguage(previousLanguage);
    }

    @Test
    void noWaterNotificationIsLocalized() {
        L10n.setLanguage("en");
        assertEquals("Forager: out of water, continuing the route.",
                L10n.get("forager.auto_drink.no_water"));

        L10n.setLanguage("ru");
        assertEquals("Forager: закончилась вода, продолжаю маршрут.",
                L10n.get("forager.auto_drink.no_water"));
    }
}
