package haven.res.gfx.fx.bprad;

import haven.Area;
import haven.Coord2d;
import haven.FColor;
import haven.Glob;
import haven.Gob;
import haven.MCache;
import haven.render.DataBuffer;
import haven.render.Environment;
import haven.render.Model;
import haven.render.Pipe;
import haven.render.Render;
import haven.render.Texture;
import haven.render.VectorFormat;
import haven.render.sl.FragData;
import nurgling.NConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BPRadFlatWorldTest {
    @Test
    void existingMilestoneCircleFlattensWhenFlatWorldIsEnabled() throws Exception {
        NConfig previous = NConfig.current;
        try {
            NConfig.current = new NConfig();
            NConfig.set(NConfig.Key.flatsurface, false);
            Glob glob = new Glob(null);
            replaceMap(glob, new SlopedMap());
            Gob milestone = new Gob(glob, Coord2d.of(100, 100));
            BPRad circle = new BPRad(milestone, null, 11f);

            circle.gtick(NOOP_RENDER);
            assertEquals(21f, circle.posa.data.get(2), 0.001f);

            NConfig.set(NConfig.Key.flatsurface, true);
            circle.gtick(NOOP_RENDER);

            int verticesPerEdge = circle.posa.size() / 2;
            assertEquals(10f, circle.posa.data.get(2), 0.001f);
            assertEquals(-10f, circle.posa.data.get((verticesPerEdge * 3) + 2), 0.001f);
        } finally {
            NConfig.current = previous;
        }
    }

    private static void replaceMap(Glob glob, MCache map) throws Exception {
        Field field = Glob.class.getField("map");
        field.setAccessible(true);
        field.set(glob, map);
    }

    private static class SlopedMap extends MCache {
        SlopedMap() {
            super(null);
        }

        @Override
        public double getcz(double px, double py) {
            return px;
        }
    }

    private static final Render NOOP_RENDER = new Render() {
        public Environment env() { return null; }
        public void submit(Render sub) { }
        public void draw(Pipe pipe, Model data) { }
        public void clear(Pipe pipe, FragData buf, FColor val) { }
        public void clear(Pipe pipe, double val) { }
        public <T extends DataBuffer> void update(T buf, DataBuffer.PartFiller<? super T> data, int from, int to) { }
        public <T extends DataBuffer> void update(T buf, DataBuffer.Filler<? super T> data) { }
        public void pget(Pipe pipe, FragData buf, Area area, VectorFormat fmt, ByteBuffer dstbuf, Consumer<ByteBuffer> callback) { }
        public void pget(Texture.Image img, VectorFormat fmt, ByteBuffer dstbuf, Consumer<ByteBuffer> callback) { }
        public void timestamp(Consumer<Long> callback) { }
        public void fence(Runnable callback) { }
        public void dispose() { }
    };
}
