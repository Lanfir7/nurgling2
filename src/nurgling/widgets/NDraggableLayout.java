package nurgling.widgets;

import haven.Coord;
import haven.DTarget;
import haven.DropTarget;
import haven.Widget;

/**
 * Geometry for a draggable HUD frame. Lock and visibility controls sit on the
 * panel itself - the same arrangement as the minimap - so the frame is the
 * content's own rectangle, not a strip reserved beside it.
 */
public final class NDraggableLayout {
    private NDraggableLayout() {
    }

    /** Space between the content and the frame. Empty: the controls overlay the panel. */
    public static Coord chrome() {
        return new Coord(0, 0);
    }

    /** Where the content starts inside the frame. Flush against the corner. */
    public static Coord contentOrigin() {
        return new Coord(0, 0);
    }

    public static Coord frameSize(Coord content) {
        Coord safe = content == null ? Coord.z : content;
        return safe.add(chrome());
    }

    /** Frame size at a given scale of the panel's natural size. */
    public static Coord scaledSize(Coord natural, double scale) {
        Coord safe = natural == null ? Coord.z : natural;
        double s = scale;
        return new Coord(Math.max(1, (int)Math.round(safe.x * s)),
                         Math.max(1, (int)Math.round(safe.y * s)));
    }

    /**
     * Map a point in the scaled frame into the content's natural coordinates.
     * Drawing uses a uniform scale about the frame origin, so a click has to be
     * divided by that scale before it is handed to a child that still thinks it
     * is its original size.
     */
    public static Coord toContent(Coord inFrame, double scale, Coord contentOrigin) {
        Coord c = inFrame == null ? Coord.z : inFrame;
        Coord off = contentOrigin == null ? Coord.z : contentOrigin;
        double s = scale == 0 ? 1.0 : scale;
        return new Coord((int)Math.round(c.x / s), (int)Math.round(c.y / s)).sub(off);
    }

    /**
     * Whether a pointer in frame coordinates lands on the scaled content.
     * The frame's own rectangle is the hit rect; anything inside it maps onto
     * the content, so visual scale and input stay aligned.
     */
    public static boolean hitsScaledContent(Coord inFrame, Coord frameSize, double scale,
                                            Coord contentOrigin, Coord contentSize) {
        if(inFrame == null || frameSize == null || !inFrame.isect(Coord.z, frameSize))
            return false;
        Coord content = contentSize == null ? Coord.z : contentSize;
        return toContent(inFrame, scale, contentOrigin).isect(Coord.z, content);
    }

    /**
     * Item take/drop does not go through {@code mousedown}. Those events walk
     * children by their unscaled rectangles unless rewritten into content space.
     */
    public static boolean remapPointerToContent(Widget.Event ev) {
        return ev instanceof DropTarget.DropEvent
            || ev instanceof DTarget.ItemEvent;
    }
}
