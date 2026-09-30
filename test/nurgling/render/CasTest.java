package nurgling.render;

import haven.Coord;
import haven.render.NumberFormat;
import haven.render.Texture2D;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_fragment_shader;

class CasTest {
    @Test void strengthAndSourceReachTheRealPassIncludingTinyImages() throws Exception {
        Cas effect = new Cas();
        try {
            for(Coord size : new Coord[] {Coord.of(1, 1), Coord.of(7, 3)}) {
                Texture2D.Sampler2D source = NPostFX.mktarget(size, NumberFormat.UNORM8);
                try {
                    for(float requested : new float[] {0, 0.45f, 1, -9, 100, Float.NaN, Float.POSITIVE_INFINITY}) {
                        effect.amount = requested;
                        PostFxTestSupport.RecordingRender render = new PostFxTestSupport.RecordingRender();
                        effect.run(PostFxTestSupport.output(render, size), source);
                        assertEquals(1, render.draws.size());
                        assertSame(source, Cas.source.value.apply(render.draws.get(0)));
                        float amount = (Float)Cas.strength.value.apply(render.draws.get(0));
                        assertTrue(Float.isFinite(amount) && amount >= 0 && amount <= 1);
                        assertEquals(Cas.safeAmount(requested), amount);
                        PostFxTestSupport.compileDraw(render.draws.get(0));
                    }
                } finally { source.dispose(); }
            }
        } finally { effect.dispose(); }
    }

    @Test void casShaderCompilesForVulkanAndRunsAfterAA() {
        PostFxTestSupport.compile("#version 450\n" +
                "layout(set=0,binding=0) uniform sampler2D source;\n" +
                "layout(location=0) out vec4 result;\n" + Cas.function.src +
                "void main(){result=hv_cas(vec4(1.0),vec2(0.5),source,0.4);}\n",
                shaderc_fragment_shader, true);
        assertTrue(new Cas().order() > new NPostFX.FXAA().order());
    }
}
