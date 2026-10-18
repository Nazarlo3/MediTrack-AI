# ЛР5 — Android журнал змін

## 17.10.2026

- Погоджено варіант А для аналізу/уточнень/історії; записано план, межі сумісності та файли.
- Попередні незакомічені зміни й виконаний варіант Б зберігаються. Результати перевірок додаються після фактичних запусків.

### Реалізація

- Додано domain/symptoms: незалежні моделі, SymptomError/SymptomFailure, SymptomRepository, AnalyzeSymptomUseCase з ін’єктованим часом.
- Додано data/symptoms: mapper API/storage ↔ domain, DefaultSymptomRepository, порт історії й SharedPreferences-адаптер наявного LocalDataStore.
- Додано ручний composition root SymptomDependencies/фабрику; MainActivity передає ViewModel екрану. ViewModel отримує залежності через конструктор і делегує аналіз/історію SymptomAnalysisController; UI використовує SymptomUiState та domain-моделі.
- Блок методів ліків залишено дослівно, зокрема підсвічення/статуси, редагування, фото й нагадування не переписувалися.
- Після успішного checkpoint додано «Скасувати аналіз»: Job/cancellation propagation та захист від пізнього успіху/помилки. Початковий cancel → Idle, уточнення → попередній Success; його чернетка зберігається в батьківському rememberSaveable, новий успіх очищує її за analysisId.
- Помилки запису історії показуються окремо від успішного AI. DTO, старі JSON-ключі та метадані не змінено.
- Додано FakeSymptomRepository й 18 JVM-тестів; адаптовано дві наявні Compose-фікстури. Оновлено README та KB без нових залежностей.

### Фактичні перевірки

- Baseline unit: BUILD SUCCESSFUL, 39 тестів.
- Checkpoint **до cancel**: BUILD SUCCESSFUL, 54 тести, 0 failures/errors/skipped.
- Фінал :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin: BUILD SUCCESSFUL in 36s; 57 тестів, 0 failures/errors/skipped; lint 0 errors / 50 warnings.
- Перевірки сумісності збереження — старі codec-тести й новий repository round trip; тести cancellation включають запізнілі success/error та повтор уточнення.
- Методи ліків у ViewModel звірено з початковим блоком: тотожні.
- UI-тести лише скомпільовано; телефон, чернетки/поворот і Android → Render після змін не перевірено. APK не встановлено; release/commit/push/deploy не виконувалися.

- Фінальне звірення SHA-256: усі 24 захищені попередньо змінені файли backend/нагадувань/сховища/ліків/IDE тотожні початковим; локальні посилання оновлених документів існують.

### Наступна погоджена перевірка

Після окремого підтвердження користувача виконано [feature_14](../feature_14/changelog.md): встановлення debug/test APK зі збереженням даних, три сценарні UI-тести на Android 15, локальний FastAPI → справжній Groq і три переглянуті скриншоти. Попередні записи «не встановлювали/не перевіряли» описують саме початкове завершення feature_13.
