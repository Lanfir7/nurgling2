package nurgling.actions.bots.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotTypeAnimalsTest {
    @Test
    void animalsExistsAtLivestockOrdinalAndLivestockConstantIsGone() {
        BotDescriptor.BotType[] values = BotDescriptor.BotType.values();

        boolean hasAnimals = false;
        boolean hasLivestock = false;
        for (BotDescriptor.BotType type : values) {
            if (type == BotDescriptor.BotType.ANIMALS) {
                hasAnimals = true;
            }
            if ("LIVESTOCK".equals(type.name())) {
                hasLivestock = true;
            }
        }

        assertTrue(hasAnimals);
        assertFalse(hasLivestock);
        assertEquals("ANIMALS", BotDescriptor.BotType.ANIMALS.name());
        assertEquals(5, BotDescriptor.BotType.ANIMALS.ordinal());
        assertEquals(BotDescriptor.BotType.ANIMALS, values[5]);
    }

    @Test
    void livestockIsNotABotTypeConstant() {
        assertEquals(BotDescriptor.BotType.ANIMALS, BotDescriptor.BotType.valueOf("ANIMALS"));
        assertThrows(IllegalArgumentException.class, () -> BotDescriptor.BotType.valueOf("LIVESTOCK"));
    }
}
