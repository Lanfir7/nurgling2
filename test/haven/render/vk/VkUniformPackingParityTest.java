package haven.render.vk;

import haven.render.sl.Array;
import haven.render.sl.Type;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class VkUniformPackingParityTest {
    private static final byte SENTINEL = (byte)0xa5;

    /* Frozen pre-port packing of the float[] and float[][] uniform paths. */
    private static void legacyPack(ByteBuffer buf, int off, Type type, Object val) {
        if(type instanceof Array) {
            Array ary = (Array)type;
            int stride = stride(ary.el);
            if(val instanceof Object[]) {
                Object[] v = (Object[])val;
                for(int i = 0; (i < ary.sz) && (i < v.length); i++)
                    legacyPack(buf, off + (i * stride), ary.el, v[i]);
                return;
            }
            float[] v = (float[])val;
            int n = width(ary.el);
            if((ary.el == Type.MAT3) && (v.length >= ary.sz * 9)) {
                for(int i = 0; i < ary.sz; i++)
                    legacyPack(buf, off + (i * stride), ary.el, Arrays.copyOfRange(v, i * 9, (i + 1) * 9));
                return;
            }
            for(int i = 0; (i < ary.sz) && (((i + 1) * n) <= v.length); i++)
                putFloats(buf, off + (i * stride), Arrays.copyOfRange(v, i * n, (i + 1) * n), n);
            return;
        }
        float[] v = (float[])val;
        if(type == Type.MAT3) {
            for(int c = 0; c < 3; c++)
                for(int r = 0; r < 3; r++)
                    buf.putFloat(off + (c * 16) + (r * 4), v[(c * 3) + r]);
        } else {
            putFloats(buf, off, v, width(type));
        }
    }

    private static void putFloats(ByteBuffer buf, int off, float[] v, int n) {
        for(int i = 0; i < n; i++)
            buf.putFloat(off + (i * 4), v[i]);
    }

    private static int width(Type type) {
        if(type == Type.FLOAT) return 1;
        if(type == Type.VEC2) return 2;
        if(type == Type.VEC3) return 3;
        if(type == Type.VEC4) return 4;
        if(type == Type.MAT3) return 12;
        if(type == Type.MAT4) return 16;
        throw new AssertionError(type);
    }

    private static int stride(Type type) {
        return ((width(type) * 4 + 15) / 16) * 16;
    }

    private static float[] values(int count) {
        float[] ret = new float[count];
        float[] special = {Float.intBitsToFloat(0x80000000), Float.intBitsToFloat(0x7fc12345),
                           Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for(int i = 0; i < count; i++)
            ret[i] = (i % 7 < special.length) ? special[i % 7] : (i * 1.25f - 7.5f);
        return ret;
    }

    private static float[] matrix(int index) {
        float[] ret = values(16);
        ret[4] = index * 2.5f;
        ret[12] = index * -1.75f;
        return ret;
    }

    private static byte[] allBytes(ByteBuffer buf) {
        ByteBuffer copy = buf.duplicate();
        copy.clear();
        byte[] ret = new byte[copy.remaining()];
        copy.get(ret);
        return ret;
    }

    private static ByteBuffer fresh(boolean direct, boolean sliced, ByteOrder order, int limit) {
        ByteBuffer backing = direct ? ByteBuffer.allocateDirect(320) : ByteBuffer.allocate(320);
        for(int i = 0; i < backing.capacity(); i++)
            backing.put(i, SENTINEL);
        if(sliced) {
            backing.position(9);
            backing.limit(300);
            backing = backing.slice();
        }
        backing.order(order);
        backing.position(3);
        backing.limit(limit);
        return backing;
    }

    private static void assertParity(String label, Type type, Object value, int limit) {
        int[] inputBits = null;
        int[][] nestedBits = null;
        if(value instanceof float[]) {
            float[] input = (float[])value;
            inputBits = new int[input.length];
            for(int i = 0; i < input.length; i++) inputBits[i] = Float.floatToRawIntBits(input[i]);
        } else if(value instanceof float[][]) {
            float[][] input = (float[][])value;
            nestedBits = new int[input.length][];
            for(int i = 0; i < input.length; i++) {
                nestedBits[i] = new int[input[i].length];
                for(int j = 0; j < input[i].length; j++) nestedBits[i][j] = Float.floatToRawIntBits(input[i][j]);
            }
        }
        for(boolean direct : new boolean[] {false, true})
            for(boolean sliced : new boolean[] {false, true})
                for(ByteOrder order : new ByteOrder[] {ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN}) {
                    ByteBuffer expected = fresh(direct, sliced, order, limit);
                    ByteBuffer actual = fresh(direct, sliced, order, limit);
                    int off = 13;
                    Throwable oldError = assertThrowsOrNull(() -> legacyPack(expected, off, type, value));
                    Throwable newError = assertThrowsOrNull(() -> VkProgram.pack(actual, off, type, value, label));
                    String caseName = label + " direct=" + direct + " sliced=" + sliced + " order=" + order;
                    assertEquals(oldError == null ? null : oldError.getClass(),
                                 newError == null ? null : newError.getClass(), caseName + " exception");
                    assertArrayEquals(allBytes(expected), allBytes(actual), caseName + " bytes and padding");
                    assertEquals(3, actual.position(), caseName + " position");
                    assertEquals(limit, actual.limit(), caseName + " limit");
                }
        if(inputBits != null) {
            float[] input = (float[])value;
            for(int i = 0; i < input.length; i++)
                assertEquals(inputBits[i], Float.floatToRawIntBits(input[i]), label + " input " + i);
        }
        if(nestedBits != null) {
            float[][] input = (float[][])value;
            for(int i = 0; i < input.length; i++)
                for(int j = 0; j < input[i].length; j++)
                    assertEquals(nestedBits[i][j], Float.floatToRawIntBits(input[i][j]), label + " input " + i + ":" + j);
        }
    }

    private static Throwable assertThrowsOrNull(Runnable action) {
        try {action.run(); return null;} catch(RuntimeException e) {return e;}
    }

    @Test void flatArraysKeepStd140BytesAndPartialLengthBehavior() {
        for(Type type : new Type[] {Type.FLOAT, Type.VEC2, Type.VEC3, Type.VEC4, Type.MAT4}) {
            int count = 3;
            int full = count * width(type);
            assertParity(type + " full", new Array(type, count), values(full), 291);
            assertParity(type + " empty", new Array(type, count), values(0), 291);
            assertParity(type + " short", new Array(type, count), values(full - 1), 291);
            assertParity(type + " extra", new Array(type, count), values(full + width(type)), 291);
        }
    }

    @Test void matrixAndExistingObjectArrayPathsKeepTheirBytes() {
        assertParity("mat3 packed", new Array(Type.MAT3, 2), values(18), 291);
        assertParity("mat3 short fallback", new Array(Type.MAT3, 2), values(12), 291);
        assertParity("mat3 extra", new Array(Type.MAT3, 2), values(25), 291);
        assertParity("matrix objects", new Array(Type.MAT4, 3),
                     new float[][] {matrix(1), matrix(2), matrix(3)}, 291);
        assertParity("single mat4", Type.MAT4, values(16), 291);
        assertParity("single vec3", Type.VEC3, values(3), 291);
    }

    @Test void bufferLimitFailureKeepsExceptionAndPartialWrites() {
        assertParity("limit inside second matrix", new Array(Type.MAT4, 3), values(48), 13 + 64 + 20);
        assertParity("limit inside first vector", new Array(Type.VEC4, 2), values(8), 13 + 6);
    }
}
