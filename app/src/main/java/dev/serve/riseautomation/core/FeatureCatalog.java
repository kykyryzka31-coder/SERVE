package dev.serve.riseautomation.core;

import java.util.Arrays;
import java.util.List;

public final class FeatureCatalog {
    private FeatureCatalog() { }
    public static final class Feature {
        public final String id, title, description;
        public final AutomationTask.Category category;
        Feature(String id, String title, String description, AutomationTask.Category category) {
            this.id = id; this.title = title; this.description = description; this.category = category;
        }
    }
    public static final List<Feature> ALL = java.util.Collections.unmodifiableList(Arrays.asList(
        new Feature("city_resources", "Ресурсы города", "Сбор ресурсов после проверки интерфейса города.", AutomationTask.Category.CITY),
        new Feature("city_building", "Строительство", "Улучшения только по заданным пользователем правилам.", AutomationTask.Category.CITY),
        new Feature("world_gathering", "Сбор на карте", "Отправка войск на выбранные ресурсы после проверки.", AutomationTask.Category.WORLD),
        new Feature("world_fog", "Исследование тумана", "План: обычная ручная отправка разведчика через интерфейс. Встроенное автоисследование с лимитом 1,5 часа в день не используется.", AutomationTask.Category.WORLD),
        new Feature("alliance_help", "Помощь альянсу", "Помощь участникам после проверки интерфейса.", AutomationTask.Category.ALLIANCE),
        new Feature("alliance_donation", "Пожертвования", "Пожертвования только с явно заданными лимитами.", AutomationTask.Category.ALLIANCE)
    ));
}
