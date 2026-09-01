# Compass Targets and Opacity Implementation Plan

**Goal:** Make compass markers right-clickable, add database villagers, nearby players and combat targets, and expose category/background presentation controls in QOL Lanfir settings.

**Architecture:** Keep `NCompassTargetCollector` as the single source of normalized targets. Extend `NCompassTarget` with a world target identity, merge overlapping sources by stable priority, and let `NCompassBar` retain hit regions from the latest frame. Route right-clicks through the existing `NPointerClickHandler`, so compass markers create the same world line and cross as legacy quest pointers. Resolve database peer coordinates only when they can be mapped into the current session.

**Tech Stack:** Java, Haven widget/event APIs, NConfig, JUnit 5, Ant.

---

### Task 1: Configuration and presentation helpers

**Files:**
- Modify: `src/nurgling/NConfig.java`
- Modify: `src/nurgling/widgets/compass/NCompassSettings.java`
- Modify: `src/nurgling/widgets/nsettings/QOLLanfirSettings.java`
- Create/Modify: `test/nurgling/widgets/compass/NCompassSettingsTest.java`

1. Add failing tests for defaults, bounded background opacity, and category visibility.
2. Add config keys for quests, party, database peers, nearby players, combat targets and background opacity.
3. Add category checkboxes and a background-opacity slider to QOL Lanfir.
4. Run the focused tests.

### Task 2: Normalize and merge target sources

**Files:**
- Modify: `src/nurgling/widgets/compass/NCompassTarget.java`
- Modify: `src/nurgling/widgets/compass/NCompassTargetCollector.java`
- Create/Modify: `test/nurgling/widgets/compass/NCompassTargetCollectorTest.java`

1. Add failing unit tests for source priority, duplicate removal, nearby-player classification and peer coordinate conversion.
2. Collect quest pointers, party members, active database peer positions, loaded nearby player gobs and `Fightview` relations.
3. Preserve gob IDs and world coordinates required by click navigation.
4. Skip database positions that cannot be resolved into the current session/segment.
5. Run focused tests.

### Task 3: Marker interaction and visual hierarchy

**Files:**
- Modify: `src/nurgling/widgets/compass/NCompassBar.java`
- Reuse: `src/nurgling/tools/NPointerClickHandler.java`
- Create/Modify: `test/nurgling/widgets/compass/NCompassHitsTest.java`
- Create/Modify: `test/nurgling/widgets/compass/NCompassPresentationTest.java`

1. Add failing tests for marker hit regions and primary-cardinal presentation.
2. Cache marker hit bounds from the frame and handle button 3 only.
3. Send the selected target to the existing directional-vector handler.
4. Apply configured alpha only to the background.
5. Render the primary N/E/S/W directions larger and bolder than diagonal directions.
6. Run focused tests.

### Task 4: Verification

1. Run all compass tests.
2. Run the full test suite and build.
3. Inspect the diff for unrelated files and commit only compass-related changes.
