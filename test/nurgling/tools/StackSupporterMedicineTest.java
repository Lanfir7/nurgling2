package nurgling.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StackSupporterMedicineTest {
    @Test
    void medicineCategoryDoesNotImplyStacking() {
        assertEquals(1, StackSupporter.getFullStackSize("Honey Wayband"));
    }
}
