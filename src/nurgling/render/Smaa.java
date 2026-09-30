package nurgling.render;

import haven.Coord;
import haven.GOut;
import haven.RUtils;
import haven.TexRaw;
import haven.render.DataBuffer;
import haven.render.NumberFormat;
import haven.render.Texture;
import haven.render.Texture2D;
import haven.render.VectorFormat;
import haven.render.sl.ShaderMacro;
import haven.render.sl.Symbol;
import haven.render.sl.Type;
import haven.render.sl.Uniform;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static haven.render.sl.Type.SAMPLER2D;

/** SMAA 1x, using the original Jimenez et al. edge, weight and neighborhood passes. */
public class Smaa extends haven.RenderContext.PostProcessor {
    private static final String RESOURCE = "/nurgling/render/smaa/";
    static final Uniform METRICS = new Uniform(Type.VEC4, new Symbol.Fix("hv_smaa_metrics"),
        p -> ((NPostFX.Pass)p.get(RUtils.adhoc)).vals[0], RUtils.adhoc);
    private static final Uniform EDGE_COLOR = NPostFX.u(SAMPLER2D, 1);
    private static final Uniform WEIGHT_EDGES = NPostFX.u(SAMPLER2D, 1);
    private static final Uniform WEIGHT_AREA = NPostFX.u(SAMPLER2D, 2);
    private static final Uniform WEIGHT_SEARCH = NPostFX.u(SAMPLER2D, 3);
    private static final Uniform BLEND_COLOR = NPostFX.u(SAMPLER2D, 1);
    private static final Uniform BLEND_WEIGHTS = NPostFX.u(SAMPLER2D, 2);

    private Texture2D.Sampler2D edges, weights, area, search;
    private Coord size;

    public int order() { return 10; }

