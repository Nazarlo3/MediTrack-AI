# ЛР5 — варіант А Android

Погоджено 17.10.2026. Сценарій — аналіз симптомів, уточнення та історія. Формат API і збережених SharedPreferences не змінювати; ліки, фото та нагадування лишаються з попередньою поведінкою. Backend, встановлення APK, commit/push/deploy не входять у задачу.

## Початкова реалізація

SymptomViewModel безпосередньо звертається до ApiClient і LocalDataStore, використовує DTO як стан UI та поєднує валідацію, мережеві помилки, контекст і збереження. Є Idle/Loading/Success/Error та retry, але немає repository/use case/DI, незалежних моделей і категорій помилок.

## Файли та межі

Відносно app/src/main/java/com/example/meditrackai/:

- Нові domain/symptoms/SymptomModels.kt, SymptomRepository.kt, AnalyzeSymptomUseCase.kt: незалежні моделі/помилки, контракт аналізу та історії, перевірка/підготовка результату.
- Нові data/symptoms/DefaultSymptomRepository.kt, SymptomMapper.kt: Retrofit/локальне сховище, помилки за категоріями, DTO ↔ domain ↔ старий JSON.
- Невеликий data/symptoms/SymptomHistorySource.kt: адаптер наявного LocalDataStore та порт для unit-тестів repository без Android. Нової схеми/БД немає.
- Нові di/SymptomDependencies.kt та ui/SymptomUiState.kt: ручний composition root/фабрика, незалежні стани UI. Hilt/нові runtime-залежності не потрібні.
- Для тестування всього скасування без Android/Robolectric винести координацію аналізу/історії в чистий Kotlin ui/SymptomAnalysisController.kt. SymptomViewModel делегує їй цей сценарій; код ліків лишається у ViewModel.
- Оновити SymptomViewModel, MainActivity, SymptomCheckScreen, HistorySection і відповідні UI-тестові фікстури. Persistence DTO лишаються у network/SymptomModels.kt для сумісності, UI переходить на domain.
- app/src/test: FakeSymptomRepository, AnalyzeSymptomUseCaseTest, SymptomRepositoryTest, SymptomAnalysisControllerTest. Перевірки без сервера/пристрою.
- README, architecture, decisions, ROADMAP, testing, known-limitations; журнал фактичних змін у changelog.md.

## Порядок

1. Мінімальний baseline наявних unit-тестів.
2. Моделі/repository/mapper/use case/DI; зберегти вміст історії, помилки збереження окремо від успішного AI, контекст і retry. Перевірити рефакторинг до нового розширення.
3. Контрольне розширення після checkpoint: «Скасувати аналіз». Скасування лише під час Loading, CancellationException не стає Error; старий результат не публікується, історія не поповнюється, ввід не очищується. Збереження починається після завершення Loading.
4. Фінальні unit-тести, lint, одна debug-збірка і компіляція Android-тестів; без установлення/запуску на телефоні. Не повторювати успішні перевірки без причини.
5. Оновлення KB і перегляд diff; не приписувати локальним тестам перевірку телефона/Render.

## Уточнення під час реалізації

Для збереження чернетки скасованого уточнення стан тексту піднято з ClarificationSection у SymptomCheckScreen (rememberSaveable). Початковий cancel повертає Idle; cancel уточнення повертає попередній Success з тим самим analysisId, тому чернетка не очищується як для нового результату. Додатково змінено ui/ClarificationSection.kt. Контрольний refactoring checkpoint пройшов до цих змін; фактичні результати — у changelog.md.
