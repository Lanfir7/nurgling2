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
