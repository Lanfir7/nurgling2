package nurgling;

import nurgling.i18n.L10n;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StudyDeskL10nTest {
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
    void fillerStatusUsesEnglish() {
        L10n.setLanguage("en");

        assertEquals("No study desk plans configured.", L10n.get("study.fill.error.no_plans"));
        assertEquals("Oak Desk is out of range or was removed; skipping.",
                L10n.get("study.fill.warning.not_found", "Oak Desk"));
        assertEquals("Study desks: 3 checked, 2 fully stocked, 1 with issues.",
                L10n.get("study.fill.summary", 3, 2, 1));
    }

    @Test
    void fillerStatusUsesRussian() {
        L10n.setLanguage("ru");

        assertEquals("Планы столов изучения не настроены.", L10n.get("study.fill.error.no_plans"));
        assertEquals("Oak Desk вне зоны видимости или удалён; пропускаю.",
                L10n.get("study.fill.warning.not_found", "Oak Desk"));
        assertEquals("Столы изучения: проверено 3, полностью заполнено 2, с проблемами 1.",
                L10n.get("study.fill.summary", 3, 2, 1));
    }
}
