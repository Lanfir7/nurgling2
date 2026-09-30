package haven.render;

import haven.Indir;
import haven.Loading;
import haven.Waitable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A bounded, single-worker queue for preparation that must not stall drawing. */
public final class PrepareTaskQueue implements AutoCloseable {
    private final Object lock = new Object();
    private final String name;
    private final int capacity;
    private final PriorityQueue<Task<?>> tasks = new PriorityQueue<>(
            Comparator.<Task<?>>comparingInt(task -> task.priority)
                    .thenComparingLong(task -> task.order));
    private long nextOrder;
    private int outstanding;
    private boolean closed;
    private Thread worker;

    public PrepareTaskQueue(String name, int capacity) {
        this.name = Objects.requireNonNull(name, "name");
        if(capacity < 1)
            throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    private static final class Task<T> {
        final Supplier<T> supplier;
        final Job<T> job;
        final int priority;
        final long order;

        Task(Supplier<T> supplier, Job<T> job, int priority, long order) {
            this.supplier = supplier;
            this.job = job;
            this.priority = priority;
            this.order = order;
        }
    }

    public static final class Job<T> implements Indir<T>, Waitable {
        private T value;
        private Throwable failure;
        private boolean done;
        private final List<Waiter> waiters = new ArrayList<>();

        /** Distinguishes a completed resource-Loading failure from pending work. */
        public synchronized boolean done() {return done;}

        private static final class Pending extends Loading {
            private final Job<?> job;

            Pending(Job<?> job) {
                this.job = job;
            }

            @Override
            public void waitfor(Runnable callback, Consumer<Waitable.Waiting> reg) {
                job.waitfor(callback, reg);
            }
        }

        private static final class Waiter implements Waitable.Waiting {
            private final Job<?> owner;
            private final Runnable callback;
            private boolean armed;
            private boolean signalled;
            private boolean fired;
            private boolean cancelled;

            Waiter(Job<?> owner, Runnable callback) {
                this.owner = owner;
                this.callback = callback;
            }

            private void arm() {
                Runnable run = null;
                synchronized(this) {
                    armed = true;
                    if(signalled && !cancelled && !fired) {
                        fired = true;
                        run = callback;
                    }
                }
                if(run != null)
                    run.run();
            }

            private void signal() {
                Runnable run = null;
                synchronized(this) {
                    signalled = true;
                    if(armed && !cancelled && !fired) {
                        fired = true;
                        run = callback;
                    }
                }
                if(run != null)
                    run.run();
            }

            @Override
            public void cancel() {
                synchronized(this) {
                    cancelled = true;
                }
                synchronized(owner) {
                    owner.waiters.remove(this);
                }
            }
        }

        @Override
        public T get() {
            synchronized(this) {
                if(!done)
                    throw new Pending(this);
                if(failure instanceof RuntimeException)
                    throw (RuntimeException)failure;
                if(failure instanceof Error)
                    throw (Error)failure;
                if(failure != null)
                    throw new RuntimeException(failure);
                return value;
            }
        }

        @Override
        public void waitfor(Runnable callback, Consumer<Waitable.Waiting> reg) {
            Objects.requireNonNull(callback, "callback");
            Objects.requireNonNull(reg, "reg");
            Waiter waiter = new Waiter(this, callback);
            boolean alreadyDone;
            synchronized(this) {
                alreadyDone = done;
                if(!alreadyDone)
                    waiters.add(waiter);
            }
            if(alreadyDone) {
                reg.accept(Waitable.Waiting.dummy);
                callback.run();
                return;
            }
            try {
                reg.accept(waiter);
            } catch(RuntimeException | Error exc) {
                waiter.cancel();
                throw exc;
            }
            waiter.arm();
        }

        private List<Waiter> complete(T value, Throwable failure) {
            synchronized(this) {
                this.value = value;
                this.failure = failure;
                this.done = true;
                List<Waiter> ready = new ArrayList<>(waiters);
                waiters.clear();
                return ready;
            }
        }
    }

    /** Returns null immediately if the queue has no free admission slot. */
    public <T> Job<T> submit(Supplier<T> supplier, int priority) {
        Objects.requireNonNull(supplier, "supplier");
        synchronized(lock) {
            if(closed || outstanding >= capacity)
                return null;
            Job<T> job = new Job<>();
            Task<T> task = new Task<>(supplier, job, priority, nextOrder++);
            tasks.add(task);
            outstanding++;
            if(worker == null) {
                Thread thread = new Thread(this::run, name);
                thread.setDaemon(true);
                try {
                    thread.start();
                } catch(RuntimeException | Error exc) {
                    tasks.remove(task);
                    outstanding--;
                    throw exc;
                }
                worker = thread;
            }
            lock.notifyAll();
            return job;
        }
    }

    private void run() {
        while(true) {
            Task<?> task;
            synchronized(lock) {
                while(tasks.isEmpty() && !closed) {
                    try {
                        lock.wait();
                    } catch(InterruptedException ignored) {
                        // An interruption must not abandon admitted preparation.
                    }
                }
                if(tasks.isEmpty())
                    return;
                task = tasks.remove();
            }
            execute(task);
        }
    }

    private <T> void execute(Task<T> task) {
        T value = null;
        Throwable failure = null;
        try {
            value = task.supplier.get();
        } catch(Throwable exc) {
            failure = exc;
        }
        synchronized(lock) {
            outstanding--;
        }
        List<Job.Waiter> callbacks = task.job.complete(value, failure);
        for(Job.Waiter callback : callbacks) {
            try {
                callback.signal();
            } catch(Throwable exc) {
                Thread current = Thread.currentThread();
                try {
                    current.getUncaughtExceptionHandler().uncaughtException(current, exc);
                } catch(Throwable ignored) {
                    // A faulty callback or handler must not strand other jobs.
                }
            }
        }
    }

    /** Rejects new work and waits for all admitted work to finish. */
    @Override
    public void close() {
        Thread join;
        synchronized(lock) {
            closed = true;
            lock.notifyAll();
            join = worker;
        }
        if(join == null || join == Thread.currentThread())
            return;
        boolean interrupted = false;
        while(true) {
            try {
                join.join();
                break;
            } catch(InterruptedException exc) {
                interrupted = true;
            }
        }
        if(interrupted)
            Thread.currentThread().interrupt();
    }
}
