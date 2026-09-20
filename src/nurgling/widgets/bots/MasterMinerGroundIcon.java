package nurgling.widgets.bots;

import haven.Coord;
import haven.GOut;
import haven.Loading;
import haven.Resource;
import haven.TexI;
import haven.UI;
import haven.Widget;
import nurgling.NStyle;
import nurgling.hotkeys.Hotkeys;
import nurgling.i18n.L10n;
import nurgling.tools.VSpec;
import nurgling.widgets.IconItem;

import java.awt.image.BufferedImage;

class MasterMinerGroundIcon extends Widget {
    final String resPath;
    final String displayName;
    private TexI tex;
    private TexI countTex;
    private TexI tip;
    private int count;
    private final MasterMinerWnd host;

    MasterMinerGroundIcon(MasterMinerWnd host, MasterMinerGroundStacks.Stack stack) {
        super(UI.scale(32, 42));
        this.host = host;
        this.resPath = stack.resPath;
        this.displayName = stack.displayName;
        setCount(stack.count);
        tryLoadIcon();
    }

    void setCount(int n) {
        if (countTex != null && n == count) {
            return;
        }
        count = n;
        if (countTex != null) {
            countTex.dispose();
        }
        countTex = new TexI(NStyle.iiqual.render(String.valueOf(n)).img);
        if (tip != null) {
            tip.dispose();
        }
        tip = new TexI(haven.RichText.render(L10n.get("bot.masterminer.ground_pickup", displayName, n)).img);
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        if (tex == null) {
            tryLoadIcon();
        }
    }

    @Override
    public void draw(GOut g) {
        g.image(IconItem.framet, Coord.z, UI.scale(32, 42));
        if (tex != null) {
            g.image(tex, Coord.z, UI.scale(32, 32));
        }
        if (countTex != null) {
            g.image(countTex, new Coord(UI.scale(16) - countTex.sz().x / 2, UI.scale(28)));
        }
    }

    @Override
    public Object tooltip(Coord c, Widget prev) {
        return tip;
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
        if (ev.b == 1) {
            boolean takeAll = Hotkeys.action(Hotkeys.MASTERMINER_PICKUP_ALL).current()
                    .matchesModifiers(ui.modflags());
            host.pickupGround(resPath, takeAll);
            return true;
        }
        return super.mousedown(ev);
    }

    @Override
    public void dispose() {
        if (tex != null) {
            tex.dispose();
        }
        if (countTex != null) {
            countTex.dispose();
        }
        if (tip != null) {
            tip.dispose();
        }
        super.dispose();
    }

    private void tryLoadIcon() {
        if (tex != null) {
            return;
        }
        BufferedImage img = loadIcon(resPath, displayName);
        if (img != null) {
            tex = new TexI(img);
        }
    }

    static BufferedImage loadIcon(String gobPath, String displayName) {
        String vspec = VSpec.getIconPath(displayName);
        String[] paths = vspec != null
                ? new String[]{vspec, MasterMinerGroundStacks.iconInvPath(gobPath)}
                : new String[]{MasterMinerGroundStacks.iconInvPath(gobPath)};
        for (String path : paths) {
            try {
                Resource res = Resource.remote().load(path).get();
                if (res != null && res.layer(Resource.imgc) != null) {
                    return res.layer(Resource.imgc).img;
                }
            } catch (Loading ignored) {
                return null;
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
