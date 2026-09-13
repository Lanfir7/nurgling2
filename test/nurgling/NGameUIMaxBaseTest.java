package nurgling;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NGameUIMaxBaseTest {

    @Test
    void nullBasesReturnZero() {
        assertEquals(0, NGameUIMaxBase.maxOf(null));
    }

    @Test
    void emptyBasesReturnZero() {
        assertEquals(0, NGameUIMaxBase.maxOf(Collections.<Integer>emptyList()));
    }

    @Test
    void allNullEntriesReturnZero() {
        assertEquals(0, NGameUIMaxBase.maxOf(Arrays.asList(null, null)));
    }

    @Test
    void singleBaseIsReturned() {
        assertEquals(40, NGameUIMaxBase.maxOf(Collections.singletonList(Integer.valueOf(40))));
    }

    @Test
    void multiBaseReturnsMaximumAndSkipsNulls() {
        assertEquals(90, NGameUIMaxBase.maxOf(Arrays.asList(null, 10, 90, 40)));
    }
}
