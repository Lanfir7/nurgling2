package nurgling.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static nurgling.tools.DefaultAnimalAlarms.Play;

class DefaultAnimalAlarmsTest {
    @ParameterizedTest
    @CsvSource({
            "gfx/kritter/bear/bear, ND_Bear.wav",
            "gfx/invobjs/kritter/bear, ND_Bear.wav",
            "gfx/kritter/bear/polarbear, ND_Bear.wav",
            "gfx/kritter/boar/boar, ND_Boar.wav",
            "gfx/invobjs/kritter/wildboar, ND_Boar.wav",
            "gfx/kritter/adder/adder, ND_Snake.wav",
            "gfx/kritter/wolf/wolf, ND_Wolf.wav",
            "gfx/invobjs/kritter/wolf, ND_Wolf.wav",
            "gfx/kritter/badger/badger, ND_Badger.wav",
            "gfx/kritter/wolverine/wolverine, ND_Wolverine.wav",
            "gfx/kritter/lynx/lynx, ND_Lynx.wav",
            "gfx/kritter/mammoth/mammoth, ND_Mammoth.wav",
            "gfx/invobjs/kritter/mammoth, ND_Mammoth.wav",
            "gfx/kritter/moose/moose, ND_Moose.wav",
            "gfx/kritter/troll/troll, ND_Troll.wav",
            "gfx/kritter/walrus/walrus, ND_Walrus.wav",
            "gfx/kritter/orca/orca, ND_Orca.wav",
            "gfx/kritter/eagle/eagle, ND_Eagle.wav",
            "gfx/kritter/goldeneagle/goldeneagle, ND_Eagle.wav",
            "gfx/kritter/eagleowl/eagleowl, ND_EagleOwl.wav",
            "gfx/kritter/greyseal/greyseal, ND_GreySeal.wav",
            "gfx/kritter/boreworm/boreworm, ND_Ambush.wav",
            "gfx/kritter/spermwhale/spermwhale, ND_Cachalot.wav",
            "gfx/kritter/caveangler/caveangler, ND_CaveAngler.wav",
            "gfx/kritter/nidbane/nidbane, ND_Nidbane.wav",
    })
    void mapsIconAndGobNamesToAlarmWav(String resName, String wav) {
        assertEquals(wav, DefaultAnimalAlarms.soundFileFor(resName));
    }

    @Test
    void unknownResourceHasNoDefault() {
        assertNull(DefaultAnimalAlarms.soundFileFor("gfx/kritter/rabbit/rabbit"));
        assertNull(DefaultAnimalAlarms.soundFileFor("gfx/terobjs/trees/oak"));
        assertNull(DefaultAnimalAlarms.soundFileFor(null));
        assertNull(DefaultAnimalAlarms.soundFileFor(""));
    }

    @Test
    void corpsePoseNeverAlarms() {
        assertEquals(Play.NEVER, DefaultAnimalAlarms.playForPose("knock", "gfx/kritter/bear/bear"));
        assertEquals(Play.NEVER, DefaultAnimalAlarms.playForPose("gfx/kritter/bear/knocked", "gfx/kritter/bear/bear"));
        assertEquals(Play.NEVER, DefaultAnimalAlarms.playForPose("dead", "gfx/kritter/wolf/wolf"));
    }

    @Test
    void livingAnimalAlarmsNow() {
        assertEquals(Play.NOW, DefaultAnimalAlarms.playForPose("idle", "gfx/kritter/bear/bear"));
        assertEquals(Play.NOW, DefaultAnimalAlarms.playForPose("gfx/kritter/bear/bear", "gfx/invobjs/kritter/bear"));
    }

    @Test
    void unknownAnimalPoseWaits() {
        assertEquals(Play.LATER, DefaultAnimalAlarms.playForPose(null, "gfx/kritter/bear/bear"));
        assertEquals(Play.LATER, DefaultAnimalAlarms.playForPose("", "gfx/kritter/bear/bear"));
    }

    @Test
    void nonAnimalNullPosePlaysImmediately() {
        assertEquals(Play.NOW, DefaultAnimalAlarms.playForPose(null, "gfx/terobjs/herbs/chantrelle"));
    }

    @Test
    void pendingAnimalAlarmSurvivesUnknownPoseUntilAnimalLoads() {
        AtomicInteger played = new AtomicInteger();
        DefaultAnimalAlarms.State state = new DefaultAnimalAlarms.State(played::incrementAndGet);

        state.poll(null, "gfx/invobjs/kritter/bear");
        state.expireVisual();

        assertEquals(0, played.get());
        assertFalse(state.isVisualActive());
        assertTrue(state.isPending());

        state.poll("gfx/kritter/bear/idle", "gfx/invobjs/kritter/bear");
        state.poll("gfx/kritter/bear/idle", "gfx/invobjs/kritter/bear");

        assertEquals(1, played.get());
        assertFalse(state.isPending());
    }

    @Test
    void corpseCancelsPendingAlarmAndVisual() {
        AtomicInteger played = new AtomicInteger();
        DefaultAnimalAlarms.State state = new DefaultAnimalAlarms.State(played::incrementAndGet);

        state.poll("gfx/kritter/bear/knocked", "gfx/invobjs/kritter/bear");

        assertEquals(0, played.get());
        assertFalse(state.isPending());
        assertFalse(state.isVisualActive());
    }

    @Test
    void completedAlarmStopsPollingPoseWhileVisualRemainsActive() {
        DefaultAnimalAlarms.State state = new DefaultAnimalAlarms.State(() -> {});

        state.poll("gfx/kritter/bear/idle", "gfx/invobjs/kritter/bear");
        state.poll("gfx/kritter/bear/knocked", "gfx/invobjs/kritter/bear");

        assertFalse(state.isPending());
        assertTrue(state.isVisualActive());
    }
}
