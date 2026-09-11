package nurgling.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialisationDataTest {
    @Test
    void barrelSubtypesIncludeTarForTarKilnCollection() {
        assertTrue(SpecialisationData.data.get("barrel").contains("Tar"));
    }

    @Test
    void cropAndSeedSpecialisationsIncludeWatermelonRadishAndWhiteOnion() {
        for (String spec : new String[]{"crop", "seed", "cropQ", "seedQ"}) {
            assertTrue(SpecialisationData.data.get(spec).contains("Watermelon"), spec);
            assertTrue(SpecialisationData.data.get(spec).contains("Radish"), spec);
            assertTrue(SpecialisationData.data.get(spec).contains("White Onion"), spec);
        }
    }
}
