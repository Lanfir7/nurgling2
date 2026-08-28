package nurgling.widgets.options;

import haven.CheckBox;
import haven.Coord;
import haven.Label;
import haven.TextEntry;
import haven.UI;
import haven.Widget;
import nurgling.NConfig;
import nurgling.NUtils;
import nurgling.i18n.L10n;
import nurgling.widgets.nsettings.Panel;

public class CookbookSettings extends Panel {
    private final TextEntry endpoint;
    private final CheckBox shareRecipes;

    public CookbookSettings() {
        int margin = UI.scale(10);
        Widget title = add(new Label(L10n.get("cookbook.upload.settings_title")), new Coord(margin, margin));
        Label endpointLabel = add(new Label(L10n.get("cookbook.upload.endpoint")),
                title.pos("bl").adds(0, UI.scale(8)));
        endpoint = add(new TextEntry(UI.scale(400), ""), endpointLabel.pos("ur").adds(UI.scale(10), 0));
        shareRecipes = add(new CheckBox(L10n.get("cookbook.upload.share")),
                endpointLabel.pos("bl").adds(0, UI.scale(8)));
        add(new Label(L10n.get("cookbook.upload.hint")), shareRecipes.pos("bl").adds(0, UI.scale(8)));

        load();
        pack();
    }

    @Override
    public void load() {
        Object savedEndpoint = NConfig.get(NConfig.Key.cookbookEndpoint);
        endpoint.settext(savedEndpoint == null ? "" : savedEndpoint.toString());
        shareRecipes.a = Boolean.TRUE.equals(NConfig.get(NConfig.Key.shareCookbookRecipes));
    }

    @Override
    public void save() {
        NConfig.set(NConfig.Key.cookbookEndpoint, endpoint.text().trim());
        NConfig.set(NConfig.Key.shareCookbookRecipes, shareRecipes.a);
        NConfig.needUpdate();
        if (NUtils.getUI() != null && NUtils.getUI().core != null
                && NUtils.getUI().core.cookbookUploader != null)
            NUtils.getUI().core.cookbookSettingsChanged();
    }
}
