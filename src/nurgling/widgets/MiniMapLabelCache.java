package nurgling.widgets;

import haven.Disposable;
import haven.Text;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** UI-thread cache: labels own their textures and release them on eviction/close. */
final class MiniMapLabelCache implements Disposable {
    private final Map<Key, Text> labels;

    MiniMapLabelCache(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        labels = new LinkedHashMap<Key, Text>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Key, Text> eldest) {
                if (size() <= capacity) return false;
                eldest.getValue().dispose();
                return true;
            }
        };
    }

    Text get(Text.Furnace furnace, String text) {
        return get(new Key(furnace, text, null));
    }

    Text get(Text.Foundry furnace, String text, Color color) {
        return get(new Key(furnace, text, color));
    }

    private Text get(Key key) {
        Text label = labels.get(key);
        if (label == null) {
            label = key.color == null ? key.furnace.render(key.text)
                    : ((Text.Foundry) key.furnace).render(key.text, key.color);
            labels.put(key, label);
        }
        return label;
    }

    @Override
    public void dispose() {
        for (Text label : labels.values()) label.dispose();
        labels.clear();
    }

    private static final class Key {
        final Text.Furnace furnace;
        final String text;
        final Color color;

        Key(Text.Furnace furnace, String text, Color color) {
            this.furnace = furnace;
            this.text = text;
            this.color = color;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return furnace == key.furnace && text.equals(key.text) && Objects.equals(color, key.color);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * System.identityHashCode(furnace) + text.hashCode()) + Objects.hashCode(color);
        }
    }
}
