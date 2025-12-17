package haven.sprites;

import haven.Gob;
import haven.OptWnd;

import java.awt.*;

public class AuraCircleSprite extends ColoredCircleSprite {

    public AuraCircleSprite(final Gob g, final Color col) {
        super(g, col, 0f, 10f, 0.45f);
    }
    public AuraCircleSprite(final Gob g, final Color col, float size) {
        super(g, col, 0f, size, 0.45f);
    }
}