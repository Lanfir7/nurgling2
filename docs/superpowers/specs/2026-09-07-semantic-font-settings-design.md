# Semantic font settings — design

## Goal

Make every user-visible client font configurable through one grouped settings panel without forcing one universal default. The initial theme should stay visually close to the current client: Open Sans for modern headings and calendar/status text, Sans for most legacy body text and meters, and Monospaced for the console. Users can tune the roles in-game after migration.

The change covers client-rendered text in Haven and Nurgling code, including plain `Text`, `RichText`, widget controls, HUD text, overlays, item overlays, chat, quests, maps, and debug text. Server/resource-authored text must no longer bypass the role system merely by naming `Sans`, `Serif`, or `Fraktur`.

## Chosen approach

Use semantic font roles with grouped settings and inheritance. A role describes why text exists rather than which widget happens to render it. Renderers request a role from one central font theme service. Each role has a complete default matching the current visual character, but can inherit from another role or override family, size, style, color, and text effect.

This avoids two rejected extremes:

- one global font, which cannot preserve the current hierarchy;
- a setting per widget, which would create hundreds of unstable options tied to implementation details.

## Role hierarchy

### General

| Role | Default | Examples |
|---|---|---|
| `SYSTEM` | Sans, 12, regular | labels, buttons, dropdowns, menus, input fields, table rows, descriptions, non-heading tooltip text |
| `HEADING` | Open Sans Semibold, 14 | section names, item/skill/wound/recipe names, tooltip headings |
| `WINDOW_TITLE` | Open Sans Semibold, 12 | window captions and settings title bars |
| `SECONDARY` | Open Sans, 10 | hints, resource paths, counters, timestamps, secondary status text |

### Communication

| Role | Default | Examples |
|---|---|---|
| `CHAT` | Sans, 14, regular | chat messages, chat input, sender names, channel labels |
| `NOTIFICATION` | Sans, 14, regular with current shadow | centre-screen messages, errors, alarms, transient notices |

### Game interface

| Role | Default | Examples |
|---|---|---|
| `HUD_INFO` | Open Sans Semibold, 12 | calendar, time, player count, ping, province, compass and status captions |
| `METER` | Sans, 12, regular with current blur | health, stamina, energy and drink values |
| `HOTKEY` | Sans, 12, bold with current outline | hotbelt keys, action keys and slot numbers |
| `COMBAT_UI` | Sans, 16, bold with current outline | openings, IP, combat controls and combat-window numeric values |

### Quests

| Role | Default | Examples |
|---|---|---|
| `QUEST_TITLE` | Sans, 12, bold | quest groups, giver names, quest titles and tracker badges |
| `QUEST_OBJECTIVE` | Sans, 11, regular | objectives, progress, distances and completion states |

Quest descriptions use `SYSTEM`; quest window captions use `WINDOW_TITLE`.

### Items

These roles preserve the existing item-overlay defaults and remain individually configurable because the current client already exposes them separately.

| Role | Default | Examples |
|---|---|---|
| `ITEM_QUALITY` | Sans, 10, bold | item and stack quality |
| `ITEM_STACK` | Sans, 10, bold | stack count/stack metadata |
| `ITEM_AMOUNT` | Sans, 10, bold | item amount |
| `ITEM_STUDY` | Sans, 10, bold, yellow | LP/study information |
| `ITEM_PROGRESS` | Sans, 10, regular, orange with background | drying, expiration and progress overlays |
| `ITEM_VOLUME` | Sans, 10, regular, green with background | volume, weight and custom secondary item labels |

Item tooltip headings use `HEADING`; all ordinary tooltip lines use `SYSTEM`; technical/resource-path lines use `SECONDARY`.

### World and map

| Role | Default | Examples |
|---|---|---|
| `CHARACTER_NAME` | Sans, 12, regular with current outline | player, kin, buddy and party names |
| `OBJECT_LABEL` | Sans, 12, regular with current outline | barrels, containers, quest givers, configured gob labels and area titles |
| `MAP_LABEL` | Open Sans Semibold, 12 | map/minimap marker names, map search and political-owner labels |
| `NAVIGATION_LABEL` | Sans, 11, regular with current outline | waypoint ETA, storage trails and measurement labels |
| `FLOATING_VALUE` | Sans, 12, bold with current outline | damage, gob health, quality, speed and check results above the world |

### Technical

| Role | Default | Examples |
|---|---|---|
| `CONSOLE` | Monospaced, 12, regular | console, command line, map inspection and debug diagnostics |

## Configuration model

Introduce a stable `FontRole` enum and store settings by role key rather than Java class name. Each `FontRoleConfig` contains:

- optional parent role;
- `inherit` flag;
- font family;
- logical size before `UI.scale`;
- style: regular, bold or italic;
- optional color override;
- optional effect override: none, shadow, blur or outline, with the existing effect parameters retained as defaults.

The resolver returns an immutable resolved style. Missing or malformed role data falls back to the shipped default for that role. Unknown/unclassified client text falls back to `SYSTEM`, ensuring it remains configurable.

The family list initially remains compatible with existing settings: Inter/Helvetica resource, Roboto, Open Sans, Open Sans Semibold, Sans, Serif, Fraktur, and Monospaced. The misleading internal `Inter -> helvetica` mapping must either be relabelled as Helvetica or backed by a real Inter resource; the UI must not claim a different font than it loads.

