# Indoor Home Territories

## Goal

Keep a location classified as home after the character enters a building that stands on a saved home claim or in a saved home village. The classification must survive relogging while the character is indoors and must work for all characters in the same game world.

Building floors and cellars inherit home status. Mines, mineholes, ladders, and natural caves never inherit home status from the surface; their own claims remain responsible for direct home detection.

## Existing Behavior

`CurrentHomeTerritories` detects `cplot` and `vlg` overlays under the player. Building interiors are separate map instances and do not expose those surface overlays, so direct detection correctly returns no claim and no village indoors.

Chunk Nav already records the stable information needed to bridge that boundary:

- server-stable source and destination grid IDs;
- portal coordinates local to each grid;
- bidirectional portal connections;
- persistent instance IDs and layers for interiors, cellars, and mines;
- per-world storage selected by genus.

Map-file segment IDs, session coordinates, and gob IDs are not suitable identities because they can change between sessions. The design uses server grid IDs and local tile coordinates only.

## Decision

Use Chunk Nav as the discovery mechanism, but keep home classification in a dedicated per-world indoor-home registry. A scanned chunk does not become an unconditional home flag. Instead, an indoor binding records why the instance is home and validates that reason against the current Home Setup configuration.

This avoids stale home chunks after a claim or village is removed, avoids changing the Chunk Nav binary format, and allows home classification to remain available even when the walkability overlay is disabled.

## Stable Home Origins

Each saved surface home has a compact stable key:

- village: normalized village name;
- claim with geometry: the saved claim anchor `(gridId, localX, localY)`;
- legacy claim without geometry: normalized owner name.

The claim anchor is retained when repeated captures expand the saved claim geometry. A claim that is deleted and later captured with a different anchor is treated as a new home; entering its building again recreates the indoor binding.

If the entrance is covered by both a saved village and a saved claim, both origins are recorded. The indoor location remains home while at least one origin is still configured.

## Indoor Registry

Add a new `homeInteriors` configuration value, partitioned by game-world genus. It contains versioned bindings with:

- stable binding ID;
- root interior instance ID;
- all known server grid IDs belonging to the binding;
- automatic origin keys;
- root exterior portal identity: source grid ID, local tile, and resource name;
- a manual override flag;
- optional display name and last-seen timestamp.

The registry is updated atomically so simultaneous sessions and Home Setup edits do not overwrite each other. New automatic bindings are saved immediately after a successful portal traversal rather than waiting for Chunk Nav's periodic save.

An automatic binding is active only when at least one of its origin keys still exists in the saved surface-home list. A manual binding is active until explicitly removed or unmarked.

## Portal Learning

Portal tracking captures the source home context while the player and clicked entrance are still on the source map. After a confirmed grid transition, it attaches the destination grid and instance to an indoor binding.

Automatic inheritance is allowed for:

- recognized building exteriors and their paired interior doors;
- ordinary building doors that change instances;
- stairs between building floors;
- cellar entrances and cellar stairs.

Automatic inheritance is forbidden for:

- mineholes and mine ladders;
- natural cave entrances and exits;
- palisade and brick-wall gates;
- unknown transitions whose portal pair was not confirmed;
- teleports, including hearth travel.

Literal wall gates remain normal walkable openings. Chunk Nav already excludes them from portal classification, so the UI should use the terms "building entrance" or "portal" rather than "gate".

When the source is already an active indoor home, an allowed nested transition propagates the same binding and origins to the destination instance. Returning to an outside layer does not mark the destination surface grid; normal claim and village detection resumes there.

Portal relationship tracking required for home inheritance runs whenever at least one home is configured, independently of the Chunk Nav walkability-overlay toggle. Full chunk scanning is not required: a confirmed transition can create minimal source and destination records.

## Resolving Current Home Status

Expose one resolver used by debug information and future storage/workstation consumers:

1. Run existing direct claim and village detection.
2. Resolve the player's current stable server grid ID.
3. Find an active indoor binding by exact grid ID.
4. If needed, use the loaded Chunk Nav chunk's persistent instance ID to match another known grid in the binding.
5. Return separate status fields for village, claim, indoor inheritance, and overall home.

Overall home is true when direct village, direct claim, or active indoor inheritance is true. No "last known home" session latch is used, so teleporting or reconnecting cannot leave a stale result.

The debug window adds:

- `Indoor home: Yes/No`;
- `Home source` such as `Lanfir's Claim → Stone Mansion`;
- current stable grid and instance IDs for diagnostics.

When the character logs in inside a previously learned building, the exact stored grid ID is sufficient to restore home status before another traversal occurs.

## User Interface

### Home Setup

Keep the existing surface-home list and add an `Indoor home zones` list. Each row shows the building or fallback stable identifier, automatic/manual status, and active origin. Rows can be removed without deleting their source claim or village.

Removing an automatic row suppresses that learned binding until the building is traversed again. Removing the source surface home immediately deactivates all automatic bindings that depended exclusively on it.

### Chunk Nav Visualizer

- highlight active home instances with a distinct color;
- show `Home: Auto`, `Home: Manual`, or `Home: No` in selected details;
- show the active origin when automatic;
- provide `Mark instance as home` / `Unmark manual home` for unrecognized entrances and legacy data.

Manual marking applies to the whole known building instance, not just the selected 100×100 grid. The action is disabled for outside and mine/cave layers; mines and caves become home only through their own directly detected saved claims.

## Existing Data and Failure Handling

- Existing saved surface homes remain compatible.
- Existing Chunk Nav portal connections can retrospectively identify claim-based entrances when the saved claim geometry contains the portal tile.
- Existing village-based interiors require one new traversal or a manual mark because only the village name, not village geometry, is currently stored.
- A first-time user already inside a building must exit and re-enter once or mark the instance manually.
- Missing or loading Chunk Nav data never converts a location to home. The resolver reports the state as unknown/loading in diagnostics and retries later.
- Unknown portal resources are not inherited automatically; this prevents a market or unrelated instance from becoming home through a false transition.
- Destroying and rebuilding a house normally produces new interior grid IDs. The old binding becomes harmless inactive data and the rebuilt house is learned on the next traversal.

## Tests

Unit tests cover:

- stable home-origin keys and registry round trips;
- exact grid and instance matching across sessions;
- village/claim dual-origin activation;
- automatic deactivation after deleting an origin;
- manual marking and removal;
- allowed building, floor, and cellar propagation;
- rejected mine, cave, wall-gate, teleport, and unknown transitions;
- a non-home market building remaining non-home;
- restart while already inside a learned home;
- concurrent automatic learning and Home Setup edits.

Integration tests cover the sequence `home surface → building → floor/cellar → building → surface` and verify that direct and inherited status switch without a stale frame. Existing Chunk Nav binary compatibility, portal-routing tests, Home Setup persistence tests, and the full client build remain part of verification.

## Non-goals

- Inferring ownership from interior objects or door names.
- Treating every scanned Chunk Nav chunk as home.
- Automatically inheriting home status into mines or caves.
- Using session coordinates, map-file segment IDs, or transient gob IDs as persistent identity.
