# Впорядкування Android-клієнта

Погоджено: спільне оформлення → відокремлення I/O → lifecycle-стан → ізоляція сховища → CI.

1. Спільні AppPage/SectionCard, заголовки й ресурси текстів; виділення контенту налаштувань. Зберегти наявну тему, вкладку ліків, підсвічення прийнятих доз і дії.
2. MedicationRepository поверх поточного LocalDataStore та ін'єкція reminder/photo; SymptomViewModel → звичайна ViewModel. SettingsRepository/ViewModel для health і діагностики; callbacks у content. Manual DI збережено.
3. Lifecycle-aware Flow/effects без збереження медичних текстів після process death; змінити лише необхідні залежності, винести наявні версії в каталог.
4. Окремі помилки історії/ліків і повторне читання без стирання пошкоджених оригіналів; регресії ізоляції.
5. GitHub Actions: Android unit/build/lint і backend unit, мінімальні права, без secrets/live API; короткий розділ README.

Файли: ui/{SymptomCheckScreen,AppSettingsScreen,DataCollectionSettingsSection,SymptomViewModel,SettingsViewModel}.kt, ui/components/AppComponents.kt; domain/medications/MedicationRepository.kt; data/{LocalDataStore,medications/DefaultMedicationRepository,settings/SettingsRepository}.kt; di/SymptomDependencies.kt; MainActivity.kt; reminders/MedicationReminderScheduler.kt (перевірка окремої помилки); data/symptoms/SymptomHistorySource.kt; res/values/strings.xml; Gradle/catalog; відповідні unit/instrumentation тести та їхні конструктори; .github/workflows/ci.yml; README та архітектурні документи/цей changelog.

Сторонні зміни зберегти; у вже зміненому Lab5PhoneScenarioTest можлива лише необхідна адаптація виклику конструктора. Без Room/Hilt/зміни JSON/API/версії, commit/push/deploy. Перевірки: Android unit + build/test APK/lint, цільові instrumentation за підключення; CI локально перевірити, не видавати за виконаний GitHub run.

Під час реалізації виділено також di/MedicationDependencies.kt і ui/{SymptomComponents,MedicationForm,MedicationCard,MedicationPhoto}.kt; актуалізуються README/docs/architecture.md/ROADMAP.md/android-guidelines.md та карта шляхів у локальній meditrack-android skill.
