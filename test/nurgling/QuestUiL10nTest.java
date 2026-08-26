package nurgling;

import haven.QuestWnd;
import nurgling.i18n.L10n;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestUiL10nTest {
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
    void returnToUsesRussian() {
        L10n.setLanguage("ru");
        assertEquals("Вернуться к Alice", QuestWnd.returnToLabel("Alice"));
    }

    @Test
    void returnToUsesEnglish() {
        L10n.setLanguage("en");
        assertEquals("Return to Alice", QuestWnd.returnToLabel("Alice"));
    }

    @Test
    void helperSectionsUseRussian() {
        L10n.setLanguage("ru");
        assertEquals("Принести", L10n.get("char.quest.section.bring"));
        assertEquals("Сбор:", L10n.get("char.quest.section.foraging"));
        assertEquals("Охота:", L10n.get("char.quest.section.hunting"));
        assertEquals("Награда:", L10n.get("char.quest.section.reward"));
        assertEquals("Кредо:", L10n.get("char.quest.credo"));
        assertEquals("Сменить режим", L10n.get("char.quest.switch_mode"));
        assertEquals("Скрыть кредо", L10n.get("char.quest.hide_credo"));
    }

    @Test
    void completedBannerUsesRussian() {
        L10n.setLanguage("ru");
        assertEquals("Квест выполнен", QuestWnd.statusBanner(true));
        assertEquals("Квест провален", QuestWnd.statusBanner(false));
    }

    @Test
    void resourceKeyTitleTranslates() {
        L10n.setLanguage("ru");
        assertEquals("Журнал заданий", QuestWnd.localizedTitle("@char.quest.title"));
    }

    @Test
    void serverTitleStaysAsIs() {
        L10n.setLanguage("ru");
        assertEquals("A Peculiar Request", QuestWnd.localizedTitle("A Peculiar Request"));
    }

    @Test
    void catchCondUsesRussian() {
        L10n.setLanguage("ru");
        assertEquals("Поймать Rabbit", QuestWnd.localizeCond("Catch a Rabbit"));
        assertEquals("Поймать Ant", QuestWnd.localizeCond("Catch an Ant"));
        assertEquals("Поймать Dragonfly 0/1", QuestWnd.localizeCond("Catch a Dragonfly 0/1"));
    }

    @Test
    void verbCondsUseRussian() {
        L10n.setLanguage("ru");
        assertEquals("Собрать yarrow", QuestWnd.localizeCond("Pick yarrow"));
        assertEquals("Победить badger", QuestWnd.localizeCond("Defeat a badger"));
        assertEquals("Поздороваться с Svein", QuestWnd.localizeCond("Greet Svein"));
        assertEquals("Убить Fox", QuestWnd.localizeCond("Kill a Fox"));
        assertEquals("Принести Branch к Bob", QuestWnd.localizeCond("Bring a Branch to Bob"));
        assertEquals("Навестить Thiot", QuestWnd.localizeCond("Visit Thiot"));
        assertEquals("Помахать Svein", QuestWnd.localizeCond("Wave at Svein"));
        assertEquals("Напасть на anthill", QuestWnd.localizeCond("Raid an anthill"));
        assertEquals("Получить Strength", QuestWnd.localizeCond("Gain Strength"));
        assertEquals("Создать Rope", QuestWnd.localizeCond("Create a Rope"));
        assertEquals("Зажечь fire", QuestWnd.localizeCond("Light a fire"));
    }

    @Test
    void verbCondsKeepEnglish() {
        L10n.setLanguage("en");
        assertEquals("Catch a Rabbit", QuestWnd.localizeCond("Catch a Rabbit"));
        assertEquals("Pick yarrow", QuestWnd.localizeCond("Pick yarrow"));
        assertEquals("Greet Svein", QuestWnd.localizeCond("Greet Svein"));
        assertEquals("Kill a Fox", QuestWnd.localizeCond("Kill a Fox"));
        assertEquals("Bring a Branch to Bob", QuestWnd.localizeCond("Bring a Branch to Bob"));
    }

    @Test
    void unknownCondUnchanged() {
        L10n.setLanguage("ru");
        assertEquals("Something else", QuestWnd.localizeCond("Something else"));
    }
}
