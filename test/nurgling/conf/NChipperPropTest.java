package nurgling.conf;

import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NChipperPropTest {
    @Test
    void mapsLegacyTinkersAxeToThrowingAxe() {
        HashMap<String, Object> values = new HashMap<>();
        values.put("username", "u");
        values.put("chrid", "c");
        values.put("tool", "Tinker's Axe");

        NChipperProp prop = new NChipperProp(values);

        assertEquals("Tinker's Throwing Axe", prop.tool);
    }
}