## Runtime service and cache invalidation

Add one central font theme service responsible for:

- resolving role inheritance;
- constructing `Text.Foundry`, `Text.Furnace`, and `RichText.Foundry` instances;
- applying `UI.scale` exactly once;
- publishing a monotonically increasing theme revision after save/reset;
- caching resolved foundries by role, color override, size delta and effect;
- disposing/rebuilding rendered text textures when the revision changes.

Widgets with cached `Tex` values compare their stored revision before drawing or updating. Dynamic widgets can rebuild immediately; static widget trees receive a theme-change notification and relayout when font metrics change. Saving font settings therefore updates the running client without a restart.

## Renderer migration rules

All user-visible renderers must obtain fonts from the theme service. Direct construction remains allowed only inside the service or for generated resource-font previews.

- `Label`, `Button`, `Dropbox`, `TextEntry`, `FlowerMenu`, `SListMenu`, generic `g.text`, and default `Text.render` use `SYSTEM`.
- Default `RichText` uses `SYSTEM`. Known heading renderers request `HEADING`; ordinary tooltip and description bodies remain `SYSTEM`.
- `Text.sans`, `Text.serif`, `Text.dfont`, and `Text.fraktur` become compatibility aliases resolved through roles rather than escape hatches.
- Rich-text `$font[...]` directives from legacy resources are mapped through configurable compatibility roles; they cannot silently force an unmanaged AWT family.
- Purpose-specific code in chat, quests, HUD, combat, map, overlay and item classes requests the corresponding role.
- Semantic colors such as quest completion, chat sender color, damage type and warning severity remain owned by their feature unless the role explicitly enables a color override.

A repository check rejects new direct user-visible `Text.Foundry`, `RichText.Foundry`, `new Font`, `Text.sans`, `Text.serif`, `Text.dfont`, and `Text.fraktur` usages outside an allowlist for the theme implementation, tests, previews and non-UI rendering.

## Settings UI

Replace the current flat five-type selector with a grouped font panel:

1. A global preset row offers the shipped defaults, “use one font everywhere”, reset, import and export.
2. Collapsible groups match the role hierarchy above.
3. Each role row shows its name, a live preview, inheritance state, and an edit button.
4. The editor exposes family, size and style. Color and effect controls appear only for roles that safely support them.
5. Changing a parent previews all inheriting children immediately.
6. Resetting one role restores its shipped role-specific default; resetting a group affects only that group.

The existing item overlay layout/corner/threshold controls remain in Item Overlay Settings. Their font controls move to or reuse the shared role editor so there is one source of truth and no duplicate values.

## Migration

On first load of the new schema:

- current `defaultFont` becomes `SYSTEM`;
- current `uiFont` seeds `WINDOW_TITLE` and legacy decorative compatibility;
- current `questsFont` seeds `QUEST_TITLE` and `QUEST_OBJECTIVE`, preserving the objective size delta;
- current `barrelsFont` seeds `OBJECT_LABEL`;
- current `charactersFont` seeds `CHARACTER_NAME`;
- the six existing item-overlay font configurations seed their matching item roles;
- roles without old equivalents use the role-specific defaults listed above.

Migration is idempotent and retains the old fields for one release as read-only fallback. Saving writes only the new schema. Invalid family names fall back to the role default and produce one diagnostic warning rather than breaking client startup.

## Scope and rollout

The work is implemented in waves behind the same final schema:

1. Theme model, migration, resolver, settings UI and generic widget primitives.
2. Chat, quests, windows, tooltips and item overlays.
3. HUD, meters, combat and notifications.
4. World overlays, character names, map/minimap, navigation and technical text.
5. Repository audit/check, removal of remaining unmanaged font paths and manual visual pass.

The feature is complete only after the audit finds no unmanaged user-visible font construction. Temporary partial coverage is acceptable during implementation, but not as the final state.

## Testing

Automated tests cover:

- default values for every role;
- inheritance and override resolution;
- JSON round-trip and malformed-value fallback;
- migration from each legacy font field and all six item overlay settings;
- role color/effect preservation;
- revision increments and foundry-cache invalidation;
- live widget relayout when font metrics change;
- RichText body/heading and legacy font-directive mapping;
- representative routing for generic controls, chat, quests, HUD, meters, tooltips, item overlays, world overlays and console;
- repository audit preventing new unmanaged font creation.

Verification runs focused font tests, the full test suite, `ant test`, `ant bin`, and a manual in-game matrix covering each settings group at normal and increased UI scale. The manual pass changes one role at a time to a deliberately distinct family and confirms that only the intended text changes.

## Completion criteria

- Every client-rendered user-visible text path resolves through a `FontRole`.
- Default appearance remains recognizably close to the current client and is not one universal font.
- Changing any role updates the running client without restart.
- Tooltip body text is `SYSTEM`; tooltip titles are `HEADING`.
- Chat input and sent messages use the same `CHAT` role.
- Existing user font and item-overlay choices survive migration.
- No duplicate font settings exist across panels.
- Unclassified text has a configurable `SYSTEM` fallback.
- Full tests and build pass, and the visual role matrix has been checked in-game.
