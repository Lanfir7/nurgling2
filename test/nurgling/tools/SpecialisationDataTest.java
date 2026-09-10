package nurgling.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialisationDataTest {
    @Test
    void barrelSubtypesIncludeTarForTarKilnCollection() {
        assertTrue(SpecialisationData.data.get("barrel").contains("Tar"));
    }
}
