package nurgling;

import haven.Gob;
import haven.Material;
import haven.MessageBuf;
import haven.Resource;
import haven.res.lib.vmat.AttrMats;
import haven.res.lib.vmat.VarMats;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NInspectMaterialsTest {
    @Test
    void modernVariableMaterialsAppearOnceWithTheirResourceTooltip() throws Exception {
        Gob gob = emptyGob();
        Resource.Virtual birch = new Resource.Virtual(null, "gfx/invobjs/wood-birch", 1);
        birch.add(birch.new Tooltip(new MessageBuf("Birch wood".getBytes(StandardCharsets.UTF_8))));
        Material.ResMaterial material = (Material.ResMaterial)new Material.Res(birch, new MessageBuf(new byte[]{0, 0})).get();
        HashMap<Integer, Material> selected = new HashMap<>();
        selected.put(0, material);
        selected.put(1, material);
        gob.attr.put(VarMats.class, new AttrMats(gob, selected));

        assertEquals(List.of("Birch wood"), NInspectMaterials.names(gob));
    }

    @Test
    void materialWithoutResourceIdentityIsNotGuessed() throws Exception {
        Gob gob = emptyGob();
        gob.attr.put(VarMats.class, new AttrMats(gob, Collections.singletonMap(0, new Material())));

        assertEquals(Collections.emptyList(), NInspectMaterials.names(gob));
    }

    private static Gob emptyGob() throws Exception {
        Unsafe unsafe = unsafe();
        Gob gob = (Gob)unsafe.allocateInstance(Gob.class);
        gob.attr = new HashMap<>();
        Field overlays = Gob.class.getDeclaredField("ols");
        unsafe.putObject(gob, unsafe.objectFieldOffset(overlays), Collections.emptyList());
        return gob;
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe)field.get(null);
    }
}
