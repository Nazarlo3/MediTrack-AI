# Надійність і налаштування

1. Усунути застарілий стан збору даних між Activity/репозиторієм. Перевірити два екземпляри налаштувань.
2. Обмежити частоту/одночасність публічних API-запитів і розмір тіла. Не створювати записи відкликання для невідомих ключів; зберегти захист від пізнього запису.
3. Винести Google-збереження з відповіді AI у обмежену чергу з повторними спробами та дедуплікацією. Явно повідомляти «у черзі», а не «збережено». Зафіксувати обмеження пам'яті/одного процесу.
4. Позначати нагадування доставленим лише після успішного notify; відновлювати невдалу доставку.
5. Окремий екран налаштувань: тема, наявний перемикач/подробиці, GitHub, фактична версія, технічні дані й безпечне копіювання діагностики.

Файли: MediTrackApplication.kt, MainActivity.kt, main/debug AndroidManifest.xml; data/DataCollectionSettings.kt, data/symptoms/DefaultSymptomRepository.kt, domain/symptoms/SymptomModels.kt; network/ApiClient.kt; ui/SymptomCheckScreen.kt, AppSettingsScreen.kt, SymptomUiState.kt; diagnostics/AppDiagnostics.kt; reminders/MedicationReminderScheduler.kt, ReminderDelivery.kt; backend/api_protection.py, collection_dispatcher.py, main.py, collection_service.py, collection_repository.py, sheets_collection_repository.py, collection_routes.py, dependencies.py; відповідні Android/backend тести; docs/api-contract.md, data-collection.md; цей план і changelog.md. Без нових бібліотек, комітів, релізу й розгортання.

Додаткові документи актуалізації: docs/architecture.md, decisions.md, ROADMAP.md. Результати: verification.json та changelog.md.

Перевірка: регресії стану/доставки, API-лімітів/черги/видалення; backend unittest, Android unit + debug build/lint. Телефон — лише за наявності підключення; особисті дані не очищати.
