# Sort Within Stacks: pack into stacks

**Date:** 2026-08-24  
**Status:** implemented

## Goal

`Sort Within Stacks` (`SortInventory.sortDeep`) still sorts by quality as today, and also **packs stackable items into full stacks**. Ten singles that stack by 3 become three stacks of 3 plus one leftover.

Regular `Sort` is unchanged.

## Non-goals

- Do not pack on the positional-only sort button.
- Do not change `StackSupporter` sizes, categories, or exceptions.
- Do not merge past `getFullStackSize` (server no-op, item stuck in hand).
- Do not unstack already-full stacks just to repack occupancy.

## Behavior

After the existing 1×1 positional pass, `sortWithinStacks` does two steps per item name:

1. **Pack occupancy** (new), if the type is stackable in this inventory (`StackSupporter.isStackable`) and `getFullStackSize(name) > 1`, and there are at least two items of that name (singles and/or stacks).
2. **Quality cycle-chase** (existing) so the highest qualities sit in the first stacks.

Packing includes types that are **only singles** (today those names are skipped because there is no `ItemStack` yet).

Non-stackable types: no packing; cycle-chase only if stacks already exist (current).

## Target occupancy

Pure function `computePackedSlotSizes(count, maxStackSize)`:

- `maxStackSize <= 1` → `count` slots of size 1 (no packing).
- else fill as many `maxStackSize` as possible, remainder last:  
  `(10, 3) → [3, 3, 3, 1]`, `(3, 3) → [3]`, `(5, 1) → [1, 1, 1, 1, 1]`.

## Packing moves

While there are extra occupied slots beyond the target count:

- Destination: a slot with size `< maxStackSize` (leftmost).
- Source: another slot of the same name (prefer rightmost extra).
- `takeItemFromSlot` from source, `addItemToSlot` onto destination (creates a stack from a single via `itemact`).

No buffer slot required for packing. Never add onto a stack already at `maxStackSize`.

Then find the quality-sort buffer (free cell in this inventory, else player inventory) and run `performCycleSort` as today. After packing, a free cell usually appears.

## Errors

- Not stackable / max size 1: skip packing, continue.
- No destination or source for a merge: stop packing that name, still try cycle-chase.
- Cycle-chase still needs a free cell; if none, keep the current message (`Need 1 free NxM slot...`) and skip quality pass for that name.
- Cancel / non-empty hand: same as current sort abort.

## Tests

Unit tests on `computePackedSlotSizes` only (no game UI):

- `(10, 3) → [3, 3, 3, 1]`
- `(3, 3) → [3]`
- `(4, 3) → [3, 1]`
- `(0, 3) → []`
- `(5, 1) → [1, 1, 1, 1, 1]`
- `(2, 5) → [2]`

## Files

- `src/nurgling/actions/SortInventory.java` — eligibility, pack pass, then existing chase.
- `test/nurgling/actions/SortInventoryPackTest.java` — new, sizes only.
