package dev.serve.riseautomation;

import android.content.Intent;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.view.*;
import android.widget.Button;

/** One 56dp control, opt-in only; no configuration panels over the game. */
final class OverlayControl implements AutoCloseable {
    private final AutomationService service;
    private final WindowManager windows;
    private Button button;
    private WindowManager.LayoutParams layout;
    OverlayControl(AutomationService service) { this.service = service; windows = (WindowManager)service.getSystemService(android.content.Context.WINDOW_SERVICE); }
    void refresh() {
        if (!new FeaturePreferences(service).overlay() || !Settings.canDrawOverlays(service)) { close(); return; }
        try {
            if (button == null) attach();
            int maxX = Math.max(0, service.getResources().getDisplayMetrics().widthPixels-layout.width);
            int maxY = Math.max(0, service.getResources().getDisplayMetrics().heightPixels-layout.height);
            int nextX = Math.max(0, Math.min(layout.x, maxX)), nextY = Math.max(0, Math.min(layout.y, maxY));
            if (layout.x != nextX || layout.y != nextY) {
                layout.x = nextX; layout.y = nextY; windows.updateViewLayout(button, layout);
            }
            boolean stopped = AppController.snapshot().state == AutomationSession.State.STOPPED;
            button.setText(stopped ? "▶" : "■");
            button.setContentDescription(stopped ? "Начать сессию" : "Остановить сессию");
        } catch (RuntimeException denied) { close(); }
    }
    private void attach() {
        button = new DragButton(); button.setAlpha(0.8f); button.setPadding(0,0,0,0);
        int size = Math.round(56 * service.getResources().getDisplayMetrics().density);
        layout = new WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        layout.gravity = Gravity.TOP | Gravity.START; layout.x = 0; layout.y = size * 2;
        button.setOnClickListener(view -> {
            String action = AppController.snapshot().state == AutomationSession.State.STOPPED ? AutomationService.START : AutomationService.STOP;
            try { service.startService(new Intent(service, AutomationService.class).setAction(action)); }
            catch (RuntimeException denied) { close(); }
        });
        windows.addView(button, layout);
    }
    private final class DragButton extends Button {
        private float downX, downY;
        private int initialX, initialY;
        private boolean dragged;
        DragButton() { super(service); }
        @Override public boolean performClick() { return super.performClick(); }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = event.getRawX(); downY = event.getRawY(); initialX = layout.x; initialY = layout.y;
                dragged = false; return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float dx = event.getRawX()-downX, dy = event.getRawY()-downY;
                if (Math.hypot(dx, dy) > ViewConfiguration.get(service).getScaledTouchSlop()) dragged = true;
                if (dragged) {
                    layout.x = Math.max(0, Math.min(service.getResources().getDisplayMetrics().widthPixels-layout.width, initialX+(int)dx));
                    layout.y = Math.max(0, Math.min(service.getResources().getDisplayMetrics().heightPixels-layout.height, initialY+(int)dy));
                    try { windows.updateViewLayout(this, layout); } catch (RuntimeException denied) { close(); }
                }
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) { if (!dragged) performClick(); return true; }
            return event.getActionMasked() == MotionEvent.ACTION_CANCEL || super.onTouchEvent(event);
        }
    }
    @Override public void close() {
        if (button != null) {
            try { windows.removeView(button); } catch (RuntimeException alreadyRemoved) { /* Permission revoked or window gone. */ }
            button = null;
        }
    }
}
