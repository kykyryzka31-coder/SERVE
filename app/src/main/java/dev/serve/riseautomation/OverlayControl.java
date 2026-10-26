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
            boolean stopped = AppController.snapshot().state == AutomationSession.State.STOPPED;
            button.setText(stopped ? "▶" : "■");
            button.setContentDescription(stopped ? "Начать сессию" : "Остановить сессию");
        } catch (RuntimeException denied) { close(); }
    }
    private void attach() {
        button = new Button(service) {
            @Override public boolean performClick() { return super.performClick(); }
        }; button.setAlpha(0.8f); button.setPadding(0,0,0,0);
        int size = Math.round(56 * service.getResources().getDisplayMetrics().density);
        layout = new WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        layout.gravity = Gravity.TOP | Gravity.START; layout.x = 0; layout.y = size * 2;
        button.setOnClickListener(view -> {
            String action = AppController.snapshot().state == AutomationSession.State.STOPPED ? AutomationService.START : AutomationService.STOP;
            try { service.startService(new Intent(service, AutomationService.class).setAction(action)); }
            catch (RuntimeException denied) { close(); }
        });
        button.setOnTouchListener(new View.OnTouchListener() {
            float x, y; int initialX, initialY; boolean dragged;
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    x = event.getRawX(); y = event.getRawY(); initialX = layout.x; initialY = layout.y; dragged = false; return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    float dx = event.getRawX()-x, dy = event.getRawY()-y;
                    if (Math.hypot(dx, dy) > ViewConfiguration.get(service).getScaledTouchSlop()) dragged = true;
                    if (dragged) {
                        layout.x = Math.max(0, Math.min(service.getResources().getDisplayMetrics().widthPixels-layout.width, initialX+(int)dx));
                        layout.y = Math.max(0, Math.min(service.getResources().getDisplayMetrics().heightPixels-layout.height, initialY+(int)dy));
                        try { windows.updateViewLayout(button, layout); } catch (RuntimeException denied) { close(); }
                    }
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP) { if (!dragged) view.performClick(); return true; }
                return event.getActionMasked() == MotionEvent.ACTION_CANCEL;
            }
        });
        windows.addView(button, layout);
    }
    @Override public void close() {
        if (button != null) {
            try { windows.removeView(button); } catch (RuntimeException alreadyRemoved) { /* Permission revoked or window gone. */ }
            button = null;
        }
    }
}
