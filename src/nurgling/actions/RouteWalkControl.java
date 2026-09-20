package nurgling.actions;

/** Interruptible, single-run state shared by the route window and its bot thread. */
public final class RouteWalkControl {
    private boolean paused;
    private boolean cancelled;
    private boolean finished;
    private volatile String statusKey = "routewalker.status_starting";
    private volatile int waypoint;
    private volatile int total;
    private volatile Thread thread;

    public synchronized boolean isPaused() { return paused; }
    public synchronized boolean isCancelled() { return cancelled; }
    /** True while launch is pending or the attached bot thread has not completed. */
    public synchronized boolean running() { return !finished; }
    public synchronized boolean abortRequested() { return paused || cancelled; }

    public synchronized void setPaused(boolean value) {
        if (finished || cancelled) return;
        paused = value;
        notifyAll();
    }

    public synchronized void cancel() {
        if (finished) return;
        cancelled = true;
        paused = false;
        statusKey = "routewalker.status_stopping";
        notifyAll();
        Thread runner = thread;
        if (runner != null) runner.interrupt();
    }

    /** Blocks cooperatively while paused and propagates Stop as an interrupt. */
    public synchronized void awaitResume() throws InterruptedException {
        while (paused && !cancelled) wait();
        if (cancelled) throw new InterruptedException("route walk cancelled");
    }

    public void status(String key) { statusKey = key; }
    public String statusKey() { return statusKey; }
    public void progress(int current, int all) { waypoint = current; total = all; }
    public int waypoint() { return waypoint; }
    public int total() { return total; }

    public synchronized void attachThread(Thread value) {
        thread = value;
        if (cancelled && value != null) value.interrupt();
    }

    public Thread thread() { return thread; }

    public synchronized void finish(String terminalStatusKey) {
        statusKey = terminalStatusKey;
        paused = false;
        finished = true;
        notifyAll();
    }
}
