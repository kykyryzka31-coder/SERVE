package dev.serve.riseautomation.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Called under the engine lock. Category round robin, weighted round robin inside each. */
public final class FairScheduler {
    private final List<AutomationTask> tasks;
    private final int[] cursor = new int[AutomationTask.Category.values().length];
    private final Map<String, Long> readyAt = new HashMap<>();
    private final Map<String, Integer> failures = new HashMap<>();
    private int categoryCursor;
    public FairScheduler(List<AutomationTask> tasks) {
        this.tasks = new ArrayList<>(tasks);
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        for (AutomationTask task : tasks) {
            if (!ids.add(task.id()) || task.timeoutMillis() <= 0) {
                throw new IllegalArgumentException("Duplicate ID or invalid timeout");
            }
        }
    }
    public AutomationTask next(long now) {
        AutomationTask.Category[] categories = AutomationTask.Category.values();
        for (int n = 0; n < categories.length; n++) {
            int category = (categoryCursor + n) % categories.length;
            List<AutomationTask> tickets = new ArrayList<>();
            for (AutomationTask task : tasks) {
                if (task.category() == categories[category] && task.enabled() && task.validated()) {
                    for (int weight = 0; weight < Math.max(1, Math.min(3, task.priority())); weight++) {
                        tickets.add(task);
                    }
                }
            }
            for (int i = 0; i < tickets.size(); i++) {
                int index = Math.floorMod(cursor[category] + i, tickets.size());
                AutomationTask candidate = tickets.get(index);
                if (now >= readyAt.getOrDefault(candidate.id(), 0L)) {
                    cursor[category] = (index + 1) % tickets.size();
                    categoryCursor = (category + 1) % categories.length;
                    return candidate;
                }
            }
        }
        return null;
    }
    public void success(AutomationTask task, long now) {
        failures.remove(task.id());
        readyAt.put(task.id(), now + 500);
    }
    public void failure(AutomationTask task, long now) {
        int count = Math.min(7, failures.getOrDefault(task.id(), 0) + 1);
        failures.put(task.id(), count);
        readyAt.put(task.id(), now + Math.min(60_000L, 1_000L << (count - 1)));
    }
}
