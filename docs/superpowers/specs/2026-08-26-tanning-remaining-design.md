# Tanning remaining time overlay

**Date:** 2026-08-26  
**Status:** approved

## Goal

On a hide in a tanning tub, the drying overlay stays as percent and adds remaining real time next to it. Total tanning time is **30 real hours**. At 50% show `50% 15h`; at 94% show `94% 1h48m`.

## Non-goals

- Do not show remaining time on drying frames or any other window.
- Do not change quality overlay, meter bar, or progress overlay settings.
- Do not guess durations other than 30h.

## Display

`Drying` overlay text:

- Tub: `{percent}% {remaining}`
- Anything else (including Drying Frame): `{percent}%` as today

Format remaining from leftover minutes, drop zero units:

- `15h` (no minutes)
- `1h48m`
- `45m` (under one hour)
- leftover `0` → omit time: `100%`

Round leftover to nearest minute. Formula: `round((1 - done) * 30 * 60)` minutes.

## Tub detection

Window caption is `Tub` or `Tanning Tub` (exact). Unknown / missing window → percent only.

`Drying` reads caption via `GItem.wi` → parent `Window.cap`. No live widget → percent only.

## Logic

Pure helper `nurgling.tools.TanningRemaining`:

- `isTubWindow(cap)`
- `remainingMinutes(done)`
- `formatRemaining(minutes)`
- `overlayText(percent, done, cap)`

`Drying.renderPercentText` uses `overlayText`. Cache key includes the formatted string, not only percent.

## Tests

- 0.50 → `50% 15h`; 0.94 → `94% 1h48m`; 1.0 → `100%`
- `Tub` / `Tanning Tub` get time; `Drying Frame` / `null` do not
