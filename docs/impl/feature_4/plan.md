# План додавання: Приховане дебаг-меню перевірки сповіщень (feature_4)

## 1. Результати аналізу КРОКУ 1 (чесна діагностика та докази з коду)
Проведено детальний пошук по коду проєкту на наявність функціоналу нагадувань:
- `AlarmManager`: **не реалізовано** (0 збігів у коді).
- `WorkManager`: **не реалізовано** (0 збігів у коді).
- `NotificationManager`: **не реалізовано** (0 збігів у коді).
- `NotificationChannel` (створення каналу): **не реалізовано** (0 збігів у коді).
- Виклик `notify(...)`: **не реалізовано** (0 збігів у коді).
- `BroadcastReceiver` / `PendingIntent`: **не реалізовано** (0 збігів у коді).

**Чесний висновок:**
Автоматичне планування нагадувань про прийом ліків за графіком у застосунку **повністю відсутнє**. Додане дебаг-меню перевіряє виключно базову доставку системних сповіщень та роботу NotificationChannel.

**Фактично наявні дозволи в коді:**
- Дозвіл `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` у [`app/src/main/AndroidManifest.xml:6`](../../../app/src/main/AndroidManifest.xml#L6).
- Запит системного дозволу `notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)` при збереженні ліків у [`MedicationSection.kt:443`](../../../app/src/main/java/com/example/meditrackai/ui/MedicationSection.kt#L443).

---

## 2. Запропоновані зміни для Дебаг-меню

### 1) [NEW] `app/src/main/java/com/example/meditrackai/ui/DebugAlarmReceiver.kt`
- Створити клас `DebugAlarmReceiver : BroadcastReceiver()`, який отримує сигнал від `AlarmManager`, створює `NotificationChannel` (якщо ще не створено) і показує тестове сповіщення "Тест: час випити Парацетамол" через `NotificationManager.notify()`.

### 2) [NEW] `app/src/main/java/com/example/meditrackai/ui/DebugMenu.kt`
Створити Composable-екран дебаг-меню (використовує `Column` з `verticalScroll`, без `LazyColumn`):
- Перевірка прапору `ApplicationInfo.FLAG_DEBUGGABLE` (відкривається лише в debug-збірках).
- Статус дозволу `POST_NOTIFICATIONS` + кнопка "Запросити дозвіл".
- Статус NotificationChannel ("med_test_channel") + кнопка "Створити канал".
- Кнопка "Тестове сповіщення зараз" (відразу відправляє сповіщення через `NotificationManager.notify()`). Якщо дозволу немає — відображає текстову підказку замість крашу.
- Кнопка "Запланувати тест через 1 хвилину" (виклики `AlarmManager.setAndAllowWhileIdle` з `PendingIntent` на `DebugAlarmReceiver`).
  * **Обґрунтування вибору `setAndAllowWhileIdle`:** Спрацьовує приблизно вчасно, може мати затримку, і при цьому **НЕ вимагає** додавання обмежувального дозволу `SCHEDULE_EXACT_ALARM` на Android 12+ (API 31+).
- Список збережених ліків: відображення кожного препарату, його часу (`mealSlot`) та чесного статусу нагадування ("Ні — системний планувальник у застосунку відсутній").
- Лог подій: останні 20 рядків у пам'яті (`mutableStateListOf`), які записують кожну діагностичну дію з точним часом.
- Кнопка "Назад": повертає до основного екрана без зміни стану додатка.

### 3) [MODIFY] `app/src/main/AndroidManifest.xml`
- Зареєструвати `DebugAlarmReceiver`:
  `<receiver android:name=".ui.DebugAlarmReceiver" android:exported="false" />`.
- Жодних нових дозволів (зокрема `SCHEDULE_EXACT_ALARM`) у маніфест **НЕ додається**.

### 4) [MODIFY] `app/src/main/java/com/example/meditrackai/ui/SymptomCheckScreen.kt`
- Додати лічильник і обробник `.clickable { ... }` на заголовок "MediTrack AI" у `Header` для 5 швидких натискань (працює виключно у Debug-режимі при `(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0`).
- **Нульові зміни вигляду:** Зовнішній вигляд, кольори, розміри та верстка заголовка залишаються 100% ідентичними.

---

## 3. Список файлів, які НЕ будуть змінюватись
- `app/src/main/java/com/example/meditrackai/ui/SymptomViewModel.kt`
- `app/src/main/java/com/example/meditrackai/ui/MedicationSection.kt`
- `app/src/main/java/com/example/meditrackai/network/SymptomModels.kt`
- `app/src/main/java/com/example/meditrackai/network/ApiClient.kt`
- Усі файли бекенду в `backend/`
- `build.gradle.kts`

---

## 4. Як перевірити вручну
1. Запустити `:app:assembleDebug`.
2. Натиснути 5 разів на заголовок "MediTrack AI" — переконатися, що відкривається дебаг-меню.
3. Перевірити наявність дозволу та натиснути "Створити канал".
4. Натиснути "Тестове сповіщення зараз" — переконатися, що сповіщення надходить негайно.
5. Натиснути "Запланувати тест через 1 хвилину" — згорнути/заблокувати пристрій та дочекатися сповіщення (може мати невелику затримку через `setAndAllowWhileIdle`).
   * *Примітка:* Якщо сповіщення через хвилину не прийшло, можлива причина — режим економії батареї (наприклад, MIUI/HyperOS) або фонові обмеження системи, це не є помилкою коду.
6. Натиснути "Назад" — переконатися у поверненні до головного екрана.
7. Перевірка Release-збірки: Запустити `:app:assembleRelease`, натиснути 5 разів на заголовок "MediTrack AI" — **переконатися, що у release-збірці меню не відкривається**.

---

## 5. Обмеження
- **Тимчасовий лог подій у пам'яті:** Діагностичний лог зберігається виключно в оперативній пам'яті та повністю зникає після виходу з дебаг-меню або перезапуску додатка.
- Після реалізації коду буде заповнено файл `docs/impl/feature_4/changelog.md`.
