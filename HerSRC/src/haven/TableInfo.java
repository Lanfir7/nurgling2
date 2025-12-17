package haven;

public class TableInfo extends Widget {

    public static CheckBox preventTablewareFromBreakingCheckBox = new CheckBox("Prevent Tableware from Breaking"){
        {a = Utils.getprefb("preventTablewareFromBreaking", true);}
        public void set(boolean val) {
            OptWnd.preventTablewareFromBreakingCheckBox.set(val);
            a = val;
        }
    };

    public TableInfo(int x, int y) {
        this.sz = new Coord(x, y);
        add(preventTablewareFromBreakingCheckBox, 10, 0);
        preventTablewareFromBreakingCheckBox.tooltip = OptWnd.preventTablewareFromBreakingCheckBox.tooltip;
    }

}
