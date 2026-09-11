package nurgling.actions;

import nurgling.tools.NAlias;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static nurgling.actions.LettucePumpkinAndWatermelonCollector.Product;

class LettucePumpkinAndWatermelonCollectorTest {
    @Test
    void seedFilterStaysOnTheSelectedCropAndIgnoresUnrelatedSeeds() {
        assertTrue(new NAlias("Seed").matches("Carrot Seeds"));
        assertTrue(new NAlias("Seed").matches("Pumpkin Seeds"));

        assertTrue(Product.LETTUCE.seedItems().matches("Lettuce Seeds"));
        assertFalse(Product.LETTUCE.seedItems().matches("Pumpkin Seeds"));
        assertFalse(Product.LETTUCE.seedItems().matches("Carrot Seeds"));

        assertTrue(Product.PUMPKIN.seedItems().matches("Pumpkin Seeds"));
        assertFalse(Product.PUMPKIN.seedItems().matches("Watermelon Seeds"));
        assertFalse(Product.PUMPKIN.seedItems().matches("Lettuce Seeds"));

        assertTrue(Product.WATERMELON.seedItems().matches("Watermelon Seeds"));
        assertFalse(Product.WATERMELON.seedItems().matches("Pumpkin Seeds"));
        assertFalse(Product.WATERMELON.seedItems().matches("Carrot Seeds"));
    }

    @Test
    void lettuceAndPumpkinKeepTheirCutVerbsAndByProducts() {
        assertTrue("Split".equals(Product.LETTUCE.verb));
        assertTrue("Lettuce Leaf".equals(Product.LETTUCE.byProduct));
        assertTrue(Product.LETTUCE.cutWhenHalfFull);

        assertTrue("Slice".equals(Product.PUMPKIN.verb));
        assertTrue("Pumpkin Flesh".equals(Product.PUMPKIN.byProduct));
        assertFalse(Product.PUMPKIN.cutWhenHalfFull);

        assertTrue("Slice".equals(Product.WATERMELON.verb));
        assertTrue("Watermelon Slice".equals(Product.WATERMELON.byProduct));
        assertFalse(Product.WATERMELON.cutWhenHalfFull);
    }

    @Test
    void radishFarmerTreatsTroughAsOptional() throws Exception {
        String src = Files.readString(
                Path.of("src/nurgling/actions/bots/farmers/RadishFarmer.java"),
                StandardCharsets.UTF_8);
        assertTrue(src.contains("opt.add(trough)"));
        assertFalse(src.contains("req.add(trough)"));
    }

    @Test
    void whiteOnionFarmerTreatsTroughAsOptionalLikeItsQVariant() throws Exception {
        String src = Files.readString(
                Path.of("src/nurgling/actions/bots/farmers/WhiteOnionFarmer.java"),
                StandardCharsets.UTF_8);
        assertTrue(src.contains("opt.add(trough)"));
        assertFalse(src.contains("req.add(trough)"));

        String q = Files.readString(
                Path.of("src/nurgling/actions/bots/WhiteOnionFarmerQ.java"),
                StandardCharsets.UTF_8);
        assertTrue(q.contains("opt.add(trough)"));
        assertFalse(q.contains("req.add(trough)"));
    }

    @Test
    void watermelonFarmerTreatsTroughAsOptionalWhileKeepingRequiredZones() throws Exception {
        String src = Files.readString(
                Path.of("src/nurgling/actions/bots/farmers/WatermelonFarmer.java"),
                StandardCharsets.UTF_8);
        assertTrue(src.contains("req.add(field)"));
        assertTrue(src.contains("req.add(seed)"));
        assertTrue(src.contains("PUT Area for Watermelon Slice required"));
        assertTrue(src.contains("opt.add(trough)"));
        assertFalse(src.contains("req.add(trough)"));
    }
}
