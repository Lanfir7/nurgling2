package nurgling;

import haven.*;
import haven.render.*;

/**
 * Атрибут для применения светло-блекло-зеленого оттенка к буксируемым мертвым животным (Orc, Whale)
 */
public class TowedAnimalHighlight extends GAttrib implements Gob.SetupMod {
    // Светло-блекло-зеленый цвет с небольшой прозрачностью
    // RGB: (120, 180, 120) - светло-блекло-зеленый, Alpha: 200 - достаточно заметный
    private static final MixColor TOWED_COLOR = new MixColor(120, 180, 120, 200);
    
    public TowedAnimalHighlight(Gob gob) {
        super(gob);
    }
    
    @Override
    public Pipe.Op gobstate() {
        return TOWED_COLOR;
    }
}
