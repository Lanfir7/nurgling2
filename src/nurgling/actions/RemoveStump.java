package nurgling.actions;

import haven.Gob;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.tools.NAlias;

/**
 * Equips a shovel and destroys a tree stump.
 */
public class RemoveStump implements Action {
    private static final NAlias SHOVEL = new NAlias("Shovel");
    private final long gobId;

    public RemoveStump(long gobId) {
        this.gobId = gobId;
    }

    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        Gob stump = NUtils.findGob(gobId);
        if (stump == null) {
            return Results.ERROR("Stump not found");
        }
        if (!new Equip(SHOVEL).run(gui).IsSuccess()) {
            return Results.ERROR("Shovel not found");
        }
        return new Destroy(stump, "gfx/borka/shoveldig").run(gui);
    }
}
