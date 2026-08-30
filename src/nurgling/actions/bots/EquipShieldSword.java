package nurgling.actions.bots;

import nurgling.NGameUI;
import nurgling.actions.Action;
import nurgling.actions.Equip;
import nurgling.actions.Results;
import nurgling.tools.NAlias;

public class EquipShieldSword implements Action {
    // Substring keys so every shield and every "...man's Sword" variant matches,
    // instead of a hardcoded list that goes stale with each new weapon.
    static final NAlias SHIELDS = new NAlias("Shield");
    static final NAlias SWORDS = new NAlias("Bronze Sword", "man's Sword");

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        new Equip(SHIELDS, SWORDS).run(gui);
        new Equip(SWORDS, SHIELDS).run(gui);
        return Results.SUCCESS();
    }
}
