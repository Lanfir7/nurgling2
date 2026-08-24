# Log saw / chop quota from context menu

**Date:** 2026-08-24  
**Status:** approved

## Goal

Ctrl+RMB on a log opens the existing gob flower menu with two new entries: saw boards and chop into blocks. Both launch the current `PrepareBoards` / `PrepareBlocks` bots (tool window → log area → pile area). Those windows also gain a quantity field. The bot stops when inventory plus items dumped to piles this run reach N, then dumps leftover inventory into stockpiles. Empty/`0` means unlimited (today’s behavior). Drinking must cancel saw/chop so items cannot overflow onto the ground.

## Non-goals

- Do not skip area selection (still log area + pile area).
- Do not change `SawBoardsFromLogsAction` / `BuildFromLogs` quota logic (ingredient count).
- Do not change global `AutoDrink` behavior; these bots already run under `BotExecutor` (`waitBot`).
- Do not count stockpiles that already existed in the pile area before the run.

## Context menu

Two `GobContextAction`s, registered in `GobContextRegistry`.

Applies to a gob whose `ngob.name`:

- starts with `gfx/terobjs/trees/`
- contains `log`

(Excludes `logcabin` and other non-tree names that happen to contain `log`.)

| Action | Label keys | `create` |
|---|---|---|
| Saw boards | `context.saw_boards` | `new PrepareBoards()` |
| Chop blocks | `context.chop_blocks` | `new PrepareBlocks()` |

Same `BotExecutor.runAsync` path as `ChipStoneAreaAction` / `ButcherAction`.

EN: `Saw boards` / `Chop into blocks`.  
RU: `Распилить доски` / `Разрубить на блоки`.

## Quantity UI

Add `count` (`int`, default `0`) to `NPrepBoardsProp` and `NPrepBlocksProp` (JSON + `HashMap` ctor). Missing key loads as `0`.

Both bot windows (`widgets.bots.PrepareBoards` / `PrepareBlocks`): after the tool picker, a label + `TextEntry.NumberValue`. Start saves `count` with the tool.

`PrepareBoards` is also opened by `BuildFromLogs`. Use `PrepareBoards(boolean askCount)`; `BuildFromLogs` passes `false` so that window has no quantity field.

Parse:

- empty, `0`, non-numeric, negative → `0` (unlimited)
- otherwise the integer

Keys: `pboards.count` / `pblocks.count`  
EN: `Quantity (0 = unlimited)`  
RU: `Количество (0 = без лимита)`

## Quota

Pure helper `nurgling.tools.PrepQuota`:

- `parse(String s) → int` as above
- `reached(int target, int inventory, int piledThisRun)`  
  `target <= 0` → `false`  
  else `inventory + piledThisRun >= target`

**On-hand** for this run = current inventory items of that type + items moved to piles during this run.

Example: inventory already has 10 boards, target 50 → produce 40 more. Dumping those 10 into piles does not reset progress (`piledThisRun` goes up by 10).

Item aliases (existing): boards `NAlias("board")`, blocks `NAlias("block")`.

`piledThisRun` update on every `TransferToPiles`:

```
int before = gui.getInventory().getItems(alias).size();
TransferToPiles(...);
piledThisRun += before - gui.getInventory().getItems(alias).size();
```

Check `reached` **before** starting a new saw/chop cycle (before `SelectFlowerAction`). If reached: `TransferToPiles` (leftovers), then `SUCCESS`.

After each wait returns, check again (may overshoot by the last board/block produced during the wait — acceptable).

Unlimited (`0`): no quota checks; on empty log area still dump leftovers as today.

If logs run out before N: dump leftovers and finish (same as today). Do not error.

## Drink / inventory

Root cause: `WaitPrepBoardsState` / `WaitPrepBlocksState` treat `TIMEFORDRINK` before `NOFREESPACE`, and `RestoreResources` drinks with `Drink(0.9, false)`, so saw/chop continues and the wait is no longer watching inventory.

Changes **only** in these two bots and their wait tasks:

1. Wait-state order: log gone → energy danger → **no free space** → drink → (blocks: wounds after space, before or after drink; wounds still abort).
2. On `TIMEFORDRINK`: `new Drink(0.9, true)` first (click character, wait idle), then existing `RestoreResources`. After a successful stop-drink, stamina is high enough that `RestoreResources` skips its own `Drink(..., false)`.
3. AutoDrink stays gated by `waitBot`; no AutoDrink change.

## Tests

`test/nurgling/tools/PrepQuotaTest.java` (no game UI):

- `parse("")`, `"0"`, `"  "`, `"x"`, `"-1"` → `0`
- `parse("50")` → `50`
- `reached(0, 10, 0)` → false
- `reached(50, 10, 0)` → false
- `reached(50, 10, 40)` → true
- `reached(50, 0, 50)` → true (all dumped)
- `reached(50, 10, 10)` → false (start 10, dumped 10, still need 30)

Wait-state priority: extract a package-visible `chooseState(...)` or small static method on both wait classes (or one shared helper) so a unit test can assert space wins over drink when both are true. If extracting a helper is heavier than the wait classes, test only `PrepQuota` and keep wait-order as an implementation checklist — prefer the helper.

## Files

- Create: `src/nurgling/contextmenu/SawBoardsAction.java`
- Create: `src/nurgling/contextmenu/ChopBlocksAction.java`
- Create: `src/nurgling/tools/PrepQuota.java`
- Create: `test/nurgling/tools/PrepQuotaTest.java`
- Modify: `GobContextRegistry.java` — register both
- Modify: `NPrepBoardsProp.java`, `NPrepBlocksProp.java` — `count`
- Modify: `widgets.bots.PrepareBoards` / `PrepareBlocks` — quantity field
- Modify: `actions.bots.PrepareBoards` / `PrepareBlocks` — quota + drink-with-stop
- Modify: `WaitPrepBoardsState` / `WaitPrepBlocksState` — space before drink
- Modify: `BuildFromLogs.java` — `new PrepareBoards(false)`
- Modify: `src/lang/messages.properties`, `messages_ru.properties`

## Out of scope follow-ups

`RestoreResources` still drinks with `withStop=false` for other bots. Not this spec.
