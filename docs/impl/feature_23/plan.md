# Добровільний збір звернень для аналізу якості AI

Погоджено 05.11.2026. Зберегти сторонні зміни та ЛР7; без commit/push/розгортання.

1. SQLite repository + service: поточний запит/відповідь, модель, час, дата; без context, IP та даних ліків. Строк 90 днів. Старі клієнти не включають збір.
2. Сервер активується COLLECTION_DB_PATH; секрет COLLECTION_ADMIN_TOKEN лише у середовищі. Власник — випадковий capability-token, у БД лише SHA-256. Видалення блокує пізній запис від запитів у польоті.
3. Android: початковий перемикач увімкнений, але збереження тільки після ознайомлення/вибору. Вимкнення, видалення серверних записів, повідомлення стану. Локальна історія незалежна; попередня історія/контекст не завантажуються.
4. Захищений перегляд і статистика API, обмежена пагінація; несправність збору не порушує AI.
5. Тести меж доступу, згоди, строку зберігання, дублів/видалення та сумісності; Android unit/build/lint. Описати запуск і межі перед хмарним використанням.

Файли: backend/collection_{repository,service,routes}.py, models.py, main.py, dependencies.py, config.py, tests/test_collection.py; Android data/DataCollectionSettings.kt, ui/DataCollectionSettingsSection.kt, network/CollectionApi.kt, network/SymptomModels.kt, data/symptoms/DefaultSymptomRepository.kt, di/SymptomDependencies.kt, MainActivity.kt, ui/SymptomCheckScreen.kt; тести, .gitignore та відповідна документація.

Це накопичення для ручної оцінки та статистики, не автоматичне навчання. Хмарне постійне сховище/резервні копії/оператор доступу визначаються перед розгортанням.

Уточнення переліку під час реалізації: CollectionPreferences.kt для незалежних unit-тестів; CollectionPolicyTest.kt, CollectionSettingsTest.kt; backup_rules.xml/data_extraction_rules.xml для виключення приватного ключа з резервних копій; docs/data-collection.md та verification.md. Інтерфейс MediTrackApi збережений у ApiClient.kt; окремого наявного MediTrackApi.kt у проєкті немає.
