# Hotkey Presets Design

## Goal

Add global hotkey presets to the categorized hotkey settings page. The client ships an immutable `Default` preset and can add more immutable built-in presets later. Users can create, select, update, share, import, and delete their own presets without changing the behavior of the existing hotkey resolver.

## User experience

The hotkey page gains a preset row above search and category tabs:

`Preset: [ Default ▼ ] [Create] [Copy code] [Paste code] [Delete]`

- The dropdown contains immutable built-in presets followed by user presets.
- Selecting a preset replaces the staged hotkey values on the page. Runtime bindings change only when the containing settings window is saved.
- Switching presets while staged hotkey or preset changes exist asks whether to discard those changes.
- Editing an immutable built-in preset automatically forks it into the first free name `Пользовательский N`. The fork remains staged until the settings window is saved.
- Editing a selected user preset keeps its identity. Saving the settings window updates that same preset.
- `Create` asks for a non-empty name and creates a user preset from the currently displayed bindings. Duplicate local names receive a numeric suffix.
- `Copy code` is enabled only for a user preset and places its share code on the system clipboard.
- `Paste code` reads a share code from the system clipboard and stages it as a new user preset. A duplicate imported name receives a suffix such as `Мой пресет (2)`.
- `Delete` is enabled only for a user preset. Deleting the selected preset stages its removal, selects `Default`, and stages the default bindings.
- Cancelling the settings window discards all staged preset selection, creation, import, update, deletion, and hotkey changes.

Buttons use localized text and the existing responsive hotkey layout rules. Long preset names are ellipsized and retain the complete name as a tooltip. The dropdown must make the current selection visually unambiguous.

## Preset model

`HotkeyPreset` is an immutable value containing:

- a stable preset ID;
- a display name;
- whether it is built-in;
- a complete map of hotkey action ID to encoded `InputGesture`.

The map includes disabled gestures (`none`). Built-in presets are provided by a catalog in code. `Default` is generated from every registered action's `defaultGesture()`, so it always follows the client version. Future programmed presets use the same catalog interface and cannot be changed or deleted by the user.

User presets are full snapshots rather than deltas. When applying a snapshot:

- entries matching current action IDs are staged;
- current actions missing from the snapshot receive their current built-in default;
- unknown action IDs from a newer client are retained in the stored preset but ignored by the current client.

This makes old presets acquire sensible bindings for newly added actions and allows a preset imported from a newer client to survive a round trip through an older one.

## Existing-user migration

The feature must never overwrite existing bindings on first launch. If no preset state exists:

- when current bindings equal `Default`, select `Default`;
- when any current binding differs, create `Пользовательский 1` from the current bindings and select it;
- do not write or change runtime bindings merely by opening the settings page.

Migration becomes durable with the first successful settings save or preset-library write.

## Staging and save transaction

Preset selection and library edits use a draft model alongside the existing `HotkeyDraftModel`. Applying a preset fills the existing hotkey draft, so conflict calculation and row rendering continue to use the established path.

The save coordinator validates all gesture types and conflicts before writing. It snapshots the current bindings, selected preset, and user-preset store, then commits the runtime bindings and the global preset store as one logical operation. On any failure it restores both snapshots, keeps the UI dirty, and reports a localized error. A successful save clears both drafts and records the selected preset ID.

Editing a built-in preset first creates a staged user fork, switches selection to that fork, and then records the gesture edit. Editing a user preset changes its staged snapshot. This guarantees that built-ins remain immutable while repeated saves update the currently selected user preset.

## Persistence

User presets and the selected preset ID are stored globally for the client in `hotkey-presets.json`, not per character or account. The file uses a versioned JSON schema and atomic replacement through the client's existing file utilities.

Schema version 1 contains:

- `version`;
- `selectedPresetId`;
- `presets`, each with `id`, `name`, and a gesture map.

Built-in preset bodies are not written to disk. A missing file means migration is required. A corrupt file is preserved for diagnostics, the client falls back to the migration rules, and the settings page shows a localized warning rather than failing client startup.

## Share-code format

Share codes contain one user preset only:

`NURGLING-HOTKEYS-1:<base64url(gzip(utf8-json))>`

The JSON contains the schema version, preset name, and gesture map. It contains no account, character, path, token, or unrelated client settings.

Import validation occurs before modifying drafts:

- require the exact prefix and supported schema version;
- reject malformed Base64, compression, JSON, gesture encodings, and duplicate action keys;
- limit encoded and decoded size, preset-name length, and binding count;
- preserve unknown action IDs but never execute or reflectively load anything from the payload.

An invalid code produces one localized error and leaves all current state unchanged.

## Components

- `HotkeyPreset` — immutable preset value and normalized gesture snapshot.
- `HotkeyPresetCatalog` — immutable built-in presets, beginning with `Default`.
- `HotkeyPresetStore` — global versioned JSON load/save with atomic writes and migration state.
- `HotkeyPresetCodec` — deterministic share-code encode/decode and validation.
- `HotkeyPresetDraftModel` — selected preset plus staged CRUD and automatic user forking.
- `HotkeyPresetSaveCoordinator` — conflict validation, runtime-binding commit, preset-store commit, and rollback.
- `HotkeyPresetControls` — dropdown, create/copy/paste/delete controls integrated into `HotkeySettings`.

The storage and codec layers do not depend on UI classes. Clipboard access stays in the UI control, using the existing Haven clipboard API.

## Error handling

- Empty names are rejected; surrounding whitespace is trimmed.
- Duplicate names are made unique with ` (N)` suffixes.
- Built-in presets cannot be overwritten, shared as user data, or deleted.
- Deleting a missing preset is harmless and refreshes the list.
- Store and clipboard failures are reported through localized UI errors and do not partially apply a preset.
- Imported conflicts use the existing conflict display and prevent save until resolved.

## Testing

Automated tests cover:

- `Default` generation from registered action defaults;
- immutable built-in behavior and future built-in registration;
- migration of unchanged and already-customized existing bindings;
- automatic user-preset creation after editing a built-in;
- repeated saves updating the selected user preset;
- create, select, delete, cancel, and duplicate-name behavior;
- missing and unknown action compatibility across catalog versions;
- deterministic codec round trips and malformed/oversized payload rejection;
- atomic persistence, corrupt-file fallback, and save rollback;
- conflict integration with `HotkeyDraftModel`;
- responsive layout, selection visibility, button enablement, and lifecycle cleanup.

The existing hotkey audit and gameplay gesture suites remain green. The feature adds no network calls and no startup work beyond loading one small local JSON file when hotkeys are initialized.

## Acceptance criteria

- `Default` reproduces all current standard bindings and cannot be changed or deleted.
- Existing customized bindings are preserved and appear as an automatically created user preset.
- Selecting a preset previews it without changing runtime behavior until settings are saved.
- Editing a built-in creates a user preset; later saves update that selected user preset.
- User presets can be explicitly created, imported, copied as a code, and deleted.
- Share codes round-trip all supported mouse, wheel, modifier, keyboard, and disabled gestures without unrelated data.
- New actions absent from an older preset use their defaults; unknown newer actions do not break import.
- Cancelling or encountering an error leaves runtime bindings and persistent presets unchanged.
