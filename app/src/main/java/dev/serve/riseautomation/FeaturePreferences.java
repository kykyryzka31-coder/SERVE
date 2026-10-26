package dev.serve.riseautomation;

import android.content.Context;
import android.content.SharedPreferences;
import dev.serve.riseautomation.core.*;
import java.util.ArrayList;
import java.util.List;

public final class FeaturePreferences {
    private final SharedPreferences preferences;
    public FeaturePreferences(Context context) { preferences = context.getSharedPreferences("features", Context.MODE_PRIVATE); }
    public boolean selected(String id) { return preferences.getBoolean(id + ".selected", false); }
    public void select(String id, boolean value) { preferences.edit().putBoolean(id + ".selected", value).apply(); }
    public int priority(String id) { return preferences.getInt(id + ".priority", 1); }
    public void priority(String id, int value) { preferences.edit().putInt(id + ".priority", Math.max(1, Math.min(3, value))).apply(); }
    public int timeout(String id) { return preferences.getInt(id + ".timeout", 30); }
    public void timeout(String id, int value) { preferences.edit().putInt(id + ".timeout", value == 15 || value == 60 ? value : 30).apply(); }
    public boolean overlay() { return preferences.getBoolean("overlay", false); }
    public void overlay(boolean value) { preferences.edit().putBoolean("overlay", value).apply(); }
    public List<AutomationTask> tasks() {
        List<AutomationTask> result = new ArrayList<>();
        for (FeatureCatalog.Feature feature : FeatureCatalog.ALL) {
            result.add(new AutomationTask() {
                public String id() { return feature.title; }
                public Category category() { return feature.category; }
                public int priority() { return FeaturePreferences.this.priority(feature.id); }
                public long timeoutMillis() { return timeout(feature.id) * 1000L; }
                public boolean enabled() { return selected(feature.id); }
                public boolean validated() { return false; }
                public int execute(Cancellation token) {
                    throw new UnsupportedOperationException("Unvalidated feature must never execute");
                }
            });
        }
        return result;
    }
}
