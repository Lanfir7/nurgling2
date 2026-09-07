package nurgling.map;

import haven.Coord;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/** Compact, opaque text representation of a map marker shared through the clipboard. */
public final class SharedMarkerCode {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int DATA_BYTES = 24;
    private static final int RAW_BYTES = DATA_BYTES + Integer.BYTES;
    private static final Pattern CODE = Pattern.compile(
        "(?i)NGM1-([A-Z2-7]{5}(?:-[A-Z2-7]{5}){8})");

    private SharedMarkerCode() {
    }

    public static final class Marker {
        public final String name;
        public final long gridId;
        public final Coord local;
        public final Color color;
        private final long worldKey;

        private Marker(String name, long worldKey, long gridId, Coord local, Color color) {
            this.name = name;
            this.worldKey = worldKey;
            this.gridId = gridId;
            this.local = local;
            this.color = color;
        }

        public boolean belongsTo(String world) {
            return worldKey == worldKey(world);
        }
    }

    public static String encode(String name, String world, long gridId, Coord local, Color color) {
        if (local == null || local.x < 0 || local.x >= 100 || local.y < 0 || local.y >= 100)
            throw new IllegalArgumentException("Marker offset must be inside a 100x100 grid");
        if (color == null)
            throw new IllegalArgumentException("Marker color is required");

        ByteBuffer raw = ByteBuffer.allocate(RAW_BYTES);
        raw.putLong(worldKey(world));
        raw.putLong(gridId);
        raw.putShort((short)local.x);
        raw.putShort((short)local.y);
        raw.putInt(color.getRGB());
        raw.putInt(checksum(raw.array(), DATA_BYTES));

        String compact = base32(raw.array());
        StringBuilder grouped = new StringBuilder(compact.length() + 8);
        for (int i = 0; i < compact.length(); i += 5) {
            if (grouped.length() > 0)
                grouped.append('-');
            grouped.append(compact, i, Math.min(i + 5, compact.length()));
        }
        return cleanName(name) + "-NGM1-" + grouped;
    }

    public static Marker decode(String text) {
        if (text == null)
            throw new IllegalArgumentException("Marker code is missing");
        Matcher match = CODE.matcher(text);
        if (!match.find())
            throw new IllegalArgumentException("Marker code is incomplete");

        byte[] raw = unbase32(match.group(1).replace("-", "").toUpperCase(Locale.ROOT));
        if (raw.length != RAW_BYTES)
            throw new IllegalArgumentException("Marker code has the wrong length");
        int expected = ByteBuffer.wrap(raw, DATA_BYTES, Integer.BYTES).getInt();
        if (expected != checksum(raw, DATA_BYTES))
            throw new IllegalArgumentException("Marker code checksum does not match");

        ByteBuffer data = ByteBuffer.wrap(raw);
        long worldKey = data.getLong();
        long gridId = data.getLong();
        int x = Short.toUnsignedInt(data.getShort());
        int y = Short.toUnsignedInt(data.getShort());
        if (x >= 100 || y >= 100)
            throw new IllegalArgumentException("Marker offset is outside its grid");
        Color color = new Color(data.getInt(), true);
        return new Marker(cleanName(text.substring(0, match.start())), worldKey, gridId,
                          new Coord(x, y), color);
    }

    private static String cleanName(String name) {
        String ret = (name == null) ? "" : name.trim();
        while (!ret.isEmpty()) {
            char c = ret.charAt(ret.length() - 1);
            if (Character.isWhitespace(c) || c == '-' || c == '\u2013' || c == '\u2014'
                || c == ':' || c == '|' || c == '`')
                ret = ret.substring(0, ret.length() - 1).trim();
            else
                break;
        }
        return ret.isEmpty() ? "Marker" : ret;
    }

    private static long worldKey(String world) {
        String normalized = (world == null ? "" : world.trim().toLowerCase(Locale.ROOT));
        long hash = 0xcbf29ce484222325L;
        for (byte b : normalized.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xffL;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static int checksum(byte[] bytes, int length) {
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, length);
        return (int)crc.getValue();
    }

    private static String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                out.append(ALPHABET.charAt((buffer >>> bits) & 31));
            }
        }
        if (bits > 0)
            out.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        return out.toString();
    }

    private static byte[] unbase32(String text) {
        byte[] out = new byte[(text.length() * 5) / 8];
        int buffer = 0;
        int bits = 0;
        int pos = 0;
        for (int i = 0; i < text.length(); i++) {
            int value = ALPHABET.indexOf(text.charAt(i));
            if (value < 0)
                throw new IllegalArgumentException("Marker code contains an invalid character");
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                if (pos >= out.length)
                    throw new IllegalArgumentException("Marker code is too long");
                out[pos++] = (byte)(buffer >>> bits);
            }
        }
        if (pos != out.length || (bits > 0 && (buffer & ((1 << bits) - 1)) != 0))
            throw new IllegalArgumentException("Marker code has invalid padding");
        return out;
    }
}
