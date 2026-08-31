package haven;

final class LazyRebuildPolicy {
    private enum State {
        NEW,
        ACTIVE,
        DISPOSED
    }

    private State state = State.NEW;

    boolean onGet() {
        if(state != State.NEW)
            return(false);
        state = State.ACTIVE;
        return(true);
    }

    boolean onInvalidate() {
        return(state == State.ACTIVE);
    }

    void onDispose() {
        state = State.DISPOSED;
    }
}
