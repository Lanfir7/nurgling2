package nurgling.areas;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class AreaPresetTest {
    private static AreaPreset sample() {
        AreaPreset preset = new AreaPreset();
        preset.name = "Farm";
        AreaPreset.Entry entry = new AreaPreset.Entry();
        entry.name = "Turnips";
        entry.path = "/Beds";
        entry.x = 1;
        entry.y = 2;
        entry.w = 3;
        entry.h = 4;
        entry.maxQuality = 42;
        entry.fill = PileFillDirection.LEFT_TO_RIGHT;
        entry.color = new Color(1, 2, 3, 90);
        entry.spec = new JSONArray().put(new JSONObject().put("name", NArea.SHOW_QUALITY_SPEC));
        preset.areas.add(entry);
        preset.normalize();
        return preset;
    }

    @Test void qualityAndRoutingSurviveJsonAndGeometryTransforms() {
        AreaPreset restored = AreaPreset.fromJson(sample().toJson());
        AreaPreset.Entry turned = restored.transformed(1, true).areas.get(0);
        assertEquals(4, turned.w);
        assertEquals(3, turned.h);
        assertEquals(42, turned.maxQuality);
        assertEquals(PileFillDirection.TOP_TO_BOTTOM, turned.fill);
        assertEquals("/Beds", turned.path);
        assertEquals(42, restored.areas.get(0).maxQuality);

        NArea placed = new NArea(turned.name);
        turned.applyTo(placed, Collections.singletonList(new NArea.Specialisation(NArea.SHOW_QUALITY_SPEC)));
        assertEquals(42, placed.maxQuality);
        assertTrue(placed.showsQuality());
        assertEquals(turned.color, placed.color);
        assertEquals(turned.fill, placed.pileFillDirection);
    }

    @Test void rotationAndMirrorMoveEachRectangleWithoutChangingItsArea() {
        AreaPreset preset = sample();
        preset.areas.get(0).x = 1;
        preset.areas.get(0).y = 2;
        AreaPreset.Entry second = new AreaPreset.Entry();
        second.name = "Store";
        second.x = 8;
        second.y = 0;
        second.w = 2;
        second.h = 2;
        preset.areas.add(second);
        preset.normalize();

        AreaPreset rotated = preset.transformed(1, true);
        assertEquals(6, rotated.w);
        assertEquals(9, rotated.h);
        assertEquals(2, rotated.areas.get(0).x);
        assertEquals(0, rotated.areas.get(0).y);
        assertEquals(4, rotated.areas.get(0).w);
        assertEquals(3, rotated.areas.get(0).h);
        assertEquals(0, rotated.areas.get(1).x);
        assertEquals(7, rotated.areas.get(1).y);
        assertEquals(2, rotated.areas.get(1).w);
        assertEquals(2, rotated.areas.get(1).h);
    }

    @Test void legacyQualityNameRemainsReadableWithoutNewField() {
        AreaPreset.Entry old = sample().areas.get(0);
        old.name = "Turnips [21]";
        old.maxQuality = -1;
        AreaPreset preset = AreaPreset.fromJson(sampleWith(old).toJson());
        assertEquals(-1, preset.areas.get(0).maxQuality);
        NArea placed = new NArea(preset.areas.get(0).name);
        preset.areas.get(0).applyTo(placed, Collections.emptyList());
        assertEquals("Turnips", placed.name);
        assertEquals(21, placed.maxQuality);
        assertTrue(placed.showsQuality());
    }

    private static AreaPreset sampleWith(AreaPreset.Entry entry) {
        AreaPreset preset = new AreaPreset();
        preset.name = "Old";
        preset.areas.add(entry);
        preset.normalize();
        return preset;
    }

    @Test void rejectsAreaWiderThanSpaceCanRepresent() {
        JSONObject json = sample().toJson();
        json.getJSONArray("areas").getJSONObject(0).put("w", 101);
        assertThrows(IllegalArgumentException.class, () -> AreaPreset.fromJson(json));
    }

    @Test void bundledPresetsAllParseAsPlaceableLayouts() throws Exception {
        Path root = Paths.get("src/nurgling/areas/presets");
        JSONObject index = new JSONObject(new String(Files.readAllBytes(root.resolve("index.json")), StandardCharsets.UTF_8));
        JSONArray files = index.getJSONArray("presets");
        assertTrue(files.length() > 40);
        for (int i = 0; i < files.length(); i++) {
            String file = files.getString(i);
            AreaPreset preset = AreaPreset.fromJson(new JSONObject(new String(
                    Files.readAllBytes(root.resolve(file)), StandardCharsets.UTF_8)));
            assertFalse(preset.areas.isEmpty(), file);
            for (AreaPreset.Entry entry : preset.areas) {
                assertTrue(entry.w > 0 && entry.w <= AreaPreset.MAX_AREA_SIDE, file);
                assertTrue(entry.h > 0 && entry.h <= AreaPreset.MAX_AREA_SIDE, file);
            }
        }
    }
}
