package nurgling.db.service;

import nurgling.areas.NArea;
import nurgling.areas.PileFillDirection;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AreaServicePileFillDirectionTest {
    @Test void serverDataIncludesPileFillDirection() {
        NArea area = new NArea("zone");
        area.id = 1;
        area.space = new NArea.Space();
        area.pileFillDirection = PileFillDirection.RIGHT_TO_LEFT;
        JSONObject data = AreaService.buildDataJson(area);
        assertEquals("RIGHT_TO_LEFT", data.getString(NArea.PILE_FILL_DIRECTION_JSON));
    }
}
