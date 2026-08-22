# BoughBee Place/Light/Timer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prefix BoughBee with place Bough Pyre, light it, ephemeral 15/30 min map timer, optional nearby tree harvest.

**Architecture:** Extend `LocalizedResourceTimer` with `autoRemoveAfterMs` + `iconRes`. Bot always does harvest? → craft/place → light → timer → existing wait/Raid. No MapFile markers. Skip Postgres for ephemeral timers.

**Tech Stack:** Java 21, JUnit 5, existing Haven/Nurgling actions (`WaitPlob`, `LightObject`, `CollectFromGob`).

## Global Constraints

- Always cycle 1–5; no raid-without-pyre restart path
- Ready at 15 min + sound; auto-delete at 30 min
- Ordinary resource timers still never auto-remove
- `harvestTrees` default false
- Boughs cap 4; branches drain the tree, need ≥2
- Marker only after confirmed light
- Do not commit unless the user asks

---

### Task 1: Ephemeral timer model

**Files:**
- Create: `test/nurgling/LocalizedResourceTimerTest.java`
- Modify: `src/nurgling/LocalizedResourceTimer.java`

- [ ] Failing tests: Ready at 15m, auto-remove at 30m, old timers never auto-remove, JSON roundtrip
- [ ] Implement fields + `shouldAutoRemove()` / `shouldPersist()`
- [ ] Tests pass

### Task 2: Prop + harvest helpers

**Files:**
- Create: `test/nurgling/conf/NBoughBeePropTest.java`
- Create: `test/nurgling/actions/bots/BoughBeeMaterialsTest.java`
- Modify: `src/nurgling/conf/NBoughBeeProp.java`
- Create: `src/nurgling/actions/bots/BoughBeeMaterials.java`

- [ ] Tests for `harvestTrees` JSON and bough/branch counts
- [ ] Implement
- [ ] Tests pass

### Task 3: Persistence, sync, minimap, alarm

**Files:**
- Modify: `LocalizedResourceTimerService.java`, `LocalTimerSyncService.java`, `NMiniMap.java`, `NAlarmWdg.java`

- [ ] Save/load Ready ephemeral; skip auto-removed; skip Postgres
- [ ] Draw icon + time; remove on auto-remove with existing Ready sound

### Task 4: LightObject bpyre + BoughBee flow

**Files:**
- Modify: `LightObject.java`, `BoughBee.java` (action + widget), L10n

- [ ] bpyre LightConfig
- [ ] Checkbox, harvest, place, light, createTimer, existing wait/Raid
