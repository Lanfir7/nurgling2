package nurgling;

import haven.Coord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtraInvPanelPrefsTest {

    private NConfig previous;
    private boolean savedCurrent;
    private NInventory.DisplayType previousDisplayType;

    @AfterEach
    void restoreCurrent() {
        if (savedCurrent) {
            NConfig.current = previous;
        }
        if (previousDisplayType != null) {
            NInventory.currentDisplayType = previousDisplayType;
        }
    }

    private void useFreshConfig() {
        previous = NConfig.current;
        savedCurrent = true;
        previousDisplayType = NInventory.currentDisplayType;
        NConfig.current = new NConfig();
        NInventory.currentDisplayType = NInventory.DisplayType.Name;
    }

    @Test
    void defaultConfigOpensClosedWithEmptyFilters() {
        useFreshConfig();

        ExtraInvPanelPrefs.Snapshot snap = ExtraInvPanelPrefs.load();

        assertEquals(NInventory.PANEL_CLOSED, snap.panelState);
        assertEquals(NInventory.Grouping.NONE, snap.grouping);
        assertEquals(NInventory.DisplayType.Name, snap.displayType);
        assertEquals("", snap.minQualityText);

        NInventory inv = new NInventory(new Coord(1, 1));
        inv.applyContainerExtraPanelPrefs(snap);
        assertEquals(NInventory.PANEL_CLOSED, inv.panelState);
        assertEquals(NInventory.Grouping.NONE, inv.currentGrouping);
        assertEquals("", inv.extraPanelMinQualityText);
    }

    @Test
    void missingConfigOpensClosed() {
        previous = NConfig.current;
        savedCurrent = true;
        previousDisplayType = NInventory.currentDisplayType;
        NConfig.current = null;

        ExtraInvPanelPrefs.Snapshot snap = ExtraInvPanelPrefs.load();
        assertEquals(NInventory.PANEL_CLOSED, snap.panelState);

        NInventory inv = new NInventory(new Coord(1, 1));
        inv.applyContainerExtraPanelPrefs(snap);
        assertEquals(NInventory.PANEL_CLOSED, inv.panelState);
    }

    @Test
    void freshContainerRestoresExpandedQ5AndMinQuality() {
        useFreshConfig();
        ExtraInvPanelPrefs.save(new ExtraInvPanelPrefs.Snapshot(
                NInventory.PANEL_EXPANDED,
                NInventory.Grouping.Q5,
                NInventory.DisplayType.Name,
                "10"));

        NInventory inv = new NInventory(new Coord(1, 1));
        inv.applyContainerExtraPanelPrefs(ExtraInvPanelPrefs.load());

        assertEquals(NInventory.PANEL_EXPANDED, inv.panelState);
        assertEquals(NInventory.Grouping.Q5, inv.currentGrouping);
        assertEquals(NInventory.DisplayType.Name, NInventory.currentDisplayType);
        assertEquals("10", inv.extraPanelMinQualityText);
    }

    @Test
    void setPanelStateOnContainerExtraPanelWritesConfig() {
        useFreshConfig();
        NInventory inv = new NInventory(new Coord(1, 1));
        inv.extraPanelInstalled = true;
        inv.setPanelState(NInventory.PANEL_EXPANDED);

        assertEquals(NInventory.PANEL_EXPANDED, ExtraInvPanelPrefs.load().panelState);
        Object mainInv = NConfig.get(NConfig.Key.inventoryRightPanelShow);
        assertFalse(mainInv instanceof Number && ((Number) mainInv).intValue() == NInventory.PANEL_EXPANDED);
        assertFalse(mainInv instanceof Boolean && (Boolean) mainInv);
    }

    @Test
    void setPanelStateOnMainInvDoesNotWriteContainerKey() {
        useFreshConfig();
        NInventory inv = new NInventory(new Coord(1, 1));
        inv.mainInvInstalled = true;
        inv.setPanelState(NInventory.PANEL_SIMPLIFIED);

        assertEquals(NInventory.PANEL_CLOSED, ExtraInvPanelPrefs.load().panelState);
        assertEquals(NInventory.PANEL_SIMPLIFIED,
                ((Number) NConfig.get(NConfig.Key.inventoryRightPanelShow)).intValue());
    }

    @Test
    void extraPanelExclusionsUnchanged() {
        assertTrue(ExtraInvGroupTransfer.shouldInstallExtraPanel("Cupboard", false));
        assertTrue(ExtraInvGroupTransfer.shouldInstallExtraPanel("Chest", false));
        assertFalse(ExtraInvGroupTransfer.shouldInstallExtraPanel("Belt", false));
        assertFalse(ExtraInvGroupTransfer.shouldInstallExtraPanel("Pouch", false));
        assertFalse(ExtraInvGroupTransfer.shouldInstallExtraPanel("Frame", false));
        assertFalse(ExtraInvGroupTransfer.shouldInstallExtraPanel("Stack", false));
        assertFalse(ExtraInvGroupTransfer.shouldInstallExtraPanel("Table", false));
        assertFalse(ExtraInvGroupTransfer.shouldInstallExtraPanel("Cupboard", true));
        assertFalse(ExtraInvGroupTransfer.shouldInstallExtraPanel(null, false));
    }

    @Test
    void keysRoundTripForJsonLoad() {
        assertEquals(NConfig.Key.extraInvPanelState, NConfig.Key.valueOf("extraInvPanelState"));
        assertEquals(NConfig.Key.extraInvGrouping, NConfig.Key.valueOf("extraInvGrouping"));
        assertEquals(NConfig.Key.extraInvDisplayType, NConfig.Key.valueOf("extraInvDisplayType"));
        assertEquals(NConfig.Key.extraInvMinQuality, NConfig.Key.valueOf("extraInvMinQuality"));
    }
}
