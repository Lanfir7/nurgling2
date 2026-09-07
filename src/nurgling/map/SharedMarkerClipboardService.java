package nurgling.map;

import haven.Coord;
import haven.MapFile;
import haven.MiniMap;
import haven.ReadLine;
import haven.UI;
import haven.Utils;
import haven.iosys.tk.Clipboard;
import nurgling.NGameUI;
import nurgling.i18n.L10n;
import nurgling.sessions.SessionManager;
import nurgling.tools.GridLocator;
import nurgling.widgets.SharedMarkerPopup;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class SharedMarkerClipboardService {
    private static final double POLL_INTERVAL = 0.75;
    private static final double UNKNOWN_NOTICE_DELAY = 3.0;

    private final NGameUI gui;
    private final SharedMarkerInbox inbox = new SharedMarkerInbox();
    private final List<PendingImport> pendingImports = new ArrayList<>();
    private SharedMarkerPopup popup;
    private double pollAge = POLL_INTERVAL;
    private volatile boolean pollPending;
    private volatile boolean clipboardReady;
    private volatile String clipboardText = "";
    private volatile String suppressedClipboard;
    private boolean disposed;

    public SharedMarkerClipboardService(NGameUI gui) {
        this.gui = gui;
    }

    public void tick(double dt) {
        if (disposed || gui.ui == null ||
                SessionManager.getInstance().getActiveUI() != gui.ui)
            return;

        if (clipboardReady && popup == null) {
            clipboardReady = false;
            String clipboard = clipboardText;
            if (clipboard.equals(suppressedClipboard)) {
                suppressedClipboard = null;
                inbox.ignore(clipboard);
            } else {
                SharedMarkerCode.Marker offered = inbox.offer(clipboard, gui.getGenus());
                if (offered != null)
                    showOffer(offered);
            }
        }

        resolvePending(dt);
        pollAge += dt;
        if (pollAge >= POLL_INTERVAL && !pollPending) {
            pollAge = 0;
            pollClipboard();
        }
    }

    private void pollClipboard() {
        pollPending = true;
        ReadLine.PCLine.cliptext(gui.ui.wnd.clipboard(Clipboard.Std.CLIPBOARD)).callback(value -> {
            clipboardText = value == null ? "" : value.toString();
            clipboardReady = true;
        }, error -> {
        }, () -> pollPending = false);
    }

    private void showOffer(SharedMarkerCode.Marker marker) {
        popup = gui.add(new SharedMarkerPopup(marker.name, () -> {
            popup = null;
            pendingImports.add(new PendingImport(marker));
        }, () -> popup = null), UIPosition.TOP_LEFT);
        popup.raise();
    }

    private void resolvePending(double dt) {
        for (Iterator<PendingImport> it = pendingImports.iterator(); it.hasNext();) {
            PendingImport pending = it.next();
            pending.age += dt;
            GridLocator.resolve(gui, pending.ref);
            MiniMap.Location location = pending.ref.loc();
            if (location != null && gui.mapfile != null) {
                MapFile.PMarker marker = SharedMarkerImporter.add(gui.mapfile.file, location, pending.marker);
                gui.mapfile.show();
                gui.mapfile.raise();
                Utils.setprefb("wndvis-map", true);
                gui.mapfile.focus(marker);
                gui.mapfile.view.center(new MiniMap.SpecLocator(location.seg.id, location.tc));
                gui.msg(L10n.get("marker.clipboard.added", pending.marker.name), new Color(120, 220, 140));
                it.remove();
            } else if (!pending.notified && pending.age >= UNKNOWN_NOTICE_DELAY) {
                pending.notified = true;
                gui.msg(L10n.get("marker.clipboard.unknown"), new Color(240, 190, 90));
            }
        }
    }

    public void dispose() {
        disposed = true;
        if (popup != null) {
            popup.reqdestroy();
            popup = null;
        }
        pendingImports.clear();
    }

    public void ignore(String clipboard) {
        suppressedClipboard = clipboard;
        inbox.ignore(clipboard);
    }

    private static final class PendingImport {
        final SharedMarkerCode.Marker marker;
        final GridLocator.Ref ref;
        double age;
        boolean notified;

        PendingImport(SharedMarkerCode.Marker marker) {
            this.marker = marker;
            this.ref = new GridLocator.Ref(marker.gridId, marker.local);
        }
    }

    private static final class UIPosition {
        static final Coord TOP_LEFT = UI.scale(15, 15);
    }
}
