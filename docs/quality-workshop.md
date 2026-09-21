# Quality workshop

Open **Craft Atlas → Quality workshop** (Russian: **Калькулятор качества**).
This is a planning window, not a crafting action. Starting inputs are quality 10;
no sample spreadsheet values are treated as the player's inventory.

The catalogue supports Soap Clay, Bone Clay, Potter's Clay, Coade Clay,
Brick, Bone Ash, Ashes from Bone Ash, Lye, and a Potter's Wheel built with Ball Clay.
It also includes Kiln, Ore Smelter, Stack Furnace, smelted metal, Stone Axe,
and ordinary stone/ore mined from a wall. The result picker groups these chains.
Recipes can be added to the watched results. Promoting a manual ingredient to a
calculated result replaces its input with its dependencies. Shared leaf inputs
appear only once. Removing a result freezes its first-craft quality for remaining
consumers; it does not remove other watched results.

Hide moves a card to a collapsible section at the bottom of its column, without
changing dependencies, entered qualities, or calculations. Inputs and results
have separate hidden sections. Expand a section to edit its cards, or return a
card to the main list. Hidden cards and section expansion are saved per profile.
The compact arrow/quality button beside each input applies the suggested value;
hover over it to see whether it comes from storage, Atlas, or the character.

Potter's Clay can be repeated up to eight times. Craft one retains its configured
brick source. Later crafts fire the preceding clay with the shared kiln/fuel and
use that brick in the next clay recipe. Other ingredients remain fixed. Other
recipes consume first-craft values, so there is no recursive/self-updating graph.

Storage suggestions use the best known matching item quality, not an assurance
that enough items exist at that quality. Workstation suggestions come from Atlas
observations; character suggestions come from the current attributes. Applying a
suggestion is explicit. Simulated values never overwrite Atlas observations.
The storage scan runs in a worker thread, with completion applied on the UI thread.

The board, sources, inputs, number of repeats, and comparison baseline are stored
in profile-local `quality_workshop.nurgling.json`. The same character restrictions
are applied at every appropriate recipe stage; calculations retain full precision.
Invalid input leaves the previous valid model value intact and is highlighted.

## Formula sources

- https://ringofbrodgar.com/wiki/Soap_Clay
- https://ringofbrodgar.com/wiki/Bone_Clay
- https://ringofbrodgar.com/wiki/Potter%27s_Clay
- https://ringofbrodgar.com/wiki/Coade_Clay
- https://ringofbrodgar.com/wiki/Brick
- https://ringofbrodgar.com/wiki/Bone_Ash
- https://ringofbrodgar.com/wiki/Lye
- https://ringofbrodgar.com/wiki/Potter%27s_Wheel

Recipe weights are per material type, not inventory count. Lye uses its own
formula, including full water quality for either cauldron type. Soap Clay halves
the water contribution when a clay cauldron is selected. Building a potter's wheel
does not use a character softcap.

## Mining and furnaces

Mining deliberately uses the same forward formula as Mining Master, shared in
`MiningQuality` rather than the simplified wiki softcap. For a stone axe the
coefficient is 0.8. First cap the wall by Masonry. If that wall quality is below
the tool quality, return the wall quality; otherwise use
`tool + 0.5 * ((wall - 10) - (tool - 10) / 0.8)`.
This preserves Mining Master's boundary behavior, including equality. Wall
quality can be entered from Mining Master; a Masonry-capped estimate can be a
lower bound. Hardness, mining speed, Quarryartz and special finds are separate
mechanics and are not estimated here.

Stone Axe uses the mean of stone and branch, softcapped by `sqrt(INT * Survival)`.
The initial stone is kept separate from the mined result. Up to eight explicit
steps craft the next axe with the preceding mined stone and the same branch and
stats. The first tool may be manually entered or calculated. Stone and ore walls
are independent inputs; other recipes use first-step values.

