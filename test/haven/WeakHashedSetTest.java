package haven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeakHashedSetTest {
    private static class CountedValue {
        static int comparisons;
        final int hash;

        CountedValue(int hash) { this.hash = hash; }

        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            comparisons++;
            return (other instanceof CountedValue) && hash == ((CountedValue) other).hash;
        }
    }

    @Test
    void skipsEqualityForDifferentHashesInSameBucket() {
        WeakHashedSet<CountedValue> set = new WeakHashedSet<>(Hash.eq);
        CountedValue first = new CountedValue(0);
        set.add(first);
        CountedValue.comparisons = 0;
        assertNull(set.find(new CountedValue(32)));
        assertEquals(0, CountedValue.comparisons);
        assertSame(first, set.find(new CountedValue(0)));
    }

    @Test
    void identityHashSurvivesResizeWhenObjectHashCodeDiffers() {
        WeakHashedSet<CountedValue> set = new WeakHashedSet<>(Hash.id);
        CountedValue[] values = new CountedValue[20];
        for (int i = 0; i < values.length; i++) {
            values[i] = new CountedValue(0);
            set.add(values[i]);
        }
        for (CountedValue value : values)
            assertSame(value, set.find(value));
    }
}
