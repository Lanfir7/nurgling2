package nurgling.actions.bots;

import haven.Area;
import haven.Button;
import haven.Coord;
import haven.Coord2d;
import haven.Gob;
import haven.Label;
import haven.Loading;
import haven.MCache;
import haven.Pair;
import haven.Resource;
import haven.UI;
import haven.Widget;
import haven.Window;
import nurgling.NGameUI;
import nurgling.NUtils;
import nurgling.actions.Action;
import nurgling.actions.Results;
import nurgling.i18n.L10n;
import nurgling.tasks.NTask;

import java.util.ArrayList;

/**
 * Suggests a flatten height for the current map chunk, or a user-selected area.
 * Target is the highest level that still leaves more soil from digging than needed to fill.
 */
public class IdealLevel implements Action {
    @Override
    public Results run(NGameUI gui) throws InterruptedException {
        Area tiles = currentChunk(gui);
        if (tiles == null) {
            return Results.ERROR(L10n.get("bot.ideallevel.no_player"));
        }
        Result r = measure(gui, tiles, true);
        if (r == null) {
            return Results.ERROR(L10n.get("bot.ideallevel.no_map"));
        }
        Wnd wnd = existing(gui);
        if (wnd == null) {
            wnd = new Wnd(gui);
            gui.add(wnd, new Coord(gui.sz.x / 2 - UI.scale(110), UI.scale(80)));
        }
        wnd.setResult(r);
        wnd.raise();
        wnd.show();
        return Results.SUCCESS();
    }

    static Area currentChunk(NGameUI gui) {
        Gob player = NUtils.player();
        if (player == null || gui.map == null || gui.map.glob == null || gui.map.glob.map == null) {
            return null;
        }
        Coord ul = player.rc.floor(MCache.tilesz).div(MCache.cmaps).mul(MCache.cmaps);
        return Area.sized(ul, MCache.cmaps);
    }

    static Result measure(NGameUI gui, Area tiles, boolean chunk) throws InterruptedException {
        Gob player = NUtils.player();
        if (player == null || gui.map == null || gui.map.glob == null || gui.map.glob.map == null) {
            return null;
        }
        MCache map = gui.map.glob.map;
        int[] heights = sampleHeights(map, tiles);
        if (heights == null || heights.length == 0) {
            return null;
        }
        int current;
        try {
            current = Math.round((float) map.getcz(player.rc));
        } catch (Loading l) {
            return null;
        }
        Coord sz = tiles.sz();
        return compute(heights, current, sz.x, sz.y, chunk);
    }

    static int[] sampleHeights(MCache map, Area tiles) throws InterruptedException {
        Area verts = Area.corni(tiles.ul, tiles.br);
        int need = Math.max(4, verts.area() / 2);
        final int[][] box = new int[1][];
        NUtils.addTask(new NTask() {
            {
                infinite = false;
                maxCounter = 120;
            }

            @Override
            public boolean check() {
                ArrayList<Integer> hs = new ArrayList<>(verts.area());
                try {
                    for (Coord vc : verts) {
                        try {
                            hs.add(Math.round((float) map.getfz(vc)));
                        } catch (Loading ignored) {
                        }
                    }
                } catch (Loading l) {
                    return false;
                }
                if (hs.size() < need) {
                    return false;
                }
                int[] arr = new int[hs.size()];
                for (int i = 0; i < hs.size(); i++) {
                    arr[i] = hs.get(i);
                }
                box[0] = arr;
                return true;
            }
        });
        return box[0];
    }

    static Result compute(int[] heights, int current) {
        return compute(heights, current, 0, 0, false);
    }

