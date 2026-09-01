# Skyrim-style compass bar

## Goal

Add a movable top compass to the Lanfir HUD that remains correct in every camera mode. The first version displays only active quest targets and party members. It can replace the existing screen-edge quest and party pointers without removing their data sources.

## User-facing behavior

- The compass is enabled by default and starts at the top center of the HUD.
- It shows a camera-centered 180-degree sector. The middle tick is the direction in which the camera is looking, independently of the selected camera implementation.
- Cardinal and intercardinal labels are localized: Russian uses `С`, `СВ`, `В`, `ЮВ`, `Ю`, `ЮЗ`, `З`, `СЗ`; other locales use `N`, `NE`, `E`, `SE`, `S`, `SW`, `W`, `NW`.
- Active quest targets and party members are placed at their relative bearings. Every visible target shows an icon, a name, and distance below it.
- Targets outside the frontal sector are assigned to the left or right edge according to the shortest turn toward them. Each edge shows the nearest target and a `+N` count for additional targets assigned to that edge.
- Party markers use the member color. Their name comes from the member gob's `KinInfo`; while that data is unavailable, the fallback is the localized equivalent of `Party member`.
- The compass does not consume ordinary game clicks.

## Position and width editing

- Position is changed through the existing HUD edit mode and is persisted by the normal draggable-widget mechanism.
- Width is changed by holding `Ctrl` and dragging the left or right edge with the left mouse button.
- Dragging an edge changes only the width: the opposite edge remains fixed. Height is fixed by the compass layout.
- The initial width is approximately 520 UI-scaled pixels. Width is clamped to 300-900 UI-scaled pixels and to the available screen width.
- The saved position and width use the current per-profile HUD configuration.
- Edge resizing is implemented only for the compass. The behavior of existing `NResizableWidget` instances is unchanged.

## Settings

`QOL Lanfir` receives two independent live settings:

1. `Show compass bar`, default `true`.
2. `Show legacy edge pointers`, default `false`.

Changing either setting applies immediately without reconnecting. Hiding legacy pointers suppresses both quest-pointer drawing and the existing party edge arrows. It does not destroy `Pointer` widgets or party state, because the compass still consumes those data sources.

## Architecture

### Compass widget

`NCompassBar` owns rendering, target collection, text/icon caches, and the fixed-height layout. It is hosted by a dedicated draggable container that adds Ctrl-edge resizing and persists the width through `NResizeProp`. `NGameUI` creates the widget with the other critical HUD widgets so early quest pointers are not missed.

The default layout table gains a `compass` top-center slot and a localized HUD-editor title. The normal layout preset and reset mechanisms therefore know its default position and visibility.

### Pure bearing model

A separate pure helper, `NCompassMath`, performs:

- normalization around the angle wrap boundary;
- camera-relative bearing calculation;
- mapping the visible `[-90 degrees, +90 degrees]` interval to the compass width;
- classification of targets outside that interval to a left or right rear bucket;
- selection of the nearest rear target and calculation of its `+N` count.

The helper receives world bearings and `MapView.camera.angle()` as plain numeric values. It does not use screen projection, so perspective, orthographic, and BAD camera projection differences cannot invert the compass.

### Target adapters

The renderer converts both sources into immutable compass-target snapshots:

- Quest targets are collected from active `haven.res.ui.locptr.Pointer` widgets. Widget traversal follows the existing `NQuestInfo` pattern and de-duplicates by widget identity. A target snapshot carries `Pointer.tc()`, `tip()`, `icon`, and distance.
- Party targets are collected from `ui.sess.glob.party.memb.values()`, excluding the local player and entries without a known coordinate. A snapshot carries `Party.Member.getc()`, color, resolved name, and distance.

Transient `Loading`, missing gob attributes, disappearing widgets, or unavailable coordinates cause only that target to be skipped for the current frame. They must not hide or break the compass.

### Legacy pointers

The legacy setting is checked at the drawing boundaries:

- `Pointer.draw` skips only its old screen-edge indicator when legacy pointers are disabled.
- `MapView.partydraw` skips old party edge arrows under the same setting.

All updates, tooltips, coordinates, and widget lifetimes remain intact.

## Rendering and overlap

- Direction ticks are drawn behind target markers, with a stronger center tick.
- Quest markers retain their supplied quest icon. Party markers use a compact party glyph tinted with the member color.
- Marker labels are centered below their icon and use an outline for readability.
- Nearby markers are assigned to a small number of vertical lanes. When markers still cannot fit, nearer targets take priority and the hidden count is folded into the closest displayed marker.
- Text and scaled textures are cached and invalidated only when their source text, icon, UI scale, or width changes.

## Tests and verification

Unit tests for `NCompassMath` cover:

- every cardinal direction;
- camera rotations and the `0/360` wrap boundary;
- exact sector edges and targets immediately on either side;
- front/rear classification for opposite headings;
- left/right rear assignment by shortest turn;
- nearest-target choice and `+N` counts;
- width mapping and width clamps.

Configuration/default-layout tests cover both new defaults, the compass slot, and persistence keys. Integration verification covers live setting changes, all available camera modes (including BAD), HUD dragging, Ctrl-resizing from both edges, and coexistence with quest and party updates. The final check runs the complete Ant test suite.

## Out of scope

- Map markers, hearth/village markers, custom waypoints, hostile creatures, and resources.
- Clicking compass markers to interact or navigate.
- Vertical resizing or generic edge resizing for other HUD widgets.
- Removing or rewriting the existing pointer data protocols.
