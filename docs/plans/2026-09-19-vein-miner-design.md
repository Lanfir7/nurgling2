# Vein miner

Date: 2026-09-19

## Goal

A bot that mines a connected vein of one rock/ore type, like Minecraft vein miner: the player starts the first tile, the bot finishes every newly opened neighbour of the same type inside the safe zone, then stops.

## Scope

New Resources bot `veinminer`, icon next to Master Miner. One run = one vein.

Out of scope: quality filter / auto-drop, mining outside supports and green dots, keeping the cursor armed for a second vein, changing Master Miner or Minesweeper Miner behaviour.

## Player flow

1. Start the bot. Mining cursor turns on.
2. Player clicks one rock tile and mines it themselves.
3. Bot remembers that exact tileset (`gfx/tiles/rocks/...`) and the seed tile.
4. After the seed becomes floor, bot mines the rest of the visible vein in the safe zone, nearest tile first.
5. When nothing new of that type opens in the safe zone, the bot stops.

## Vein queue

Worklist, not a one-shot flood fill of the whole map.

- After each finished tile (player seed, then every bot tile), wait until the map tileset for nearby cells is loaded.
- Look at the **currently visible** tileset names. A tile joins the queue only if it is:
  - the same exact resource name as the seed,
  - still mineable rock,
  - 8-adjacent (Chebyshev 1, diagonals included) to an already finished vein tile,
  - in the safe zone,
  - not already queued or mined.
- Digging a wall typically reveals about three face tiles. Diagonals stay hidden until a connecting tile opens; they are **not** enqueued while their tileset is not yet that ore.
- Always mine the queued tile closest to the player (world distance to tile centre).
- Repeat until the queue is empty.

The player’s seed is accepted even if it is not safe. The bot never mines an unsafe tile.

## Safe zone

A tile is safe if the player already sees it as safe:

- the support overlay lights that tile (`NMiningSupport` mask), or
- a green safe dot sits on it (`NMiningSafeOverlay` / remembered greens).

Tiles with neither mark are skipped. They may join later if a green dot or support appears.

## Mining loop

Reuse the existing miner pattern (`MinesweeperMiner.mineTile` / `TunnelingBot`): path to tile, restore stamina, `NUtils.mine` + `sel`, wait until the tileset changes, chip nearby bumlings, reset cursor so movement is not swallowed.

Stop the run on: fight, loose rock in range, support hp ≤ 0.25 near the tile, failed stamina restore, user stop. Empty queue is success.

## UI

- `BotRegistry`: id `veinminer`, type RESOURCES, immediately after `masterminer`.
- Icon: copy `resources/src/nurgling/bots/icons/masterminer/` to `veinminer/`. Tooltip layers must be unique: `@bot.veinminer.title` and `@bot.veinminer.desc` (not Master Miner strings).
- L10n in `messages.properties` and `messages_ru.properties`.

## Architecture

```text
player click (mine cursor)
        |
        v
seed tile + exact tileset name
        |
        v
VeinWorklist  <-- after each mined tile, scan visible 8-neighbours
        |
        v
nearest safe same-type tile --> existing mine-one-tile loop
```

- `VeinMiner` implements `Action`: cursor, seed wait, loop, stop. No window.
- `VeinWorklist`: pure tile math (mined set, queue, offer visible neighbours, take nearest). Unit-tested without the client.
- Map and safe-zone reads stay in the Action. Do not recompute minesweeper; read the support mask and green dots already drawn.
- Seed capture: while the mine cursor is active, take the next mining selection tile, read its tileset immediately, then wait until that tile is no longer that resource. A single-tile click is the expected input.

## Empty and error states

- No player / no map: error, stop.
- Cursor cancelled before a seed: stop, no mining by the bot.
- Seed tile never finishes: wait until the bot is stopped.
- Seed type has no visible safe neighbours: success, stop.
- Newly opened tile of another rock type: ignore.
- Unloaded tile: skip until loaded; do not crash.

## Testing

- `VeinWorklistTest`: after mining a wall face, only currently visible same-type neighbours enqueue; a hidden diagonal enqueues only after the connecting tile is mined; nearest tile is chosen; unsafe tiles are rejected; other rock types are rejected.
- Registry/icon test like `WormFarmBotRegistryTest`: sits next to `masterminer`, own icon path, tooltip keys `@bot.veinminer.title` / `@bot.veinminer.desc`, not Master Miner text.
- L10n keys present in EN and RU.

No overlay widget test.

## Player note

After implementation, bilingual `changes/` note: new mining macro next to Master Miner; click one tile, the bot finishes the connected vein inside supports or green safe tiles.
