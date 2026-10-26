# Rise Automation — Android foundation

Phase 2: нативное Java-приложение с управлением сессией, foreground-уведомлением,
планировщиком задач, пользовательским Accessibility-доступом и опциональной
плавающей кнопкой Start/Stop. Экраны: Главная, Задачи (Город/Мир/Альянс),
Настройки по каждой функции. Поддерживаемый диапазон — API 26–35.

**Все игровые исполнители отключены до проверки.** В приложении нет готовых
RoK-задач, распознавания экрана или фиктивных успешных действий. Исследование
тумана запланировано через обычную ручную отправку разведчиков, без встроенного
автоисследования с дневным лимитом.

Архитектура, ограничения отмены и план ручной проверки:
[docs/AUTOMATION_CORE.md](docs/AUTOMATION_CORE.md).

## Требования и структура

- JDK 21 (исходный код Java 17), Gradle Wrapper 8.13, Android Gradle Plugin 8.13.2.
- Android SDK Platform 35, Build Tools 35.0.0, Platform Tools (ADB).
- Минимальная версия устройства: Android 8.0 / API 26; compile/target SDK 35.
- `app/src/main/` — AndroidManifest, Activity, ресурсы и основа жизненного цикла.
- `app/src/test/` — JVM-тесты без эмулятора.
- `scripts/` — установка в облаке, проверки и smoke-тест через ADB.
- `.github/workflows/android.yml` — сборка, lint, unit-тесты и сохранение отчётов/APK.

## Облачная среда Linux

Из корня существующего checkout (в этой среде `/workspace/SERVE`):

```bash
python3 scripts/bootstrap-cloud.py
source scripts/cloud-env.sh
./scripts/check.sh
```

Установщик требует Python 3.12+ и доступ к официальным серверам Google,
Gradle и Maven, а также право записи в `/workspace/.android-tools`.
Он устанавливает Temurin JDK 21.0.13 с проверкой SHA-256 по официальному
релизу Adoptium и проверяет архив Gradle по SHA-256 с официального сервера, SDK CLI 19.0 —
по checksum из HTTPS-метаданных Google. Кэши, SDK и параметры прокси Gradle
хранятся вне checkout. Для Java используется системное хранилище доверенных
сертификатов `/etc/ssl/certs/java/cacerts`, если оно доступно; TLS-проверка
сохраняется. Скрипт принимает лицензии Android SDK; ознакомьтесь
с лицензиями Google перед самостоятельным использованием.
Существующий `ANDROID_TOOLS_DIR` можно задать для другого каталога.
ADB 37 использует стандартный каталог `~/.android`: он должен существовать
и быть доступен для записи. В ограниченной среде это может потребовать
разрешения платформы. Не изменяйте `HOME` и не отключайте TLS-проверку.

## Локальная сборка

Установите JDK 21 и SDK через Android Studio / SDK Manager; задайте
`ANDROID_HOME` или локальный `sdk.dir` в игнорируемом `local.properties`.

```bash
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Windows: используйте `gradlew.bat` с теми же задачами. Shell-скрипты требуют
Bash (например, Git Bash); проверка экрана в ADB smoke-тесте требует Python 3.

Результаты:

- APK: `app/build/outputs/apk/debug/app-debug.apk` (debug-подпись, не release).
- Unit-тесты: `app/build/reports/tests/testDebugUnitTest/index.html`.
- Android Lint: `app/build/reports/lint-results-debug.html`.

Для сборки достаточно `./gradlew assembleDebug`, для проверок —
`./scripts/check.sh`. На чистой машине нужен Интернет для зависимостей.
Wrapper проверяет SHA-256 дистрибутива Gradle. Release-подпись не настроена.

## Эмулятор / LDPlayer и ADB

Эти действия выполняются на локальном компьютере с работающим Android-эмулятором.
В LDPlayer включите ADB в настройках и уточните адрес/порт конкретного экземпляра.
Порт зависит от версии и номера экземпляра; не считайте его фиксированным.

```bash
adb connect 127.0.0.1:PORT
adb devices -l
export ANDROID_SERIAL=127.0.0.1:PORT  # точный serial из adb devices -l
./scripts/adb-smoke.sh
```

Для Android Studio Emulator обычно достаточно запустить AVD, затем выбрать
его serial (`emulator-5554` — только пример); `adb connect` не требуется.
PowerShell: `$env:ANDROID_SERIAL = "SERIAL"` и выполняйте эквивалентные команды
из скрипта либо запускайте его из Bash. При нескольких устройствах всегда
указывать serial; скрипт требует его явно.

Smoke-тест проверяет завершение загрузки Android, устанавливает debug APK,
запускает Activity, проверяет PID, название приложения и экран статистики через UIAutomator.
При заблокированном экране или незавершённой загрузке сначала разблокируйте
эмулятор. Скрипт устанавливает/обновляет пакет `dev.serve.riseautomation` и
оставляет приложение запущенным. Это проверка оболочки приложения, не игры.

Облачный `127.0.0.1` не указывает на ваш компьютер. Подключение к локальному
LDPlayer требует отдельно настроенного безопасного сетевого маршрута;
не публикуйте незащищённый ADB-порт в Интернет. Предпочтительный путь —
скопировать APK и запустить smoke-тест локально. Cloud не имеет `/dev/kvm`,
подключённых Android-устройств или LDPlayer. Тестирование на LDPlayer здесь
не выполнялось. Android instrumentation/UI-тесты в CI пока не настроены;
CI проверяет JVM-тесты, lint и сборку.

## Проверено в этой облачной среде — Phase 2

- `testDebugUnitTest`: 16 тестов, 0 ошибок, 0 пропусков (состояние, планировщик, отмена,
  тайм-аут, backoff, изоляция ошибок и относительные координаты).
- `lintDebug` и `assembleDebug`: успешно.
- `apksigner verify --verbose`: debug APK имеет корректную подпись v2.
- `aapt dump badging`: пакет и запускаемая Activity присутствуют, min SDK 26.
- Повторный запуск bootstrap: успешно; ADB запускается, список устройств пуст.
- Lint: 0 ошибок; оставшиеся предупреждения о target/compile API 35,
  правилах backup и отсутствующей иконке. API 35 сохранён по требованиям проекта.
- Предупреждение записи аналитики SDK в закрытый `~/.android` не блокирует сборку.
- GitHub Actions запускает те же unit-тесты, lint и сборку. Первый запуск Phase 2
  завершился на setup-android до сборки: исключён устаревший пакет `tools`,
  зафиксированы SDK CLI 19 и runner Ubuntu 24.04. Итог удалённого запуска
  проверяется отдельно по ссылке Actions для текущего коммита.
- Phase 2 на устройстве, Android Emulator и LDPlayer ещё не проверялась.
  Успешная установка старого skeleton на LDPlayer Android 9 сообщена пользователем
  и не является проверкой новых служб и жестов.
