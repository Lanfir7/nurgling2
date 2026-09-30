# Optional modern image effects

Graphics method choices are under Nurgling settings → General → Graphics.
The existing `fxaa`, `sharpen` and `ssao` enable flags remain unchanged.
New selectors default to zero when absent or invalid, preserving saved looks:

| Selector | 0 (legacy) | 1 |
| --- | --- | --- |
| `aamethod` | FXAA | SMAA 1x, High preset |
| `sharpmethod` | Original sharpening | CAS, sharpen-only |
| `aomethod` | Original SSAO | Horizon-based AO |

Only one algorithm per stage is active. Ultra explicitly selects the new
algorithms; Enhanced keeps legacy algorithms; Classic disables the effects.
The Vulkan-only gate is unchanged. No temporal accumulation, motion vectors,
upscaling, frame generation or ray tracing is added.

## Pipeline and sources

AO runs before bloom/tone mapping (order -150), AA at 10, sharpening at 20,
before the view's existing resampling at 100. Effects process the 3D view,
not the surrounding interface.

- SMAA uses the original three passes and area/search lookup data from
  [iryoku/smaa](https://github.com/iryoku/smaa). See the bundled source license
  and provenance under `src/nurgling/render/smaa/`. All edge pixels are written,
  avoiding stale data from the original shader's discard optimization.
- CAS ports AMD's per-channel, precise-arithmetic sharpen-only filter from
  [FidelityFX-CAS](https://github.com/GPUOpen-Effects/FidelityFX-CAS), under MIT.
  It uses the documented gamma-2 conversion for display-referred input and
  blends the result with the source, so slider zero leaves the image unchanged.
  It does not upscale. The legacy sharpener is not renamed or replaced.
- HBAO is our fragment implementation of horizon/tangent integration with
  per-sample radius attenuation from
  [Bavoil & Sainz, SIGGRAPH 2008](https://developer.download.nvidia.com/presentations/2008/SIGGRAPH/HBAO_SIG08b.pdf).
  It reconstructs normals from depth, supports perspective/orthographic cameras,
  uses stable spatial noise and joint bilateral upsampling at AO texel centres.
  It is not NVIDIA HBAO+, nor AMD CACAO. Half/full resolution use 4×4/8×6 samples.

Full CACAO needs compute dispatch, storage resources, additional synchronization
and shader pipeline support. The current Render API and Vulkan compiler expose
graphics vertex/fragment stages only. That integration is a separate renderer
project, not an additional menu label in this change.

## Verification

`ant test` compiles the actual effect-generated GLSL using shaderc without a GPU,
also checks Vulkan GLSL compatibility, settings compatibility and routing,
tiny/odd image sizes, camera modes, lookup integrity and effect lifecycle.
Shader compilation and recorded draws are not a visual or performance test.

Before enabling Ultra as a recommendation, compare each new method with its
legacy counterpart in-game: outdoor foliage/fences, caves and mine overlays,
water, night lighting and a stationary camera. Resize the window and switch
effects repeatedly. Check both camera types and half/full AO. Measure frame
times on representative scenes rather than assuming an FPS improvement.
