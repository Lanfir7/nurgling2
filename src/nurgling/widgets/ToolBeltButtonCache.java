package nurgling.widgets;

import haven.IButton;
import java.util.Objects;
import java.util.function.Supplier;

/** UI-owned cache with at most one generated button per toolbar slot. */
public final class ToolBeltButtonCache {
    private final Entry[] entries;

    public ToolBeltButtonCache(int slots) {
        entries = new Entry[slots];
    }

    private static final class Entry {
        final Object source, images;
        final String mapping, name, icon;
        final int imageSize, fontSize;
        final IButton button;

        Entry(Object source, String mapping, String name, String icon, Object images,
              int imageSize, int fontSize, IButton button) {
            this.source = source;
            this.mapping = mapping;
            this.name = name;
            this.icon = icon;
            this.images = images;
            this.imageSize = imageSize;
            this.fontSize = fontSize;
            this.button = button;
        }

        boolean matches(Object source, String mapping, String name, String icon, Object images,
                        int imageSize, int fontSize) {
            return this.source == source && this.images == images
                    && Objects.equals(this.mapping, mapping) && Objects.equals(this.name, name)
                    && Objects.equals(this.icon, icon)
                    && this.imageSize == imageSize && this.fontSize == fontSize;
        }
    }

    public IButton get(int slot, Object source, String mapping, String name, String icon,
                       Object images, int imageSize, int fontSize, Supplier<? extends IButton> create) {
        Entry old = entries[slot];
        if (old != null && old.matches(source, mapping, name, icon, images, imageSize, fontSize))
            return old.button;
        // Loading/failure must not discard the previous owned texture before a replacement exists.
        IButton button = create.get();
        entries[slot] = new Entry(source, mapping, name, icon, images, imageSize, fontSize, button);
        if (old != null)
            old.button.dispose();
        return button;
    }

    public void remove(int slot) {
        if (slot < 0 || slot >= entries.length)
            return;
        Entry old = entries[slot];
        entries[slot] = null;
        if (old != null)
            old.button.dispose();
    }

    public void clear() {
        for (int slot = 0; slot < entries.length; slot++)
            remove(slot);
    }
}
