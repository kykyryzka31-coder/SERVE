package dev.serve.riseautomation.core;

import dev.serve.riseautomation.AutomationSession;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

/** A monitor and one worker: Android's main thread never executes tasks or waits for them. */
public final class AutomationEngine implements AutoCloseable {
    private final AutomationSession session;
    private final FairScheduler scheduler;
    private final LongSupplier clock;
    private final ScheduledExecutorService monitor;
    private final ExecutorService worker;
    private Attempt current;
    private long generation;
    private boolean closed;
    private static final class Attempt {
        final AutomationTask task;
        final Cancellation cancellation = new Cancellation();
        final long generation, deadline;
        volatile boolean exited;
        boolean accounted;
        volatile Thread thread;
        Attempt(AutomationTask task, long generation, long now) {
            this.task = task; this.generation = generation; deadline = now + task.timeoutMillis();
        }
    }
    public AutomationEngine(AutomationSession session, List<AutomationTask> tasks, LongSupplier clock) {
        this.session = session; this.scheduler = new FairScheduler(tasks); this.clock = clock;
        worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "automation-worker"));
        monitor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "automation-monitor"));
        monitor.scheduleWithFixedDelay(this::tickSafely, 0, 100, TimeUnit.MILLISECONDS);
    }
    public synchronized void start() { if (!closed) session.start(clock.getAsLong()); }
    public synchronized void pause(String reason) {
        session.pause(clock.getAsLong(), reason); cancelCurrent();
    }
    public synchronized void resume() { if (!closed) session.resume(clock.getAsLong()); }
    public synchronized void stop() { session.stop(clock.getAsLong()); cancelCurrent(); }
    private void cancelCurrent() {
        generation++;
        if (current != null) {
            current.cancellation.cancel();
            if (current.thread != null) current.thread.interrupt();
        }
    }
    private void tickSafely() {
        try { tick(); }
        catch (RuntimeException failure) { pause("Проверка задач остановлена после ошибки"); }
    }
    private synchronized void tick() {
        if (closed) return;
        long now = clock.getAsLong();
        if (current != null) {
            if (!current.exited && !current.accounted && now >= current.deadline
                    && !current.cancellation.isCancelled()) {
                current.accounted = true;
                session.failure(); scheduler.failure(current.task, now);
                current.cancellation.cancel();
                if (current.thread != null) current.thread.interrupt();
            }
            // Future.cancel alone does not prove the underlying work has actually stopped.
            if (!current.exited) return;
            current = null;
        }
        if (!session.isRunning()) return;
        AutomationTask task = scheduler.next(now);
        if (task == null) return;
        Attempt attempt = new Attempt(task, generation, now);
        current = attempt;
        worker.submit(() -> run(attempt));
    }
    private void run(Attempt attempt) {
        attempt.thread = Thread.currentThread();
        try {
            attempt.cancellation.check();
            int actions = attempt.task.execute(attempt.cancellation);
            attempt.cancellation.check();
            synchronized (this) {
                if (valid(attempt)) {
                    session.result(actions, attempt.task.id());
                    scheduler.success(attempt.task, clock.getAsLong());
                    attempt.accounted = true;
                }
            }
        } catch (CancellationException | InterruptedException cancelled) {
            // User cancellation and disconnect are not task failures.
        } catch (Exception failure) {
            synchronized (this) {
                if (valid(attempt)) {
                    session.failure(); scheduler.failure(attempt.task, clock.getAsLong());
                    attempt.accounted = true;
                }
            }
        } finally { attempt.exited = true; }
    }
    private boolean valid(Attempt attempt) {
        return !closed && !attempt.accounted && !attempt.cancellation.isCancelled()
                && attempt.generation == generation && session.isRunning();
    }
    @Override public synchronized void close() {
        if (closed) return;
        stop(); closed = true; monitor.shutdownNow(); worker.shutdownNow();
    }
}
