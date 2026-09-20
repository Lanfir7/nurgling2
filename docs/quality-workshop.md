# Quality workshop

Open **Craft Atlas → Quality workshop** (Russian: **Калькулятор качества**).
This is a planning window, not a crafting action. Starting inputs are quality 10;
no sample spreadsheet values are treated as the player's inventory.

The first version supports Soap Clay, Bone Clay, Potter's Clay, Coade Clay,
Brick, Bone Ash, Ashes from Bone Ash, Lye, and a Potter's Wheel built with Ball Clay.
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

Stone/axe spiralling and arbitrary Atlas recipes are outside this first catalogue;
they require their own verified formulas, not a generic average fallback.
