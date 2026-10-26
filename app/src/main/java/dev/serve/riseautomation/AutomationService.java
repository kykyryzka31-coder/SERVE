package dev.serve.riseautomation;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import android.provider.Settings;
import dev.serve.riseautomation.core.AutomationEngine;

public final class AutomationService extends Service {
    public static final String START = "start", STOP = "stop", PAUSE = "pause", RESUME = "resume", PANEL = "panel", CLOSE = "close";
    private static final String CHANNEL = "automation_session";
    private static final int NOTIFICATION = 10;
    private final Handler main = new Handler(Looper.getMainLooper());
    private AutomationEngine engine;
    private OverlayControl overlay;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (AppController.SESSION.isRunning() && !GameAccessibilityService.connected()) AppController.accessibilityLost();
            if (overlay != null) overlay.refresh();
            if (AppController.snapshot().state == AutomationSession.State.STOPPED && !panelWanted()) { stopSelf(); return; }
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION, notification());
            main.postDelayed(this, 1000);
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Сессия автоматизации", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Состояние сессии и кнопка остановки");
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        engine = new AutomationEngine(AppController.SESSION, new FeaturePreferences(this).tasks(), SystemClock::elapsedRealtime);
        AppController.engine = engine;
        overlay = new OverlayControl(this);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        // Always foreground immediately, including denial/stop paths after startForegroundService.
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOTIFICATION, notification());
        String action = intent == null ? STOP : intent.getAction();
        if (START.equals(action) || RESUME.equals(action)) {
            if (GameAccessibilityService.connected()) {
                if (RESUME.equals(action)) engine.resume(); else engine.start();
            } else {
                engine.pause("Включите управление через специальные возможности");
                android.widget.Toast.makeText(this, "Откройте приложение и включите доступ в специальных возможностях", android.widget.Toast.LENGTH_LONG).show();
            }
        } else if (PAUSE.equals(action)) engine.pause("На паузе");
        else if (STOP.equals(action) || CLOSE.equals(action)) engine.stop();
        overlay.refresh();
        if (CLOSE.equals(action) || (!AppController.SESSION.isRunning()
                && AppController.snapshot().state == AutomationSession.State.STOPPED && !panelWanted())) {
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
        } else {
            main.removeCallbacks(refresh); main.post(refresh);
        }
        return START_NOT_STICKY;
    }
    private boolean panelWanted() { return new FeaturePreferences(this).overlay() && Settings.canDrawOverlays(this); }
    private PendingIntent command(String action, int request) {
        return PendingIntent.getService(this, request, new Intent(this, AutomationService.class).setAction(action), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
    private Notification notification() {
        AutomationSession.Snapshot snapshot = AppController.snapshot();
        String text = snapshot.state == AutomationSession.State.RUNNING ? "Работает · ожидание проверенных задач"
                : snapshot.state == AutomationSession.State.PAUSED ? "На паузе" : "Остановлено · панель управления";
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Rise Automation")
                .setContentText(text).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true);
        if (snapshot.state != AutomationSession.State.STOPPED) {
            boolean paused = snapshot.state == AutomationSession.State.PAUSED;
            builder.addAction(new Notification.Action.Builder(null, paused ? "Продолжить" : "Пауза", command(paused ? RESUME : PAUSE, 1)).build());
            builder.addAction(new Notification.Action.Builder(null, "Стоп", command(STOP, 2)).build());
        }
        builder.addAction(new Notification.Action.Builder(null, "Закрыть", command(CLOSE, 3)).build());
        return builder.build();
    }
    @Override public void onTaskRemoved(Intent rootIntent) { engine.stop(); stopSelf(); }
    @Override public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (overlay != null) overlay.close();
        if (engine != null) engine.close();
        if (AppController.engine == engine) AppController.engine = null;
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
