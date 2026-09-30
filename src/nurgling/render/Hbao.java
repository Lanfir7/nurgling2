package nurgling.render;

import haven.render.sl.ShaderMacro;
import haven.render.sl.Uniform;
import static haven.render.sl.Type.*;
import static nurgling.render.NPostFX.*;

/* Horizon-based ambient occlusion, following the horizon/tangent integral and
 * per-sample radial attenuation in Bavoil & Sainz, SIGGRAPH 2008 (NVIDIA):
 * https://developer.download.nvidia.com/presentations/2008/SIGGRAPH/HBAO_SIG08b.pdf
 * This is our fragment implementation, not NVIDIA's HBAO+ library or CACAO.
 * Sampling is spatial only: no animated noise or previous-frame dependency.
 */
final class Hbao {
    static final RawFunction function = new RawFunction(VEC4, "hv_hbao", 7, DEPTHLIB +
        "float hv_hdepth(sampler2D dep, vec2 tc) {\n" +
        "    ivec2 sz = textureSize(dep, 0);\n" +
        "    return texelFetch(dep, clamp(ivec2(tc * vec2(sz)), ivec2(0), sz - 1), 0).r;\n" +
        "}\n" +
        "vec4 hv_hbao(vec4 col, vec2 tc, sampler2D dep, vec4 pp, vec2 pr, vec2 par, float quality) {\n" +
        "    ivec2 size = textureSize(dep, 0);\n" +
        "    tc = (vec2(clamp(ivec2(tc * vec2(size)), ivec2(0), size - 1)) + 0.5) / vec2(size);\n" +
        "    if(hv_hdepth(dep, tc) >= 0.99999) return vec4(1.0);\n" +
        "    vec2 px = 1.0 / vec2(textureSize(dep, 0));\n" +
        "    vec3 P = hv_vpos(dep, tc, pp, pr);\n" +
        "    vec3 l = hv_vpos(dep, tc - vec2(px.x, 0.0), pp, pr);\n" +
        "    vec3 r = hv_vpos(dep, tc + vec2(px.x, 0.0), pp, pr);\n" +
        "    vec3 b = hv_vpos(dep, tc - vec2(0.0, px.y), pp, pr);\n" +
        "    vec3 t = hv_vpos(dep, tc + vec2(0.0, px.y), pp, pr);\n" +
        "    vec3 dx = (abs(r.z - P.z) < abs(P.z - l.z)) ? r - P : P - l;\n" +
        "    vec3 dy = (abs(t.z - P.z) < abs(P.z - b.z)) ? t - P : P - b;\n" +
        "    vec3 normal = cross(dx, dy);\n" +
        "    if(dot(normal, normal) < 1e-12) return vec4(1.0);\n" +
        "    normal = normalize(normal);\n" +
        "    if(normal.z < 0.0) normal = -normal;\n" +
        "    float radius = max(par.y, 0.001);\n" +
        "    float distance = max(-P.z, 0.001);\n" +
        "    vec2 reach = radius * abs(pr) * 0.5 / ((pp.z > 0.5) ? 1.0 : distance);\n" +
        "    reach = min(reach, 64.0 * px);\n" +
        "    int directions = (quality > 0.5) ? 8 : 4;\n" +
        "    int steps = (quality > 0.5) ? 6 : 4;\n" +
        "    /* Hash stays tied to the depth pixel, never to frame time. */\n" +
        "    float rotation = fract(52.9829189 * fract(dot(floor(tc / px), vec2(0.06711056, 0.00583715))));\n" +
        "    float occlusion = 0.0;\n" +
        "    for(int direction = 0; direction < 8; direction++) {\n" +
        "        if(direction >= directions) break;\n" +
        "        float angle = (float(direction) + rotation) * 6.28318530718 / float(directions);\n" +
        "        vec2 ray = vec2(cos(angle), sin(angle));\n" +
        "        vec2 viewray = normalize(ray * reach / pr);\n" +
        "        float tangent = atan(-dot(normal.xy, viewray), max(normal.z, 0.001));\n" +
        "        float horizon = min(tangent + 0.12, 1.57079632679);\n" +
        "        for(int step = 1; step <= 6; step++) {\n" +
        "            if(step > steps) break;\n" +
        "            vec2 sampletc = tc + ray * reach * (float(step) / float(steps));\n" +
        "            if(any(lessThan(sampletc, vec2(0.0))) || any(greaterThanEqual(sampletc, vec2(1.0)))) continue;\n" +
        "            if(hv_hdepth(dep, sampletc) >= 0.99999) continue;\n" +
        "            vec3 v = hv_vpos(dep, sampletc, pp, pr) - P;\n" +
        "            float vv = dot(v, v);\n" +
        "            if(vv < 1e-10 || vv >= radius * radius) continue;\n" +
        "            float elevation = atan(v.z, length(v.xy));\n" +
        "            if(elevation > horizon) {\n" +
        "                occlusion += (sin(elevation) - sin(horizon)) * (1.0 - vv / (radius * radius));\n" +
        "                horizon = elevation;\n" +
        "            }\n" +
        "        }\n" +
        "    }\n" +
        "    float ao = clamp(1.0 - clamp(par.x, 0.0, 2.0) * occlusion / float(directions), 0.0, 1.0);\n" +
        "    return vec4(ao, ao, ao, 1.0);\n" +
        "}\n");
    static final Uniform depth = u(SAMPLER2D, 0), projection = u(VEC4, 1), scale = u(VEC2, 2);
    static final Uniform parameters = u(VEC2, 3), quality = u(FLOAT, 4);
    static final ShaderMacro shader = shader(function, depth, projection, scale, parameters, quality);

