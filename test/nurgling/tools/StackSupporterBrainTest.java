package nurgling.tools;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StackSupporterBrainTest {
    @Test
    void bigBrainIsRecognizedWithoutClaimingAStack() {
        assertEquals("gfx/invobjs/brain-large", VSpec.categories.get("Curiosities").stream()
                .filter(o -> "Big Brain".equals(o.getString("name")))
                .findFirst().orElseThrow(AssertionError::new).getString("static"));
        assertTrue(VSpec.getCategory("Big Brain").contains("Curiosities"));
        assertEquals(1, StackSupporter.getFullStackSize("Big Brain"));
    }

    @ParameterizedTest
    @CsvSource({
            "Tiny Brain, gfx/invobjs/brain-tiny, 4",
            "Small Brain, gfx/invobjs/brain-small, 3",
            "Brain, gfx/invobjs/brain, 2",
    })
    void brainsAreInStackableCuriositiesWithCorrectStackSize(String name, String path, int stackSize) {
        ArrayList<JSONObject> entries = VSpec.categories.get("Stackable Curiosities");
        Map<String, String> byName = entries.stream()
                .collect(Collectors.toMap(o -> o.getString("name"), o -> o.getString("static"), (a, b) -> a));
        assertEquals(path, byName.get(name));
        assertEquals(stackSize, StackSupporter.getFullStackSize(name));
        assertTrue(VSpec.getCategory(name).contains("Stackable Curiosities"));
    }
}
