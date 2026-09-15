package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class WormFarmerConstructTest {
    @Test
    void instantiatesWithAndWithoutSettings() {
        assertNotNull(new WormFarmer());
        assertNotNull(new WormFarmer(Map.of()));
    }
}
