package nurgling.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftTargetTest {

    @Test
    void emptyFieldMeansOne() {
        assertEquals(Integer.valueOf(1), CraftTarget.parse(null));
        assertEquals(Integer.valueOf(1), CraftTarget.parse(""));
        assertEquals(Integer.valueOf(1), CraftTarget.parse("   "));
    }

    @Test
    void numericTextIsTheCount() {
        assertEquals(Integer.valueOf(10), CraftTarget.parse("10"));
        assertEquals(Integer.valueOf(10), CraftTarget.parse(" 10 "));
        assertEquals(Integer.valueOf(1), CraftTarget.parse("1"));
    }

    @Test
    void garbageIsInvalid() {
        assertNull(CraftTarget.parse("abc"));
        assertNull(CraftTarget.parse("10x"));
        assertNull(CraftTarget.parse("1.5"));
        assertNull(CraftTarget.parse("0"));
        assertNull(CraftTarget.parse("-3"));
    }

    @Test
    void storedCountCapsBatchIterations() {
        assertEquals(10, CraftTarget.capIterations(10, 1000));
        assertEquals(10, CraftTarget.capIterations(10, 10));
        assertEquals(4, CraftTarget.capIterations(10, 4));
        assertEquals(0, CraftTarget.capIterations(10, 0));
        assertEquals(0, CraftTarget.capIterations(0, 10));
    }

    @Test
    void craftAllDoesNotCapAtStoredCount() {
        assertTrue(CraftTarget.isAll(CraftTarget.ALL));
        assertFalse(CraftTarget.isAll(10));
        assertEquals(50, CraftTarget.capIterations(CraftTarget.ALL, 50));
        assertFalse(CraftTarget.reachedCap(50, CraftTarget.ALL));
        assertTrue(CraftTarget.reachedCap(10, 10));
        assertFalse(CraftTarget.reachedCap(9, 10));
    }
}
