# Area Ring Appearance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make animal and beehive `NAreaRad` rings configurable (band height, line width, fill alpha, separate colors) and fix mountain-goat path.

**Architecture:** Pure helpers `NAreaRad.migrateList` and `NAreaRadStyle` own defaults/clamps. Overlays read them each tick and rebuild materials. Settings live in `NRingSettings`.

**Tech Stack:** Java 8, JUnit 5 (`ant test`), `NConfig`, Haven render (`BaseColor`, `LineWidth`).

## Global Constraints

- Defaults match current look: band 10, line 4, fill alpha 128, animal fill `(192,0,0)`, animal edge `(255,224,96)`, beehive fill `(0,163,192)`, beehive edge `(0,192,0)`.
- Trough / mound bed keep hardcoded colors; inherit band height and line width only.
- World beehive checkbox stays (`showBeehiveRadius`).
- No commits unless the user asks.

---

### Task 1: Goat migration + style helpers (TDD)

**Files:**
- Create: `test/nurgling/conf/NAreaRadMigrateTest.java`
- Create: `test/nurgling/conf/NAreaRadStyleTest.java`
- Modify: `src/nurgling/conf/NAreaRad.java`
- Create: `src/nurgling/conf/NAreaRadStyle.java`

- [ ] Failing tests for `migrateList` (old path, both paths, already correct, missing)
- [ ] Failing tests for `withAlpha` / `numberOr`
- [ ] Implement until `ant test` passes those classes

### Task 2: NConfig keys

**Files:**
- Modify: `src/nurgling/NConfig.java` (enum, defaults, call `migrateList` in existing animalrad migration)

Keys: `areaRadBandHeight`, `areaRadLineWidth`, `areaRadFillAlpha`, `areaRadAnimalFill`, `areaRadAnimalEdge`, `areaRadBeehiveFill`, `areaRadBeehiveEdge`, `beehiveRadius`. Default goat path `gfx/kritter/goat/wildgoat`.

### Task 3: Overlays

**Files:**
- Modify: `src/nurgling/overlays/NAreaRad.java` — palette ANIMAL/BEEHIVE/CUSTOM, live materials, band height from style, `setz` writes `posa.data`
- Modify: `src/nurgling/overlays/NAreaRange.java` — live radius; animal palette
- Modify: `src/nurgling/overlays/NBeehiveRadius.java` — beehive palette + `beehiveRadius`

### Task 4: Settings UI + L10n

**Files:**
- Modify: `src/nurgling/widgets/options/NRingSettings.java` — scrollport, sliders, animal/beehive colors, beehive vis+radius, animal list
- Modify: `src/lang/messages.properties`, `src/lang/messages_ru.properties`

### Task 5: Verify

- [ ] `ant test` (full suite)
