package haven.res.ui.inspect;

import haven.Drawable;
import haven.Gob;
import haven.Resource;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalInspectTest {
    @Test
    void iconlessDrawableUsesResourceTooltip() throws Exception {
        Gob gob = emptyGob();
        gob.attr.put(Drawable.class, new TestDrawable(gob, resourceWithTooltip("Oak log")));

        assertEquals("Oak log", LocalInspect.gobname(gob));
    }

    @Test
    void iconlessDrawableFallsBackToTheReadableResourceName() throws Exception {
        Gob gob = emptyGob();
        gob.attr.put(Drawable.class, new TestDrawable(gob, resourceWithName("gfx/terobjs/trees/oaklog")));

        assertEquals("Oaklog", LocalInspect.gobname(gob));
    }

    private static Gob emptyGob() throws Exception {
        Gob gob = (Gob) unsafe().allocateInstance(Gob.class);
        gob.attr = new HashMap<>();
        return gob;
    }

    private static Resource resourceWithTooltip(String name) throws Exception {
        Resource resource = (Resource) unsafe().allocateInstance(Resource.class);
        Resource.Tooltip tooltip = (Resource.Tooltip) unsafe().allocateInstance(Resource.Tooltip.class);
        set(tooltip, "t", name);
        set(resource, "layers", new ArrayList<Resource.Layer>() {{ add(tooltip); }});
        return resource;
    }

    private static Resource resourceWithName(String name) throws Exception {
        Resource resource = (Resource) unsafe().allocateInstance(Resource.class);
        set(resource, "name", name);
        set(resource, "layers", new ArrayList<Resource.Layer>());
        return resource;
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        unsafe().putObject(target, unsafe().objectFieldOffset(field), value);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static class TestDrawable extends Drawable {
        private final Resource resource;

        TestDrawable(Gob gob, Resource resource) {
            super(gob);
            this.resource = resource;
        }

        @Override
        public Resource getres() {
            return resource;
        }
    }
}
