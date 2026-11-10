# Android: екрани, стан, дані та CI

## Реалізовано

- Спільні AppPage/SectionCard для основного екрана та налаштувань; тема/кольори прийнятих доз збережені. Налаштування розділено на route/content, тексти нових секцій і вкладок — у strings.xml. Форма/картка/bitmap-helper ліків та компоненти аналізу виділені в окремі файли без зміни поведінки.
- SymptomViewModel тепер звичайна ViewModel та використовує MedicationRepository. LocalDataStore/Android-ефекти нагадувань і видалення фото приховані в data/DI. Старий JSON, DTO й manual DI збережені.
- SettingsViewModel/SettingsRepository відповідають за health і технічну діагностику; повторний health під час поточного запиту не стартує, застаріле завантаження діагностики скасовується. Обидві ViewModel утримуються власником lifecycle Activity. Налаштування збору мають чинну поведінку без нового діалогу/видалення.
- Flow у змінених route/налаштуваннях збирається через collectAsStateWithLifecycle, повернення до екрана — LifecycleResumeEffect. Медичні тексти додатково не персистуються.
- Розділено historyReadError/medicationsReadError, агрегат readError залишено для сумісності. Пошкоджений оригінал не стирається; справне сховище працює, повторне читання після явного ремонту оновлює стан. History update перечитує актуальний JSON, щоб інший екземпляр не затер новий запис. Scheduler перевіряє лише ліки.
- Наявні ручні Gradle-версії винесені в каталог без їх підвищення; додано lifecycle-runtime-compose відповідної runtime-версії. Додано workflow GitHub: Android unit/build/lint + backend unit, без secrets/live API/release/deploy, мінімальні права. README має короткий запуск і команди перевірок.

## Фактичні перевірки

- Android: :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug — BUILD SUCCESSFUL; **71 unit-тест**, 0 failures/errors/skips; lint **0 errors / 49 warnings**. Перший lint зупинився на двох нових context.getString у Compose callbacks; виправлено через stringResource, потім фінальний прогін успішний.
- Телефон спочатку не знайдено за старим USB serial; перевірка актуального ADB-підключення знайшла той самий Redmi Note 12S за моделлю. Через install -r встановлено debug без очищення/видалення; MainActivity запускається.
- **15/15** instrumentation: CollectionSettingsTest, SettingsDiagnosticsTest, ReminderRecoveryTest та HistoryStorageInstrumentedTest (включно з новими регресіями ізоляції/ремонту/оновлення з різних store instances). Окремо **1/1** MedicationViewModelInstrumentedTest: додавання, статус дози, редагування зі збереженням статусу, видалення та ін'єктовані ефекти. Разом **16 успішних, 0 пропущених**, без live мережі. Для останнього нового сценарію окремо зібрано лише test APK.
- Хеші користувацьких ліків/історії/налаштувань/збору незмінні після встановлення й обох телефонних прогонів. Тестові дані ізольовані та очищені; реальні файли не очищалися. Журнали — локальна .artifacts/feature27*.
- TOML/XML та YAML workflow перевірені; GitHub Actions ще не запускалися. git diff --check успішний; локальна skill валідна. 28 сторонніх файлів збігаються зі збереженим хеш-знімком; у Lab5PhoneScenarioTest змінено лише необхідний виклик конструктора, попередні правки користувача звірено й збережено.

## Межі

Версія 0.5.1, JSON/API, backend-код і політика медичних даних не змінені. Новий публічний APK, commit/push/Render не виконувалися. CI реально перевіриться після погодженого push. Великий шрифт/TalkBack, вимірювання продуктивності, process death і довгі сценарії наступного дня/reboot цим прогоном не підтверджуються. Це завершені локальні покращення, а не повна міграція всіх рядків/Android-джерел або повний редизайн.
