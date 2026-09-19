package nurgling.actions.bots;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectSameItemsFromEarthTest {
    @Test
    void collectionDoesNotDependOnASecondInventoryWidget() throws Exception {
        String source = Files.readString(
                Path.of("src/nurgling/actions/bots/CollectSameItemsFromEarth.java"));
        String compactSource = source.replaceAll("\\s+", " ");
        int aliasBeforePickup = source.indexOf("itemName = new NAlias(target.ngob.name)");
        int pickup = source.indexOf("NUtils.rclickGob(target)");

        assertTrue(aliasBeforePickup >= 0 && aliasBeforePickup < pickup,
                "the selected ground resource must identify the collection before picking up the sample");
        assertTrue(compactSource.contains("itemName, new NAlias(target.ngob.name)"),
                "ground matching must stay tied to the selected resource even when inventory pickup stacks");

        String utils = Files.readString(Path.of("src/nurgling/NUtils.java"));
        assertTrue(!utils.contains("WaitAnotherSize(NUtils.getGameUI().getInventory(), size)"),
                "earth pickup must not wait for a new inventory widget when the item stacks");
    }
}