    /* Joint bilateral upsample/denoise at actual AO texel centres. A depth edge
     * must not receive shadow from the opposite side (nor darken the sky). */
    static final RawFunction compositeFunction = new RawFunction(VEC4, "hv_hcomposite", 6, DEPTHLIB +
        "vec4 hv_hcomposite(vec4 col, vec2 tc, sampler2D dep, sampler2D ao, vec4 pp, vec2 pr) {\n" +
        "    ivec2 dsz = textureSize(dep, 0), asz = textureSize(ao, 0);\n" +
        "    float centreDepth = texelFetch(dep, clamp(ivec2(tc * vec2(dsz)), ivec2(0), dsz - 1), 0).r;\n" +
        "    if(centreDepth >= 0.99999) return col;\n" +
        "    float centreDistance = hv_lindist(centreDepth, pp);\n" +
        "    vec2 position = tc * vec2(asz) - 0.5;\n" +
        "    ivec2 base = ivec2(floor(position));\n" +
        "    float sum = 0.0, weightSum = 0.0;\n" +
        "    for(int y = -1; y <= 2; y++) {\n" +
        "        for(int x = -1; x <= 2; x++) {\n" +
        "            ivec2 p = clamp(base + ivec2(x, y), ivec2(0), asz - 1);\n" +
        "            vec2 sampletc = (vec2(p) + 0.5) / vec2(asz);\n" +
        "            float sampleDepth = texelFetch(dep, clamp(ivec2(sampletc * vec2(dsz)), ivec2(0), dsz - 1), 0).r;\n" +
        "            if(sampleDepth >= 0.99999) continue;\n" +
        "            float difference = abs(hv_lindist(sampleDepth, pp) - centreDistance);\n" +
        "            vec2 delta = vec2(p) - position;\n" +
        "            float weight = exp(-0.5 * dot(delta, delta) - difference * 4.0);\n" +
        "            sum += texelFetch(ao, p, 0).r * weight;\n" +
        "            weightSum += weight;\n" +
        "        }\n" +
        "    }\n" +
        "    float visibility = (weightSum > 1e-6) ? sum / weightSum : 1.0;\n" +
        "    return vec4(col.rgb * clamp(visibility, 0.0, 1.0), col.a);\n" +
        "}\n");
    static final ShaderMacro composite = shader(compositeFunction, dc_dep, dc_ao, dc_pp, dc_pr);
}
