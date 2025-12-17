package haven;

public class InventoryProxy extends Widget implements DTarget {
    private final Inventory target;

    public InventoryProxy(Inventory target) {
	super(target.sz);
	this.target = target;
    }

    @Override
    public void draw(GOut g) {
	target.draw(g);
    }

    @Override
    public void draw(GOut g, boolean strict) {
	target.draw(g, strict);
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
	return ev.dispatch(target);
    }

    @Override
    public boolean mouseup(MouseUpEvent ev) {
	return ev.dispatch(target);
    }

    @Override
    public boolean mousewheel(MouseWheelEvent ev) {
	return ev.dispatch(target);
    }

    @Override
    public void mousemove(MouseMoveEvent ev) {
	ev.dispatch(target);
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
	WItem item = itemat(c);
	return (item != null) ? item.tooltip(c.sub(item.c), (prev == this) ? item : prev) : null;
    }

    private WItem itemat(Coord c) {
	for (WItem item : target.children(WItem.class)) {
	    if (c.isect(xlate(item.c, true), item.sz)) {
		return item;
	    }
	}
	return null;
    }

    @Override
    public boolean drop(Coord cc, Coord ul) {
	return target.drop(cc, ul);
    }

    @Override
    public boolean iteminteract(Coord cc, Coord ul) {
	return target.iteminteract(cc, ul);
    }
}
