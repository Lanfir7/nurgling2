package nurgling.widgets.bots;

import haven.*;
import nurgling.actions.bots.BuildRecipes;
import nurgling.i18n.L10n;

import java.util.List;

public class MultiAreaConfirm extends Window implements Checkable {

    public enum State {
        PENDING,
        ADD_ANOTHER,
        BUILD,
        CANCELLED
    }

    private State state = State.PENDING;
    private boolean ready = false;

    public MultiAreaConfirm(int positionsSoFar, int areasSoFar) {
        this(null, positionsSoFar, areasSoFar);
    }

    public MultiAreaConfirm(String buildingName, int positionsSoFar, int areasSoFar) {
        super(new Coord(260, 130), "Add another area?");

        String summary = areasSoFar + " area" + (areasSoFar == 1 ? "" : "s") +
                         " selected, " + positionsSoFar + " building" +
                         (positionsSoFar == 1 ? "" : "s") + " queued.";
        Widget prev = add(new Label(summary), new Coord(UI.scale(10), UI.scale(10)));

        List<BuildRecipes.Line> totals = BuildRecipes.totals(buildingName, positionsSoFar);
        if (!totals.isEmpty()) {
            String buildingLabel = L10n.get("build.name." + BuildRecipes.slug(buildingName));
            prev = add(new Label(L10n.get("build.calc.item", buildingLabel, positionsSoFar)),
                    prev.pos("bl").add(UI.scale(0, 8)));
            prev = add(new Label(L10n.get("build.calc.need")),
                    prev.pos("bl").add(UI.scale(0, 4)));
            for (BuildRecipes.Line line : totals) {
                String mat = L10n.get("build.mat." + line.materialId);
                prev = add(new Label(L10n.get("build.calc.item", mat, line.count)),
                        prev.pos("bl").add(UI.scale(0, 2)));
            }
        }

        prev = add(new Button(UI.scale(220), "Add another area") {
            @Override
            public void click() {
                super.click();
                state = State.ADD_ANOTHER;
                ready = true;
            }
        }, prev.pos("bl").add(UI.scale(0, 15)));

        prev = add(new Button(UI.scale(220), "Start building") {
            @Override
            public void click() {
                super.click();
                state = State.BUILD;
                ready = true;
            }
        }, prev.pos("bl").add(UI.scale(0, 5)));

        pack();
    }

    @Override
    public boolean check() {
        return ready;
    }

    public State getState() {
        return state;
    }

    @Override
    public void wdgmsg(String msg, Object... args) {
        if (msg.equals("close")) {
            state = State.CANCELLED;
            ready = true;
            hide();
        }
        super.wdgmsg(msg, args);
    }
}
