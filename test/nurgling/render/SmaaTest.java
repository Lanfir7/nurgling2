package nurgling.render;

import haven.Coord;
import haven.render.NumberFormat;
import haven.render.Texture;
import haven.render.Texture2D;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.shaderc.Shaderc;

import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SmaaTest {
    @Test void realDrawsCompileAndTargetsFollowSizeAndLifetime() throws Exception {
        Smaa effect = new Smaa();
        Texture2D.Sampler2D first = NPostFX.mktarget(Coord.of(7, 3), NumberFormat.UNORM8);
        Texture2D.Sampler2D second = NPostFX.mktarget(Coord.of(1, 1), NumberFormat.UNORM8);
        try {
            PostFxTestSupport.RecordingRender render = new PostFxTestSupport.RecordingRender();
            effect.run(PostFxTestSupport.output(render, first.tex.sz()), first);
            assertEquals(3, render.draws.size());
            for(haven.render.Pipe draw : render.draws)
                PostFxTestSupport.compileDraw(draw);
            NPostFX.Pass edgePass = PostFxTestSupport.pass(render.draws.get(0));
            NPostFX.Pass weightPass = PostFxTestSupport.pass(render.draws.get(1));
            NPostFX.Pass blendPass = PostFxTestSupport.pass(render.draws.get(2));
            assertSame(first, edgePass.vals[1]);
            Texture2D.Sampler2D edges = (Texture2D.Sampler2D)weightPass.vals[1];
            Texture2D.Sampler2D area = (Texture2D.Sampler2D)weightPass.vals[2];
            Texture2D.Sampler2D search = (Texture2D.Sampler2D)weightPass.vals[3];
            Texture2D.Sampler2D weights = (Texture2D.Sampler2D)blendPass.vals[2];
            assertSame(first, blendPass.vals[1]);
            assertArrayEquals(new float[] {1f / 7f, 1f / 3f, 7f, 3f},
                (float[])edgePass.vals[0]);
            assertSame(edgePass.vals[0], Smaa.METRICS.value.apply(render.draws.get(0)));
            assertEquals(first.tex.sz(), edges.tex.sz());
            assertEquals(first.tex.sz(), weights.tex.sz());
            assertEquals(Coord.of(160, 560), area.tex.sz());
            assertEquals(Coord.of(64, 16), search.tex.sz());
            assertEquals(2, area.tex.ifmt.nc);
            assertEquals(1, search.tex.ifmt.nc);
            assertEquals(Texture.Filter.LINEAR, area.minfilter);
            assertEquals(Texture.Filter.LINEAR, search.minfilter);
            AtomicInteger oldTargetsDisposed = new AtomicInteger();
            edges.tex.ro = oldTargetsDisposed::incrementAndGet;
            weights.tex.ro = oldTargetsDisposed::incrementAndGet;
            AtomicInteger lookupsDisposed = new AtomicInteger();
            area.tex.ro = lookupsDisposed::incrementAndGet;
            search.tex.ro = lookupsDisposed::incrementAndGet;

            PostFxTestSupport.RecordingRender sameSize = new PostFxTestSupport.RecordingRender();
            effect.run(PostFxTestSupport.output(sameSize, first.tex.sz()), first);
            assertSame(edges, PostFxTestSupport.pass(sameSize.draws.get(1)).vals[1]);
            assertSame(weights, PostFxTestSupport.pass(sameSize.draws.get(2)).vals[2]);

            PostFxTestSupport.RecordingRender resized = new PostFxTestSupport.RecordingRender();
            effect.run(PostFxTestSupport.output(resized, second.tex.sz()), second);
            assertEquals(3, resized.draws.size());
            assertEquals(2, oldTargetsDisposed.get());
            assertEquals(0, lookupsDisposed.get());
            assertEquals(Coord.of(1, 1),
                ((Texture2D.Sampler2D)PostFxTestSupport.pass(resized.draws.get(1)).vals[1]).tex.sz());
            assertSame(area, PostFxTestSupport.pass(resized.draws.get(1)).vals[2]);
            assertSame(search, PostFxTestSupport.pass(resized.draws.get(1)).vals[3]);
            assertArrayEquals(new float[] {1f, 1f, 1f, 1f},
                (float[])PostFxTestSupport.pass(resized.draws.get(0)).vals[0]);
            assertSame(PostFxTestSupport.pass(resized.draws.get(0)).vals[0],
                Smaa.METRICS.value.apply(resized.draws.get(0)));
            for(int pass = 0; pass < 3; pass++)
                assertSame(PostFxTestSupport.pass(render.draws.get(pass)).shader(),
                    PostFxTestSupport.pass(resized.draws.get(pass)).shader());
            for(haven.render.Pipe draw : resized.draws)
                PostFxTestSupport.compileDraw(draw);
            effect.dispose();
            assertEquals(2, lookupsDisposed.get());
        } finally {
            effect.dispose();
            first.dispose();
            second.dispose();
        }
    }

    @Test void officialLookupsHaveExpectedPixels() throws Exception {
        byte[] area = Smaa.lookupPixels("AreaTexDX9.dds", 160, 560, 2);
        byte[] search = Smaa.lookupPixels("SearchTex.dds", 64, 16, 1);
        assertEquals(179200, area.length);
        assertEquals(1024, search.length);
        assertEquals("35065cef2a02cabcad711d6bf430239ae64e27d71c4e4fa06f29cce2c992f0d2", sha256(area));
        assertEquals("3694eae5e9d44b8ebb4415a13f8c7b94dc08a2fc86658434d771c4610fe5744d", sha256(search));
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder result = new StringBuilder();
        for(byte b : digest)
            result.append(String.format("%02x", b & 255));
        return result.toString();
    }

    private static String fragment(Coord size, int pass, int version) {
        StringBuilder source = new StringBuilder("#version " + version + "\n");
        source.append("layout(location=0) in vec2 tc; layout(location=0) out vec4 outputColor;\n");
        if(version >= 450)
            source.append("layout(set=0,binding=0) ");
        source.append("uniform SmaaMetrics { vec4 hv_smaa_metrics; };\n");
        source.append(Smaa.shaderSource(pass));
        int count = pass == 0 ? 1 : pass == 1 ? 3 : 2;
        for(int i = 0; i < count; i++) {
            if(version >= 450)
                source.append("layout(set=0,binding=" + (i + 1) + ") ");
            source.append("uniform sampler2D tex" + i + ";\n");
        }
        if(pass == 0)
            source.append("void main() { outputColor = hv_smaa_edge(vec4(0.0), tc, tex0); }\n");
        else if(pass == 1)
            source.append("void main() { outputColor = hv_smaa_weight(vec4(0.0), tc, tex0, tex1, tex2); }\n");
        else
            source.append("void main() { outputColor = hv_smaa_blend(vec4(0.0), tc, tex0, tex1); }\n");
        return source.toString();
    }

    @Test void allPassesCompileForVulkanAtNormalAndTinySizes() {
        long compiler = Shaderc.shaderc_compiler_initialize();
        long options = Shaderc.shaderc_compile_options_initialize();
        assertNotEquals(0L, compiler);
        assertNotEquals(0L, options);
        try {
            Shaderc.shaderc_compile_options_set_target_env(options,
                Shaderc.shaderc_target_env_vulkan, Shaderc.shaderc_env_version_vulkan_1_0);
            for(Coord size : new Coord[] {Coord.of(1920, 1080), Coord.of(1, 1)}) {
                for(int pass = 0; pass < 3; pass++) {
                    long result = Shaderc.shaderc_compile_into_spv(compiler,
                        fragment(size, pass, 450), Shaderc.shaderc_glsl_fragment_shader,
                        "smaa-" + pass + ".frag", "main", options);
                    assertNotEquals(0L, result);
                    try {
                        assertEquals(Shaderc.shaderc_compilation_status_success,
                            Shaderc.shaderc_result_get_compilation_status(result),
                            () -> Shaderc.shaderc_result_get_error_message(result));
                    } finally {
                        Shaderc.shaderc_result_release(result);
                    }
                }
            }
        } finally {
            Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
        }
    }

    @Test void allPassesCompileForOpenGL() {
        long compiler = Shaderc.shaderc_compiler_initialize();
        long options = Shaderc.shaderc_compile_options_initialize();
        try {
            Shaderc.shaderc_compile_options_set_target_env(options,
                Shaderc.shaderc_target_env_opengl, Shaderc.shaderc_env_version_opengl_4_5);
            Shaderc.shaderc_compile_options_set_auto_bind_uniforms(options, true);
            for(int pass = 0; pass < 3; pass++) {
                long result = Shaderc.shaderc_compile_into_spv(compiler,
                    fragment(Coord.of(1280, 720), pass, 450)
                        .replace("layout(set=0,binding=", "layout(binding="),
                    Shaderc.shaderc_glsl_fragment_shader,
                    "smaa-gl-" + pass + ".frag", "main", options);
                assertNotEquals(0L, result);
                try {
                    assertEquals(Shaderc.shaderc_compilation_status_success,
                        Shaderc.shaderc_result_get_compilation_status(result),
                        () -> Shaderc.shaderc_result_get_error_message(result));
                } finally {
                    Shaderc.shaderc_result_release(result);
                }
            }
        } finally {
            Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
        }
    }

    @Test void rejectsNonPositiveTargetsAndBadLookupShape() {
        assertThrows(IllegalArgumentException.class, () -> Smaa.metrics(Coord.of(0, 1)));
        assertThrows(IllegalArgumentException.class, () -> Smaa.shaderSource(-1));
        assertThrows(IllegalStateException.class,
            () -> Smaa.lookupPixels("AreaTexDX9.dds", 160, 560, 1));
    }

    @Test void neighborhoodPassKeepsTheOriginalFragmentAlpha() {
        assertTrue(Smaa.shaderSource(2).contains("return vec4(result.rgb, ignored.a);"));
    }
}
