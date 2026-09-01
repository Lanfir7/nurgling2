package nurgling.contextmenu;

import haven.Coord2d;
import haven.Gob;
import nurgling.tools.LiftableCatalog;
import nurgling.tools.NAlias;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarryManyActionTest {

    @Test
    void appliesToLiftableGobsOnly() {
        CarryManyAction action = new CarryManyAction();
        assertTrue(action.appliesTo(gob("gfx/terobjs/barrel")));
        assertTrue(action.appliesTo(gob("gfx/terobjs/trees/oaklog")));
        assertTrue(action.appliesTo(gob("gfx/terobjs/bushes/arrowwood")));
        assertFalse(action.appliesTo(gob("gfx/terobjs/kiln")));
        assertFalse(action.appliesTo(gob("gfx/terobjs/trees/oakstump")));
        assertFalse(action.appliesTo(gob("gfx/borka/body")));
        assertFalse(action.appliesTo(gob(null)));
    }

    @Test
    void transferFilterUsesClickedResourceName() {
        NAlias oak = LiftableCatalog.objectFilter("gfx/terobjs/trees/oaklog");
        assertTrue(oak.matches("gfx/terobjs/trees/oaklog"));
        assertFalse(oak.matches("gfx/terobjs/trees/pinelog"));
        assertFalse(oak.matches("gfx/terobjs/trees/birchlog"));
    }

    @Test
    void isBotNotUiOnlyAndHasNoCarrierDialog() throws Exception {
        String src = read("src/nurgling/contextmenu/CarryManyAction.java");
        assertFalse(src.contains("isUiAction"), "must keep default isUiAction=false (M badge)");
        assertFalse(src.contains("Carrier"), src);
        assertFalse(src.contains("NCarrierProp"), src);
        assertTrue(src.contains("requireGlobalZones") || src.contains("createArea"), src);
        assertTrue(src.contains("baubles/inputArea"), src);
        assertTrue(src.contains("baubles/outputArea"), src);
        assertTrue(src.contains("LiftObject"), src);
        assertTrue(src.contains("FindPlaceAndAction"), src);
        assertTrue(src.contains("LiftableCatalog.objectFilter"), src);
        assertTrue(src.contains("context.carry_many"), src);
    }

    @Test
    void registeredBeforeConfigureGob() throws Exception {
        String src = read("src/nurgling/contextmenu/GobContextRegistry.java");
        int carryMany = src.indexOf("register(new CarryManyAction());");
        int configure = src.indexOf("register(new ConfigureGobAction());");
        assertTrue(carryMany >= 0 && configure >= 0, src);
        assertTrue(carryMany < configure, src);
    }

    @Test
    void labelsAreCarryMany() throws Exception {
        Properties en = load("src/lang/messages.properties");
        Properties ru = load("src/lang/messages_ru.properties");
        assertEquals("Carry many", en.getProperty("context.carry_many"));
        assertEquals("Перенести много", ru.getProperty("context.carry_many"));
    }

    private static Gob gob(String name) {
        Gob g = new Gob(null, Coord2d.of(0, 0), 1);
        g.ngob.name = name;
        return g;
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
