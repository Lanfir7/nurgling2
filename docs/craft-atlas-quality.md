# Craft Atlas quality reference

Formula audit: 2026-09-20. These are estimates based on the documented game
mechanics, not quality values returned by the server for a proposed craft.
Use current-world pages, not the `Legacy:` formulas.

The automatic preview uses the selected material batches and the quality values
entered for the required tools, workstation and cauldron water. An absent saved
quality defaults to 10. A saved workstation quality is not proof that the player
is currently using that particular workstation; use the actual station, hammer
and water values when comparing against an in-game craft. Manual preview quality
is the desired final quality and does not run the automatic calculation.

Let `I` be the recipe's ingredient quality, `S` workstation quality, `W` water
quality, `A` anvil quality and `H` smithy's hammer quality.

| Workstation | Quality before the character softcap | Reference |
| --- | --- | --- |
| Metal cauldron | `(6I + S + W) / 8` | [Metal Cauldron](https://ringofbrodgar.com/wiki/Metal_Cauldron) |
| Clay cauldron | `(6I + S + max(1, W / 2)) / 8` | [Clay Cauldron](https://ringofbrodgar.com/wiki/Clay_Cauldron) |
| Anvil and smithy's hammer | `(9I + 4A + 3H) / 16` | [Anvil](https://ringofbrodgar.com/wiki/Anvil) |
| Loom | `(3I + S) / 4` | [Loom](https://ringofbrodgar.com/wiki/Loom) |
| Spinning wheel | `(3I + S) / 4` | [Spinning Wheel](https://ringofbrodgar.com/wiki/Spinning_Wheel) |
| Churn | `(3I + S) / 4` | [Churn](https://ringofbrodgar.com/wiki/Churn) |
| Meatgrinder | `(3I + S) / 4` | [Meatgrinder](https://ringofbrodgar.com/wiki/Meatgrinder) |
| Potter's wheel | `(3I + S) / 4` when using the wheel | [Potter's Wheel](https://ringofbrodgar.com/wiki/Potter%27s_Wheel) |
| Extraction press | `(3I + S) / 4` | [Extraction Press](https://ringofbrodgar.com/wiki/Extraction_Press) |

For the character softcap, take the geometric mean `C` of the recipe's relevant
character attributes. If `C` is below the workstation-adjusted quality `Q`, the
result is `(Q + C) / 2`; otherwise it remains `Q`.
See [Quality](https://ringofbrodgar.com/wiki/Quality#Craft).

Independent numeric checks (before display rounding):

| Inputs | Before softcap | With character softcap 80 |
| --- | --- | --- |
| Ingredients 100, anvil 200, hammer 50 | 115.625 | 97.8125 |
| Ingredients 100, metal cauldron 200, water 40 | 105 | 92.5 |
| Ingredients 100, clay cauldron 200, water 40 | 102.5 | 91.25 |

The character limit is applied after the workstation calculation, not just to
the ingredients. Keep full precision until formatting the displayed result.

Ingredient quantities are not quality weights. For example,
[Hardened Leather](https://ringofbrodgar.com/wiki/Hardened_Leather) consumes
three leather and two wax, but its ingredient quality is
`(average leather quality + average wax quality) / 2`. Other recipes can have
their own weights. Do not infer those weights from stack sizes or liquid volumes.

Water consumed from a cauldron and water explicitly listed as a recipe ingredient
are distinct inputs; do not remove a listed ingredient merely because the recipe
also uses a cauldron. Fuel quality is not part of the cauldron formula.

Anvil crafting must not be applied to metal casting merely because its output is
metal. Likewise, kiln firing and oven baking are separate processing stages with
their own fuel and workstation formulas. A requirement without an implemented
formula is not evidence that its quality has no effect. Querns are a documented
exception: they [have no quality](https://ringofbrodgar.com/wiki/Quern#Quality).
