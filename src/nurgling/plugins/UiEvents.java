package nurgling.plugins;

import haven.UI;
import haven.Widget;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Generic event seam for plugins observing widget traffic. Listeners receive copies of every
 * argument array, so they cannot change core traffic or another listener's observation.
 * Outgoing messages are delivered asynchronously: widget references may have advanced by the
 * time a listener receives one, so observers should use the id/message snapshot and never
 * mutate the UI from an outgoing callback.
 */
public class UiEvents {
    /** Every method is optional; implement only what is needed. */
    public interface Listener {
        default void onNewWidget(UI ui, int id, String type, Widget wdg, Object[] cargs) {}
        default void onAddWidget(UI ui, int id, Widget wdg, int parent, Widget pwdg, Object[] pargs) {}
        default void onUiMsg(UI ui, int id, Widget wdg, String msg, Object[] args) {}
        default void onDestroyWidget(UI ui, int id, Widget wdg) {}
        /**
         * Runs later on the plugin outgoing-event thread. Widget references may be stale; use
         * the id/message snapshot and do not mutate the UI from this callback.
         */
        default void onOutgoing(UI ui, int id, Widget sender, String msg, Object[] args) {}
    }

    private interface Event {
        void fire(Listener listener, Object[] args);
    }

    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    /* Client-owned synchronous observers. They must not enable plugin outgoing observation. */
    private static final CopyOnWriteArrayList<Listener> systemListeners = new CopyOnWriteArrayList<>();
    private static final int OUTGOING_QUEUE_CAPACITY = 256;
    private static final BlockingQueue<OutgoingEvent> outgoing =
            new ArrayBlockingQueue<>(OUTGOING_QUEUE_CAPACITY);
    private static final AtomicBoolean outgoingDropWarned = new AtomicBoolean(false);

    private static final class OutgoingEvent {
        private final UI ui;
        private final int id;
        private final Widget sender;
        private final String msg;
        private final Object[] args;
        private final Listener[] listeners;

        private OutgoingEvent(UI ui, int id, Widget sender, String msg, Object[] args, Listener[] listeners) {
            this.ui = ui;
            this.id = id;
            this.sender = sender;
            this.msg = msg;
            this.args = args;
            this.listeners = listeners;
        }

        private void fire() {
            UiEvents.fire(listeners, args, (listener, copy) -> listener.onOutgoing(ui, id, sender, msg, copy));
        }
    }

    static {
        Thread dispatcher = new Thread(() -> {
            while (true) {
                try {
                    outgoing.take().fire();
                } catch (InterruptedException ignored) {
                    // A daemon observer thread must continue serving later accepted events.
                }
            }
        }, "Nurgling plugin UI outgoing events");
        dispatcher.setDaemon(true);
        dispatcher.start();
    }

    private UiEvents() {}

    /** Registers a listener once. */
    public static void addListener(Listener listener) {
        if (listener != null)
            listeners.addIfAbsent(listener);
    }

    public static void removeListener(Listener listener) {
        if (listener != null)
            listeners.remove(listener);
    }

    /** Registers a client-owned synchronous listener without affecting plugin observation state. */
    public static void addSystemListener(Listener listener) {
        if(listener != null)
            systemListeners.addIfAbsent(listener);
    }

    public static void removeSystemListener(Listener listener) {
        if(listener != null)
            systemListeners.remove(listener);
    }

    /** Lets core skip event dispatch when nobody listens. */
    public static boolean active() {
        return (!listeners.isEmpty());
    }

    /** Lets core dispatch synchronous widget events to either plugin or client observers. */
    public static boolean observing() {
        return(!listeners.isEmpty() || !systemListeners.isEmpty());
    }

    private static void fire(Object[] args, Event event) {
        Listener[] external = listeners.toArray(new Listener[0]);
        Listener[] internal = systemListeners.toArray(new Listener[0]);
        Listener[] targets = new Listener[external.length + internal.length];
        System.arraycopy(external, 0, targets, 0, external.length);
        System.arraycopy(internal, 0, targets, external.length, internal.length);
        fire(targets, args, event);
    }

    private static void fire(Listener[] targets, Object[] args, Event event) {
        for (Listener listener : targets) {
            try {
                event.fire(listener, (args == null) ? null : args.clone());
            } catch (RuntimeException e) {
                // A broken plugin must never interrupt the client or other listeners.
                System.err.println("[Plugins] UI event listener error: " + e);
            } catch (Error error) {
                rethrowFatal(error);
                System.err.println("[Plugins] UI event listener error: " + error);
            }
        }
    }

    private static void rethrowFatal(Error error) {
        if (error instanceof VirtualMachineError)
            throw (VirtualMachineError) error;
        if (error instanceof ThreadDeath)
            throw (ThreadDeath) error;
    }

    public static void fireNewWidget(UI ui, int id, String type, Widget wdg, Object[] cargs) {
        fire(cargs, (listener, copy) -> listener.onNewWidget(ui, id, type, wdg, copy));
    }

    public static void fireAddWidget(UI ui, int id, Widget wdg, int parent, Widget pwdg, Object[] pargs) {
        fire(pargs, (listener, copy) -> listener.onAddWidget(ui, id, wdg, parent, pwdg, copy));
    }

    public static void fireUiMsg(UI ui, int id, Widget wdg, String msg, Object[] args) {
        fire(args, (listener, copy) -> listener.onUiMsg(ui, id, wdg, msg, copy));
    }

    public static void fireDestroyWidget(UI ui, int id, Widget wdg) {
        fire(null, (listener, ignored) -> listener.onDestroyWidget(ui, id, wdg));
    }

    public static void fireOutgoing(UI ui, int id, Widget sender, String msg, Object[] args) {
        Listener[] targets = listeners.toArray(new Listener[0]);
        if (targets.length == 0)
            return;
        Object[] snapshot = (args == null) ? null : args.clone();
        if (!outgoing.offer(new OutgoingEvent(ui, id, sender, msg, snapshot, targets))
                && outgoingDropWarned.compareAndSet(false, true)) {
            System.err.println("[Plugins] UI outgoing-event queue is full; dropping observer events.");
        }
    }
}
