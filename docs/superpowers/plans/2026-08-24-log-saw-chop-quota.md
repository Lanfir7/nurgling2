# Log Saw/Chop Quota Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (inline; user said «делай»). Steps use checkbox (`- [ ]`) syntax for tracking. Do **not** commit unless the user asks (user git rule).

**Goal:** Ctrl+RMB on a log offers saw-boards / chop-blocks; both bots gain a quantity field, stop at N (then dump leftovers to piles), and cancel work before drinking so items do not hit the ground.

**Architecture:** Pure `PrepQuota` owns parse/reached/log-detect/wait priority. Existing `PrepareBoards` / `PrepareBlocks` bots consume it. Two thin `GobContextAction`s launch those bots.

**Tech Stack:** Java, JUnit 5 via `ant test`.

## Global Constraints

- Still select log area + pile area (no skip).
- Do not change `SawBoardsFromLogsAction` ingredient quota.
- Do not change global `AutoDrink`.
- Pre-existing piles in the pile area do not count.
- Empty/`0` quantity = unlimited.
- On reaching N, dump leftover inventory into piles, then stop.
- No git commits unless the user requests them.

## File structure

- Create: `src/nurgling/tools/PrepQuota.java`
- Create: `test/nurgling/tools/PrepQuotaTest.java`
- Create: `src/nurgling/contextmenu/SawBoardsAction.java`
- Create: `src/nurgling/contextmenu/ChopBlocksAction.java`
- Modify: `NPrepBoardsProp.java`, `NPrepBlocksProp.java`
- Modify: `widgets.bots.PrepareBoards` / `PrepareBlocks`
- Modify: `actions.bots.PrepareBoards` / `PrepareBlocks`
- Modify: `WaitPrepBoardsState.java`, `WaitPrepBlocksState.java`
- Modify: `BuildFromLogs.java`
- Modify: `GobContextRegistry.java`
- Modify: `src/lang/messages.properties`, `messages_ru.properties`

---

### Task 1: PrepQuota

**Files:**
- Create: `test/nurgling/tools/PrepQuotaTest.java`
- Create: `src/nurgling/tools/PrepQuota.java`

**Interfaces:**
- Produces: `PrepQuota.parse(String)`, `reached(int,int,int)`, `isLog(String)`, `Halt pickBoards(...)`, `Halt pickBlocks(...)`

- [ ] **Step 1: Write the failing test** (`test/nurgling/tools/PrepQuotaTest.java`) — see implementation in this session.

- [ ] **Step 2: Run to verify fail**

Run: `ant test` filtered to `nurgling.tools.PrepQuotaTest`  
Expected: FAIL — class not found

- [ ] **Step 3: Implement `PrepQuota`**

- [ ] **Step 4: Tests pass**

- [ ] **Step 5: Do not commit**

---

### Task 2: Props + windows + i18n

**Files:**
- Modify: `src/nurgling/conf/NPrepBoardsProp.java`, `NPrepBlocksProp.java`
- Modify: `src/nurgling/widgets/bots/PrepareBoards.java`, `PrepareBlocks.java`
- Modify: `src/nurgling/actions/bots/BuildFromLogs.java`
- Modify: `src/lang/messages.properties`, `messages_ru.properties`

**Interfaces:**
- Consumes: `PrepQuota.parse`
- Produces: `prop.count` (`int`, default 0); `PrepareBoards(boolean askCount)`

- [ ] Add `count` to both props (JSON + HashMap ctor, missing → 0)
- [ ] Quantity field after tool; `BuildFromLogs` uses `new PrepareBoards(false)`
- [ ] Keys `pboards.count` / `pblocks.count` / `context.saw_boards` / `context.chop_blocks`

---

### Task 3: Wait order + bot quota + drink-with-stop

**Files:**
- Modify: `WaitPrepBoardsState.java`, `WaitPrepBlocksState.java`
- Modify: `actions.bots.PrepareBoards.java`, `PrepareBlocks.java`

**Interfaces:**
- Consumes: `PrepQuota.reached`, `PrepQuota.Halt`, `Drink(0.9, true)` then `RestoreResources`

- [ ] Wait: log gone → danger → **no space** → drink → (blocks: wound overwrites)
- [ ] Before each flower action and after each wait: if reached → dump piles → SUCCESS
- [ ] `piledThisRun` += items removed by `TransferToPiles`
- [ ] TIMEFORDRINK: `Drink(0.9, true)` then `RestoreResources`

---

### Task 4: Context menu

**Files:**
- Create: `SawBoardsAction.java`, `ChopBlocksAction.java`
- Modify: `GobContextRegistry.java`

- [ ] `appliesTo` via `PrepQuota.isLog(gob.ngob.name)`
- [ ] `create` returns `new PrepareBoards()` / `new PrepareBlocks()`
- [ ] Register both in `GobContextRegistry`

---

### Task 5: Verify

- [ ] `ant test` — PrepQuota tests pass
- [ ] Spec checklist: menu, quantity both entry points, stop at N then dump, drink cancels work, space before drink, 0 = unlimited, BuildFromLogs hides quantity
