package nurgling.hotkeys;

import haven.Utils;

public interface PreferenceStore {
    String get(String key, String fallback);
    void set(String key, String value);
    default void remove(String key) { set(key, null); }

    PreferenceStore SYSTEM = new PreferenceStore() {
        public String get(String key, String fallback) {
            return Utils.getpref(key, fallback);
        }

        public void set(String key, String value) {
            Utils.setpref(key, value);
        }

        public void remove(String key) {
            Utils.setpref(key, null);
        }
    };
}
