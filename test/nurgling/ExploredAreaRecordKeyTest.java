package nurgling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExploredAreaRecordKeyTest {
    @Test
    void recordKeyRoundTripsForJsonLoad() {
        assertEquals(NConfig.Key.exploredAreaRecord, NConfig.Key.valueOf("exploredAreaRecord"));
    }

    @Test
    void defaultsAreOffForNewInstalls() {
        assertFalse(Boolean.TRUE.equals(new NConfig().conf.get(NConfig.Key.exploredAreaRecord)));
        assertFalse(Boolean.TRUE.equals(new NConfig().conf.get(NConfig.Key.exploredAreaEnable)));
    }

    @Test
    void fogDisplayDoesNotShareTheRecordKey() {
        assertTrue(NConfig.Key.exploredAreaEnable != NConfig.Key.exploredAreaRecord);
    }
}
