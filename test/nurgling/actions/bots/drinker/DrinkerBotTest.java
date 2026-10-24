package nurgling.actions.bots.drinker;

import haven.GItem;
import haven.Loader;
import haven.Resource;
import haven.WItem;
import nurgling.NGItem;
import nurgling.NCore;
import nurgling.NConfig;
import nurgling.NGameUI;
import nurgling.NUI;
import nurgling.NMapView;
import nurgling.actions.bots.ScenarioRunner;
import nurgling.actions.bots.registry.BotDescriptor;
import nurgling.actions.bots.registry.BotRegistry;
import nurgling.scenarios.BotStep;
import nurgling.sessions.ThreadLocalUI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.json.JSONObject;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DrinkerBotTest {
    private NConfig priorConfig;

    @BeforeEach void configure() {
        priorConfig = NConfig.current;
        if (NConfig.current == null) NConfig.current = new NConfig();
    }

    @AfterEach void restoreConfig() { NConfig.current = priorConfig; }

    private static <T> T bare(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static WItem cup(String name, boolean tooltipReady, String content) throws Exception {
        return cup(name, tooltipReady, content, "gfx/invobjs/mug", 10f);
    }

    private static WItem cup(String name, boolean tooltipReady, String content, String resourceName, Float quality) throws Exception {
        NGItem item = bare(NGItem.class);
        Field nameField = NGItem.class.getDeclaredField("name");
        nameField.setAccessible(true);
        nameField.set(item, name);
        item.infoseq = tooltipReady ? 1 : 0;
        item.quality = quality;
        Resource resource = bare(Resource.class);
        Field resourceNameField = Resource.class.getDeclaredField("name");
        resourceNameField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField();
        unsafe.putObject(resource, unsafe.objectFieldOffset(resourceNameField), resourceName);
        item.res = () -> resource;
        Field contentField = NGItem.class.getDeclaredField("content");
        contentField.setAccessible(true);
        ArrayList<NGItem.NContent> contents = new ArrayList<>();
        if (content != null) contents.add(new NGItem.NContent(10, "0.5 l of " + content));
        contentField.set(item, contents);
        WItem wrapper = bare(WItem.class);
        Field itemField = WItem.class.getDeclaredField("item");
        itemField.setAccessible(true);
        unsafe.putObject(wrapper, unsafe.objectFieldOffset(itemField), item);
        return wrapper;
    }

    private static Object unsafeField() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return field.get(null);
    }

    @Test void exactLiquidsAndUnloadedCupsNeverBecomeEmptyByGuess() throws Exception {
        assertTrue(Vessels.MILK.holds("Cowsmilk"));
        assertTrue(Vessels.MILK.holds("Goatsmilk"));
        assertTrue(Vessels.MILK.holds("Milk"));
        assertFalse(Vessels.MILK.holds("Unknownmilk"));
        assertFalse(Vessels.drink("Beer").holds("Millet Beer"));
        assertFalse(DrinkerBot.knownEmpty(cup("Mug", false, null)));
        assertTrue(DrinkerBot.knownEmpty(cup("Mug", true, null)));
        assertFalse(DrinkerBot.knownEmpty(cup("Mug", true, "Wine")));
        assertTrue(DrinkerBot.knownContent(cup("Wooden Cup", true, "Cowsmilk"), "Milk"));
        assertFalse(DrinkerBot.knownContent(cup("Wooden Cup", true, "Unknownmilk"), "Milk"));
    }

    @Test void receiptRequiresUniqueNewItemAndSourceRemoval() {
        Object old = new Object(); Object source = new Object(); Object arrived = new Object(); Object foreign = new Object();
        assertSame(arrived, DrinkerBot.uniqueArrival(Set.of(old), List.of(old, arrived), source, true));
        assertNull(DrinkerBot.uniqueArrival(Set.of(old), List.of(old, arrived), source, false));
        assertNull(DrinkerBot.uniqueArrival(Set.of(old), List.of(old, arrived, foreign), source, true));
        assertSame(source, DrinkerBot.uniqueArrival(Set.of(old), List.of(old, source), source, false));
        assertNull(DrinkerBot.uniqueArrival(Set.of(old), List.of(old, arrived), source, true, v -> v == foreign));
        assertSame(arrived, DrinkerBot.uniqueArrival(Set.of(old), List.of(old, arrived), source, true, v -> v == arrived));
    }

    @Test void actualFingerprintAcceptsRecreatedCupButRejectsSameNameForeignSignatures() throws Exception {
        WItem source = cup("Mug", true, null, "gfx/invobjs/mug", 20f);
        WItem replacement = cup("Mug", true, null, "gfx/invobjs/mug", 20f);
        WItem otherResource = cup("Mug", true, null, "gfx/invobjs/metalmug", 20f);
        WItem otherQuality = cup("Mug", true, null, "gfx/invobjs/mug", 12f);
        WItem otherContents = cup("Mug", true, "Wine", "gfx/invobjs/mug", 20f);
        WItem unread = cup("Mug", false, null, "gfx/invobjs/mug", 20f);
        DrinkerBot.Fingerprint expected = DrinkerBot.fingerprint(source.item);
        assertNotNull(expected);
        assertEquals(expected, DrinkerBot.fingerprint(replacement.item));
        assertNotEquals(expected, DrinkerBot.fingerprint(otherResource.item));
        assertNotEquals(expected, DrinkerBot.fingerprint(otherQuality.item));
        assertNotEquals(expected, DrinkerBot.fingerprint(otherContents.item));
        assertNull(DrinkerBot.fingerprint(unread.item));
        assertSame(replacement.item, DrinkerBot.uniqueArrival(Set.of(), List.of(replacement.item), source.item,
                true, item -> expected.equals(DrinkerBot.fingerprint(item))));
        for (WItem foreign : List.of(otherResource, otherQuality, otherContents, unread))
            assertNull(DrinkerBot.uniqueArrival(Set.of(), List.of(foreign.item), source.item,
                    true, item -> expected.equals(DrinkerBot.fingerprint(item))));
        assertNull(DrinkerBot.uniqueArrival(Set.of(), List.of(replacement.item, otherResource.item), source.item,
                true, item -> expected.equals(DrinkerBot.fingerprint(item))));
    }

    @Test void aSipNeedsLessLiquidAndSettledPoseNotJustTooltipChange() {
        assertFalse(DrinkerBot.sipCompleted(0.5, 0.5, true, true));
        assertFalse(DrinkerBot.sipCompleted(0.5, 0.4, false, true));
        assertFalse(DrinkerBot.sipCompleted(0.5, 0.4, true, false));
        assertTrue(DrinkerBot.sipCompleted(0.5, 0.4, true, true));
        assertTrue(DrinkerBot.sipCompleted(0.1, 0.0, true, true));
    }

    @Test void unconfirmedActionSuppressesAllStorageReturnNavigation() throws Exception {
        DrinkerBot.Cup fetched = new DrinkerBot.Cup("Mug", bare(nurgling.tools.Container.class), cup("Mug", true, null).item);
        assertEquals(List.of(fetched), DrinkerBot.returnPlan(false, List.of(fetched)));
        assertTrue(DrinkerBot.returnPlan(true, List.of(fetched)).isEmpty());
    }

    @Test void sipMenuOwnerIsInvalidatedByAnotherItemClickBeforeChooseOrClose() throws Exception {
        NCore core = bare(NCore.class);
        WItem ourCup = cup("Mug", true, "Wine");
        WItem playerCup = cup("Mug", true, "Wine");
        core.setLastAction(ourCup);
        NCore.LastActions marker = core.getLastActions();
        assertTrue(DrinkerBot.menuOwnerStill(core, marker, ourCup));
        core.setLastAction(playerCup);
        assertFalse(DrinkerBot.menuOwnerStill(core, marker, ourCup));
        core.setLastAction(ourCup);
        assertFalse(DrinkerBot.menuOwnerStill(core, marker, ourCup)); // stale marker cannot claim a later menu
    }

    @Test void returnGuardRejectsPlayerCupAndUnreadOrFilledFetchedCup() throws Exception {
        WItem owned = cup("Mug", true, null);
        WItem sameNamePlayerCup = cup("Mug", true, null);
        DrinkerBot.Cup fetched = new DrinkerBot.Cup("Mug", bare(nurgling.tools.Container.class), owned.item);
        assertTrue(DrinkerBot.canReturn(fetched, owned));
        assertFalse(DrinkerBot.canReturn(fetched, sameNamePlayerCup));
        assertFalse(DrinkerBot.canReturn(new DrinkerBot.Cup("Mug", null, owned.item), owned));
        WItem unread = cup("Mug", false, null);
        WItem filled = cup("Mug", true, "Wine");
        assertFalse(DrinkerBot.canReturn(new DrinkerBot.Cup("Mug", fetched.origin, unread.item), unread));
        assertFalse(DrinkerBot.canReturn(new DrinkerBot.Cup("Mug", fetched.origin, filled.item), filled));
    }

    @Test void scenarioCannotOpenDialogForMissingInvalidOrZeroSettings() throws Exception {
        BotDescriptor descriptor = BotRegistry.byId("drinker");
        assertNotNull(descriptor);
        assertFalse(((DrinkerBot) descriptor.instantiateForScenario(Map.of())).configured());
        assertFalse(((DrinkerBot) descriptor.instantiateForScenario(Map.of("sips:Wine", 0))).configured());
        assertFalse(((DrinkerBot) descriptor.instantiateForScenario(Map.of("sips:Wine", -1))).configured());
        assertFalse(((DrinkerBot) descriptor.instantiateForScenario(Map.of("sips:Wine", "5"))).configured());
        assertFalse(((DrinkerBot) descriptor.instantiateForScenario(Map.of("sips:Wine", 2, "cup:Wine", "Unknown"))).configured());
        assertTrue(((DrinkerBot) descriptor.instantiateForScenario(Map.of("sips:Wine", 2, "cup:Wine", "Mug"))).configured());
        assertTrue(descriptor.instantiate(Map.of()) instanceof DrinkerBot);
        BotDescriptor oldBot = BotRegistry.byId("bed");
        assertEquals(oldBot.instantiate(Map.of()).getClass(), oldBot.instantiateForScenario(Map.of()).getClass());
        BotStep saved = new BotStep(new JSONObject().put("id", "drinker").put("params",
                new JSONObject().put("sips:Wine", 2).put("cup:Wine", "Mug").put("futureOption", "retained")));
        assertEquals("retained", new BotStep(saved.toJson()).getSetting("futureOption"));

        NUI prior = ThreadLocalUI.get();
        try {
            NUI ui = bare(NUI.class);
            RecordingGameUI gui = bare(RecordingGameUI.class);
            ui.gui = gui;
            ThreadLocalUI.set(ui);
            assertFalse(ScenarioRunner.runSteps(gui, List.of(new BotStep("drinker"), new BotStep("bed"))).IsSuccess());
            assertEquals(1, gui.errors);
        } finally {
            if (prior == null) ThreadLocalUI.clear(); else ThreadLocalUI.set(prior);
        }
    }

    @Test void actualFlowRejectsForeignHandAndPlacementBeforeAnyAction() throws Exception {
        NUI prior = ThreadLocalUI.get();
        try {
            NUI ui = bare(NUI.class);
            RecordingGameUI gui = bare(RecordingGameUI.class);
            ui.gui = gui;
            ThreadLocalUI.set(ui);
            gui.map = bare(NMapView.class);
            WItem playerItem = cup("Mug", true, null);
            gui.vhand = playerItem;
            DrinkerBot bot = new DrinkerBot(Map.of("sips:Wine", 1), true);
            assertFalse(DrinkerBot.readyForAction(gui));
            java.lang.reflect.Method flow = DrinkerBot.class.getDeclaredMethod("perform", NGameUI.class, List.class, List.class);
            flow.setAccessible(true);
            assertFalse(((nurgling.actions.Results) flow.invoke(bot, gui,
                    List.of(new DrinkerBot.Order("Wine", "Mug", 1)), new ArrayList<DrinkerBot.Cup>())).IsSuccess());
            assertSame(playerItem, gui.vhand);
            gui.vhand = null;
            gui.map.placing = bare(Loader.Future.class);
            assertFalse(DrinkerBot.readyForAction(gui));
            assertFalse(((nurgling.actions.Results) flow.invoke(bot, gui,
                    List.of(new DrinkerBot.Order("Wine", "Mug", 1)), new ArrayList<DrinkerBot.Cup>())).IsSuccess());
            assertEquals(2, gui.errors);
        } finally {
            if (prior == null) ThreadLocalUI.clear(); else ThreadLocalUI.set(prior);
        }
    }

    private static final class RecordingGameUI extends NGameUI {
        int errors;
        private RecordingGameUI() { super("", 0L, null, null); }
        @Override public void error(String message) { errors++; }
        @Override public void msg(String message) { }
    }
}
