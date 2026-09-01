package nurgling;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtraInvPanelPrefsTest {

    private static Map<NConfig.Key, Object> emptyStore() {
        return new HashMap<>();
    }

    private static Function<NConfig.Key, Object> getter(Map<NConfig.Key, Object> store) {
        return store::get;
    }

    private static BiConsumer<NConfig.Key, Object> setter(Map<NConfig.Key, Object> store) {
        return store::put;
    }

    @Test
    void defaultConfigOpensClosedWithEmptyFilters() {
        ExtraInvPanelPrefs.Snapshot snap = ExtraInvPanelPrefs.read(k -> null);

        assertEquals(0, snap.panelState);
        assertEquals(NInventory.Grouping.NONE, snap.grouping);
        assertEquals(NInventory.DisplayType.Name, snap.displayType);
        assertEquals("", snap.minQualityText);
    }

    @Test
    void missingConfigOpensClosed() {
        ExtraInvPanelPrefs.Snapshot snap = ExtraInvPanelPrefs.read(k -> null);
        assertEquals(0, snap.panelState);
    }

    @Test
    void freshContainerRestoresExpandedQ5AndMinQuality() {
        Map<NConfig.Key, Object> store = emptyStore();
        ExtraInvPanelPrefs.write(new ExtraInvPanelPrefs.Snapshot(
                2,
                NInventory.Grouping.Q5,
                NInventory.DisplayType.Name,
                "10"), setter(store));

        ExtraInvPanelPrefs.Snapshot snap = ExtraInvPanelPrefs.snapshotForInstall(getter(store));
        assertEquals(2, snap.panelState);
        assertEquals(NInventory.Grouping.Q5, snap.grouping);
        assertEquals(NInventory.DisplayType.Name, snap.displayType);
        assertEquals("10", snap.minQualityText);
    }

    @Test
    void setPanelStateOnContainerExtraPanelWritesConfig() {
        Map<NConfig.Key, Object> store = emptyStore();
        store.put(NConfig.Key.inventoryRightPanelShow, false);
        ExtraInvPanelPrefs.onPanelStateChanged(false, true, 2, setter(store));

        assertEquals(2, ExtraInvPanelPrefs.read(getter(store)).panelState);
        assertEquals(Boolean.FALSE, store.get(NConfig.Key.inventoryRightPanelShow));
    }

    @Test
    void setPanelStateOnMainInvDoesNotWriteContainerKey() {
        Map<NConfig.Key, Object> store = emptyStore();
        ExtraInvPanelPrefs.onPanelStateChanged(true, false, 1, setter(store));

        assertEquals(0, ExtraInvPanelPrefs.read(getter(store)).panelState);
        assertEquals(1, store.get(NConfig.Key.inventoryRightPanelShow));
        assertNull(store.get(NConfig.Key.extraInvPanelState));
    }

    @Test
    void filterWritesSurviveLaterPanelStateChange() {
        Map<NConfig.Key, Object> store = emptyStore();
        ExtraInvPanelPrefs.write(new ExtraInvPanelPrefs.Snapshot(
                2,
                NInventory.Grouping.Q5,
                NInventory.DisplayType.Name,
                "10"), setter(store));
        ExtraInvPanelPrefs.onPanelStateChanged(false, true, 1, setter(store));

        ExtraInvPanelPrefs.Snapshot snap = ExtraInvPanelPrefs.read(getter(store));
        assertEquals(1, snap.panelState);
        assertEquals(NInventory.Grouping.Q5, snap.grouping);
        assertEquals("10", snap.minQualityText);
    }

    @Test
    void extraPanelExcludesCharacterSheetAndStudyDesks() {
        assertTrue(ExtraInvGroupTransfer.shouldInstallExtraPanel("Cupboard", false));
        assertTrue(ExtraInvGroupTransfer.shouldInstallExtraPanel("Chest", false));
        assertFalse(ExtraInvGroupTransfer.shouldInstallExtraPanel("Belt", false));
        assertFalse(ExtraInvGroupTransfer.shouldInstallExtraPanel("Pouch", false));
        assertFalse(ExtraInvGroupTransfer.shouldInstallExtraPanel("Frame", false));
        assertFalse(ExtraInvGroupTransfer.shouldInstallExtraPanel("Stack", false));
        assertFalse(ExtraInvGroupTransfer.shouldInstallExtraPanel("Table", false));
        assertFalse(ExtraInvGroupTransfer.shouldInstallExtraPanel("Cupboard", true));
        assertFalse(ExtraInvGroupTransfer.shouldInstallExtraPanel(null, false));
        assertFalse(ExtraInvGroupTransfer.shouldInstallExtraPanel("Character Sheet", false));
        assertFalse(ExtraInvGroupTransfer.shouldInstallExtraPanel("Study Desk", false));
        assertFalse(ExtraInvGroupTransfer.shouldInstallExtraPanel("Fine Study Desk", false));
        assertFalse(ExtraInvGroupTransfer.shouldInstallExtraPanel("Grand Study Desk", false));
        assertTrue(ExtraInvGroupTransfer.skipExtraPanelForHost(true, false));
        assertTrue(ExtraInvGroupTransfer.skipExtraPanelForHost(false, true));
        assertFalse(ExtraInvGroupTransfer.skipExtraPanelForHost(false, false));
    }

    @Test
    void keysRoundTripForJsonLoad() {
        assertEquals(NConfig.Key.extraInvPanelState, NConfig.Key.valueOf("extraInvPanelState"));
        assertEquals(NConfig.Key.extraInvGrouping, NConfig.Key.valueOf("extraInvGrouping"));
        assertEquals(NConfig.Key.extraInvDisplayType, NConfig.Key.valueOf("extraInvDisplayType"));
        assertEquals(NConfig.Key.extraInvMinQuality, NConfig.Key.valueOf("extraInvMinQuality"));
    }
}
