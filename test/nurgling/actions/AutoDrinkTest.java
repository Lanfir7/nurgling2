package nurgling.actions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AutoDrinkTest {
    @Test
    void autodrinkDoesNotResetCraftBuildMenuToHome() {
        assertFalse(AutoDrink.resetMenuAfterDrink());
    }
}
