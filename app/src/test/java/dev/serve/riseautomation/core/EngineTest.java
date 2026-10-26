package dev.serve.riseautomation.core;

import dev.serve.riseautomation.AutomationSession;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public final class EngineTest {
    private static class Task implements AutomationTask {
        final String id; final Category category; final int priority;
        boolean validated = true; boolean enabled = true;
        Task(String id, Category category, int priority) {
            this.id = id; this.category = category; this.priority = priority;
        }
        public String id() { return id; }
        public Category category() { return category; }
        public int priority() { return priority; }
        public long timeoutMillis() { return 100; }
        public boolean enabled() { return enabled; }
        public boolean validated() { return validated; }
        public int execute(Cancellation token) throws Exception { token.check(); return 0; }
    }
    @Test public void categoryRoundRobinAndPriorityWithoutStarvation() {
        Task high = new Task("high", AutomationTask.Category.CITY, 3);
        Task low = new Task("low", AutomationTask.Category.CITY, 1);
        Task world = new Task("world", AutomationTask.Category.WORLD, 1);
        Task alliance = new Task("alliance", AutomationTask.Category.ALLIANCE, 1);
        FairScheduler scheduler = new FairScheduler(Arrays.asList(high, low, world, alliance));
        int highCount = 0, lowCount = 0;
        for (int i = 0; i < 12; i++) {
            AutomationTask selected = scheduler.next(0);
            if (i % 3 == 0) {
                assertEquals(AutomationTask.Category.CITY, selected.category());
                if (selected == high) highCount++; else lowCount++;
            } else assertSame(i % 3 == 1 ? world : alliance, selected);
        }
        assertEquals(3, highCount); assertEquals(1, lowCount);
    }
    @Test public void placeholdersAndDisabledTasksNeverSelected() {
        Task placeholder = new Task("p", AutomationTask.Category.WORLD, 3);
        placeholder.validated = false;
        Task disabled = new Task("d", AutomationTask.Category.CITY, 1); disabled.enabled = false;
        assertNull(new FairScheduler(Arrays.asList(placeholder, disabled)).next(100));
    }
    @Test public void retriesBackOffCapAndResetAfterSuccess() {
        Task task = new Task("a", AutomationTask.Category.CITY, 1);
        FairScheduler scheduler = new FairScheduler(Collections.singletonList(task));
        long now = 0;
        for (int i = 0; i < 9; i++) {
            scheduler.failure(task, now);
            long delay = Math.min(60000, 1000L << Math.min(i, 6));
            assertNull(scheduler.next(now + delay - 1));
            now += delay; assertSame(task, scheduler.next(now));
        }
        scheduler.success(task, now); scheduler.failure(task, now);
        assertNull(scheduler.next(now + 999)); assertSame(task, scheduler.next(now + 1000));
    }
    @Test public void cancellationIsVisibleAcrossThreads() throws Exception {
        Cancellation token = new Cancellation();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> result = executor.submit(() -> {
                while (!token.isCancelled()) Thread.yield();
                try { token.check(); return false; } catch (CancellationException expected) { return true; }
            });
            token.cancel(); assertTrue(result.get(2, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }
    @Test public void sessionTimeExcludesPauseAndCountsOnlyRealActions() {
        AutomationSession session = new AutomationSession();
        session.start(100); session.start(200); session.result(0, "placeholder");
        assertEquals("Действий ещё не было", session.snapshot(300).lastAction);
        session.pause(400, "Пауза"); session.resume(1000); session.result(2, "Проверено");
        session.stop(1200);
        AutomationSession.Snapshot snapshot = session.snapshot(9999);
        assertEquals(500, snapshot.elapsedMillis); assertEquals(2, snapshot.succeeded);
        assertEquals(AutomationSession.State.STOPPED, snapshot.state);
    }
    @Test public void timeoutInterruptsWorkerAndOtherTaskSurvives() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), interrupted = new CountDownLatch(1), next = new CountDownLatch(1);
        Task slow = new Task("slow", AutomationTask.Category.CITY, 1) {
            public int execute(Cancellation token) throws Exception {
                entered.countDown();
                try { Thread.sleep(10000); } catch (InterruptedException e) { interrupted.countDown(); throw e; }
                return 10;
            }
        };
        Task good = new Task("good", AutomationTask.Category.WORLD, 1) {
            public int execute(Cancellation token) { next.countDown(); return 0; }
        };
        AutomationSession session = new AutomationSession();
        try (AutomationEngine engine = new AutomationEngine(session, Arrays.asList(slow, good), () -> System.nanoTime()/1000000)) {
            engine.start(); assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertTrue(interrupted.await(2, TimeUnit.SECONDS)); assertTrue(next.await(2, TimeUnit.SECONDS));
            assertEquals(1, session.snapshot(0).failures); assertEquals(0, session.snapshot(0).succeeded);
        }
    }
    @Test public void pauseCancelsAndResumeRestartsWithoutStaleSuccess() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), cancelled = new CountDownLatch(1);
        Task task = new Task("cancel", AutomationTask.Category.CITY, 1) {
            public long timeoutMillis() { return 10000; }
            public int execute(Cancellation token) throws Exception {
                entered.countDown();
                try { Thread.sleep(10000); } finally { cancelled.countDown(); }
                return 20;
            }
        };
        AutomationSession session = new AutomationSession();
        try (AutomationEngine engine = new AutomationEngine(session, Collections.singletonList(task), () -> System.nanoTime()/1000000)) {
            engine.start(); assertTrue(entered.await(2, TimeUnit.SECONDS)); engine.pause("Пауза");
            assertTrue(cancelled.await(2, TimeUnit.SECONDS));
            assertEquals(AutomationSession.State.PAUSED, session.snapshot(0).state);
            engine.resume(); assertTrue(session.isRunning()); engine.stop();
            assertEquals(0, session.snapshot(0).failures); assertEquals(0, session.snapshot(0).succeeded);
        }
    }
    @Test public void exceptionInOneTaskDoesNotBreakOtherCategory() throws Exception {
        CountDownLatch good = new CountDownLatch(1);
        Task broken = new Task("broken", AutomationTask.Category.CITY, 1) {
            public int execute(Cancellation token) { throw new IllegalStateException("test"); }
        };
        Task healthy = new Task("healthy", AutomationTask.Category.ALLIANCE, 1) {
            public int execute(Cancellation token) { good.countDown(); return 0; }
        };
        AutomationSession session = new AutomationSession();
        try (AutomationEngine engine = new AutomationEngine(session, Arrays.asList(broken, healthy), () -> System.nanoTime()/1000000)) {
            engine.start(); assertTrue(good.await(2, TimeUnit.SECONDS)); assertEquals(1, session.snapshot(0).failures);
        }
    }
    @Test public void stopIsNonblockingAndLateUncooperativeWorkIsQuarantined() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), second = new CountDownLatch(1);
        AtomicReference<Thread> workerThread = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        Task stubborn = new Task("stubborn", AutomationTask.Category.CITY, 1) {
            public long timeoutMillis() { return 10000; }
            public int execute(Cancellation token) {
                workerThread.set(Thread.currentThread());
                if (calls.incrementAndGet() == 1) {
                    entered.countDown();
                    boolean released = false;
                    while (!released) {
                        try { released = release.await(2, TimeUnit.SECONDS); }
                        catch (InterruptedException ignored) { /* Deliberately noncooperative test task. */ }
                    }
                    return 99;
                }
                return 0;
            }
        };
        Task other = new Task("other", AutomationTask.Category.WORLD, 1) {
            public int execute(Cancellation token) { second.countDown(); return 0; }
        };
        AutomationSession session = new AutomationSession();
        try (AutomationEngine engine = new AutomationEngine(session, Arrays.asList(stubborn, other), () -> System.nanoTime()/1000000)) {
            engine.start(); assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertNotSame(Thread.currentThread(), workerThread.get());
            long before = System.nanoTime(); engine.stop();
            assertTrue("Stop blocked on worker", System.nanoTime()-before < TimeUnit.SECONDS.toNanos(1));
            engine.start();
            assertFalse("New work overlapped cancelled work", second.await(250, TimeUnit.MILLISECONDS));
            release.countDown(); assertTrue(second.await(2, TimeUnit.SECONDS));
            assertEquals(0, session.snapshot(0).succeeded); assertEquals(0, session.snapshot(0).failures);
        } finally { release.countDown(); }
    }
    @Test public void closedEngineCannotRestart() {
        AutomationSession session = new AutomationSession();
        AutomationEngine engine = new AutomationEngine(session, Collections.emptyList(), () -> 100);
        engine.start(); engine.close(); engine.start(); engine.resume();
        assertEquals(AutomationSession.State.STOPPED, session.snapshot(200).state);
    }
}
