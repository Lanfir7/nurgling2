package nurgling.widgets.bots;

import haven.Button;
import haven.Coord;
import haven.Label;
import haven.TextEntry;
import haven.UI;
import haven.Window;

/**
 * Информативное окно для МастерМайнер.
 * Показывает качества и рассчитанное "качество в стене" (wallQ) + максимум.
 */
public class MasterMinerWnd extends Window {
    private volatile boolean closed = false;

    private final Label masonryLbl;
    private final Label toolLbl;
    private final Label stoneLbl;
    private final Label wallLbl;
    private final Label maxLbl;
    private final Label stoneAxeLbl;
    private final Label tinkerAxeLbl;
    private final Label pickaxeLbl;
    private final TextEntry thresholdEntry;

    private double maxWallQ = Double.NaN;

    public MasterMinerWnd() {
        super(new Coord(UI.scale(240), UI.scale(220)), "МастерМайнер");

        Coord pad = UI.scale(8, 6);
        Coord cur = pad;

        masonryLbl = add(new Label("Masonry: (ожидание)"), cur);
        cur = masonryLbl.pos("bl").add(0, UI.scale(4));

        toolLbl = add(new Label("Инструмент: (не найден)"), cur);
        cur = toolLbl.pos("bl").add(0, UI.scale(4));

        stoneLbl = add(new Label("Камень: (ожидание)"), cur);
        cur = stoneLbl.pos("bl").add(0, UI.scale(4));

        wallLbl = add(new Label("Стена: (ожидание)"), cur);
        cur = wallLbl.pos("bl").add(0, UI.scale(4));

        maxLbl = add(new Label("Макс: (ещё нет)"), cur);
        cur = maxLbl.pos("bl").add(0, UI.scale(6));

        stoneAxeLbl = add(new Label("Кам.топор: -"), cur);
        cur = stoneAxeLbl.pos("bl").add(0, UI.scale(2));
        tinkerAxeLbl = add(new Label("Тинкер: -"), cur);
        cur = tinkerAxeLbl.pos("bl").add(0, UI.scale(2));
        pickaxeLbl = add(new Label("Кирка: -"), cur);
        cur = pickaxeLbl.pos("bl").add(0, UI.scale(6));

        add(new Label("Порог сброса:"), cur);
        cur = cur.add(UI.scale(0, UI.scale(18)));
        thresholdEntry = add(new TextEntry(UI.scale(80), ""), cur);
        cur = thresholdEntry.pos("bl").add(0, UI.scale(6));

        add(new Button(UI.scale(160), "Сбросить максимум") {
            @Override
            public void click() {
                super.click();
                maxWallQ = Double.NaN;
                maxLbl.settext("Макс: (ещё нет)");
            }
        }, cur);

        pack();
    }

    public boolean isClosed() {
        return closed;
    }

    public void setMasonry(int masonry) {
        masonryLbl.settext("Masonry: " + masonry);
    }

    public void setTool(String toolName) {
        if (toolName == null) toolName = "(не найден)";
        toolLbl.settext("Инструмент: " + toolName);
    }

    public void setStone(double f3, String stoneName) {
        String name = (stoneName != null && !stoneName.isEmpty()) ? " " + stoneName : "";
        stoneLbl.settext(String.format("Камень: %.2f%s", f3, name));
    }

    public void setWallQ(double wallQ, String stoneName) {
        String name = (stoneName != null && !stoneName.isEmpty()) ? " " + stoneName : "";
        wallLbl.settext(String.format("Стена: %.2f%s", wallQ, name));
        if (Double.isNaN(maxWallQ) || wallQ > maxWallQ) {
            maxWallQ = wallQ;
            maxLbl.settext(String.format("Макс: %.2f%s", maxWallQ, name));
        }
    }

    public double getDropThreshold() {
        try {
            String txt = thresholdEntry.text().trim();
            if (txt.isEmpty()) return Double.NaN;
            return Double.parseDouble(txt.replace(',', '.'));
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    public void setAltPredictions(Double stoneAxe, Double tinkerAxe, Double pickaxe) {
        stoneAxeLbl.settext("Кам.топор: " + fmt(stoneAxe));
        tinkerAxeLbl.settext("Тинкер: " + fmt(tinkerAxe));
        pickaxeLbl.settext("Кирка: " + fmt(pickaxe));
    }

    private static String fmt(Double v) {
        if (v == null || v.isNaN() || v.isInfinite()) return "-";
        return String.format("%.2f", v);
    }

    @Override
    public void wdgmsg(String msg, Object... args) {
        if ("close".equals(msg)) {
            closed = true;
            hide();
        }
        super.wdgmsg(msg, args);
    }
}

