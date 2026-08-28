package nurgling.tasks;

import nurgling.NUtils;
import nurgling.tasks.NTask;

public class ISRemoved extends NTask {
    int id;
    public ISRemoved(int id) {
        this(id, true);
    }

    public ISRemoved(int id, boolean criticalOnTimeout) {
        super();
        this.id = id;
        this.infinite = false;
        this.criticalOnTimeout = criticalOnTimeout;
    }

    @Override
    public boolean check() {
        if (NUtils.getUI() == null)
            return false;
        return NUtils.getUI().getwidget(id) == null;
    }
}
