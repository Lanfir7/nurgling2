package nurgling.contextmenu;

import nurgling.tools.NAlias;
import nurgling.tools.NParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level checks so this file compiles without constructing a Gob.
 */
class UnloadAnimalsActionTest {

    @Test
    void appliesToCartAndWagonNotRandomGobs() {
        NAlias vehicle = new NAlias("vehicle");
        assertTrue(NParser.checkName("gfx/terobjs/vehicle/cart", vehicle));
        assertTrue(NParser.checkName("gfx/terobjs/vehicle/wagon", vehicle));
        assertFalse(NParser.checkName("gfx/terobjs/kiln", vehicle));
        assertFalse(NParser.checkName("gfx/terobjs/stockpile", vehicle));
        assertFalse(NParser.checkName("gfx/kritter/horse/horse", vehicle));
        assertTrue(UnloadAnimalsAction.matches("gfx/terobjs/vehicle/cart"));
        assertTrue(UnloadAnimalsAction.matches("gfx/terobjs/vehicle/wagon"));
        assertFalse(UnloadAnimalsAction.matches("gfx/terobjs/kiln"));
        assertFalse(UnloadAnimalsAction.matches(null));
    }

    @Test
    void destinationSpecIsDeadkritterAnimalCarcasses() throws Exception {
        String src = read("src/nurgling/contextmenu/UnloadAnimalsAction.java");
        assertTrue(src.contains("findSpec(\"deadkritter\")"), src);
        assertFalse(src.contains("findArea"), src);
        assertFalse(src.contains("findSpecGlobal"), src);
        assertTrue(src.contains("deadkritter"), src);
        String spec = read("src/nurgling/widgets/Specialisation.java");
        assertTrue(spec.contains("deadkritter,"), spec);
        assertTrue(spec.contains("SpecName.deadkritter.toString(),\"Animal carcasses\""), spec);
    }

    @Test
    void registeredImmediatelyAfterUnloadVehicle() throws Exception {
        String src = read("src/nurgling/contextmenu/GobContextRegistry.java");
        int unload = src.indexOf("register(new UnloadVehicleAction());");
        int animals = src.indexOf("register(new UnloadAnimalsAction());");
        assertTrue(unload >= 0 && animals >= 0, src);
        assertTrue(unload < animals, src);
        String between = src.substring(unload, animals);
        assertEquals(1, between.split("register\\(", -1).length - 1, between);
    }

    @Test
    void isBotNotUiOnlyAndUnloadsIntoSpecWithoutSelectArea() throws Exception {
        String src = read("src/nurgling/contextmenu/UnloadAnimalsAction.java");
        assertFalse(src.contains("isUiAction"), "must keep default isUiAction=false (M badge)");
        assertFalse(src.contains("SelectArea"), src);
        assertTrue(src.contains("TakeFromVehicle"), src);
        assertTrue(src.contains("FindPlaceAndAction"), src);
        assertTrue(src.contains("new FindPlaceAndAction(gob, dest, true)"), src);
        assertTrue(src.contains("findLiftedbyPlayer"), src);
        assertTrue(src.contains("NAlias(\"vehicle\")"), src);
        assertTrue(src.contains("Results.ERROR(\"No carcass area\")"), src);
    }

    @Test
    void labelsAreUnloadAnimals() throws Exception {
        Properties en = load("src/lang/messages.properties");
        Properties ru = load("src/lang/messages_ru.properties");
        assertTrue("Unload animals".equals(en.getProperty("context.unload_animals")),
                String.valueOf(en.getProperty("context.unload_animals")));
        assertTrue("Выгрузить животных".equals(ru.getProperty("context.unload_animals")),
                String.valueOf(ru.getProperty("context.unload_animals")));
        String src = read("src/nurgling/contextmenu/UnloadAnimalsAction.java");
        assertTrue(src.contains("context.unload_animals"), src);
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static Properties load(String path) throws Exception {
        Properties p = new Properties();
        try (java.io.InputStreamReader in = new java.io.InputStreamReader(
                Files.newInputStream(Paths.get(path)), StandardCharsets.UTF_8)) {
            p.load(in);
        }
        return p;
    }
}
