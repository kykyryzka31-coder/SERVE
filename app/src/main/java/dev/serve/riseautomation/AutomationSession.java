package dev.serve.riseautomation;

/** Thread-safe session statistics; time comes from an injected monotonic clock. */
public final class AutomationSession {
    public enum State { STOPPED, RUNNING, PAUSED }
    public static final class Snapshot {
        public final State state;
        public final long succeeded, failures, elapsedMillis;
        public final String lastAction, message;
        private Snapshot(State state, long succeeded, long failures, long elapsedMillis,
                         String lastAction, String message) {
            this.state = state; this.succeeded = succeeded; this.failures = failures;
            this.elapsedMillis = elapsedMillis; this.lastAction = lastAction; this.message = message;
        }
    }
    private State state = State.STOPPED;
    private long succeeded, failures, accumulated, startedAt;
    private String lastAction = "Действий ещё не было";
    private String message = "Остановлено";
    public synchronized boolean isRunning() { return state == State.RUNNING; }
    public synchronized void start() { start(0); }
    public synchronized void start(long now) {
        if (state != State.STOPPED) return;
        succeeded = failures = accumulated = 0;
        startedAt = now; state = State.RUNNING;
        lastAction = "Действий ещё не было"; message = "Ожидание проверенных задач";
    }
    public synchronized void pause(long now, String reason) {
        if (state != State.RUNNING) return;
        accumulated += Math.max(0, now - startedAt); state = State.PAUSED; message = reason;
    }
    public synchronized void resume(long now) {
        if (state != State.PAUSED) return;
        startedAt = now; state = State.RUNNING; message = "Ожидание проверенных задач";
    }
    public synchronized void stop() { stop(0); }
    public synchronized void stop(long now) {
        if (state == State.RUNNING) accumulated += Math.max(0, now - startedAt);
        state = State.STOPPED; message = "Остановлено";
    }
    public synchronized void result(int actions, String label) {
        if (actions < 0) throw new IllegalArgumentException("Negative action count");
        succeeded += actions;
        if (actions > 0) lastAction = label;
    }
    public synchronized void failure() { failures++; message = "Задача приостановлена после ошибки"; }
    public synchronized Snapshot snapshot(long now) {
        return new Snapshot(state, succeeded, failures,
                accumulated + (state == State.RUNNING ? Math.max(0, now - startedAt) : 0),
                lastAction, message);
    }
}
