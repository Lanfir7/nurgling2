# Quest Objective Actions Design

## Goal

Add quest-aware resource guidance and crafting shortcuts to every objective row in both the compact quest tracker and the full quest journal.

The feature applies only to active quest objectives. It must not add global automation outside the quest UI.

## User-visible behavior

- Active objectives that request a tree product automatically enable the corresponding tree map icons through the vanilla `GobIcon.Settings` state.
- Tree products include the fruit/seed/bough/bark products and the species-specific boards and blocks already described by `VSpec.object`.
- Automatically enabled tree icons remain enabled until the owning quest is completed or removed. Completing only the individual objective does not release them.
- An icon that was already enabled by the player before the first quest claim remains enabled after all related quests end.
- If several active quests need the same tree icon, the icon is released only after the last quest ends.
- A recognized forage objective gets a small action button at the right edge of its row. The button opens `Map Tools -> Terrain Search` and selects the forageable's terrain set, using the same flow as `ForageHelperWindow`.
- A recognized stone or ore objective gets the same style of button. It opens the terrain search with the corresponding `gfx/tiles/rocks/*` resource selected, so mine walls containing that resource are highlighted.
- A recognized `Create` objective gets a craft button. It activates the exact learned crafting pagina and opens the normal crafting window; it never starts crafting automatically.
- Buttons appear in both `NQuestInfo` and the selected quest's full journal view.
- Clicking the non-button portion of a compact tracker row keeps its existing behavior of opening the quest.
- Unknown, malformed, unavailable, or ambiguous objectives remain ordinary objective rows without a button.

## Architecture

### Objective parsing and resolution

Extend the existing quest parsing model instead of parsing independently in each UI.

`QCond` remains the normalized representation of a server condition. It will expose the requested item for `PICK`, `BRING`, and `CREATE` objectives while preserving the current safe fallback to `Verb.OTHER`. Existing hunt and giver parsing behavior must not change.

A new focused `QuestObjectiveActionResolver` consumes a `QCond` and returns an immutable action description:

- `TREE_ICON`: one or more tree/icon resource identifiers;
- `FORAGE_TERRAIN`: canonical terrain names from the matching `Forageables.Entry`;
- `ROCK_TERRAIN`: one or more exact mine tile resources resolved through `RockResourceMapper`;
- `CRAFT`: one exact learned `MenuGrid.Pagina` candidate;
- no action when the objective is complete, unsupported, ambiguous, or unresolved.

Resolution uses normalized, case-insensitive display names but returns exact existing resource identifiers. It reuses these data sources:

- `VSpec.object` for tree products, including `*-log` board/block entries normalized back to their living tree species;
- `Forageables` for forageable display names and terrain lists;
- `RockResourceMapper` for stone/ore names and mine tile resources;
- the current session's learned `MenuGrid` paginae for crafting recipes.

The resolver must not infer an action solely from a loose substring when more than one exact normalized candidate exists.

### Tree icon claim controller

A per-UI `QuestTreeIconController` owns temporary quest claims. Its state is keyed by quest id and icon setting id.

On every authoritative active-quest condition update it computes the required tree icons, then diffs them against the prior claims:

1. Apply a session-local effective-visibility override keyed by the complete `GobIcon.Setting.ID`.
2. Leave `GobIcon.Setting.show` unchanged so any concurrent settings save contains only the player's preference.
3. Make minimap icon consumers read the effective visibility (temporary override first, persisted `show` otherwise).
4. When a quest completes or is removed, release its claims.
5. When the final claim is released, remove the override so the current persisted preference becomes effective again.

The controller is session-local. After reconnect/login, the current active quest list recreates claims. It tolerates icon resources that are still loading by retrying reconciliation on later quest/model updates without blocking the UI thread.

### Shared row actions

Both UIs receive the same resolved action model and dispatch through a small `QuestObjectiveActions` service:

- forage actions call `MapToolsWindow.openTerrainSearch(entry.terrains)`;
- rock actions call a new exact-resource variant of the terrain-search opener, avoiding fuzzy preset matching;
- craft actions activate the resolved pagina through the existing `MenuGrid.use(...)` path.

`NQuestInfo.CondRow` reserves a small right-side button area and elides text against the remaining width. Button clicks consume the event; other left clicks retain `openQuest(row.questId)`.

The full journal replaces only ordinary `DefaultCond` rows with an action-capable condition widget. Server-provided custom condition widgets remain untouched. The custom row keeps the vanilla status glyph, color, localized condition text, wrapping, and update lifecycle while adding the same right-side action button.

Buttons use existing client textures where a suitable map/craft glyph exists, include a tooltip, and remain hidden rather than disabled when their action cannot currently succeed.

## Data flow

1. `QuestWnd` receives authoritative quest and condition messages.
2. `QuestModel` builds `QCond` values for the compact tracker.
3. The shared resolver classifies each active condition.
4. The tree controller reconciles automatic icon claims for the entire active quest, including already-ready objectives until quest removal.
5. Each UI row renders a button only for a currently executable forage, rock, or craft action.
6. A button dispatches to the existing map-highlight or crafting mechanism.
7. Moving a quest to completed or removing it releases its tree icon claims.

## Failure handling

- Condition parsing is bounds-checked and returns no action on unknown text.
- Missing forage, rock, tree, icon-setting, or pagina data produces no button and no state mutation.
- Loading resources are not waited on from draw or input handlers.
- UI callbacks verify that the current `NGameUI`, `MenuGrid`, and target pagina still exist before dispatch.
- One failed objective resolution must not prevent other quest objectives from rendering or reconciling.

## Testing

Follow TDD for every production behavior.

- Extend `QCond` tests with representative `Pick`, `Bring`, and `Create` strings plus malformed inputs.
- Add resolver tests for tree fruit, tree board/block, forageable, quartz/stone, craft, ambiguous, completed, and unknown objectives.
- Add claim-controller tests covering initially-off icons, initially-on manual icons, two quests sharing one icon, ready objectives retaining claims, quest removal, and late icon availability.
- Add row-action layout/input tests proving button clicks dispatch once and normal row clicks still open the quest.
- Add full-journal tests proving ordinary conditions gain actions while server-provided custom condition widgets remain untouched.
- Run the targeted tests, the full Java test suite, and the project build before completion.

## Non-goals

- Automatically walking to, collecting, delivering, mining, or crafting an item.
- Adding action buttons outside quest objective rows.
- Persisting automatic tree-icon choices as player preferences.
- Guessing a recipe or resource when matching is ambiguous.
- Changing existing quest grouping, filtering, localization, giver markers, or objective completion rules.
