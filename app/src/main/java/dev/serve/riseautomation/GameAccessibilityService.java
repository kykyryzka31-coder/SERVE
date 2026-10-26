package dev.serve.riseautomation;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import dev.serve.riseautomation.core.Cancellation;
import dev.serve.riseautomation.core.RelativePoint;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** User-enabled service. All Android UI access is short, bounded and on the main thread. */
public final class GameAccessibilityService extends AccessibilityService {
    public static final String GAME_PACKAGE = "com.lilithgame.roc.gp";
    private static volatile GameAccessibilityService instance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<CompletableFuture<?>> pending = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private boolean gestureInFlight;
    public static boolean connected() { return instance != null; }
    public static GameAccessibilityService current() { return instance; }
    @Override protected void onServiceConnected() { instance = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { /* No event logging or game claims. */ }
    @Override public void onInterrupt() { disconnect(); }
    @Override public boolean onUnbind(Intent intent) { disconnect(); return super.onUnbind(intent); }
    @Override public void onDestroy() { disconnect(); main.removeCallbacksAndMessages(null); super.onDestroy(); }
    private void disconnect() {
        if (instance == this) instance = null;
        for (CompletableFuture<?> future : new ArrayList<>(pending)) future.completeExceptionally(new java.util.concurrent.CancellationException("Accessibility disconnected"));
        pending.clear(); gestureInFlight = false; AppController.accessibilityLost();
    }
    private AccessibilityNodeInfo gameRoot(Cancellation token) {
        token.check();
        if (instance != this || !AppController.canAct()) return null;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        if (!GAME_PACKAGE.contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) {
            root.recycle(); return null;
        }
        return root;
    }
    public static final class Element {
        public final String text, viewId;
        public final Rect bounds;
        Element(AccessibilityNodeInfo node) {
            text = node.getText() == null ? "" : node.getText().toString();
            viewId = node.getViewIdResourceName(); bounds = new Rect(); node.getBoundsInScreen(bounds);
        }
    }
    public CompletableFuture<List<Element>> inspect(Cancellation token) {
        CompletableFuture<List<Element>> result = new CompletableFuture<>();
        pending.add(result); result.whenComplete((value, error) -> pending.remove(result));
        main.post(() -> {
            AccessibilityNodeInfo root = null;
            try {
                root = gameRoot(token); List<Element> elements = new ArrayList<>();
                if (root != null) collect(root, elements, 0, new int[]{200});
                token.check(); result.complete(Collections.unmodifiableList(elements));
            } catch (RuntimeException error) { result.completeExceptionally(error); }
            finally { if (root != null) root.recycle(); }
        });
        return result;
    }
    private void collect(AccessibilityNodeInfo node, List<Element> result, int depth, int[] budget) {
        if (depth > 15 || budget[0]-- <= 0) return;
        if (node.isVisibleToUser()) result.add(new Element(node));
        for (int i = 0; i < node.getChildCount() && budget[0] > 0; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) { try { collect(child, result, depth + 1, budget); } finally { child.recycle(); } }
        }
    }
    /** Semantic target re-resolved against the live game window; ambiguous targets are refused. */
    public CompletableFuture<Boolean> tapText(String exactText, Cancellation token) {
        return gesture(token, root -> {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(exactText);
            Rect target = null;
            try {
                for (AccessibilityNodeInfo node : nodes) {
                    if (node.isVisibleToUser() && exactText.contentEquals(node.getText() == null ? "" : node.getText())) {
                        if (target != null) return null;
                        target = new Rect(); node.getBoundsInScreen(target);
                    }
                }
                Rect window = new Rect(); root.getBoundsInScreen(window);
                if (target == null || target.isEmpty() || !window.contains(target)) return null;
                Path path = new Path(); path.moveTo(target.exactCenterX(), target.exactCenterY()); return path;
            } finally { for (AccessibilityNodeInfo node : nodes) node.recycle(); }
        }, 60);
    }
    public CompletableFuture<Boolean> tap(float x, float y, Cancellation token) {
        if (!ratio(x) || !ratio(y)) return CompletableFuture.completedFuture(false);
        return gesture(token, root -> {
            Rect bounds = new Rect(); root.getBoundsInScreen(bounds);
            if (bounds.isEmpty()) return null;
            Path path = new Path();
            path.moveTo(RelativePoint.coordinate(x, bounds.left, bounds.width()),
                    RelativePoint.coordinate(y, bounds.top, bounds.height()));
            return path;
        }, 60);
    }
    /** Ratios are relative to the current active game bounds, not fixed device pixels. */
    public CompletableFuture<Boolean> swipe(float x1, float y1, float x2, float y2, Cancellation token) {
        if (!ratio(x1) || !ratio(y1) || !ratio(x2) || !ratio(y2)) return CompletableFuture.completedFuture(false);
        return gesture(token, root -> {
            Rect bounds = new Rect(); root.getBoundsInScreen(bounds);
            if (bounds.isEmpty()) return null;
            Path path = new Path();
            path.moveTo(RelativePoint.coordinate(x1, bounds.left, bounds.width()), RelativePoint.coordinate(y1, bounds.top, bounds.height()));
            path.lineTo(RelativePoint.coordinate(x2, bounds.left, bounds.width()), RelativePoint.coordinate(y2, bounds.top, bounds.height()));
            return path;
        }, 300);
    }
    private boolean ratio(float value) { return Float.isFinite(value) && value >= 0 && value <= 1; }
    private interface PathFactory { Path create(AccessibilityNodeInfo root); }
    private CompletableFuture<Boolean> gesture(Cancellation token, PathFactory factory, long duration) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        pending.add(result); result.whenComplete((value, error) -> pending.remove(result));
        main.post(() -> {
            AccessibilityNodeInfo root = null;
            try {
                root = gameRoot(token);
                if (root == null || gestureInFlight || result.isCancelled()) { result.complete(false); return; }
                Path path = factory.create(root); token.check();
                if (path == null) { result.complete(false); return; }
                pending.add(result); gestureInFlight = true;
                boolean accepted = dispatchGesture(new GestureDescription.Builder()
                        .addStroke(new GestureDescription.StrokeDescription(path, 0, duration)).build(),
                    new GestureResultCallback() {
                        private void finish(boolean completed) {
                            gestureInFlight = false; pending.remove(result);
                            // Dispatch success is not evidence a game task succeeded.
                            result.complete(completed && !token.isCancelled() && AppController.canAct());
                        }
                        @Override public void onCompleted(GestureDescription gesture) { finish(true); }
                        @Override public void onCancelled(GestureDescription gesture) { finish(false); }
                    }, main);
                if (!accepted) { pending.remove(result); gestureInFlight = false; result.complete(false); }
            } catch (RuntimeException error) {
                pending.remove(result); gestureInFlight = false; result.completeExceptionally(error);
            } finally { if (root != null) root.recycle(); }
        });
        return result;
    }
}
