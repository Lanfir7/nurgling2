# Beehive Smoker (BoughBee): place, light, ephemeral map timer

**Date:** 2026-08-22  
**Status:** draft for review

## Goal

Keep the existing BoughBee wait→Raid loop. Prefix it with: user places a Bough Pyre, bot lights it, then an ephemeral map icon+timer appears. Optional nearby-tree harvest for boughs/branches.

## Non-goals

- No special restart path that raids a calm hive without a pyre. Every run is always the 1–5 cycle below.
- No MapFile `SMarker` / `PMarker`. Icon lives only in the timer overlay and vanishes with the timer.
- Do not change how ordinary localized-resource timers stay as `Ready` for the rest of the session.

## Cycle (always)

1. Existing settings window, plus new checkbox **Harvest from trees** (`boughbee.harvest_trees`).
2. If harvest is on and inventory has fewer than 4 items matching `Bough`: pick boughs from nearby bough-bearing trees until 4. If still short → error, no pyre.
3. Open craft **Bough Pyre**, wait for the placement ghost, user clicks the spot, wait until gob `bpyre` exists.
4. If harvest is on and lighting will need branches (no easier LightObject source, fewer than 2 `Branch` in inventory): pick branches from nearest trees. Take everything that tree yields (minimum 2 total). If still short → error, no light, no marker.
5. Light via `LightObject` (add `bpyre` config). Only after a confirmed lit gob: create the ephemeral timer at that tile.
6. Existing wait: pyre gone + hive marker `0` → `Raid!` → after-harvest action. User may stop during wait; the marker is independent of the bot.

## Timer overlay

Reuse `LocalizedResourceTimer` / `LocalizedResourceTimerService` / `NMiniMap.drawResourceTimers` / `NAlarmWdg.checkResourceTimers`.

| t from light | Map | Sound |
|---|---|---|
| 0–15 min | `boughpyre` icon + countdown under it | none |
| 15 min | icon + `Ready` | `alarm/question` once (same as resource timers) |
| 15–30 min | icon + `Ready` | none |
| 30 min | remove timer (icon and text gone) | none |

Constants:

- `READY_MS = 15 * 60 * 1000`
- `AUTO_REMOVE_MS = 30 * 60 * 1000`
- `resourceType = "nurgling/boughpyre"`
- `icon` = existing bot icon `nurgling/bots/icons/boughpyre`

New timer fields (JSON, default so old timers unchanged):

- `autoRemoveAfterMs` (0 = never auto-remove)
- `iconRes` (nullable; if set, minimap draws that icon at `tileCoords` then the time text below)

`isExpired()` stays “ready at `duration`” (`duration = READY_MS`).  
`shouldAutoRemove()` is `now - startTime >= autoRemoveAfterMs` when `autoRemoveAfterMs > 0`.

Persistence:

- Save countdown and Ready ephemeral timers until `shouldAutoRemove()` (0–30 min). Do **not** save after that.
- Load the same way.
- `LocalTimerSyncService` must **skip** timers with `autoRemoveAfterMs > 0` (no Postgres). Ordinary resource timers unchanged.

`NAlarmWdg.checkResourceTimers`: keep current Ready sound. After that, if `shouldAutoRemove()`, call `removeTimer` and drop the id from alarm sets.

`NMiniMap.drawResourceTimers`: if `iconRes` is set, draw the icon centered on the tile, time text under it (existing furnaces). Other timers still text-only.

## Harvest (checkbox off = current “missing items → error”)

**Boughs** — only for the pyre, cap 4 in inventory.

- Candidates: in-view trees (`gfx/terobjs/trees`, skip log/stump/oldtrunk) whose basename is in `HarvestState.hasBough`.
- Prefer nearest to the player.
- Flower `Take bough` via existing `CollectFromGob` (inventory only, no piles).
- Stop at 4 boughs.

**Branches** — fuel for lighting, 2+ from the tree.

- Candidates: nearest in-view living trees.
- Flower `Break branch` via `CollectFromGob`.
- Empty that tree (do not stop at 2 if it still yields). If inventory still has `< 2` Branch, next tree.
- Need at least 2 Branch before lighting.

Search radius: objects currently loaded in view. Nearest-first (`NUtils.d_comp`).

## Lighting

Add `LightObject.getConfig` for names containing `bpyre`:

- displayName `Bough Pyre`
- `fuelFlag = 0` (the pyre *is* the fuel)
- `fireFlag`: start with fireplace/`pow` value `4`; wait on `WaitGobModelAttr` as today. If a live lit pyre uses another bit, change only this constant.

Marker is created only after `isLit` is true. Failed light → no marker, `Results.ERROR`.

## Placement

Same pattern as `GelatinAction.openGelatinCraft` + construction ghost:

1. Find MenuGrid pagina named `Bough Pyre`, `use` it.
2. Wait until `craftwnd.makeWidget.rcpnm` matches.
3. Trigger craft (placement ghost).
4. `WaitPlob` until the user clicks.
5. Wait until `Finder.findGob(new NAlias("bpyre"))` is the new gob near the click.

Cancel / no ghost / no gob → error, no marker.

## Settings

`NBoughBeeProp.harvestTrees` boolean, default `false`. Persist in existing JSON (`harvestTrees`). Checkbox in `nurgling.widgets.bots.BoughBee` with L10n:

- `boughbee.harvest_trees` EN: `Harvest nearby trees`
- RU: `Рвать с деревьев`

## Files

- Modify: `src/nurgling/actions/bots/BoughBee.java` — harvest, place, light, timer, then existing wait/raid
- Modify: `src/nurgling/widgets/bots/BoughBee.java` — checkbox
- Modify: `src/nurgling/conf/NBoughBeeProp.java` — `harvestTrees`
- Modify: `src/nurgling/LocalizedResourceTimer.java` — fields + `shouldAutoRemove`
- Modify: `src/nurgling/LocalizedResourceTimerService.java` — create/save/load/skip auto-removed
- Modify: `src/nurgling/LocalTimerSyncService.java` — skip ephemeral
- Modify: `src/nurgling/widgets/NMiniMap.java` — icon + timer text
- Modify: `src/nurgling/widgets/NAlarmWdg.java` — remove at 30 min
- Modify: `src/nurgling/actions/bots/LightObject.java` — bpyre config
- Modify: `src/lang/messages.properties`, `messages_ru.properties`
- Test: `test/nurgling/LocalizedResourceTimerTest.java` — Ready at 15m, remove at 30m, JSON roundtrip, old timers still never auto-remove
- Test: `test/nurgling/conf/NBoughBeePropTest.java` — `harvestTrees` persist
- Test: harvest selection helpers (pure: stop at 4 boughs; branch drain-tree) if extracted from the bot

## Errors

| Case | Result |
|---|---|
| Harvest off, `< 4` Bough | error, stop |
| Harvest on, still `< 4` Bough after trees | error, stop |
| Place cancelled / no bpyre | error, stop |
| Light failed | error, no marker |
| Harvest on, still `< 2` Branch and LightObject has no other source | error, no marker |
| Safety (player/animal) during wait | existing actions, marker stays |

## Testing

Unit-test timer math and JSON without the client. Bot UI and gob lighting are manual: place pyre → icon+countdown on minimap → at 15m sound and Ready → at 30m gone; stop bot during wait, marker remains.
