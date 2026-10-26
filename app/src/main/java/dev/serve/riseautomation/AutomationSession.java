package dev.serve.riseautomation;

/** Explicit lifecycle foundation; no game interaction or background execution. */
public final class AutomationSession {
    private boolean running;

    public boolean isRunning() { return running; }
    public void start() { running = true; }
    public void stop() { running = false; }
}
