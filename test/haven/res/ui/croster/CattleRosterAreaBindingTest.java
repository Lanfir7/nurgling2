package haven.res.ui.croster;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CattleRosterAreaBindingTest {
    @Test
    void updateKeepsExistingAreaBinding() {
        assertEquals(42, CattleRoster.preserveAreaId(42, -1));
    }

    @Test
    void updateKeepsParsedAreaWhenNoBindingExists() {
        assertEquals(-1, CattleRoster.preserveAreaId(-1, -1));
        assertEquals(7, CattleRoster.preserveAreaId(-1, 7));
    }
}
