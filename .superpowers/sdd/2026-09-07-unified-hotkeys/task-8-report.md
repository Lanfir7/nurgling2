# Task 8 report: gameplay gesture conversion

## TDD evidence

- Added `GameplayGestureCatalogTest` and `GameplayGestureDispatchTest` before the implementation.
- RED run: `ant test` failed on the missing Task 8 catalog entries and dispatch assertions.
- GREEN run: final `ant test` completed successfully with 1,655 tests and 0 failures.

## Implemented

- Registered the complete Task 8 gameplay gesture table, including modifier-only flower/action-menu modes, wheel direction, contexts, and canonical server modifiers.
- Converted world/map, crafting, combat, stockpile, secondary-window, layout, and global/window handlers to resolve current gestures at dispatch time.
- Preserved legacy keyboard binding IDs/preferences; split `fgt-cycle` (next) from `fgt-cycle-prev` (previous) and removed the ignored-Shift behavior from the legacy binding.
- Removed the pre-dispatch F8 toggle so capture can consume it; rendering toggle now runs after normal widget dispatch.
- Replaced the one-purpose quick-marker helper with the catalog action.

## Checks

- `ant test`: PASS (1,655/1,655).
- `git diff --check`: PASS.

## Concerns / deferred

- Existing resource-processing and test-fixture diagnostics remain noisy but are non-failing and pre-existing.
- Task 9 source-audit and localization-completeness work remains deferred as requested.
