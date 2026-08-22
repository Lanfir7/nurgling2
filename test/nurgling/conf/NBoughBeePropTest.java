package nurgling.conf;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NBoughBeePropTest {
    @Test
    void harvestTreesDefaultsOff() {
        NBoughBeeProp prop = new NBoughBeeProp("u", "c");
        assertFalse(prop.harvestTrees);
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
