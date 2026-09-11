package nurgling.tools;

import haven.GItem;
import haven.ItemInfo;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NBuffCheckerTest {

    @Test
    void matchingBuffWithoutNumberInfoReturnsUnknownSentinel() {
        NBuffChecker.BuffProbe probe = NBuffChecker.probe(tansy(Collections.<ItemInfo>emptyList()));

        assertEquals(-1, probe.count);
        assertTrue(probe.present);
    }

    @Test
    void matchingBuffWithoutNumberInfoIsPresentNotAbsent() {
        NBuffChecker.BuffProbe probe = NBuffChecker.probe(tansy(Collections.<ItemInfo>emptyList()));

        assertTrue(NBuffChecker.hasScentOfTansy(probe));
        assertEquals(-1, NBuffChecker.getScentOfTansyCount(probe));
    }

    @Test
    void loadedNumberInfoPassesThroughUnchanged() {
        NBuffChecker.BuffProbe probe = NBuffChecker.probe(tansy(Collections.<ItemInfo>singletonList(new FakeNumber(7))));

        assertEquals(7, probe.count);
        assertTrue(probe.present);
    }

    @Test
    void loadedNumberInfoOneIsNotInvented() {
        NBuffChecker.BuffProbe probe = NBuffChecker.probe(tansy(Collections.<ItemInfo>singletonList(new FakeNumber(1))));

        assertEquals(1, probe.count);
        assertTrue(probe.present);
    }

    @Test
    void loadedZeroCountStillCountsAsPresent() {
        NBuffChecker.BuffProbe probe = NBuffChecker.probe(tansy(Collections.<ItemInfo>singletonList(new FakeNumber(0))));

        assertEquals(0, probe.count);
        assertTrue(probe.present);
        assertTrue(NBuffChecker.hasScentOfTansy(probe));
    }

    @Test
    void loadingInfoAfterMatchReturnsUnknownButPresent() {
        NBuffChecker.BuffProbe probe = NBuffChecker.probe(NBuffChecker.BuffSnapshot.loadingInfo("gfx/hud/buffs/scent-of-tansy"));

        assertTrue(probe.present);
        assertEquals(-1, probe.count);
        assertTrue(NBuffChecker.hasScentOfTansy(probe));
        assertEquals(-1, NBuffChecker.getScentOfTansyCount(probe));
    }

    @Test
    void unrelatedBuffIsTreatedAsAbsent() {
        NBuffChecker.BuffProbe probe = NBuffChecker.probe(
                new NBuffChecker.BuffSnapshot("gfx/hud/buffs/swiftness", Collections.<ItemInfo>singletonList(new FakeNumber(4))));

        assertFalse(probe.present);
        assertEquals(-1, probe.count);
        assertFalse(NBuffChecker.hasScentOfTansy(probe));
    }

    @Test
    void missingGuiIsAbsent() {
        assertFalse(NBuffChecker.hasScentOfTansy());
        assertEquals(-1, NBuffChecker.getScentOfTansyCount());
    }

    private static NBuffChecker.BuffSnapshot tansy(List<ItemInfo> info) {
        return new NBuffChecker.BuffSnapshot("gfx/hud/buffs/scent-of-tansy", info);
    }

    private static final class FakeNumber extends ItemInfo implements GItem.NumberInfo {
        private final int n;

        FakeNumber(int n) {
            super(null);
            this.n = n;
        }

        public int itemnum() {
            return n;
        }
    }
}
