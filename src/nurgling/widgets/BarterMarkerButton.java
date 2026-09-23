package nurgling.widgets;

import haven.*;
import haven.iosys.tk.Clipboard;
import nurgling.NGameUI;
import nurgling.i18n.L10n;
import nurgling.map.SharedMarkerCode;
import nurgling.map.SharedMarkerImporter;
import java.awt.Color;

/** Local map bookmark at the player's position, using the normal shared-marker format. */
public class BarterMarkerButton extends Widget {
    public BarterMarkerButton() {
        super(UI.scale(18, 15));
        tooltip = L10n.get("barter.marker.tip");
    }

    public void draw(GOut g) {
        g.chcolor(new Color(255, 210, 125));
        g.line(UI.scale(4, 2), UI.scale(4, 13), Math.max(1, UI.scale(1)));
        Coord[] flag = {UI.scale(4, 2), UI.scale(14, 2), UI.scale(11, 5),
                UI.scale(14, 8), UI.scale(4, 8)};
        for(int i = 1; i < flag.length; i++)
            g.line(flag[i - 1], flag[i], Math.max(1, UI.scale(1)));
        g.chcolor();
    }

    public boolean mousedown(MouseDownEvent ev) {
        if(ev.b != 1) return super.mousedown(ev);
        createMarker();
        return true;
    }

    private void createMarker() {
        GameUI gui = getparent(GameUI.class);
        if(gui == null) return;
        try {
            if(gui.map == null || gui.mapfile == null || ui.wnd == null)
                throw new Loading();
            Gob player = gui.map.player();
            if(player == null) throw new Loading();
            Coord tile = player.rc.floor(MCache.tilesz);
            MCache.Grid grid = ui.sess.glob.map.getgrid(tile.div(MCache.cmaps));
            MapFile file = gui.mapfile.file;
            String code = SharedMarkerCode.encode(L10n.get("barter.marker.name"), gui.genus,
                    grid.id, tile.sub(grid.ul), Color.ORANGE);
            if(!file.lock.writeLock().tryLock()) throw new Loading();
            try {
                MapFile.GridInfo info = file.gridinfo.get(grid.id);
                if(info == null) throw new Loading();
                MapFile.Segment segment = file.segments.get(info.seg);
                if(segment == null) throw new Loading();
                MiniMap.Location location = new MiniMap.Location(segment,
                        info.sc.mul(MCache.cmaps).add(tile.sub(grid.ul)));
                SharedMarkerImporter.add(file, location, SharedMarkerCode.decode(code));
            } finally {
                file.lock.writeLock().unlock();
            }
            ui.wnd.clipboard(Clipboard.Std.CLIPBOARD).put(new Clipboard.Contents(
                    new Clipboard.Item<CharSequence>(Clipboard.Format.TEXT, code)));
            if(gui instanceof NGameUI) ((NGameUI)gui).ignoreSharedMarkerClipboard(code);
            gui.msg(L10n.get("barter.marker.created"), new Color(120, 220, 140));
        } catch(Loading e) {
            gui.error(L10n.get("barter.marker.loading"));
        }
    }
}
