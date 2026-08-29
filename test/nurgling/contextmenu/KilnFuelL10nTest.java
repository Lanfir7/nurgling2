package nurgling.contextmenu;

import nurgling.i18n.L10n;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KilnFuelL10nTest {
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
    void englishMenuAndWindowLabels() {
        L10n.setLanguage("en");
        assertEquals("Kiln fuel", L10n.get("context.kiln_fuel"));
        assertEquals("Kiln fuel", L10n.get("kiln_fuel.title"));
        assertEquals("Item", L10n.get("kiln_fuel.col.item"));
        assertEquals("Branches", L10n.get("kiln_fuel.col.fuel"));
        assertEquals("Real time", L10n.get("kiln_fuel.col.real"));
        assertEquals("In-game", L10n.get("kiln_fuel.col.ingame"));
        assertEquals("Notes", L10n.get("kiln_fuel.col.notes"));
    }

    @Test
    void russianMenuAndWindowLabels() {
        L10n.setLanguage("ru");
        assertEquals("Топливо печи", L10n.get("context.kiln_fuel"));
        assertEquals("Топливо печи", L10n.get("kiln_fuel.title"));
        assertEquals("Предмет", L10n.get("kiln_fuel.col.item"));
        assertEquals("Ветки", L10n.get("kiln_fuel.col.fuel"));
        assertEquals("Реальное", L10n.get("kiln_fuel.col.real"));
        assertEquals("Игровое", L10n.get("kiln_fuel.col.ingame"));
        assertEquals("Заметки", L10n.get("kiln_fuel.col.notes"));
    }
}
