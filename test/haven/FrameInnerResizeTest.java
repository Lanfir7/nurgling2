package haven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrameInnerResizeTest {
    private static IBox box(int border) {
        return new IBox() {
            public Coord btloff() { return Coord.of(border, border); }
            public Coord ctloff() { return Coord.of(border, border); }
            public Coord bbroff() { return Coord.of(border, border); }
            public Coord cbroff() { return Coord.of(border, border); }
            public Coord bisz() { return Coord.of(border * 2, border * 2); }
            public Coord cisz() { return Coord.of(border * 2, border * 2); }
            public void draw(GOut g, Coord tl, Coord sz) {}
        };
    }

    @Test
    void resizeGrowsTheChildToFillTheInnerArea() {
        IBox box = box(4);
        Widget child = new Widget(Coord.of(40, 40));
        Frame frame = new Frame(Coord.of(40, 40), true, box);
        frame.add(child, Coord.z);
        assertEquals(Coord.of(48, 48), frame.sz);

        frame.resize(Coord.of(80, 70));

        assertEquals(Coord.of(80, 70), frame.sz);
        assertEquals(frame.inner(), child.sz);
        assertEquals(Coord.of(72, 62), child.sz);
    }
}
