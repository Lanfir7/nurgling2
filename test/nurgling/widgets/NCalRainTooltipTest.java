package nurgling.widgets;

import haven.Cal;
import haven.Coord;
import haven.Tex;
import nurgling.NConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NCalRainTooltipTest {

    @Test
    void acceptsRainIconForTooltipDispatchWithoutWideningEmptySpace() throws Exception {
        NConfig.getGlobalInstance();
        Object previousVerboseMode = NConfig.get(NConfig.Key.verboseCal);
        try {
            NConfig.set(NConfig.Key.verboseCal, false);
            assertHitAcceptance(false);
            NConfig.set(NConfig.Key.verboseCal, true);
            assertHitAcceptance(true);
        } finally {
            NConfig.set(NConfig.Key.verboseCal, previousVerboseMode);
        }
    }

    private static void assertHitAcceptance(boolean verbose) throws Exception {
        NCal cal = new NCal();
        cal.resize(verbose ? NCal.VERBOSE_SZ : NCal.COMPACT_SZ);
        eventNames(cal).add("rain");

        Coord graphicCenter = graphicCenter(cal, verbose);
        Coord rainIconCenter = verbose
                ? graphicCenter.add(0, background().sz().y / 2 + staticInt("ICON_GAP") + (staticInt("ICON_SZ") / 2))
                : graphicCenter.add(background().sz().x / 2 + staticInt("PAD"), -haven.UI.scale(10));

        assertTrue(cal.checkhit(rainIconCenter), "rain icon must pass Widget's tooltip hit gate");
        assertTrue(cal.checkhit(graphicCenter), "calendar graphic remains interactive");
        assertFalse(cal.checkhit(Coord.z), "transparent empty space remains non-interactive");
    }

    @Test
    void choosesLocalizedActionForCurrentRainEffectState() throws Exception {
        Properties english = load("src/lang/messages.properties");
        Properties russian = load("src/lang/messages_ru.properties");

        assertEquals("Click to disable rain effects", english.getProperty(RainTooltip.key(true)));
        assertEquals("Click to enable rain effects", english.getProperty(RainTooltip.key(false)));
        assertEquals("Нажмите, чтобы отключить эффекты дождя", russian.getProperty(RainTooltip.key(true)));
        assertEquals("Нажмите, чтобы включить эффекты дождя", russian.getProperty(RainTooltip.key(false)));
    }

    private static Properties load(String path) throws Exception {
        Path file = Paths.get(path);
        Properties properties = new Properties();
        try (InputStreamReader in = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
            properties.load(in);
        }
        return properties;
    }

    @SuppressWarnings("unchecked")
    private static ArrayList<String> eventNames(NCal cal) throws Exception {
        Field field = Cal.class.getDeclaredField("eventNames");
        field.setAccessible(true);
        return (ArrayList<String>)field.get(cal);
    }

    private static Tex background() throws Exception {
        Field field = Cal.class.getDeclaredField("bg");
        field.setAccessible(true);
        return (Tex)field.get(null);
    }

    private static Coord graphicCenter(NCal cal, boolean verbose) throws Exception {
        if(!verbose)
            return cal.sz.div(2);
        Tex bg = background();
        int top = (cal.sz.y - (bg.sz().y + staticInt("ICON_GAP") + staticInt("ICON_SZ"))) / 2;
        return new Coord(staticInt("TIME_COL_W") + staticInt("PAD") + (bg.sz().x / 2), top + (bg.sz().y / 2));
    }

    private static int staticInt(String name) throws Exception {
        Field field = NCal.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(null);
    }
}
