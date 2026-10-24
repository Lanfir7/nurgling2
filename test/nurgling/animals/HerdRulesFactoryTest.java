package nurgling.animals;

import nurgling.NConfig;
import nurgling.conf.CowsHerd;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HerdRulesFactoryTest {
    @TempDir Path temp;
    private final NConfig previousConfig = NConfig.current;
    private final CowsHerd previousCurrent = CowsHerd.getCurrent();

    @AfterEach void restore() {
        NConfig.current = previousConfig;
        CowsHerd.setCurrent(previousCurrent == null ? null : previousCurrent.name);
    }

    @Test void staleAreaPresetNeverManufacturesRulesOrChangesCurrentOrFile() throws Exception {
        NConfig config = new NConfig();
        Path file = temp.resolve("profile.json");
        Field path = NConfig.class.getDeclaredField("path");
        path.setAccessible(true);
        path.set(config, file.toString());
        NConfig.current = config;
        CowsHerd saved = new CowsHerd("valid");
        saved.adultCows = 7;
        NConfig.set(NConfig.Key.cowsprop, new ArrayList<>(List.of(saved)));
        NConfig.setAreaRankPreset(12, "cows", "deleted");
        CowsHerd.setCurrent("valid");
        Files.writeString(file, "existing profile fixture");
        String before = Files.readString(file);

        assertNull(HerdRulesFactory.forArea(12, "cows"));
        assertEquals("valid", CowsHerd.getCurrent().name);
        assertEquals(before, Files.readString(file));
        assertEquals("valid", HerdRulesFactory.current("cows").preset);
        assertEquals(7, HerdRulesFactory.current("cows").keepFemales);

        NConfig.setAreaRankPreset(12, "cows", "valid");
        assertEquals("valid", HerdRulesFactory.forArea(12, "cows").preset);
        CowsHerd draft = new CowsHerd("unsaved draft");
        draft.adultCows = 99;
        HerdRulesFactory.of(draft);
        assertEquals(7, ((CowsHerd) ((List<?>) NConfig.get(NConfig.Key.cowsprop)).get(0)).adultCows);
        assertEquals("valid", CowsHerd.getCurrent().name);
        assertEquals(before, Files.readString(file)); // preview never persists a draft
    }
}
