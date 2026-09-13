package nurgling.iteminfo;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NFoodInfoLayoutTest {

    private static final Object CHRWDG = new Object();
    private static final Object BATTR = new Object();

    @Test
    void nullChrwdgSkipsEvenWhenConsPopulated() {
        OptionalDouble found = NFoodInfoLayout.layoutConsEfficiency(
                null, BATTR, Arrays.asList(0.4), new int[]{0});
        assertFalse(found.isPresent());
    }

    @Test
    void nullBattrSkipsEvenWhenConsPopulated() {
        OptionalDouble found = NFoodInfoLayout.layoutConsEfficiency(
                CHRWDG, null, Arrays.asList(0.4), new int[]{0});
        assertFalse(found.isPresent());
    }

    @Test
    void nullConsSkipsWhenChrwdgAndBattrPresent() {
        OptionalDouble found = NFoodInfoLayout.layoutConsEfficiency(
                CHRWDG, BATTR, null, new int[]{0});
        assertFalse(found.isPresent());
    }

    @Test
    void populatedConsReturnsMatchingEfficiencyPercent() {
        List<Double> consAs = Arrays.asList(0.4, 0.8);
        OptionalDouble found = NFoodInfoLayout.layoutConsEfficiency(
                CHRWDG, BATTR, consAs, new int[]{1});
        assertEquals(80.0, found.getAsDouble(), 0.0);
    }

    @Test
    void lastMatchingTypeWins() {
        List<Double> consAs = Arrays.asList(0.4, 0.7);
        OptionalDouble found = NFoodInfoLayout.layoutConsEfficiency(
                CHRWDG, BATTR, consAs, new int[]{0, 1});
        assertEquals(70.0, found.getAsDouble(), 0.0);
    }

    @Test
    void typePastConsSizeSkips() {
        OptionalDouble found = NFoodInfoLayout.layoutConsEfficiency(
                CHRWDG, BATTR, Collections.singletonList(0.5), new int[]{3});
        assertFalse(found.isPresent());
    }

    @Test
    void nullConsEntryDoesNotSetEfficiency() {
        OptionalDouble found = NFoodInfoLayout.layoutConsEfficiency(
                CHRWDG, BATTR, Collections.<Double>singletonList(null), new int[]{0});
        assertFalse(found.isPresent());
    }
}
