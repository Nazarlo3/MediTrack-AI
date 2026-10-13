# Журнал змін: Виправлення збереження та відображення фото ліків (feature_3)

## Дата
- 7 жовтня 2026 року

## Що змінено
- Створено ізольований об'єкт `PhotoStorage` для роботи з локальними фотографіями ліків у внутрішній директорії додатка (`context.filesDir/med_photos/`).
- Реалізовано асинхронне копіювання тимчасових `content://` зображень із PhotoPicker у внутрішнє сховище при додаванні препарату на `Dispatchers.IO`.
- Збереження абсолютного шляху файлу (`File.absolutePath`) у `SharedPreferences` замість тимчасового Picker URI.
- Додано утилітарну конвертацію шляху в `Uri` через `Uri.fromFile(File(path))` та безпечне декодування.
- Додано видалення локального файлу з внутрішньої директорії `med_photos` при видаленні препарату у `SymptomViewModel.kt` на `Dispatchers.IO`.

## Файли
- `app/src/main/java/com/example/meditrackai/ui/PhotoStorage.kt` [NEW]
- `app/src/main/java/com/example/meditrackai/ui/MedicationSection.kt` [MODIFY]
- `app/src/main/java/com/example/meditrackai/ui/SymptomViewModel.kt` [MODIFY]
- `docs/impl/feature_3/plan.md` [MODIFY]
- `docs/impl/feature_3/changelog.md` [MODIFY]

## Перевірка
- Запуск `:app:assembleDebug` та `:app:assembleRelease` — збірка успішна.
- Ручна перевірка відтворення та збереження фотографій ліків при повному перезапуску додатка.

## Обмеження
- Старі записи у `SharedPreferences` із тимчасовими `content://` URI безпечно перехоплюються через `try-catch` і показують заглушку 💊 без крашу додатка.
