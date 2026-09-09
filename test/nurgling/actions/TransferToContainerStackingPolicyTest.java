package nurgling.actions;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferToContainerStackingPolicyTest {
    @Test
    void containerTransferUsesItemStackabilityInsteadOfAutomaticStackingToggle() throws Exception {
        String source = Files.readString(Path.of("src/nurgling/actions/TransferToContainer.java"));

        assertFalse(source.contains("maininv).bundle.a"),
                "container transfer must not bypass destination stacks when the UI toggle mirror is false");
        assertTrue(source.contains("StackSupporter.isStackable(targetInv, itemName)"),
                "container transfer must still respect whether the item type is stackable");
    }
}
