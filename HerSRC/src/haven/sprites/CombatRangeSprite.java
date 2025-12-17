package haven.sprites;

import haven.Gob;

import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class CombatRangeSprite extends ColoredCircleSprite {
    public CombatRangeSprite(final Gob g, final Float range, Color col) {
        super(g, col, range-0.7f, range, 0.8F);
    }
}