package haven.sprites;

import haven.Gob;
import haven.sprites.ColoredCircleSprite;

import java.awt.*;

public class PartyCircleSprite extends ColoredCircleSprite {
    public Color partyMemberColor;

    public PartyCircleSprite(final Gob g, final Color col) {
        super(g, col, 4.0f, 5.25f, 0.6f);
        this.partyMemberColor = col;
    }
}