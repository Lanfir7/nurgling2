package nurgling.overlays;

import haven.Coord3f;
import haven.Drawable;
import haven.Gob;
import haven.ItemInfo;
import haven.Loading;
import haven.MessageBuf;
import haven.PUtils;
import haven.ResDrawable;
import haven.Resource;
import haven.TexI;
import haven.UI;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A small, state-driven action/status marker for shearable sheep/goats and smelters.
 *
 * <p>The marker deliberately derives its state from {@link ResDrawable#sdt} every tick:
 * the server normally updates that buffer in place, without replacing the drawable.
 */
public final class NActionStatusOverlay extends NObjectTexLabel {
    static final String STACK_FURNACE = "gfx/terobjs/primsmelter";
    static final String ORE_SMELTER = "gfx/terobjs/smelter";
    static final String SHEARS_ICON = "gfx/invobjs/shears";
    static final String BAR_ICON = "gfx/invobjs/bar-lead";
    static final String COLD_ICON = "gfx/hud/gob/cold";

    private final Gob gob;
    private Status shown = Status.NONE;

    public NActionStatusOverlay(Gob gob) {
        super(gob);
        this.gob = gob;
        // NObjectTexLabel's five-unit position is the standard over-gob billboard height.
        this.pos = new Coord3f(0, 0, 5);
    }

    public static boolean supports(String resourceName) {
        return isFleeceResource(resourceName) || STACK_FURNACE.equals(resourceName) ||
                ORE_SMELTER.equals(resourceName);
    }

    /** Schedule one overlay for a supported gob; recheck after queued drawable work settles. */
    public static void ensureAttached(Gob gob) {
        gob.defer(() -> {
            Drawable drawable = gob.getattr(Drawable.class);
            try {
                if (drawable == null || drawable.getres() == null ||
                        !supports(drawable.getres().name) ||
                        gob.findol(NActionStatusOverlay.class) != null)
                    return;
                gob.addol(new Gob.Overlay(gob, new NActionStatusOverlay(gob)), false);
            } catch (Loading ignored) {
                // A later drawable notification retries attachment after its resource is ready.
            }
        });
    }

    static Status statusFor(String resourceName, int state) {
        if (isFleeceResource(resourceName))
            return Status.SHEARS;
        if (STACK_FURNACE.equals(resourceName)) {
            boolean bars = (state & 0x04) != 0;
            boolean cold = (state & 0x01) != 0 && (state & 0x02) != 0 &&
                    (state & 0x10) == 0;
            if (bars && cold)
                return Status.BAR_AND_COLD;
            if (bars)
                return Status.BAR;
            if (cold)
                return Status.COLD;
        } else if (ORE_SMELTER.equals(resourceName) && (state & 0x38) != 0) {
            return Status.BAR;
        }
        return Status.NONE;
    }

    private static boolean isFleeceResource(String resourceName) {
        return resourceName != null && resourceName.contains("-fleece") &&
                (resourceName.contains("/sheep/") || resourceName.contains("/goat/"));
    }

    private static int drawableState(Drawable drawable) {
        if (!(drawable instanceof ResDrawable))
            return 0;
        MessageBuf data = ((ResDrawable) drawable).sdt;
        if (data == null || data.eom())
            return 0;
        return data.clone().uint8();
    }

    @Override
    public boolean tick(double dt) {
        Drawable drawable = gob.getattr(Drawable.class);
        if (drawable == null)
            return true;
        try {
            Resource resource = drawable.getres();
            if (resource == null)
                return true;
            Status next = statusFor(resource.name, drawableState(drawable));
            if (next == Status.NONE)
                return true;
            if (next != shown) {
                setStatus(next);
                shown = next;
            }
            return false;
        } catch (Loading ignored) {
            // Do not tear down an already-valid marker while its resource is being reloaded.
            return false;
        }
    }

    private void setStatus(Status status) {
        List<BufferedImage> icons = new ArrayList<>(status.iconResources.size());
        for (String iconResource : status.iconResources) {
            BufferedImage icon = loadIcon(iconResource);
            if (icon != null)
                icons.add(icon);
        }
        if (icons.isEmpty()) {
            this.label = null;
            this.img = null;
            return;
        }
        BufferedImage image = icons.size() == 1 ? icons.get(0) :
                ItemInfo.catimgsh(UI.scale(1), icons.toArray(new BufferedImage[0]));
        this.label = new TexI(image);
        this.img = this.label;
    }

    /** Remote game resources hold inventory and HUD icons; local-only lookup can fail at runtime. */
    private static BufferedImage loadIcon(String resourceName) {
        try {
            BufferedImage image = Resource.remote().loadwait(resourceName).layer(Resource.imgc).img;
            return PUtils.convolvedown(image, UI.scale(20, 20), haven.CharWnd.iconfilter);
        } catch (Exception ignored) {
            return null;
        }
    }

    enum Status {
        NONE(),
        SHEARS(SHEARS_ICON),
        BAR(BAR_ICON),
        COLD(COLD_ICON),
        BAR_AND_COLD(BAR_ICON, COLD_ICON);

        final List<String> iconResources;

        Status(String... iconResources) {
            this.iconResources = iconResources.length == 0 ? Collections.emptyList() :
                    Collections.unmodifiableList(Arrays.asList(iconResources));
        }
    }
}
