# Stoat click circle

Date: 2026-09-19

## Goal

Give stoat the same ground click circle as squirrels, so a left click on the disc hits the animal. No auto-attack. Dead stoat keeps the circle so the carcass is easier to click.

## Scope

Only `gfx/kritter/stoat/stoat` (winter coat is the same gob). Color, radius and visibility live in existing Critter Circles settings.

Out of scope: `aggro` on click, other animals, forage-marker changes, new settings panel.

## Player flow

1. Living or dead stoat shows a disc under it when Critter Circles are on and the stoat row is checked.
2. Left click on the disc is a normal gob click (same as clicking the model). Player attacks with the fight cursor themselves.
3. Settings: Game environment → Critter Circles → Stoat.

## Architecture

```text
NCritterCircle.CRITTER_PATHS   → forage + circle (squirrel, …)
NCritterCircle.HITBOX_PATHS    → circle only (stoat)

hasCircle()  = critter or hitbox   → overlay + settings rows
isCritter()  = CRITTER_PATHS only  → ForagePickupMarker, observeForageCritter
keepWhenDead() = hitbox            → overlay tick does not remove on dead/knock
```

Click path is unchanged: overlay is a gob sprite, so `GobClick` already sends a gob click. Do not intercept MapView or call `NUtils.attack`.

## Overlay rules

- Attach `NCritterCircle` when `hasCircle(name)`, not `isCritter(name)`.
- Collectible critters: drop the overlay on `dead`/`knock` (current behaviour).
- Hitbox animals: keep the overlay after death. Do not rewrite the shared squirrel path; gate removal with `keepWhenDead(path)`.
- Default colour/radius for stoat: same purple disc as non-rabbit critters (`193,0,255,140`, radius 10).

## Settings

- `buildDefaultConfigs()` and the settings list iterate `CRITTER_PATHS + HITBOX_PATHS`.
- Existing saved configs lack stoat: if a circle path has no `NCritterCircleConf`, create the default and append it to the global `critterCircleSettings` list so it persists.

## Tests

- `isCritter("gfx/kritter/stoat/stoat")` is false.
- `hasCircle("gfx/kritter/stoat/stoat")` is true; squirrel still `isCritter` and `hasCircle`.
- `keepWhenDead` is true for stoat, false for squirrel.
- Settings/default configs include the stoat path.

## Player note

Bilingual `changes/` note when implemented: stoat has a click circle like small critters; find it in Critter Circles. No class names.
