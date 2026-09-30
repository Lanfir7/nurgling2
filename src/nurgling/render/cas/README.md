# CAS provenance

`Cas.java` ports the sharpen-only branch of `CasFilter` and the sharpness
constant from `CasSetup` in AMD's `ffx-cas/ffx_cas.h`, version 1.20190610,
from [GPUOpen-Effects/FidelityFX-CAS](https://github.com/GPUOpen-Effects/FidelityFX-CAS),
commit `9fabcc9a2c45f958aff55ddfda337e74ef894b7f`.

The port uses per-channel coefficients (`CAS_SLOW`), precise reciprocal/square
root (`CAS_GO_SLOWER`), and the default five-tap neighborhood without
`CAS_BETTER_DIAGONALS`. It converts display-referred input using AMD's documented
gamma-2 approximation, preserves alpha, clamps out-of-bounds loads and protects
the zero-color denominator. An additional source/result blend makes the user's
zero-strength setting an identity operation. There is no scaling or compute
dispatch in this fragment-stage adaptation.

The AMD MIT notice is included here so it is also shipped inside the client JAR.
