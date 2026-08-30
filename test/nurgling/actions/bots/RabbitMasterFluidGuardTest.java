package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class RabbitMasterFluidGuardTest {
    @Test
    void missingFluidAreasAreSkipped() {
        RabbitMaster rabbitMaster = new RabbitMaster();

        assertDoesNotThrow(() -> rabbitMaster.fillConfiguredFluids(
                null, new ArrayList<>(), null, null));
    }
}
