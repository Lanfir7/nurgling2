package haven.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstanceListOrphanSlotTest {
    @Test
    void invalidSlotKeepsInvalidRemovalPath() {
	assertEquals(InstanceList.AbsentSlotAction.HANDLE_INVALID,
		     InstanceList.absentInstancableSlot(true));
    }

    @Test
    void orphanedSlotWarnsAndReturnsInsteadOfThrowing() {
	assertEquals(InstanceList.AbsentSlotAction.WARN_AND_RETURN,
		     InstanceList.absentInstancableSlot(false));
    }
}
