package haven;

/**
 * Axis-aligned rectangle outline used by {@link GOut#rect2}.
 * {@link GOut#line} first rejects segments whose bounding box misses the
 * clip; {@link Line2d#clip} alone does not drop horizontals/verticals that
 * lie fully outside and are parallel to a pair of clip edges.
 */
final class RectOutlineClip {
    private RectOutlineClip() {
    }

    static Coord[] corners(Coord ul, Coord br) {
	return(new Coord[] {
	    ul,
	    Coord.of(br.x, ul.y),
	    br,
	    Coord.of(ul.x, br.y)
	});
    }

    static boolean aabbMiss(Coord a, Coord b, Coord ul, Coord br) {
	return((Math.max(a.x, b.x) < ul.x) || (Math.min(a.x, b.x) >= br.x)
	       || (Math.max(a.y, b.y) < ul.y) || (Math.min(a.y, b.y) >= br.y));
    }

    static boolean anyEdgeVisible(Coord rectUl, Coord rectBr, Coord clipUl, Coord clipBr) {
	Coord[] p = corners(rectUl, rectBr);
	Coord clipMax = clipBr.sub(1, 1);
	for(int i = 0; i < 4; i++) {
	    Coord a = p[i], b = p[(i + 1) % 4];
	    if(aabbMiss(a, b, clipUl, clipBr))
		continue;
	    if(a.isect2(clipUl, clipBr) && b.isect2(clipUl, clipBr))
		return(true);
	    if(Line2d.twixt(Coord2d.of(a), Coord2d.of(b)).clip(Coord2d.of(clipUl), Coord2d.of(clipMax)) != null)
		return(true);
	}
	return(false);
    }
}
