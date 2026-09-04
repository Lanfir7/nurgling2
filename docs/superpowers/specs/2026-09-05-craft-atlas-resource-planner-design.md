# Craft Atlas resource planner

## Goal

Craft Atlas should turn every observed recipe into an actionable material plan. A player can choose the concrete member of a recipe ingredient group, see matching inventory and warehouse stock by quality, estimate the crafted quality, and collect the required quantity without opening the normal craft window first.

## Scope

This change adds material selection, quality planning, and resource collection to the existing Craft Atlas details view. It reuses recipes captured from server Make windows, `VSpec` groups, the warehouse database, the player inventory, and the existing storage-fetch actions. Reference-only recipes remain encyclopedic: their known ingredients may be displayed, but collection is enabled only when the recipe has usable observed ingredient resources.

## Ingredient candidates

Each recipe input is a separate slot. A slot may contain a concrete item or a group such as Fabric. Group membership is expanded through `VSpec`; observed concrete inputs continue to work without a group.

For each slot the planner builds one mixed candidate list:

- live items in the player's main inventory, marked with `★`;
- matching warehouse database rows, including their storage name;
- candidates sorted by quality descending within each source representation;
- duplicate inventory and database records are not merged, because they describe different physical stock.

The displayed row format is `★ Linen Cloth · Q120 · 3 pcs. · Inventory` or `Linen Cloth · Q105 · 12 pcs. · Chest name`.

When a matching inventory item exists, the default selection is the best-quality inventory candidate. Otherwise it is the highest-quality warehouse candidate. A group slot also offers `All matching`, which permits allocation across every concrete member of the `VSpec` group.

## Selection and allocation

A selected candidate sets both the concrete material and the preferred starting batch. For example, selecting Linen Cloth Q100 restricts the whole slot to Linen Cloth. Allocation consumes the selected batch first and then other Linen Cloth batches in descending quality until the required count is reached. It never substitutes another kind of fabric in this mode.

With `All matching`, allocation may consume any member of the slot's `VSpec` group and orders all available batches by quality descending. Inventory stock is counted before planning warehouse transfers, so an item already carried is never fetched again.

The required quantity for a slot is:

`recipe slot quantity × requested craft count`.

Optional ignored inputs contribute neither demand nor quality. The plan is computed for every required slot before collection starts. If any slot is short, no transfer starts; the Atlas reports the missing material and count.

## Quality calculation

The details header contains `Quality`, its value field, and an `Auto` checkbox. The field moves left far enough to keep the checkbox and label inside the header.

When `Auto` is enabled, the manual field is disabled. Each slot quality is the quantity-weighted arithmetic mean of the batches allocated to that slot. The projected craft quality is the arithmetic mean of the required slot averages, matching the slot-averaging rule already used by the normal craft window. Changing a material, preferred batch, or craft count rebuilds the plan and refreshes projected food and gilding bonuses.

When `Auto` is disabled, the player can enter quality manually and the existing Atlas bonus projection uses that value.

If a candidate has no usable quality or a required slot cannot be fully allocated, automatic quality is shown as unavailable rather than as zero.

## User interface

Every ingredient row keeps its icon, name, and required count. A dropdown beside it shows the current candidate and opens the mixed inventory/warehouse list. Empty slots show that no matching stock was found.

The footer is laid out as:

`[craft count] [Collect resources] [Open craft]`.

The favorite button remains at the left. `Collect resources` is enabled only for an open or previously observed recipe with complete required inputs and a feasible plan. `Open craft` keeps its current behavior.

The controls follow the existing responsive Atlas layout. In the narrow details page the footer controls remain visible and the ingredient dropdown stays within the details width.

## Collection execution

Collection runs as one bot job to avoid overlapping transfers. It first rechecks the live inventory, subtracts already present matching quantities, then fetches the planned warehouse batches in order. Existing storage navigation and item-transfer code remains responsible for opening containers and moving items.

After each fetched batch, the job validates the received count. If a storage record is stale, the job refreshes that storage record and stops with a clear shortage message rather than silently switching to a different material. A new button press recomputes the plan from current inventory and database state.

## Components

- `CraftAtlasMaterialPlanner`: pure candidate filtering, default selection, allocation, shortages, and quality calculation.
- `CraftAtlasMaterialSource`: adapts player inventory and `CraftIngredientStock` warehouse results into planner candidates.
- `CraftAtlasIngredientSelector`: dropdown UI for one input slot.
- `CraftAtlasResourceCollector`: executes a validated plan through existing fetch and inventory-transfer operations.
- `CraftAtlasDetails` and `CraftAtlasWindow`: host the selectors, Auto control, craft count, and collection button.

The pure planner owns no Haven widgets or database access, allowing deterministic unit tests.

## State and refresh

Selections are retained by recipe resource and slot index while the Atlas window remains open. They are discarded when their concrete material is no longer valid for the refreshed recipe. Candidate stock refreshes when the selected recipe changes, after collection completes, and when the warehouse observation revision changes. Inventory quantities are read live whenever a plan is rebuilt.

## Validation

Unit tests cover:

- `VSpec` group expansion and concrete inputs;
- inventory-first default selection, then warehouse highest quality;
- selected-material restriction with lower-quality fallback;
- cross-material allocation only in `All matching` mode;
- craft-count multiplication and all-or-nothing shortage detection;
- quantity-weighted slot quality and mean-of-slots craft quality;
- exclusion of ignored optional inputs;
- stable candidate sorting and inventory markers.

Widget tests cover header/footer bounds, disabled manual quality in Auto mode, selector refresh, and button enablement. Existing Craft Atlas, storage search, and full client test suites must pass before the build artifact is updated.
