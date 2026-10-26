package dev.serve.riseautomation;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import dev.serve.riseautomation.core.*;
import java.util.Locale;

/** Native, scrollable screens: one shared bounded column on phones and tablets. */
public final class MainActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout content;
    private FeaturePreferences preferences;
    private String page = "home";
    private TextView status, statistics, permissions;
    private Button start, pause;
    private final Runnable update = new Runnable() {
        @Override public void run() { updateHome(); main.postDelayed(this, 1000); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); preferences = new FeaturePreferences(this);
        if (state != null) page = state.getString("page", "home");
        render();
    }
    @Override protected void onSaveInstanceState(Bundle state) { state.putString("page", page); super.onSaveInstanceState(state); }
    @Override protected void onResume() { super.onResume(); render(); main.post(update); }
    @Override protected void onPause() { main.removeCallbacks(update); super.onPause(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String label, int size) {
        TextView view = new TextView(this); view.setText(label); view.setTextSize(size);
        view.setTextColor(Color.rgb(25, 35, 45)); view.setPadding(0, dp(8), 0, dp(8));
        content.addView(view); return view;
    }
    private Button button(String label, Runnable click) {
        Button view = new Button(this); view.setText(label); view.setAllCaps(false);
        view.setMinHeight(dp(48)); content.addView(view, new LinearLayout.LayoutParams(-1, -2));
        view.setOnClickListener(ignored -> click.run()); return view;
    }
    private void render() {
        status = statistics = permissions = null; start = pause = null;
        LinearLayout shell = new LinearLayout(this); shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.rgb(245, 247, 250));
        shell.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout nav = new LinearLayout(this);
        String[] labels = {"Главная", "Задачи", "Настройки"}; String[] pages = {"home", "tasks", "settings"};
        for (int i = 0; i < pages.length; i++) {
            final String selected = pages[i]; Button tab = new Button(this); tab.setText(labels[i]);
            tab.setAllCaps(false); tab.setTextSize(13); tab.setMinWidth(0); tab.setMinimumWidth(0);
            tab.setSelected(page.equals(selected)); tab.setContentDescription(labels[i] + (page.equals(selected) ? ", выбрано" : ""));
            nav.addView(tab, new LinearLayout.LayoutParams(0, -2, 1));
            tab.setOnClickListener(ignored -> { page = selected; render(); });
        }
        shell.addView(nav);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        FrameLayout center = new FrameLayout(this);
        content = new LinearLayout(this) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int width = Math.min(MeasureSpec.getSize(widthSpec), dp(840));
                super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightSpec);
            }
        };
        content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(20), dp(12), dp(20), dp(28));
        center.addView(content, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        scroll.addView(center); shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(shell); shell.requestApplyInsets();
        if (page.equals("tasks")) tasks(); else if (page.equals("settings")) settings(); else home();
    }
    private void home() {
        text("Rise Automation", 28);
        status = text("", 21); statistics = text("", 18);
        text("Игровые действия ещё не доступны. Выбор задач сохраняется, но выполнение будет разрешено только после проверки каждой функции в игре.", 16);
        start = button("Начать", () -> {
            if (!GameAccessibilityService.connected()) {
                new android.app.AlertDialog.Builder(this).setTitle("Нужен доступ к управлению")
                    .setMessage("Приложение сможет читать интерфейс игры и выполнять жесты. Доступ включается только вами в специальных возможностях Android. Игровые задачи пока отключены.")
                    .setPositiveButton("Открыть настройки", (dialog, which) -> open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                    .setNegativeButton("Отмена", null).show(); return;
            }
            send(AutomationService.START);
        });
        pause = button("Пауза", () -> send(AppController.snapshot().state == AutomationSession.State.PAUSED ? AutomationService.RESUME : AutomationService.PAUSE));
        button("Остановить", () -> send(AutomationService.STOP));
        permissions = text("", 15); updateHome();
    }
    private void updateHome() {
        if (status == null) return;
        AutomationSession.Snapshot snapshot = AppController.snapshot();
        String label = snapshot.state == AutomationSession.State.RUNNING ? "Сессия запущена"
                : snapshot.state == AutomationSession.State.PAUSED ? "На паузе" : "Остановлено";
        status.setText(getString(R.string.session_status, label, snapshot.message));
        long seconds = snapshot.elapsedMillis / 1000;
        statistics.setText(String.format(Locale.forLanguageTag("ru"), "Успешных действий: %d\nОшибок: %d\nВремя работы: %02d:%02d:%02d\nПоследнее действие: %s", snapshot.succeeded, snapshot.failures,
                seconds/3600, seconds/60%60, seconds%60, snapshot.lastAction));
        start.setEnabled(snapshot.state == AutomationSession.State.STOPPED);
        pause.setEnabled(snapshot.state != AutomationSession.State.STOPPED);
        pause.setText(snapshot.state == AutomationSession.State.PAUSED ? "Продолжить" : "Пауза");
        permissions.setText(GameAccessibilityService.connected() ? "Доступ к управлению включён" : "Доступ к управлению выключен. Сессия не выполняет действия.");
    }
    private String category(AutomationTask.Category category) {
        switch (category) { case CITY: return "Город"; case WORLD: return "Мир"; default: return "Альянс"; }
    }
    private void tasks() {
        text("Задачи", 28);
        text("Переключатели сохраняют ваш выбор для будущего запуска. Все функции ожидают проверки и пока отключены в движке.", 16);
        for (AutomationTask.Category category : AutomationTask.Category.values()) {
            text(category(category), 23);
            for (FeatureCatalog.Feature feature : FeatureCatalog.ALL) if (feature.category == category) {
                Switch toggle = new Switch(this); toggle.setText(getString(R.string.feature_pending, feature.title));
                toggle.setTextSize(16); toggle.setPadding(0, dp(12), 0, dp(12)); toggle.setMinHeight(dp(56));
                toggle.setChecked(preferences.selected(feature.id));
                toggle.setOnCheckedChangeListener((view, checked) -> preferences.select(feature.id, checked));
                content.addView(toggle, new LinearLayout.LayoutParams(-1, -2));
                text(feature.description, 15);
            }
        }
    }
    private void settings() {
        text("Настройки", 28); text("Доступ и управление", 23);
        text("Доступ к специальным возможностям включается вручную. При отключении сессия ставится на паузу; возобновление — только по вашей команде.", 16);
        button("Специальные возможности", () -> open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        if (Build.VERSION.SDK_INT >= 33) button("Разрешить уведомления", () -> {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 5);
            else open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        });
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            text("Уведомления выключены: Android может скрывать кнопки управления. Остановить сессию можно в приложении.", 15);
        }
        Switch overlay = new Switch(this); overlay.setText("Маленькая кнопка поверх игры"); overlay.setMinHeight(dp(56));
        overlay.setChecked(preferences.overlay() && Settings.canDrawOverlays(this)); content.addView(overlay);
        overlay.setOnCheckedChangeListener((view, checked) -> {
            if (checked && !Settings.canDrawOverlays(this)) {
                view.setChecked(false);
                new android.app.AlertDialog.Builder(this).setTitle("Кнопка поверх других приложений")
                    .setMessage("Нужна только для плавающей кнопки «Старт/Стоп». После выдачи разрешения включите переключатель снова.")
                    .setPositiveButton("Открыть настройки", (dialog, which) -> open(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))))
                    .setNegativeButton("Отмена", null).show();
            } else {
                preferences.overlay(checked); send(AutomationService.PANEL);
            }
        });
        text("Кнопку можно перетащить к краю экрана. В ней нет игровых настроек. «Закрыть» в уведомлении убирает панель и останавливает сессию.", 15);
        for (AutomationTask.Category category : AutomationTask.Category.values()) {
            text(category(category), 23);
            for (FeatureCatalog.Feature feature : FeatureCatalog.ALL) if (feature.category == category) {
                text(feature.title, 19); text(feature.description + " Функция пока не выполняется.", 15);
                text("Частота в очереди", 15);
                choice(new String[]{"Обычная", "Повышенная", "Высокая"}, preferences.priority(feature.id)-1, index -> preferences.priority(feature.id, index+1));
                text("Максимальное время одной попытки", 15);
                int timeout = preferences.timeout(feature.id);
                choice(new String[]{"15 секунд", "30 секунд", "60 секунд"}, timeout == 15 ? 0 : timeout == 60 ? 2 : 1,
                        index -> preferences.timeout(feature.id, new int[]{15,30,60}[index]));
            }
        }
    }
    private interface Selection { void changed(int index); }
    private void choice(String[] options, int selected, Selection selection) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, Math.min(options.length-1, selected))); spinner.setMinimumHeight(dp(48));
        content.addView(spinner, new LinearLayout.LayoutParams(-1, -2));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { selection.changed(position); }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }
    private void open(Intent intent) {
        try { startActivity(intent); } catch (ActivityNotFoundException unavailable) { Toast.makeText(this, "Этот раздел настроек недоступен на устройстве", Toast.LENGTH_LONG).show(); }
    }
    private void send(String action) {
        if (AutomationService.RESUME.equals(action) && !GameAccessibilityService.connected()) {
            Toast.makeText(this, "Сначала включите доступ к управлению", Toast.LENGTH_LONG).show(); return;
        }
        try { startForegroundService(new Intent(this, AutomationService.class).setAction(action)); }
        catch (RuntimeException denied) { Toast.makeText(this, "Не удалось запустить сессию. Откройте приложение и проверьте разрешения.", Toast.LENGTH_LONG).show(); }
        updateHome();
    }
}
