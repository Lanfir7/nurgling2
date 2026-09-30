# SMAA upstream assets

`SMAA.hlsl`, `AreaTexDX9.dds`, `SearchTex.dds`, and `LICENSE.txt` come from
[iryoku/smaa](https://github.com/iryoku/smaa), commit
`71c806a838bdd7d517df19192a20f0c61b3ca29d` (MIT license). Only trailing
whitespace in `SMAA.hlsl` is normalized; the lookup data and license are unchanged.

At runtime, `Smaa.java` selects the upstream GLSL 3 path and high preset,
provides target-size metrics through a uniform, replaces no-edge `discard` operations with
zero edges because our edge target is fully overwritten, and calls the three
upstream passes through fragment-stage wrappers. The DDS payloads are uploaded
unchanged: the area lookup is RG8 (160×560), and the search lookup is R8
(64×16). Both use linear filtering and clamp, as required by upstream.
