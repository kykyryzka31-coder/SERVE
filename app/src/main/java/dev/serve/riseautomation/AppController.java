package dev.serve.riseautomation;

import android.os.SystemClock;
import dev.serve.riseautomation.core.AutomationEngine;

/** In-memory only: killing/reopening the process never resumes automation. */
public final class AppController {
    public static final AutomationSession SESSION = new AutomationSession();
    static volatile AutomationEngine engine;
    private AppController() { }
    public static AutomationSession.Snapshot snapshot() { return SESSION.snapshot(SystemClock.elapsedRealtime()); }
    public static boolean canAct() { return engine != null && SESSION.isRunning() && GameAccessibilityService.connected(); }
    public static void accessibilityLost() {
        AutomationEngine current = engine;
        if (current != null) current.pause("Нет доступа к управлению. Включите его в настройках и нажмите «Продолжить».");
    }
}
