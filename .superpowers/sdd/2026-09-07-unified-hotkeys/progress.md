# SDD ledger — plan: docs/superpowers/plans/2026-09-07-unified-hotkeys.md

Workspace: `C:/Game/Lanfir-nurgling2/.worktrees/unified-hotkeys`
Branch: `codex/unified-hotkeys`
Merge base: `0573c59e7618507db44d35e05584662900c41004`
Baseline: `rtk ant test` — BUILD SUCCESSFUL, 1609/1609 tests passed.

Ruling: The pre-existing Ant build emits resource-decoder errors, compiler warnings, and expected test diagnostics despite exit 0 and 1609 passing tests — treat the baseline output as accepted project noise and require no new failure class/count from this feature — if wrong, new warning noise could be mistaken for baseline noise.

## Pre-flight consistency scan

| Scope | Producer / consumer check | Finding / ruling |
|---|---|---|
| Task 1 self | Tests cover all five gesture types, matching, codec, invalid modifier modes; implementation contract supplies each API | Consistent. |
| Task 2 self | Adapter tests exercise legacy identity/reset and gesture persistence/corruption; implementation preserves `keybind/*` and uses `gesturebind/*` | Consistent. |
| Task 3 self | Tests cover global/context overlap, duplicate IDs, staged replacement/cancel; implementation defines matching registry/draft APIs | Consistent; test helpers must build the three actions named by the test. |
| Task 4 self | Catalog tests cover representative hidden/static and dynamic actions; implementation adds snapshot and registration hooks | Consistent; final audit, not the representative test alone, proves full coverage. |
| Task 5 self | Model, tab, layout, and capture tests correspond to four new pure model classes | Consistent. |
| Task 6 self | Page/layout/navigation tests correspond to the new editor and legacy-editor removal | Consistent; adapt the redirect to the actual `OptWnd` ownership path while preserving the stable `hotkeys` page outcome. |
| Task 7 self | Exact item/inventory defaults and explicit modifier transport are tested before handler conversion | Consistent. |
| Task 8 self | Explicit gesture table, dispatch semantics, F8 capture ordering, and handler list agree | Consistent. |
| Task 9 self | Audit and localization parity prove the spec’s completeness guard | Consistent. Source scanning is intentional policy behavior required by the spec, not a prose/change-detector test. |
| Tasks 1 → 2 | `InputGesture` is consumed by both binding adapters | Interfaces align. |
| Tasks 1 → 3 | Gesture equality/type/matching drives conflict and staged values | Interfaces align. |
| Tasks 1 → 5 | Allowed input families and modifier capture consume gesture types | Interfaces align. |
| Tasks 1 → 7 | Mouse/wheel matching is consumed by inventory resolvers | Interfaces align. |
| Tasks 1 → 8 | Keyboard/mouse/wheel/modifier matching is consumed by gameplay handlers | Interfaces align. |
| Tasks 2 → 3 | `HotkeyBinding` is held by immutable action metadata and mutated only on draft save | Interfaces align. |
| Tasks 2 → 4 | Legacy and gesture adapters back catalog registrations | Interfaces align. |
| Tasks 2 → 7 | Gesture persistence backs item/inventory actions | Interfaces align. |
| Tasks 2 → 8 | Gesture persistence and legacy bindings back gameplay actions | Interfaces align. |
| Tasks 3 → 4 | Registry/action/context types are populated by the catalog | Interfaces align. |
| Tasks 3 → 5 | Registry and draft model are consumed by filtering/conflict presentation | Interfaces align. |
| Tasks 3 → 6 | Page owns one draft-backed settings model | Interfaces align. |
| Tasks 3 → 7 | Registered action metadata supplies contexts and canonical modifiers | Interfaces align. |
| Tasks 3 → 8 | Registered action metadata supplies contexts and canonical modifiers | Interfaces align. |
| Tasks 4 → 6 | Global registry and dynamic listeners feed the editor | Interfaces align. |
| Tasks 4 → 7 | Both touch `Hotkeys.java`; Task 7 extends the catalog after Task 4 creates it | Ordered dependency, no contradiction. |
| Tasks 4 → 8 | Task 8 extends `Hotkeys` and splits `fgt-cycle` after catalog creation | Ordered dependency; keep old `fgt-cycle` preference ID for next-target. |
| Tasks 4 → 9 | `knownStaticIds()` and `KeyBinding.snapshot()` feed audit completeness | Interfaces align. |
| Tasks 5 → 6 | Pure navigation/capture/layout models are consumed by page widgets | Interfaces align. |
| Tasks 6 → 9 | Both touch localization bundles; Task 9 completes action labels and verifies parity | Ordered additive edits. |
| Tasks 7 → 8 | Task 8 reuses resolver and explicit modifier transport; both extend `Hotkeys.java` | Ordered dependency; preserve Task 7 APIs. |
| Tasks 7 → 9 | Converted item handlers are included in the no-direct-shortcut audit set | Interfaces align. |
| Tasks 8 → 9 | Converted gameplay handlers and new labels are included in the audit/parity checks | Interfaces align. |

