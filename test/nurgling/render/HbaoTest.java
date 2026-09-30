package nurgling.render;

import haven.*;
import haven.render.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_fragment_shader;

class HbaoTest {
    private static PView view(Coord size, boolean perspective) {
        PView view = new PView(size) {};
        Matrix4f matrix = perspective ? Projection.makefrustum(new Matrix4f(), -1, 1, -1, 1, 1, 1000)
                : Projection.makeortho(new Matrix4f(), -20, 20, -15, 15, 1, 1000);
        view.basic.ostate(new Projection(matrix));
        view.depth = new Texture2D(size, DataBuffer.Usage.STATIC, Texture.DEPTH, null);
        return view;
    }

    @Test void bothCameraTypesAndResolutionsRouteThroughHorizonAndBilateralPasses() throws Exception {
        for(boolean perspective : new boolean[] {false, true}) {
            PView view = view(Coord.of(7, 3), perspective);
            NPostFX.DepthFX effect = new NPostFX.DepthFX(view);
            effect.aomethod = 1; effect.aostr = 0.8f;
            try {
                for(int quality : new int[] {0, 1}) {
                    effect.aoq = quality;
                    for(Coord size : new Coord[] {Coord.of(7, 3), Coord.of(1, 1)}) {
                        Texture2D.Sampler2D source = NPostFX.mktarget(size, NumberFormat.UNORM8);
                        try {
                            PostFxTestSupport.RecordingRender render = new PostFxTestSupport.RecordingRender();
                            effect.run(PostFxTestSupport.output(render, size), source);
                            assertEquals(2, render.draws.size());
                            assertSame(Hbao.shader, PostFxTestSupport.pass(render.draws.get(0)).shader());
                            assertSame(Hbao.composite, PostFxTestSupport.pass(render.draws.get(1)).shader());
                            float[] parameters = (float[])Hbao.parameters.value.apply(render.draws.get(0));
                            assertArrayEquals(new float[] {0.8f, 7}, parameters);
                            assertEquals((float)quality, Hbao.quality.value.apply(render.draws.get(0)));
                            float[] projection = (float[])Hbao.projection.value.apply(render.draws.get(0));
                            assertEquals(perspective ? 0 : 1, projection[2]);
                            Texture2D.Sampler2D ao = (Texture2D.Sampler2D)NPostFX.dc_ao.value.apply(render.draws.get(1));
                            assertEquals(quality == 0 ? Coord.of(Math.max(1, size.x / 2), Math.max(1, size.y / 2)) : size, ao.tex.sz());
                            for(Pipe pipe : render.draws) PostFxTestSupport.compileDraw(pipe);
                        } finally { source.dispose(); }
                    }
                }
                effect.aomethod = 0;
                Texture2D.Sampler2D source = NPostFX.mktarget(Coord.of(7, 3), NumberFormat.UNORM8);
                try {
                    PostFxTestSupport.RecordingRender render = new PostFxTestSupport.RecordingRender();
                    effect.run(PostFxTestSupport.output(render, source.tex.sz()), source);
                    assertSame(NPostFX.ao_sh, PostFxTestSupport.pass(render.draws.get(0)).shader());
                    assertSame(NPostFX.dc_sh, PostFxTestSupport.pass(render.draws.get(1)).shader());
                } finally { source.dispose(); }
            } finally { effect.dispose(); view.depth.dispose(); view.dispose(); }
        }
    }

    @Test void missingOrMultisampledDepthPassesTheImageThrough() {
        PView view = new PView(Coord.of(1, 1)) {};
        NPostFX.DepthFX effect = new NPostFX.DepthFX(view);
        effect.aomethod = 1;
        Texture2D.Sampler2D source = NPostFX.mktarget(Coord.of(1, 1), NumberFormat.UNORM8);
        try {
            for(Texture depth : new Texture[] {null, new Texture2DMS(Coord.of(1, 1), 4, Texture.DEPTH)}) {
                view.depth = depth;
                PostFxTestSupport.RecordingRender render = new PostFxTestSupport.RecordingRender();
                effect.run(PostFxTestSupport.output(render, source.tex.sz()), source);
                assertEquals(1, render.draws.size());
                assertNull(render.draws.get(0).get(RUtils.adhoc));
                if(depth != null) depth.dispose();
            }
        } finally { source.dispose(); effect.dispose(); view.dispose(); }
    }

    @Test void bothHbaoShadersCompileForVulkan() {
        String header = "#version 450\nlayout(set=0,binding=0) uniform sampler2D depth;\n" +
                "layout(set=0,binding=1) uniform sampler2D ao;\nlayout(location=0) out vec4 result;\n";
        PostFxTestSupport.compile(header + Hbao.function.src +
                "void main(){result=hv_hbao(vec4(1),vec2(0.5),depth,vec4(-1,-2,0,0),vec2(1),vec2(1,7),1);}\n",
                shaderc_fragment_shader, true);
        PostFxTestSupport.compile(header + Hbao.compositeFunction.src +
                "void main(){result=hv_hcomposite(vec4(1),vec2(0.5),depth,ao,vec4(-1,-2,0,0),vec2(1));}\n",
                shaderc_fragment_shader, true);
    }

    @Test void depthSamplerCleanupNeverDisposesTheViewsDepthTexture() throws Exception {
        PView view = view(Coord.of(7, 3), false);
        NPostFX.DepthFX effect = new NPostFX.DepthFX(view);
        effect.aomethod = 1; effect.aostr = Float.NaN;
        Texture2D.Sampler2D source = NPostFX.mktarget(Coord.of(7, 3), NumberFormat.UNORM8);
        Texture originalDepth = view.depth;
        AtomicInteger depthReleased = new AtomicInteger(), samplerReleased = new AtomicInteger();
        originalDepth.ro = depthReleased::incrementAndGet;
        Field dsamp = NPostFX.DepthFX.class.getDeclaredField("dsamp");
        dsamp.setAccessible(true);
        try {
            PostFxTestSupport.RecordingRender render = new PostFxTestSupport.RecordingRender();
            effect.run(PostFxTestSupport.output(render, source.tex.sz()), source);
            assertEquals(1f, ((float[])Hbao.parameters.value.apply(render.draws.get(0)))[0]);
            ((Texture2D.Sampler2D)dsamp.get(effect)).ro = samplerReleased::incrementAndGet;
            view.depth = new Texture2D(Coord.of(7, 3), DataBuffer.Usage.STATIC, Texture.DEPTH, null);
            effect.run(PostFxTestSupport.output(render, source.tex.sz()), source);
            assertEquals(1, samplerReleased.get());
            assertEquals(0, depthReleased.get());
            ((Texture2D.Sampler2D)dsamp.get(effect)).ro = samplerReleased::incrementAndGet;
            effect.dispose(); effect.dispose();
            assertEquals(2, samplerReleased.get());
            assertEquals(0, depthReleased.get());
        } finally { source.dispose(); effect.dispose(); originalDepth.dispose(); view.depth.dispose(); view.dispose(); }
    }
}
