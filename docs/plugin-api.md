# Minimap plugin buttons

`NMiniMapWnd.addToggle(Widget)` adds an icon to one minimap window's dock. Pass an
`ICheckBox` (or a subclass) with a positive size that fits that minimap. Call it
on the window's UI thread after construction. The button keeps its own click,
state, tooltip, and key binding behavior; the dock controls its drawing and
pointer hit area. Adding the same instance again does nothing. Null, other
widget types, buttons owned by another parent or UI, and invalid sizes are
rejected before the panel changes.

Registration belongs to that window. A plugin should call `reqdestroy()` on its
button when unloading. There is no global button registration or automatic
plugin installation. Removal takes effect for drawing and pointer input at once;
the remaining icons are packed on the next minimap resize or button addition.