Ruling: When `L10n.get(labelKey)` returns the untranslated key for a missing entry, `HotkeyAction.label()` must fall back to the technical action ID — this implements the spec’s missing-localization behavior — if wrong, an intentional label identical to its resource key would display the ID.

Ruling: Pull explicit category/context localization keys and both language values forward from Task 6 into Task 5 so localized search is real and warning-free when its model ships — the spec requires localized category/context search and task boundaries are secondary — if wrong, Task 5 will touch two localization files earlier than the written file list.

## Tasks

- Task 1: complete
- Task 2: complete
- Task 3: complete
- Task 4: complete
- Task 5: complete
- Task 6: complete
- Task 7: complete
- Task 8: complete
- Task 9: complete

Task 1: fix round 1/5 (3 addressed, 0 open — defensive `KeyMatch` copies; reject `k:n`; required codec/display coverage; commits 88749a9..1cd48eb)
Task 1: complete (commits 0573c59..1cd48eb, review clean)
Task 2: minor (deferred): verification output repeats accepted baseline resource/compiler noise.
Task 2: controller check: diff touches only the five planned Java/test files; build source/target and runtime dependency configuration are unchanged.
Task 2: complete (commits 1cd48eb..778e7df, review clean; 1 deferred minor)
Task 3: minor (deferred): add focused coverage for listener reentrancy, label fallback, ordering tie-breaks, `resetAll`, and rollback failures.
Task 3: minor (deferred): verification output repeats accepted baseline resource/compiler/test noise.
Task 3: complete (commits 778e7df..133c5ee, review clean; 2 deferred minors)
Task 4: fix round 1/5 (4 addressed, 0 open — binding identity; complete dynamic metadata; `togglenature` migration; `fgt-cycle` modign; commits 7f0bc9e..3c4ec9e)
Task 4: complete (commits 133c5ee..3c4ec9e, review clean)
Task 5: fix round 1/5 (5 addressed, 0 open — stable localized label API/resources; warning-free search; truthful fixtures; tab boundaries; commits e2c2c43..8cd7b60)
Task 5: minor (deferred): English/Russian category/context labels are duplicated in enum fallback metadata and resource bundles, permitting drift or L10n bypass.
Task 5: complete (commits 3c4ec9e..8cd7b60, review clean; 1 deferred minor)
Task 6: fix round 1/5 (5 addressed, 1 open — registry listener; overflow tabs; capture lifecycle; conflict checkpoint; localized capture feedback; commits 6dc8474..4a57f27)
Task 6: fix round 2/5 (1 addressed, 1 open — recursive parent listener disposal; commits 4a57f27..5aaa4c3)
Task 6: fix round 3/5 (1 addressed, 1 open — parent destroy/idempotence coverage; commit e535e07)
Task 6: fix round 4/5 (1 addressed, 0 open — one-time lifecycle test resource initialization; commit 5479985)
Task 6: complete (commits 8cd7b60..5479985, review clean)
Task 7: fix round 1/5 (3 addressed, 0 open — action-ID dispatch without physical button gates; specialized direction/count semantics; explicit legacy held Ctrl+RMB modifiers; commits e52f15c..79546ff)
Task 7: complete (commits 5479985..79546ff, review clean)
Task 8: fix round 1/5 (6 addressed, 3 open — wound dispatch; action-ID wheel semantics; canonical stockpile modifiers; waypoint/delete catalog dispatch; canonical MapView args; commits ce16e51..8d9dcda)
Task 8: fix round 2/5 (3 addressed, 2 open — physical mouse gates; stale wound helper; behavioral rebinding tests; commit d309ce9)
Task 8: fix round 3/5 (2 addressed, 1 open — delete-before-record priority; event-level dispatch API; commit 2677fde)
Task 8: fix round 4/5 (1 addressed, 0 open — actual ViewFrame/NMiniMap event-handler coverage and test isolation; commit 4c96794)
Task 8: complete (commits 79546ff..4c96794, review clean)
Task 9: fix round 1/5 (6 addressed, 0 open — per-condition audit; exact system allowlist; rebound crafting dispatch; narrow dynamic belt detection; strict localization parity; explicit missing-input failures; commits 84aa985..fdbef46)
Task 9: complete (commits 4c96794..fdbef46, review clean)
Final review: 8 Important open — unsavable full-catalog defaults; runtime-overlap conflict detection; held Ctrl+LMB ground drop; placement rotation semantics; rebound world mouse routing/server button; remaining hardcoded gameplay helpers/modes; truncated source-audit exemptions; incompatible persisted gesture families.
Final review: 3 Minor open — reversed wheel labels; duplicated locale fallback metadata/literal capture feedback; deferred registry/draft focused coverage.

Final integrated fix pass: all 8 Important and 3 Minor findings addressed; see `final-fix-report.md` for RED/GREEN evidence, canonical input/context decisions and audit mutations. Focused 90/90; full `rtk ant test` 1691/1691; `rtk ant` successful; diff whitespace and forbidden-editor/helper searches clean. Remaining validation limitation: no live game-server/GPU interactive smoke test.
