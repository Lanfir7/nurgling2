# Sort Within Stacks Pack Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (inline; user said «делай»). Steps use checkbox (`- [ ]`) syntax for tracking. Do **not** commit unless the user asks (user git rule).

**Goal:** `Sort Within Stacks` packs stackable items into full stacks (plus remainder), then quality-sorts as today. Regular Sort is unchanged.

**Architecture:** Pure `computePackedSlotSizes(count, maxStackSize)` drives occupancy. `sortWithinStacks` includes stackable singles, packs by moving items from extra/right slots onto left incomplete stacks, then existing cycle-chase.

**Tech Stack:** Java, JUnit 5 via `ant test`.

## Global Constraints

- Only `sortDeep` / `sortWithinStacks`. `sort()` stays positional-only.
- Never merge past `StackSupporter.getFullStackSize`.
- Do not change `StackSupporter` tables.
- No git commits unless the user requests them.

## File structure

- Modify: `src/nurgling/actions/SortInventory.java`
- Create: `test/nurgling/actions/SortInventoryPackTest.java`

---

### Task 1: `computePackedSlotSizes`

**Files:**
- Create: `test/nurgling/actions/SortInventoryPackTest.java`
- Modify: `src/nurgling/actions/SortInventory.java`

**Interfaces:**
- Produces: `static List<Integer> computePackedSlotSizes(int count, int maxStackSize)` (package-private)

- [x] **Step 1: Write the failing test**

```java
package nurgling.actions;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SortInventoryPackTest {
    @Test
    void tenItemsStackByThree() {
        assertEquals(List.of(3, 3, 3, 1), SortInventory.computePackedSlotSizes(10, 3));
    }

    @Test
    void exactFullStack() {
        assertEquals(List.of(3), SortInventory.computePackedSlotSizes(3, 3));
    }

    @Test
    void remainderAfterOneFull() {
        assertEquals(List.of(3, 1), SortInventory.computePackedSlotSizes(4, 3));
    }

    @Test
    void zeroItems() {
        assertEquals(List.of(), SortInventory.computePackedSlotSizes(0, 3));
    }

    @Test
    void maxSizeOneKeepsSingles() {
        assertEquals(List.of(1, 1, 1, 1, 1), SortInventory.computePackedSlotSizes(5, 1));
    }

    @Test
    void partialSmallerThanMax() {
        assertEquals(List.of(2), SortInventory.computePackedSlotSizes(2, 5));
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `ant test` (or compile+select `nurgling.actions.SortInventoryPackTest`)
Expected: FAIL — method not found

- [x] **Step 3: Minimal implementation**

```java
static List<Integer> computePackedSlotSizes(int count, int maxStackSize) {
    List<Integer> sizes = new ArrayList<>();
    if (count <= 0) return sizes;
    int max = maxStackSize <= 1 ? 1 : maxStackSize;
    if (max == 1) {
        for (int i = 0; i < count; i++) sizes.add(1);
        return sizes;
    }
    while (count > 0) {
        int take = Math.min(max, count);
        sizes.add(take);
        count -= take;
    }
    return sizes;
}
```

- [x] **Step 4: Run tests — expect PASS**

---

### Task 2: Pack pass in `sortWithinStacks`

**Files:**
- Modify: `src/nurgling/actions/SortInventory.java`

**Steps:**
- [x] Collect names: any `NGItem` name in the inventory (not only existing `ItemStack`).
- [x] For each name: if `StackSupporter.isStackable(inventory, name)` and `getFullStackSize(name) > 1` and total items ≥ 2, run `packStacks(name)`.
- [x] `packStacks`: loop (cap 500): scan slots of that name with **counts** (stack `wmap.size()` or 1); target = `computePackedSlotSizes(total, max)`; if current counts equal target, stop; else take any item from a source slot that is over target (prefer rightmost extra) and `addItemToSlot` onto the leftmost dest that is under target / under max.
- [x] Never `itemact` onto a slot already at max.
- [x] `takeAnyFromSlot`: first stack member, or the single (no quality match required). Same dissolve waits as `takeItemFromSlot`.
- [x] After packing that name, `findBuffer` + `performCycleSort` as today. If no buffer: existing message, skip quality pass only.
- [x] Types with max size 1: skip packing; chase only if stacks already exist (old behavior).

- [x] **Verify:** `ant test` still green.

---

### Task 3: Manual check (no UI test)

- [ ] Sort Within Stacks on 10 stack-by-3 singles → 3 stacks of 3 + 1 leftover, high quality first.
- [ ] Regular Sort does not pack.
