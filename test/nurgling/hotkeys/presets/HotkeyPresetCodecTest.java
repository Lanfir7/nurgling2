package nurgling.hotkeys.presets;

import haven.KeyMatch;
import nurgling.hotkeys.InputGesture;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class HotkeyPresetCodecTest {
    @Test void shareCodeRoundTripsEveryGestureFamilyAndUnknownIds() {
        Map<String, InputGesture> values = new LinkedHashMap<>();
        values.put("disabled", InputGesture.none());
        values.put("key", InputGesture.key(KeyMatch.forcode(KeyEvent.VK_K, KeyMatch.C)));
        values.put("mouse", InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.C | KeyMatch.S));
        values.put("wheel", InputGesture.wheel(-1, KeyMatch.MODS, KeyMatch.S));
        values.put("modifier", InputGesture.modifier(KeyMatch.M));
        values.put("future.unknown", InputGesture.mouse(4, KeyMatch.MODS, 0));
        HotkeyPreset source = new HotkeyPreset("user-1", "Raid", false, values);

        String code = HotkeyPresetCodec.encode(source);
        HotkeyPreset decoded = HotkeyPresetCodec.decode(code);

        assertTrue(code.startsWith("NURGLING-HOTKEYS-1:"));
        assertEquals("Raid", decoded.name());
        assertFalse(decoded.builtIn());
        assertNotEquals(source.id(), decoded.id());
        assertEquals(values, decoded.gestures());
    }

    @Test void shareCodeIsDeterministicRegardlessOfInputMapOrder() {
        assertEquals(HotkeyPresetCodec.encode(presetInOrder("b", "a")),
                HotkeyPresetCodec.encode(presetInOrder("a", "b")));
    }

    @Test void malformedDuplicateAndOversizedPayloadsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> HotkeyPresetCodec.decode("bad"));
        assertThrows(IllegalArgumentException.class, () -> HotkeyPresetCodec.decode(duplicateBindingCode()));
        assertThrows(IllegalArgumentException.class, () -> HotkeyPresetCodec.decode(oversizedCode()));
    }

    @Test void builtInPresetCannotBeSharedAsUserData() {
        HotkeyPreset builtIn = new HotkeyPreset("builtin.default", "Default", true,
                new LinkedHashMap<>());
        assertThrows(IllegalArgumentException.class, () -> HotkeyPresetCodec.encode(builtIn));
    }

    private static HotkeyPreset presetInOrder(String first, String second) {
        Map<String, InputGesture> values = new LinkedHashMap<>();
        values.put(first, first.equals("a") ? InputGesture.none() : InputGesture.modifier(KeyMatch.S));
        values.put(second, second.equals("a") ? InputGesture.none() : InputGesture.modifier(KeyMatch.S));
        return new HotkeyPreset("user-order", "Order", false, values);
    }

    private static String duplicateBindingCode() {
        return codeForJson("{\"version\":1,\"name\":\"Duplicate\",\"bindings\":[" +
                "{\"id\":\"item.take\",\"gesture\":\"n\"}," +
                "{\"id\":\"item.take\",\"gesture\":\"n\"}]}");
    }

    private static String oversizedCode() {
        char[] chars = new char[HotkeyPresetCodec.MAX_JSON_BYTES + 1];
        Arrays.fill(chars, 'x');
        return codeForJson(new String(chars));
    }

    private static String codeForJson(String json) {
        return HotkeyPresetCodec.PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(gzip(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] gzip(byte[] bytes) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try(GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                gzip.write(bytes);
            }
            return out.toByteArray();
        } catch(IOException failure) {
            throw new AssertionError(failure);
        }
    }
}
