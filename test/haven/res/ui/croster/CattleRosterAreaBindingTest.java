package haven.res.ui.croster;

import nurgling.NConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CattleRosterAreaBindingTest {
    private static NConfig previousConfig;

    @BeforeAll static void configure() {
        previousConfig = NConfig.current;
        NConfig.current = new NConfig();
    }

    @AfterAll static void restore() {
        NConfig.current = previousConfig;
    }

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
