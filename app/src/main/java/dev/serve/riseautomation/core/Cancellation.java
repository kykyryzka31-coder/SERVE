package dev.serve.riseautomation.core;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Cancellation {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    public void cancel() { cancelled.set(true); }
    public boolean isCancelled() { return cancelled.get(); }
    public void check() {
        if (isCancelled() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Session cancelled");
        }
    }
}
