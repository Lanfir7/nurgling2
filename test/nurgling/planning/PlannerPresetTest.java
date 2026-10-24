package nurgling.planning;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PlannerPresetTest {
    @TempDir Path root;

    private static PlannerPreset sample(String name) {
        PlannerPreset preset = new PlannerPreset();
        preset.name = name;
        preset.w = 2;
        preset.h = 1;
        PlannerPreset.Layer layer = new PlannerPreset.Layer("Workshop");
        layer.ghosts.add(new PlannerPreset.Ghost("gfx/terobjs/smelter", new byte[]{1, 2}, 2, 3, 0));
        preset.layers.add(layer);
        return preset;
    }

    @Test void roundtripCopiesSpriteBytesAndRejectsFutureOrInvalidDocuments() {
        PlannerPreset original = sample("Smelter");
        PlannerPreset restored = PlannerPreset.fromJson(original.toJson());
        original.layers.get(0).ghosts.get(0).sdt[0] = 9;
        assertEquals(1, restored.layers.get(0).ghosts.get(0).sdt[0]);
        assertEquals("gfx/terobjs/smelter", restored.layers.get(0).ghosts.get(0).res);

        JSONObject future = restored.toJson().put("format", PlannerPreset.FORMAT + 1);
        assertThrows(IllegalArgumentException.class, () -> PlannerPreset.fromJson(future));
        JSONObject malformed = restored.toJson();
        malformed.getJSONArray("layers").getJSONObject(0).getJSONArray("ghosts")
                .getJSONObject(0).put("x", 1_000_000);
        assertThrows(RuntimeException.class, () -> PlannerPreset.fromJson(malformed));
        JSONObject outsideFrame = restored.toJson();
        outsideFrame.getJSONArray("layers").getJSONObject(0).getJSONArray("ghosts")
                .getJSONObject(0).put("x", 100);
        assertThrows(IllegalArgumentException.class, () -> PlannerPreset.fromJson(outsideFrame));
    }

    @Test void quarterTurnAndMirrorPreserveOriginalAndFacing() {
        PlannerPreset original = sample("Workshop");
        PlannerPreset transformed = original.transformed(1, true);
        assertEquals(1, transformed.w);
        assertEquals(2, transformed.h);
        assertEquals(3, transformed.layers.get(0).ghosts.get(0).x, 0.0001);
        assertEquals(2, transformed.layers.get(0).ghosts.get(0).y, 0.0001);
        assertEquals(Math.PI / 2, transformed.layers.get(0).ghosts.get(0).angle, 0.0001);
        assertEquals(2, original.layers.get(0).ghosts.get(0).x);
    }

    @Test void manyTurnsAndHugeFiniteAnglesStayWithinOneWireRevolution() {
        PlannerPreset turns = sample("Turns");
        turns.layers.get(0).ghosts.get(0).angle = 9 * Math.PI;
        PlannerPreset restored = PlannerPreset.fromJson(turns.toJson());
        assertEquals(Math.PI, restored.layers.get(0).ghosts.get(0).angle, 1e-9);
        assertEquals(Math.PI, turns.layers.get(0).ghosts.get(0).angle, 1e-9);

        PlannerPreset huge = sample("Huge");
        huge.layers.get(0).ghosts.get(0).angle = 1e100;
        PlannerPreset parsed = PlannerPreset.fromJson(huge.toJson());
        double angle = parsed.layers.get(0).ghosts.get(0).angle;
        assertTrue(Double.isFinite(angle));
        assertTrue(angle >= 0 && angle < 2 * Math.PI);
        assertTrue(Math.round(angle * 32768 / Math.PI) < 65536);
    }

    @Test void collisionDoesNotOverwriteAndFailedAtomicWriteRemovesOnlyItsReservation() throws Exception {
        PlannerPreset first = sample("Workshop");
        PlannerPresetStore.saveNew(first, root, (path, json) -> Files.writeString(path, json));
        String firstJson = Files.readString(root.resolve(first.file));
        PlannerPreset second = sample("Workshop");
        PlannerPresetStore.saveNew(second, root, (path, json) -> Files.writeString(path, json));
        assertNotEquals(first.file, second.file);
        assertEquals(firstJson, Files.readString(root.resolve(first.file)));

        PlannerPreset failed = sample("Workshop");
        assertThrows(IOException.class, () -> PlannerPresetStore.saveNew(failed, root,
                (path, json) -> { Files.writeString(path, "partial"); throw new IOException("disk full"); }));
        assertNull(failed.file);
        assertEquals(2, Files.list(root).count());
        assertEquals(firstJson, Files.readString(root.resolve(first.file)));
    }
}
