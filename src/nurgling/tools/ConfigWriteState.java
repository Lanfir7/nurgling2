package nurgling.tools;

/** Tracks which local snapshot a config save started from without losing changes made during I/O. */
public final class ConfigWriteState {
    private String baseline = "{}";
    private long revision;
    private boolean dirty;

    public void initialize(String baseline) {
        this.baseline = baseline == null ? "{}" : baseline;
        this.revision = 0;
        this.dirty = false;
    }

    public void markDirty() {
        revision++;
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public Save begin() {
        return new Save(baseline, revision);
    }

    public void complete(Save save, String localSnapshot) {
        baseline = localSnapshot;
        if (revision == save.revision) {
            dirty = false;
        }
    }

    String baseline() {
        return baseline;
    }

    public static final class Save {
        public final String baseline;
        private final long revision;

        private Save(String baseline, long revision) {
            this.baseline = baseline;
            this.revision = revision;
        }
    }
}
