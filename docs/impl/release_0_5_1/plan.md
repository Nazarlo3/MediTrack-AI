# Версія 0.5.1

1. Включити погоджені зміни feature_25 (перемикач/API, Google/нагадування, налаштування).
2. Зберігати прийняті Google-задачі в приватному SQLite outbox до підтвердження Google; відновлювати після запуску. Не записувати сирі capability-ключі. Зберегти дедуплікацію й видалення під час запиту/черги. Не заявляти стійкість при втраті диска.
3. Версія Android 0.5.1 / code 6; оновити README й короткий опис оновлення.
4. Перевірити відновлення/збій після запису Google/відкликання/строк; відповідні backend-тести, Android-збірку через наявні залежності. Перевірити staged diff, секрети й збереження сторонніх змін.
5. Один коміт із назвою та стислим описом поведінки/перевірок. Push/Render — лише після відповіді на окреме уточнення. Новий публічний APK не публікувати без запиту.

Нові файли: backend/collection_outbox.py, backend/tests/test_collection_outbox.py, docs/releases/v0.5.1.md, цей plan.md/changelog.md/verification.json. Зміни: backend/collection_dispatcher.py, collection_service.py, sheets_collection_repository.py, dependencies.py, config.py, main.py; app/build.gradle.kts; README.md; відповідні API/архітектурні документи та feature_25. Нових бібліотек немає.

Ризик: Render без постійного диска стирає локальні файли при deploy/restart інстансу. До перевірки конфігурації хмари не називати outbox стійким у production; не створювати платні ресурси автоматично.
