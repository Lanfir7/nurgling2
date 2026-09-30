package nurgling.render;

import haven.GOut;
import haven.render.Texture2D;
import haven.render.sl.ShaderMacro;
import haven.render.sl.Uniform;
import haven.RenderContext.PostProcessor;
import static haven.render.sl.Type.*;
import static nurgling.render.NPostFX.*;

/* Sharpen-only, per-channel GLSL port of AMD FidelityFX CAS (CasFilter,
 * CAS_SLOW + CAS_GO_SLOWER). The existing post chain is display-referred;
 * use AMD's documented gamma-2 input/output conversion. The strength slider
 * additionally blends with the original so zero really means no sharpening.
 * Source: https://github.com/GPUOpen-Effects/FidelityFX-CAS/blob/master/ffx-cas/ffx_cas.h
 *
 * Copyright (c) 2017-2019 Advanced Micro Devices, Inc. All rights reserved.
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
public class Cas extends PostProcessor {
    float amount;

    static final RawFunction function = new RawFunction(VEC4, "hv_cas", 4,
        "vec3 hv_cas_load(sampler2D tex, ivec2 p) {\n" +
        "    vec3 c = clamp(texelFetch(tex, clamp(p, ivec2(0), textureSize(tex, 0) - 1), 0).rgb, 0.0, 1.0);\n" +
        "    return c * c;\n" +
        "}\n" +
        "vec4 hv_cas(vec4 col, vec2 tc, sampler2D tex, float amount) {\n" +
        "    ivec2 p = clamp(ivec2(tc * vec2(textureSize(tex, 0))), ivec2(0), textureSize(tex, 0) - 1);\n" +
        "    vec4 original = texelFetch(tex, p, 0);\n" +
        "    float strength = clamp(amount, 0.0, 1.0);\n" +
        "    if(strength <= 0.0) return original;\n" +
        "    vec3 b = hv_cas_load(tex, p + ivec2(0, -1));\n" +
        "    vec3 d = hv_cas_load(tex, p + ivec2(-1, 0));\n" +
        "    vec3 e = hv_cas_load(tex, p);\n" +
        "    vec3 f = hv_cas_load(tex, p + ivec2(1, 0));\n" +
        "    vec3 h = hv_cas_load(tex, p + ivec2(0, 1));\n" +
        "    vec3 mn = min(min(min(d, e), f), min(b, h));\n" +
        "    vec3 mx = max(max(max(d, e), f), max(b, h));\n" +
        "    vec3 amp = sqrt(clamp(min(mn, 1.0 - mx) / max(mx, vec3(1e-6)), 0.0, 1.0));\n" +
        "    vec3 w = amp * (-1.0 / mix(8.0, 5.0, strength));\n" +
        "    vec3 filtered = clamp(((b + d + f + h) * w + e) / (1.0 + 4.0 * w), 0.0, 1.0);\n" +
        "    return vec4(mix(original.rgb, sqrt(filtered), strength), original.a);\n" +
        "}\n");
    static final Uniform source = u(SAMPLER2D, 0), strength = u(FLOAT, 1);
    static final ShaderMacro shader = shader(function, source, strength);

    static float safeAmount(float value) {
        return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0.4f;
    }

    public int order() { return 20; }

    public void run(GOut g, Texture2D.Sampler2D in) {
        blit(g, in, new Pass(shader, in, safeAmount(amount)));
    }
}
