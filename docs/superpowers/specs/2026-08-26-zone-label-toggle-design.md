# Zone name labels: live toggle

**Date:** 2026-08-26  
**Status:** approved design

## Goal

Minimap toggle “show all zone names” keeps names on the world as the player walks. Labels appear when a zone’s grids become loaded, stay while the toggle is on, and survive closing the area editor. Hidden zones stay visible in gray. The toggle persists across restart.

## Non-goals

- Do not redraw names without dummy gobs (`NAreaLabel` stays).
- Do not show names of zones whose grids are not loaded yet (`getRCArea` still cannot place them).
- Do not change area editor selection / click behavior.
- Do not restyle label fonts or icons.

## Why the current toggle fails

Names live on virtual dummy gobs created by `NMapView.createAreaLabel`. Creation is one-shot (`initDummys`) and skipped when `getRCArea(false)` is null. Closing the area editor always calls `destroyDummys()`, so labels vanish even if the toggle is still on. Walking into new grids never creates missing labels. `showAllZonesAlways` is only a field on `NMiniMap` and is not saved.

## Visibility

A label is drawn when:

1. Area editor is open **or** toggle is on, and
2. If `area.hide` is true, the label uses the existing gray texture (`graylabel`). If `area.hide` is false, normal / selected textures as today.

If the editor is closed **and** the toggle is off, nothing is drawn and dummies are destroyed.

## Persistence

New `NConfig.Key.showAllZonesAlways`, default `false`. Minimap checkbox reads and writes this key (`needUpdate`). After relog the checkbox and live-sync follow the saved value.

## Live sync

Replace one-shot “create everything now” with `syncAreaLabels()`:

- For each area in `glob.map.areas`:
  - If a dummy already exists for `area.gid` and the gob is still in `oc` → leave it.
  - Else if `getRCArea(false)` is non-null → `createAreaLabel` (idempotent: no second dummy).
  - If the area was removed → drop its dummy.
- Call from `NMapView` tick, throttled (~0.5 s), while labels should be live (toggle on **or** editor open).
- Toggle on: start sync (do not wait for the editor).
- Toggle off and editor closed: `destroyDummys()`.
- `NAreasWidget.hide()` calls `destroyDummys()` only when the toggle is off.

`createAreaLabel` / `initDummys` callers (add/duplicate area, reload from file, minimap toggle) go through the same idempotent path so duplicates cannot appear.

## Overlay

`NAreaLabel.draw` keeps the current hide / editor / toggle checks, reading the toggle from config (not only from `NMiniMap` field). `tick` must not remove the overlay just because `getGameUI()` is briefly null.

## Tests

- Sync decision without a live map: missing dummy + locatable area → create; existing dummy → skip; deleted area → remove.
- Labels live iff toggle on or editor open.
- Config default for `showAllZonesAlways` is false.
