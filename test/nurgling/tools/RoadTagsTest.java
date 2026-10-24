package nurgling.tools;

import haven.Coord;
import haven.Coord2d;
import haven.Gob;
import nurgling.NConfig;
import nurgling.NGob;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RoadTagsTest {
    @TempDir Path temp;
    private final NConfig prior = NConfig.current;

    @AfterEach void restore() { NConfig.current = prior; }

    private static void usePath(NConfig config, Path path) throws Exception {
        Field field = NConfig.class.getDeclaredField("path");
        field.setAccessible(true);
        field.set(config, path.toString());
    }

    @Test
    void postAndRoadKeysSeparateWorldPlacesAndLiteralSuffixes() {
        Gob gob = NGob.getDummy(Coord2d.of(0, 0), 0, (String) null);
        assertNull(RoadTags.postKey(gob));
        gob.ngob.grid_id = 91;
        gob.ngob.gcoord = Coord.of(-7, 12);
        assertEquals("91:-7,12", RoadTags.postKey(gob));
        assertNotEquals(RoadTags.postKey(91, -7, 12), RoadTags.postKey(92, -7, 12));
        assertNotEquals(RoadTags.roadKey("A", 1), RoadTags.roadKey("A#2", 0));
        assertNotEquals(RoadTags.roadKey("A:1", 0), RoadTags.roadKey("A", 1));
    }

    @Test
    void copiesNestedStoreAndSurvivesWrongTypesAndBlankDeletion() {
        String post = RoadTags.postKey(91, -7, 12);
        String first = RoadTags.roadKey("A", 0);
        String second = RoadTags.roadKey("A", 1);
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("other-setting", "keep");
        original.put("posts", Map.of(post, Map.of(first, "old")));
        Map<String, Object> changed = RoadTags.set(original, post, second, "  " + "X".repeat(45) + "  ");
        assertEquals("old", RoadTags.get(original, post, first));
        assertNull(RoadTags.get(original, post, second));
        assertEquals(40, RoadTags.get(changed, post, second).length());
        assertEquals("keep", changed.get("other-setting"));
        assertEquals("old", RoadTags.get(changed, post, first));

        Map<String, Object> deleted = RoadTags.set(changed, post, second, "  ");
        assertNull(RoadTags.get(deleted, post, second));
        deleted = RoadTags.set(deleted, post, first, "");
        assertFalse(((Map<?, ?>) deleted.get("posts")).containsKey(post));
        assertNull(RoadTags.get(Map.of("posts", "bad"), post, first));
        assertNull(RoadTags.get(Map.of("posts", Map.of(post, 3)), post, first));
        assertEquals("new", RoadTags.get(RoadTags.set(Map.of("posts", 4), post, first, "new"), post, first));
    }

    @Test
    void privateTagsRoundTripWithoutChangingOtherProfileSettings() throws Exception {
        Path file = temp.resolve("config.json");
        Files.writeString(file, new JSONObject().put("gridbox", true).put("showBB", true)
                .put("milestones", "old routes").put("tunnelingOreStop", "ore choice").toString());
        NConfig config = new NConfig();
        usePath(config, file);
        NConfig.current = config;
        config.read();
        assertEquals("", NConfig.get(NConfig.Key.roadTags));
        String post = RoadTags.postKey(91, -7, 12);
        String road = RoadTags.roadKey("East", 0);
        RoadTags.set(post, road, "  ore route  ");
        config.write();

        NConfig loaded = new NConfig();
        usePath(loaded, file);
        NConfig.current = loaded;
        loaded.read();
        assertEquals("ore route", RoadTags.get(post, road));
        assertEquals(true, NConfig.get(NConfig.Key.gridbox));
        assertEquals(true, NConfig.get(NConfig.Key.showBB));
        assertEquals("old routes", NConfig.get(NConfig.Key.milestones));
        assertEquals("ore choice", NConfig.get(NConfig.Key.tunnelingOreStop));

        NConfig.set(NConfig.Key.roadTags, "{not JSON");
        assertNull(RoadTags.get(post, road));
        RoadTags.set(post, road, "repaired");
        assertEquals("repaired", RoadTags.get(post, road));
        loaded.write();

        NConfig otherProfile = new NConfig();
        usePath(otherProfile, temp.resolve("other-profile.json"));
        NConfig.current = otherProfile;
        otherProfile.read();
        assertNull(RoadTags.get(post, road));
        RoadTags.set(post, road, "other profile");
        otherProfile.write();
        NConfig.current = loaded;
        loaded.read();
        assertEquals("repaired", RoadTags.get(post, road));
    }
}
