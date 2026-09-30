package haven.render.vk;

import haven.render.sl.ShaderMacro;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProgramKeyTest {
    @Test void sameIdentityVariantsDeduplicateEvenWithTrailingNulls() {
        ShaderMacro macro = program -> {};
        VkEnvironment.ProgramKey a = new VkEnvironment.ProgramKey(new ShaderMacro[] {macro});
        VkEnvironment.ProgramKey b = new VkEnvironment.ProgramKey(new ShaderMacro[] {macro, null, null});
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test void keySnapshotsItsInputAndDistinguishesSlotOrder() {
        ShaderMacro a = program -> {}, b = program -> {};
        ShaderMacro[] input = {a, b};
        VkEnvironment.ProgramKey key = new VkEnvironment.ProgramKey(input);
        Map<VkEnvironment.ProgramKey, String> requests = new HashMap<>();
        requests.put(key, "first");
        input[0] = b;
        assertEquals("first", requests.get(new VkEnvironment.ProgramKey(new ShaderMacro[] {a, b})));
        assertNull(requests.get(new VkEnvironment.ProgramKey(new ShaderMacro[] {b, a})));
    }
}
