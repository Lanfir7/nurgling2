package nurgling.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWorldClassifierTest {
    @ParameterizedTest
    @CsvSource({
            "gfx/terobjs/trees/oak, tree",
            "gfx/terobjs/trees/pine, tree",
            "gfx/terobjs/trees/oaklog, log",
            "gfx/terobjs/trees/pinelog, log",
            "gfx/terobjs/trees/oldtrunk, log",
            "gfx/terobjs/trees/oakstump, stump",
            "gfx/terobjs/arch/palisadegate, gate",
            "gfx/terobjs/arch/palisadebiggate, gate",
            "gfx/terobjs/arch/polegate, gate",
            "gfx/terobjs/arch/polebiggate, gate",
            "gfx/terobjs/arch/drystonewallgate, gate",
            "gfx/terobjs/arch/palisade, palisade",
            "gfx/terobjs/vehicle/cart, cart",
            "gfx/terobjs/vehicle/wagon, wagon",
            "gfx/kritter/bear/bear, aggressive",
            "gfx/kritter/wolf/wolf, aggressive",
            "gfx/kritter/rabbit/rabbit, animal",
            "gfx/terobjs/cheeserack, other",
            "gfx/terobjs/arch/logcabin, other",
            "'', other"
    })
    void classifiesByResourceName(String resName, String kind) {
        assertEquals(kind, AgentWorldClassifier.classify(resName));
    }

    @Test
    void aggressiveUsesAnimalAlarms() {
        assertTrue(AgentWorldClassifier.isAggressive("gfx/kritter/lynx/lynx"));
        assertFalse(AgentWorldClassifier.isAggressive("gfx/kritter/rabbit/rabbit"));
        assertFalse(AgentWorldClassifier.isAggressive("gfx/terobjs/trees/oak"));
    }

    @Test
    void nullNameIsOther() {
        assertEquals("other", AgentWorldClassifier.classify(null));
        assertFalse(AgentWorldClassifier.isAggressive(null));
    }
}
