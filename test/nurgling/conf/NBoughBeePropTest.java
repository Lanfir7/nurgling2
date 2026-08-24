package nurgling.conf;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NBoughBeePropTest {
    @Test
    void runDefaultsMatchSmokerGui() {
        NBoughBeeProp prop = NBoughBeeProp.runDefaults();
        assertEquals("logout", prop.onPlayerAction);
        assertEquals("logout", prop.onAnimalAction);
        assertEquals("logout", prop.afterHarvestAction);
        assertTrue(prop.harvestTrees);
        assertFalse(NBoughBeeProp.useSettingsGui());
    }

    @Test
    void harvestTreesDefaultsOn() {
        NBoughBeeProp prop = new NBoughBeeProp("u", "c");
        assertTrue(prop.harvestTrees);
        assertEquals("logout", prop.onPlayerAction);
        assertEquals("logout", prop.afterHarvestAction);
    }

    @Test
    void harvestTreesPersistsThroughMapAndJson() {
        HashMap<String, Object> values = new HashMap<>();
        values.put("username", "u");
        values.put("chrid", "c");
        values.put("harvestTrees", true);

        NBoughBeeProp loaded = new NBoughBeeProp(values);
        assertTrue(loaded.harvestTrees);

        JSONObject json = loaded.toJson();
        assertTrue(json.getBoolean("harvestTrees"));
    }
}
