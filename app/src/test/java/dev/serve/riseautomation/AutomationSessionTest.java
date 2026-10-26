package dev.serve.riseautomation;

import org.junit.Test;
import static org.junit.Assert.*;

public final class AutomationSessionTest {
    @Test public void startsStopped() {
        assertFalse(new AutomationSession().isRunning());
    }
    @Test public void explicitStartAndStop() {
        AutomationSession session = new AutomationSession();
        session.start();
        assertTrue(session.isRunning());
        session.stop();
        assertFalse(session.isRunning());
    }
    @Test public void repeatedStopIsSafe() {
        AutomationSession session = new AutomationSession();
        session.stop();
        session.stop();
        assertFalse(session.isRunning());
    }
}
