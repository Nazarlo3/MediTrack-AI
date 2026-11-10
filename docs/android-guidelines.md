# Android: стандарт роботи та черга впорядкування

Доповнення feature_27: MedicationRepository/звичайна ViewModel, SettingsRepository/SettingsViewModel, lifecycle-aware collection, окремі помилки сховищ і workflow CI реалізовані в локальному коді. Виділено компоненти форм/карток/аналізу й спільні AppPage/SectionCard; нові тексти налаштувань/вкладок — у strings.xml. Це поступове впорядкування, не повний редизайн чи завершена міграція всіх джерел/рядків. CI на GitHub та новий телефонний прогін не вважаються успішними за наявністю файлів; актуальні результати — [changelog](impl/feature_27/changelog.md). Таблиця нижче — вихідна перевірка до цього етапу.

Звірено 10.11.2026 з кодом після ea0a58e (0.5.1). Це статичне порівняння, без нового запуску/профілювання. База Kotlin: app/src/main/java/com/example/meditrackai/. Позначки P1/P2 — наш пріоритет, а не оцінка Google.

## Джерела та локальна адаптація

- [Google architecture recommendations](https://developer.android.com/topic/architecture/recommendations): repository між UI й джерелами, UDF, lifecycle-aware Flow, ViewModel без Context; manual DI допустима для невеликих застосунків.
- [Now in Android](https://github.com/android/nowinandroid), [його AGENTS.md](https://github.com/android/nowinandroid/blob/main/AGENTS.md) — приклади; не шаблон для копіювання всієї модульності.
- [android/skills](https://github.com/android/skills): взято тільки jetpack-compose-performance та android-permissions-security з ревізії `5815d27fda564aa9f07ebdcb5e57ed3e8d904e0d`, із references та ліцензією. Файли Google не переробляються; проектні обмеження й routing — у власній meditrack-android skill. Маніфест походження — .agents/skills/UPSTREAM.json.
- Каталог Google зосереджений на спеціальних задачах і не є готовим універсальним стандартом архітектури/дизайну. Локальна skill є нашою адаптацією, а не схваленням MediTrack компанією Google.

## Уже відповідає напрямку

Compose/Material3 та спільна тема — MainActivity.kt, ui/theme/Theme.kt. Аналіз має UI/controller → use case → repository і типізовані помилки — SymptomAnalysisController.kt, AnalyzeSymptomUseCase.kt, DefaultSymptomRepository.kt. Manual DI зі спільним графом — MediTrackApplication.kt, di/SymptomDependencies.kt. Є unit/fake та instrumentation-тести у app/src/test і app/src/androidTest. Окремі MutableStateFlow для незалежного стану допустимі; сама їх кількість не є помилкою.

## Конкретні розбіжності й рекомендації

| Пріоритет | Підтверджено кодом | Пропонована зміна / критерій готовності |
|---|---|---|
| P1 | SymptomViewModel : AndroidViewModel приймає LocalDataStore, створює MedicationReminderScheduler(getApplication()), викликає PhotoStorage; di/SymptomDependencies передає store напряму | Виділити MedicationRepository та ін'єктовані адаптери reminder/photo; спочатку залишити поточний JSON. VM не створює Android-залежності, поведінка ліків/графіків збережена |
| P1 | MainActivity, SymptomCheckScreen, AppSettingsScreen і DataCollectionSettingsSection використовують collectAsState; екрани також вручну отримують LifecycleOwner через Context | Перейти на сумісні lifecycle-aware Flow/effects, перевіривши залежності. Оновлення не дублюються після background/return/rotation |
| P1 | AppSettingsScreen безпосередньо викликає ApiClient.diagnosticsApi.health(), AppDiagnostics і settings; запит живе в rememberCoroutineScope UI | Settings state holder/ViewModel + repository/адаптер діагностики. Екран приймає стан/дії; cancellation та повтор контрольовані, немає нового запиту просто через recomposition |
| P1 | LocalDataStore.load() має спільний sticky readError для ліків та історії; одна помилка блокує обидва записи | Розділити результати читання/помилки за джерелами, надати явний шлях відновлення без стирання оригіналу. Пошкоджена історія не блокує справні ліки |
| P2 | SymptomCheckScreen поєднує route, вкладки, аналіз, історію й debug; MedicationSection містить форму, список та permission UI | Виділити route/content і компоненти за відповідальністю; єдиний shell/theme. Не називати розмір файла доказом повільної роботи |
| P2 | selectedTab/showSettings — rememberSaveable та умовні гілки SymptomCheckScreen; Settings має власний BackHandler | Визначити навігаційний контракт й регресії Back/rotation. Navigation розглянути при розділенні destinations; відсутність бібліотеки сама по собі не означає зламану навігацію |
| P2 | conversation/lastRequest/initialDetails у SymptomAnalysisController — лише поля RAM; чернетки форми мають rememberSaveable | Визначити політику process-death окремо від rotation. Безпечне мінімальне відновлення не повинно тихо записувати медичні тексти або автоматично повторювати AI-запит |
| P2 | UI-рядки безпосередньо у SymptomCheckScreen, AppSettingsScreen, MedicationSection; тема вже є в ui/theme | Поступово винести рядки/повторювані компоненти, узгодити відступи й дії. Великий шрифт/TalkBack/контраст поки не перевірено — це відкриті перевірки, не доведені дефекти |
| P2 | app/build.gradle.kts містить частину версій напряму; lifecycle runtime у каталозі й viewmodel-compose задані окремо; .github/workflows відсутня | Централізувати Gradle-версії після перевірки сумісності; додати мінімальний CI за окремою задачею. Різні номери бібліотек не доводять конфлікт |

## Порядок наступних ітерацій

1. **Екрани:** узгодити короткий UI-стандарт (тема, рядки, картки, loading/error/empty), виділити content із callbacks без редизайну поведінки. Кандидати: SymptomCheckScreen.kt, MedicationSection.kt, AppSettingsScreen.kt, ui/theme/, res/values/strings.xml; нові компоненти лише за фактичною повторюваністю.
2. **Стан:** lifecycle-aware collection, SettingsViewModel/SettingsRepository, MedicationRepository, ін'єкція нагадувань/фото через існуючий DI; поступово прибрати AndroidViewModel. Один сценарій за ітерацію. Окремо вирішити process-death/приватність.
3. **Дані:** ізолювати помилки history/medications та визначити відновлення; тільки потім оцінити DataStore для налаштувань/Room для ліків. Міграція, прийняті дози, фото, згода й історія мають зберегтися.

Це план наступних задач, не вже виконаний рефакторинг. Для кожної — конкретні файли, мінімальна регресія й ченджлог. Заміна AlarmManager на WorkManager, повний перепис під Clean Architecture, Hilt чи модульність Now in Android не є автоматичними наступними кроками. Performance/Baseline Profiles — після вимірювань у придатній release/profile-збірці.