Ore Smelter construction averages brick, stone, and hard metal qualities.
Stack Furnace averages clay, stone, board, block, and leather qualities.
These estimates follow the material-type construction averaging rule, not the
counts of items in the building recipe. Smelting uses
`(2 * ore + furnace + average fuel) / 4`; the ordinary smelter takes coal,
while Stack Furnace also takes wood and is restricted to nonferrous metals.
The calculation estimates quality on success, not yield/probability. Furnace
qualities remain manual until explicitly promoted to calculated construction.
Ore can similarly be entered or sourced from the mining result.

Kiln quality equals the average clay quality used for construction, with no
skill cap. Its dedicated clay input avoids a live kiln/clay/ash cycle. Copying a
clay result captures its current quality once; repeating that action models the
next kiln generation. The copied input is shared by every use of the built kiln.
Inspected Ore Smelter, Stack Furnace and Kiln qualities are registered even when
the crafting menu has no recipes requiring those processing stations.

Additional sources:
- https://ringofbrodgar.com/wiki/Stone_Axe
- https://ringofbrodgar.com/wiki/Ore_Smelter
- https://ringofbrodgar.com/wiki/Stack_Furnace
- https://ringofbrodgar.com/wiki/Kiln
- https://ringofbrodgar.com/wiki/Quality (construction averaging)
- Mining Master: `MasterMiner.invDropQ`, `calcWallQ`, and wall-quality Masonry cap display.

Arbitrary Atlas recipes still require their own verified formulas.

## Cast anvil and material quantities

Anvil casting consumes 10 casting material and 5 hard metal bars per anvil.
The mold is destroyed on cracking. Its quality estimate is
`(3 * average hard-metal quality + average casting-material quality) / 4`.
Choose a specific clay chain, sand, or manually enter the average of a mixed
casting material. A lit Ore Smelter or Stack Furnace is required nearby, but its
quality does not enter this formula. The selected casting furnace is separate
from the smelting recipe's furnace. Sources:
- https://ringofbrodgar.com/wiki/Anvil
- https://ringofbrodgar.com/wiki/Casting_Material

Each watched result has a desired finished amount, initially zero. A positive
amount is output to keep, in addition to any amount consumed by other targets.
Zero leaves a result available as an intermediate or for quality comparison.
Hidden results still contribute; switching a result to manual stops its target
and expansion, while preserving its saved amount for later use.

The input column shows the combined shopping list and reusable equipment.
The result cards show total production, including intermediate consumption and
batch rounding. Counts are separate from quality weights: the five metal bars
in an anvil do not change the quality formula's metal coefficient of three.
Powders (ashes and lye) use kg; water and salt water use litres; other results
use whole pieces. Quality suggestions do not imply available quantities, so
storage stock is not deducted. Desired amounts and casting choices persist
with the profile. Invalid edits retain the last valid value.

Potter's Clay output count is not confirmed by the reference page. Its card
therefore asks for the actual output per craft shown in the player's recipe.
Zero (the default) explicitly leaves clay as a ready-made requirement. A positive
output enables ingredient planning, including the configured finite repeats;
it does not change the quality formula. Ashes yield 0.2 kg per bone ash, lye
converts ashes at 10:1 by mass, and Coade Clay yields six pieces per craft.

Recipe quantities are expanded only for watched recipes. Unwatched inputs are
listed ready-made; watched equipment is built once and shared across its uses.
Repeated clay and mining steps must be planned as distinct generations rather
than an unbounded cycle. Fuel/cauldron consumption not covered by a verified
batch model is excluded. These limitations are described in the material and
equipment heading tooltips, without a block above the material list.
Smelting yield is random, so a metal target remains a ready-made bar requirement rather than a
promise of a fixed amount of ore.

Requested Stone Axes refer to the initial quality shown on their card. One of
those axes also serves the first mining step. Later steps each require one
separate axe of that generation's quality and one stone from the previous step,
regardless of the final stone target. With two requested initial axes and three
mining generations, the total is four axes: two initial and two later-generation
axes. Tool wear, fuel and extraction time are not inferred from this count.
