package nurgling.widgets;

import haven.*;
import haven.Window;
import nurgling.NConfig;
import nurgling.NUtils;
import nurgling.conf.NDefaultLayout;
import nurgling.conf.NDragProp;
import nurgling.i18n.L10n;
import nurgling.tools.NParser;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;

/**
 * Export and import the HUD layout as a single copy-pasteable token.
 *
 * The point is social rather than technical: a guide or a streamer can publish
 * their layout as one line, and a new player gets a sane HUD without arranging
 * twenty panels by hand. The text lives in a normal TextEntry so the client's
 * existing copy/paste plumbing does the clipboard work.
 */
public class NLayoutShare extends Window {
    private static final int VERSION = 1;
    private static final int width = UI.scale(380);

    private final TextEntry entry;
    private final Label status;

    public NLayoutShare() {
        super(Coord.z, L10n.get("layout.share.title"));
        Widget prev = add(new Label(L10n.get("layout.share.intro")), Coord.z);
        entry = add(new TextEntry(width, ""), prev.pos("bl").adds(0, UI.scale(6)));
        prev = add(new Button(UI.scale(120), L10n.get("layout.share.export")) {
            @Override
            public void click() {
                super.click();
                doExport();
            }
        }, entry.pos("bl").adds(0, UI.scale(6)));
        add(new Button(UI.scale(120), L10n.get("layout.share.import")) {
            @Override
            public void click() {
                super.click();
                doImport();
            }
        }, prev.pos("ur").adds(UI.scale(8), 0));
        status = add(new Label(""), prev.pos("bl").adds(0, UI.scale(6)));
        status.sz = new Coord(width, status.sz.y);
        pack();
    }

    private void doExport() {
        JSONArray widgets = new JSONArray();
        Object saved = NConfig.get(NConfig.Key.dragprop);
        if (saved instanceof ArrayList) {
            for (Object o : (ArrayList<?>) saved) {
                if (o instanceof NDragProp)
                    widgets.put(((NDragProp) o).toJson());
            }
        }
        JSONObject root = new JSONObject();
        root.put("v", VERSION);
        root.put("preset", NDefaultLayout.Preset.current().name());
        root.put("w", widgets);
        entry.settext(Base64.getUrlEncoder().withoutPadding()
                      .encodeToString(root.toString().getBytes(StandardCharsets.UTF_8)));
        say(L10n.get("layout.share.exported", widgets.length()));
    }

    private void doImport() {
        String raw = entry.text().trim();
        if (raw.isEmpty()) {
            say(L10n.get("layout.share.empty"));
            return;
        }
        ArrayList<NDragProp> parsed;
        String preset;
        try {
            JSONObject root = new JSONObject(new String(Base64.getUrlDecoder().decode(raw),
                                                       StandardCharsets.UTF_8));
            if (root.optInt("v", 0) > VERSION) {
                say(L10n.get("layout.share.newer"));
                return;
            }
            preset = root.optString("preset", NDefaultLayout.Preset.CLASSIC.name());
            JSONArray widgets = root.getJSONArray("w");
            parsed = new ArrayList<>();
            for (int i = 0; i < widgets.length(); i++) {
                JSONObject o = widgets.getJSONObject(i);
                NDragProp prop = new NDragProp(NParser.str2coord(o.getString("coord")),
                                               o.optBoolean("locked", false),
                                               o.optBoolean("vis", true),
                                               o.getString("name"));
                prop.flip = o.optBoolean("flip", false);
                parsed.add(prop);
            }
        } catch (Exception e) {
            /* Anything malformed lands here: bad base64, truncated paste, wrong
             * string entirely. The layout is untouched in that case. */
            say(L10n.get("layout.share.invalid"));
            return;
        }
        try {
            NDefaultLayout.Preset.valueOf(preset);
            NConfig.set(NConfig.Key.layoutPreset, preset);
        } catch (IllegalArgumentException e) {
            /* Unknown preset name: keep the current one, the explicit
             * placements below are what actually matters. */
        }
        NConfig.set(NConfig.Key.dragprop, parsed);
        if (NUtils.getGameUI() != null)
            NDraggableWidget.reloadLayout(NUtils.getGameUI());
        say(L10n.get("layout.share.imported", parsed.size()));
    }

    private void say(String text) {
        status.settext(text);
    }

    @Override
    public void wdgmsg(String msg, Object... args) {
        if (msg.equals("close"))
            destroy();
        else
            super.wdgmsg(msg, args);
    }
}
