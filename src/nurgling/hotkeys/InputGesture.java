package nurgling.hotkeys;

import haven.KeyMatch;

import java.awt.event.KeyEvent;

/** Immutable, typed description of an input gesture. */
public final class InputGesture {
    public enum Type { NONE, KEY, MOUSE_BUTTON, MOUSE_WHEEL, MODIFIER }

    private final Type type;
    private final KeyMatch key;
    private final int code;
    private final int modmask;
    private final int modmatch;

    private InputGesture(Type type, KeyMatch key, int code, int modmask, int modmatch) {
        this.type = type;
        this.key = key;
        this.code = code;
        this.modmask = modmask;
        this.modmatch = modmatch;
    }

    public static InputGesture none() {
        return new InputGesture(Type.NONE, null, 0, 0, 0);
    }

    public static InputGesture key(KeyMatch key) {
        if(key == null || key == KeyMatch.nil)
            return none();
        KeyMatch copy = copyKey(key);
        return new InputGesture(Type.KEY, copy, 0, copy.modmask, copy.modmatch);
    }

    public static InputGesture mouse(int button, int mask, int match) {
        return new InputGesture(Type.MOUSE_BUTTON, null, button, mask, match);
    }

    public static InputGesture wheel(int direction, int mask, int match) {
        if(direction == 0)
            throw new IllegalArgumentException("wheel direction is zero");
        return new InputGesture(Type.MOUSE_WHEEL, null, Integer.signum(direction), mask, match);
    }

    public static InputGesture modifier(int mod) {
        if(Integer.bitCount(mod) != 1 || (mod & ~KeyMatch.MODS) != 0)
            throw new IllegalArgumentException("modifier gesture must contain one bit");
        return new InputGesture(Type.MODIFIER, null, mod, 0, 0);
    }

    public Type type() {
        return type;
    }

    public KeyMatch key() {
        return copyKey(key);
    }

    public int code() {
        return code;
    }

    public int modmask() {
        return modmask;
    }

    public int modmatch() {
        return modmatch;
    }

    private boolean mods(int actual) {
        return (actual & modmask) == (modmatch & modmask);
    }

    public boolean matches(KeyEvent event, int ignored) {
        return type == Type.KEY && key.match(event, ignored);
    }

    public boolean matchesMouse(int button, int mods) {
        return type == Type.MOUSE_BUTTON && code == button && mods(mods);
    }

    public boolean matchesWheel(int amount, int mods) {
        return type == Type.MOUSE_WHEEL && Integer.signum(amount) == code && mods(mods);
    }

    public boolean matchesModifiers(int mods) {
        return type == Type.MODIFIER && code == (mods & KeyMatch.MODS);
    }

    public String encode() {
        switch(type) {
        case NONE:
            return "n";
        case KEY:
            return "k:" + key.reduce();
        case MOUSE_BUTTON:
            return "b:" + code + ":" + modmask + ":" + modmatch;
        case MOUSE_WHEEL:
            return "w:" + code + ":" + modmask + ":" + modmatch;
        case MODIFIER:
            return "m:" + code;
        default:
            throw new AssertionError(type);
        }
    }

    public static InputGesture decode(String encoded) {
        if(encoded == null || encoded.length() == 0)
            throw new IllegalArgumentException("empty gesture");
        try {
            if(encoded.equals("n"))
                return none();
            if(encoded.startsWith("k:")) {
                KeyMatch key = KeyMatch.restore(encoded.substring(2));
                if(key == null || key == KeyMatch.nil)
                    throw new IllegalArgumentException("invalid key gesture");
                return key(key);
            }
            if(encoded.startsWith("b:") || encoded.startsWith("w:")) {
                String[] parts = encoded.split(":", -1);
                if(parts.length != 4)
                    throw new IllegalArgumentException("invalid gesture field count");
                int code = Integer.parseInt(parts[1]);
                int mask = Integer.parseInt(parts[2]);
                int match = Integer.parseInt(parts[3]);
                return parts[0].equals("b") ? mouse(code, mask, match) : wheel(code, mask, match);
            }
            if(encoded.startsWith("m:")) {
                String[] parts = encoded.split(":", -1);
                if(parts.length != 2)
                    throw new IllegalArgumentException("invalid modifier gesture");
                return modifier(Integer.parseInt(parts[1]));
            }
        } catch(IllegalArgumentException e) {
            throw e;
        } catch(RuntimeException e) {
            throw new IllegalArgumentException("invalid gesture", e);
        }
        throw new IllegalArgumentException("invalid gesture");
    }

    public String displayName() {
        switch(type) {
        case NONE:
            return "None";
        case KEY:
            return key.name();
        case MOUSE_BUTTON:
            return withModifiers(buttonName(code));
        case MOUSE_WHEEL:
            return withModifiers(code > 0 ? "Wheel Up" : "Wheel Down");
        case MODIFIER:
            return modifierName(code);
        default:
            throw new AssertionError(type);
        }
    }

    private String withModifiers(String name) {
        if((modmatch & KeyMatch.MODS) == 0)
            return name;
        return KeyMatch.modname(modmatch & KeyMatch.MODS) + "+" + name;
    }

    private static String buttonName(int button) {
        switch(button) {
        case 1:
            return "LMB";
        case 2:
            return "MMB";
        case 3:
            return "RMB";
        default:
            return "Button " + button;
        }
    }

    private static String modifierName(int mod) {
        switch(mod) {
        case KeyMatch.S:
            return "Shift";
        case KeyMatch.C:
            return "Ctrl";
        case KeyMatch.M:
            return "Alt";
        default:
            throw new AssertionError(mod);
        }
    }

    private static KeyMatch copyKey(KeyMatch source) {
        if(source == null)
            return null;
        KeyMatch copy = new KeyMatch(source.chr, source.casematch, source.code,
                source.extmatch, source.keyname, source.modmask, source.modmatch);
        copy.chr = source.chr;
        copy.casematch = source.casematch;
        copy.code = source.code;
        copy.extmatch = source.extmatch;
        copy.keyname = source.keyname;
        copy.modmask = source.modmask;
        copy.modmatch = source.modmatch;
        return copy;
    }

    @Override
    public boolean equals(Object other) {
        if(this == other)
            return true;
        if(!(other instanceof InputGesture))
            return false;
        InputGesture that = (InputGesture)other;
        return type == that.type && code == that.code && modmask == that.modmask &&
                modmatch == that.modmatch && keyEquals(key, that.key);
    }

    private static boolean keyEquals(KeyMatch left, KeyMatch right) {
        return left == null ? right == null : left.equals(right);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + code;
        result = 31 * result + modmask;
        result = 31 * result + modmatch;
        if(key != null) {
            result = 31 * result + key.chr;
            result = 31 * result + (key.casematch ? 1 : 0);
            result = 31 * result + key.code;
            result = 31 * result + (key.extmatch ? 1 : 0);
            result = 31 * result + key.modmask;
            result = 31 * result + key.modmatch;
        }
        return result;
    }
}