    private static byte[] resource(String name) {
        try(InputStream in = Smaa.class.getResourceAsStream(RESOURCE + name)) {
            if(in == null)
                throw new IllegalStateException("Missing SMAA resource " + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for(int n; (n = in.read(buf)) != -1;)
                out.write(buf, 0, n);
            return out.toByteArray();
        } catch(IOException e) {
            throw new IllegalStateException("Cannot read SMAA resource " + name, e);
        }
    }

    /* The upstream DX9 DDS files contain uncompressed RG8 and R8 pixels at
     * byte 128. Their rows are kept in upstream order for OpenGL upload. */
    static byte[] lookupPixels(String name, int width, int height, int channels) {
        byte[] dds = resource(name);
        int count = width * height * channels;
        if(dds.length != 128 + count || dds[0] != 'D' || dds[1] != 'D' ||
           dds[2] != 'S' || dds[3] != ' ' || little32(dds, 12) != height ||
           little32(dds, 16) != width || little32(dds, 88) != channels * 8)
            throw new IllegalStateException("Unexpected SMAA DDS layout: " + name);
        byte[] pixels = new byte[count];
        System.arraycopy(dds, 128, pixels, 0, count);
        return pixels;
    }

    private static int little32(byte[] bytes, int at) {
        return (bytes[at] & 255) | ((bytes[at + 1] & 255) << 8) |
               ((bytes[at + 2] & 255) << 16) | ((bytes[at + 3] & 255) << 24);
    }

    private static Texture2D.Sampler2D lookup(String name, int width, int height, int channels) {
        byte[] pixels = lookupPixels(name, width, height, channels);
        Texture2D tex = new Texture2D(Coord.of(width, height), DataBuffer.Usage.STATIC,
                                      new VectorFormat(channels, NumberFormat.UNORM8),
                                      DataBuffer.Filler.of(pixels));
        Texture2D.Sampler2D sampler = tex.sampler();
        sampler.minfilter(Texture.Filter.LINEAR).magfilter(Texture.Filter.LINEAR);
        sampler.swrap(Texture.Wrapping.CLAMP).twrap(Texture.Wrapping.CLAMP);
        return sampler;
    }

    private static final String EDGE_WRAPPER =
        "vec4 hv_smaa_edge(vec4 ignored, vec2 tc, sampler2D src) {\n" +
        "    vec4 offsets[3];\n" +
        "    SMAAEdgeDetectionVS(tc, offsets);\n" +
        "    return vec4(SMAAColorEdgeDetectionPS(tc, offsets, src), 0.0, 0.0);\n" +
        "}\n";
    private static final String WEIGHT_WRAPPER =
        "vec4 hv_smaa_weight(vec4 ignored, vec2 tc, sampler2D edges, sampler2D area, sampler2D search) {\n" +
        "    vec2 pixel; vec4 offsets[3];\n" +
        "    SMAABlendingWeightCalculationVS(tc, pixel, offsets);\n" +
        "    return SMAABlendingWeightCalculationPS(tc, pixel, offsets, edges, area, search, vec4(0.0));\n" +
        "}\n";
    private static final String BLEND_WRAPPER =
        "vec4 hv_smaa_blend(vec4 ignored, vec2 tc, sampler2D src, sampler2D weights) {\n" +
        "    vec4 offset; SMAANeighborhoodBlendingVS(tc, offset);\n" +
        "    vec4 result = SMAANeighborhoodBlendingPS(tc, offset, src, weights);\n" +
        "    return vec4(result.rgb, ignored.a);\n" +
        "}\n";

    private static final ShaderMacro edgeShader = shader(0, "hv_smaa_edge", 3, EDGE_COLOR);
    private static final ShaderMacro weightShader = shader(1, "hv_smaa_weight", 5,
        WEIGHT_EDGES, WEIGHT_AREA, WEIGHT_SEARCH);
    private static final ShaderMacro blendShader = shader(2, "hv_smaa_blend", 4,
        BLEND_COLOR, BLEND_WEIGHTS);

    static String shaderSource(int pass) {
        String upstream = new String(resource("SMAA.hlsl"), StandardCharsets.UTF_8);
        /* GLSL 1.40 treats trailing backslashes in upstream ASCII-art comments
         * as line continuations, even though they are inside comments. */
        upstream = upstream.replace("\\\r\n", "\r\n").replace("\\\n", "\n");
        /* The fullscreen target is not explicitly cleared. Replacing upstream's
         * no-edge discard with zero gives every pixel a defined edge value. */
        upstream = upstream.replace("discard;", "return float2(0.0);");
        String wrapper = (pass == 0) ? EDGE_WRAPPER : (pass == 1) ? WEIGHT_WRAPPER :
                         (pass == 2) ? BLEND_WRAPPER : null;
        if(wrapper == null)
            throw new IllegalArgumentException("Unknown SMAA pass " + pass);
        return "#define SMAA_GLSL_3\n" +
               "#define SMAA_PRESET_HIGH\n" +
               "#define SMAA_RT_METRICS hv_smaa_metrics\n" +
               upstream + "\n" + wrapper;
    }

    static float[] metrics(Coord size) {
        if(size.x <= 0 || size.y <= 0)
            throw new IllegalArgumentException("SMAA size must be positive");
        return new float[] {1.0f / size.x, 1.0f / size.y, size.x, size.y};
    }

    private static ShaderMacro shader(int pass, String name, int nargs, Uniform... samplers) {
        ShaderMacro delegate = NPostFX.shader(new RawFunction(Type.VEC4, name, nargs,
            shaderSource(pass)), samplers);
        return prog -> {
            METRICS.use(prog.fctx);
            delegate.modify(prog);
        };
    }

    private void ensure(Coord requested) {
        if(size != null && size.equals(requested))
            return;
        if(edges != null) edges.dispose();
        if(weights != null) weights.dispose();
        edges = NPostFX.mktarget(requested, NumberFormat.UNORM8);
        weights = NPostFX.mktarget(requested, NumberFormat.UNORM8);
        size = requested;
        if(area == null) area = lookup("AreaTexDX9.dds", 160, 560, 2);
        if(search == null) search = lookup("SearchTex.dds", 64, 16, 1);
    }

    public void run(GOut g, Texture2D.Sampler2D in) {
        Coord requested = in.tex.sz();
        if(requested.x <= 0 || requested.y <= 0) {
            g.image(new TexRaw(in, true), Coord.z, g.sz());
            return;
        }
        ensure(requested);
        float[] metrics = metrics(requested);
        NPostFX.blit(NPostFX.target(g, edges), in, new NPostFX.Pass(edgeShader, metrics, in));
        NPostFX.blit(NPostFX.target(g, weights), edges,
                     new NPostFX.Pass(weightShader, metrics, edges, area, search));
        NPostFX.blit(g, in, new NPostFX.Pass(blendShader, metrics, in, weights));
    }

    public void dispose() {
        super.dispose();
        if(edges != null) { edges.dispose(); edges = null; }
        if(weights != null) { weights.dispose(); weights = null; }
        if(area != null) { area.dispose(); area = null; }
        if(search != null) { search.dispose(); search = null; }
        size = null;
    }
}
