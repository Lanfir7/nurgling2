# Task 7 report — inventory, item, wheel, and held-item actions

## Implementation

- Added the registry-backed `HotkeyResolver` with ordered mouse and wheel lookup by delivery context.
- Registered the complete Task 7 gesture table, including generic/Nurgling item contexts, inventory wheel transfers, held-item dispatch, and canonical modifiers for item interaction and fire lighting.
- Routed `WItem`, `NWItem`, grouped `NInventory` rows, `Inventory`, and `ItemDrag` through the resolver while retaining legacy message names and counts.
- Added explicit modifier transport to `DTarget.Interact`, including propagation through derived events and the three-argument `iteminteract` compatibility overload; `WItem` and `MapView` now use supplied server modifiers.

## TDD evidence

- RED: the first `rtk ant test` after adding `InventoryHotkeysTest` and `DTargetExplicitModifiersTest` failed at test compilation because `HotkeyResolver` and the explicit `Interact` constructor/modifier field did not exist.
- GREEN: after the minimal implementation and handler conversion, `rtk ant test` completed successfully with 1648/1648 tests passing.

## Checks

- `rtk ant test` — BUILD SUCCESSFUL, 1648 tests passed.
- `rtk git diff --check` — clean.
- Existing resource decoder, compiler, and integration-test diagnostics remain the accepted baseline noise recorded in `progress.md`.

## Self-review / concerns

- Gesture bindings intentionally use the existing `PreferenceStore.SYSTEM` and `gesturebind/<id>` persistence boundary; old keyboard bindings remain untouched.
- The legacy held-item Ctrl+RMB fallback remains in place for compatibility; it is outside the explicit Task 7 action table and is a candidate for the broader gameplay conversion/audit in Task 8.

## Review fixes

- Removed physical LMB/RMB gates from `WItem`, `NWItem`, grouped `NInventory` rows, and `ItemDrag`; each handler now resolves the configured gesture first and dispatches from the resolved action ID, including MMB/rebound buttons.
- Grouped transfer/drop direction and quantity mode now come from `transfer_same.*`/`drop_same.*` action IDs. Both `drop_same` directions remain bulk operations, while only the descending variant keeps the legacy low-quality-first order. This preserves default Ctrl+Alt+LMB `drop_same.desc` as drop-all with `reverse=false`.
- Preserved legacy held-item Ctrl+RMB by routing it through `MapView.heldItemRmb(..., 0)`. The path sends an ordinary map RMB with explicit zero server modifiers and never mutates `ui.modctrl`; explicit action modifiers remain used for configured held-item actions.

## Review TDD evidence

- RED: after adding the first three review regression tests, `rtk ant test` found 1651 tests with 1648 successful and 3 failures: physical button gates, physical-button-derived grouped semantics, and the legacy explicit-map path.
- GREEN: focused `InventoryHotkeysTest` passed 5/5; the final full `rtk ant test` passed 1652/1652.

## Review checks

- `rtk ant test` — BUILD SUCCESSFUL, 1652 tests passed.
- Focused JUnit `InventoryHotkeysTest` — 5 tests passed.
- `rtk git diff --check` — clean.
- Known resource decoder/compiler diagnostics and test-injected warnings remain the accepted baseline noise recorded in `progress.md`.

## Review self-review / concerns

- No unrelated files or build/dependency changes were made.
- The held-item legacy fallback intentionally retains its physical RMB guard until the planned Task 8 modifier-aware map audit; its server modifier transport is now explicit and side-effect free.
