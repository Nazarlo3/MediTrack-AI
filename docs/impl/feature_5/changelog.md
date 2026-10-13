# Feature 5 — Журнал змін

## Дата

- 12 жовтня 2026 року

## Що змінено

- Додано `LocalDataStore`: читання і запис історії та ліків виконуються послідовно; стан екрана змінюється після успішного запису.
- Новий формат ліків записується у `medications_list_v2`. Старий `medications_list_v1` використовується для читання, доки новий формат відсутній, і не видаляється під час переходу.
- Статус прийому прив'язано до дати. Старий `isTaken` без дати не вважається прийомом сьогодні.
- Додано строгі перевірки опису симптому, назви, дозування, часу, днів і кінцевої дати курсу.
- Поля форми й вибрана вкладка зберігаються після зміни конфігурації екрана.
- Фото перевіряється після копіювання; при помилці неповний файл прибирається, а препарат не додається.
- Додано повідомлення про помилку локального збереження та тести валідації і сумісності старого запису.

## Файли

- `app/src/main/java/com/example/meditrackai/data/LocalDataStore.kt` [NEW]
- `app/src/main/java/com/example/meditrackai/validation/FormValidators.kt` [NEW]
- `app/src/main/java/com/example/meditrackai/network/SymptomModels.kt` [MODIFY]
- `app/src/main/java/com/example/meditrackai/ui/SymptomViewModel.kt` [MODIFY]
- `app/src/main/java/com/example/meditrackai/ui/SymptomCheckScreen.kt` [MODIFY]
- `app/src/main/java/com/example/meditrackai/ui/MedicationSection.kt` [MODIFY]
- `app/src/main/java/com/example/meditrackai/ui/PhotoStorage.kt` [MODIFY]
- `app/src/test/java/com/example/meditrackai/FormValidatorsTest.kt` [NEW]

## Перевірка

- `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, `:app:assembleRelease` — успішно.
- Три нові unit-тести пройшли. Lint: 0 помилок, 25 попереджень (серед них попередні рекомендації щодо version catalog).
- Ручна перевірка на пристрої ще потрібна для відновлення стану, фото та переходу на нову дату.

## Обмеження

- Старе `isTaken` не містило дати, тому його не можна достовірно віднести до конкретного дня.
- Нагадування за графіком не входять до цього виправлення.