    static Result compute(int[] heights, int current, int w, int h, boolean chunk) {
        long sum = 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int z : heights) {
            sum += z;
            if (z < min) min = z;
            if (z > max) max = z;
        }
        int ideal;
        if (min == max) {
            ideal = min;
        } else {
            double mean = sum / (double) heights.length;
            ideal = (int) Math.floor(mean - 1e-9);
            if (ideal < min) ideal = min;
            if (ideal > max) ideal = max;
            while (ideal > min && !digMoreThanFill(heights, ideal)) {
                ideal--;
            }
        }
        int dig = 0;
        int fill = 0;
        for (int z : heights) {
            if (z > ideal) {
                dig += z - ideal;
            } else {
                fill += ideal - z;
            }
        }
        return new Result(ideal, current, dig, fill, w, h, chunk);
    }

    static boolean digMoreThanFill(int[] heights, int target) {
        long leftover = 0;
        for (int z : heights) {
            leftover += (long) z - target;
        }
        return leftover > 0;
    }

    private static Wnd existing(NGameUI gui) {
        Window wnd = gui.getWindow(L10n.get("bot.ideallevel.window"));
        return wnd instanceof Wnd ? (Wnd) wnd : null;
    }

    static final class Result {
        final int ideal;
        final int current;
        final int dig;
        final int fill;
        final int w;
        final int h;
        final boolean chunk;

        Result(int ideal, int current, int dig, int fill, int w, int h, boolean chunk) {
            this.ideal = ideal;
            this.current = current;
            this.dig = dig;
            this.fill = fill;
            this.w = w;
            this.h = h;
            this.chunk = chunk;
        }
    }

    static class Wnd extends Window {
        private final NGameUI gui;
        private final Label idealLbl;
        private final Label currentLbl;
        private final Label digLbl;
        private final Label fillLbl;
        private final Label areaLbl;
        private volatile boolean selecting;

        Wnd(NGameUI gui) {
            super(Coord.z, L10n.get("bot.ideallevel.window"), true);
            this.gui = gui;
            Label prev = add(idealLbl = new Label("..."), Coord.z);
            prev = add(currentLbl = new Label("..."), prev.pos("bl").adds(0, 4));
            prev = add(digLbl = new Label("..."), prev.pos("bl").adds(0, 8));
            prev = add(fillLbl = new Label("..."), prev.pos("bl").adds(0, 4));
            prev = add(areaLbl = new Label("..."), prev.pos("bl").adds(0, 8));
            add(new Button(UI.scale(200), L10n.get("bot.ideallevel.select")) {
                @Override
                public void click() {
                    startSelect();
                }
            }, prev.pos("bl").adds(0, 10));
            pack();
        }

        @Override
        public void wdgmsg(Widget sender, String msg, Object... args) {
            if ("close".equals(msg)) {
                reqdestroy();
                return;
            }
            super.wdgmsg(sender, msg, args);
        }

        void setResult(Result r) {
            idealLbl.settext(L10n.get("bot.ideallevel.ideal", r.ideal));
            currentLbl.settext(L10n.get("bot.ideallevel.current", r.current));
            digLbl.settext(L10n.get("bot.ideallevel.dig", r.dig));
            fillLbl.settext(L10n.get("bot.ideallevel.fill", r.fill));
            String areaKey = r.chunk ? "bot.ideallevel.area_chunk" : "bot.ideallevel.area_zone";
            areaLbl.settext(L10n.get(areaKey, r.w, r.h));
            pack();
        }

        private void startSelect() {
            if (selecting) {
                return;
            }
            selecting = true;
            areaLbl.settext(L10n.get("bot.ideallevel.selecting"));
            new Thread(() -> {
                try {
                    SelectArea sa = new SelectArea(Resource.loadsimg("baubles/inputArea"));
                    Results rr = sa.run(gui);
                    if (!rr.IsSuccess()) {
                        return;
                    }
                    Pair<Coord2d, Coord2d> rc = sa.getRCArea();
                    if (rc == null) {
                        return;
                    }
                    Coord ul = rc.a.div(MCache.tilesz).floor();
                    Coord br = rc.b.div(MCache.tilesz).floor();
                    Area tiles = new Area(ul, br, true);
                    Result r = measure(gui, tiles, false);
                    if (r != null) {
                        setResult(r);
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    selecting = false;
                }
            }, "IdealLevel-Select").start();
        }
    }
}
