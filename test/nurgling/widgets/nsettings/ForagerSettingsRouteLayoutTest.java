package nurgling.widgets.nsettings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForagerSettingsRouteLayoutTest {
    @Test
    void routeCapFieldsEachGetAFullLabelColumnAndStayInsideTheRow() {
        ForagerSettingsPanel.RouteCapSlot[] slots = ForagerSettingsPanel.routeCapSlots();
        assertEquals(3, slots.length, "max branches / max distance / max branch distance");
        for (int i = 0; i < slots.length; i++) {
            ForagerSettingsPanel.RouteCapSlot slot = slots[i];
            assertTrue(slot.labelBudget() >= 200,
                    "field " + i + " label column is too narrow for EN/RU captions");
            assertTrue(slot.entryRight() <= ForagerSettingsPanel.routeRowWidth(),
                    "field " + i + " entry overflows the route row");
        }
    }

    @Test
    void stackedCapFieldsShareTheBrushValueColumn() {
        ForagerSettingsPanel.RouteCapSlot[] slots = ForagerSettingsPanel.routeCapSlots();
        for (ForagerSettingsPanel.RouteCapSlot slot : slots) {
            assertEquals(0, slot.labelX);
            assertEquals(ForagerSettingsPanel.routeValueX(), slot.entryX);
        }
    }

    @Test
    void routeMapFillsLeftoverViewportAndNeverGoesBelowUsableHeight() {
        assertEquals(220, ForagerSettingsPanel.routeMapHeight(220, 180, 360));
        assertEquals(180, ForagerSettingsPanel.routeMapHeight(60, 180, 360));
        assertEquals(360, ForagerSettingsPanel.routeMapHeight(500, 180, 360));
    }

    @Test
    void guardOutcomeSitsOnTheRightWithRoomForTravelHearthAndLongLabels() {
        int rowW = ForagerSettingsPanel.routeRowWidth();
        int outcomeX = ForagerSettingsPanel.guardOutcomeX(rowW);
        assertTrue(ForagerSettingsPanel.guardOutcomeWidth() >= 130);
        assertEquals(rowW, outcomeX + ForagerSettingsPanel.guardOutcomeWidth());
        assertTrue(ForagerSettingsPanel.guardLabelMaxWidth(rowW) >= 250,
                "checkbox+label must not collide with the outcome dropdown");
        assertTrue(ForagerSettingsPanel.guardInputStartX() + 50 + 8 + 70
                < outcomeX, "first input+unit must stay left of the outcome column");
    }
}
