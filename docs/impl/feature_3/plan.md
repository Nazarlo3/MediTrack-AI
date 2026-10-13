# План виправлення: Збереження та відображення фото ліків після перезапуску застосунку (feature_3)

## 1. Причина проблеми з підтвердженим Logcat
- **Шлях до файлу:** [`app/src/main/java/com/example/meditrackai/ui/MedicationSection.kt`](../../../app/src/main/java/com/example/meditrackai/ui/MedicationSection.kt#L418-L422) (рядки 418–422).
- **Суть проблеми:** При додаванні нового препарату значення `formState.selectedPhotoUri?.toString()` передається як звичайний рядок тимчасового `content://` URI (наприклад `"content://media/picker_get_content/0/com.android.providers.media.photopicker/media/1000134728"`) і зберігається в `SharedPreferences` через `SymptomViewModel.kt` (рядки 111–133).
- **Підтвердження з Logcat при відтворенні:**
```text
java.lang.SecurityException: Calling uid ( 10537 ) does not have permission to access picker uri: content://media/picker_get_content/0/com.android.providers.media.photopicker/media/1000134728
URI: content://media/picker_get_content/0/com.android.providers.media.photopicker/media/1000134728
```
Тимчасовий дозвіл читання `content://` URI з Android Photo Picker анулюється системою після вивантаження процесу додатка або перезапуску смартфона. При наступному запуску додатка виклик `context.contentResolver.openInputStream(uri)` у [MedicationSection.kt:600](../../../app/src/main/java/com/example/meditrackai/ui/MedicationSection.kt#L600) викидає виняток `java.lang.SecurityException`. Блок `try-catch` перехоплює виняток і повертає `null`, викликаючи відображення дефолтної іконки 💊 замість обраного фото.

---

## 2. Запропоновані зміни

### 1) [NEW] `app/src/main/java/com/example/meditrackai/ui/PhotoStorage.kt`
Створити окремий утилітарний об'єкт `PhotoStorage`, у який винести всю логіку збереження, конвертації та видалення фотографій:
- `suspend fun savePhotoToInternalStorage(context: Context, uri: Uri): String?` — виконується обов'язково на **`Dispatchers.IO`** (`withContext(Dispatchers.IO)`), зчитує потік із тимчасового `content://` URI та зберігає файл у приватній директорії додатка `context.filesDir/med_photos/med_<uuid>.jpg`. Повертає **абсолютний шлях файлу** (`File.absolutePath`), наприклад `/data/user/0/com.example.meditrackai/files/med_photos/med_123.jpg`.
- `suspend fun deletePhotoFromInternalStorage(filePath: String?)` — виконується на **`Dispatchers.IO`**, перевіряє наявність локального файлу у папці `med_photos` та безпечно видаляє його з диска при видаленні препарату.
- `getUriFromPathOrContent(pathOrUriString: String?): Uri?` — якщо рядок містить абсолютний шлях файлу, повертає `Uri.fromFile(File(path))`; якщо рядок починається з `content://`, повертає `Uri.parse(string)` для зворотної сумісності зі старими записами.

### 2) [MODIFY] `app/src/main/java/com/example/meditrackai/ui/MedicationSection.kt`
- Прибрати тимчасові класи й блоки діагностики (`DiagnosticLog` та відображення помилки під картою).
- У `PhotoAndSaveRowSection` при збереженні викликати корутину для завантаження фото в кеш через `PhotoStorage.savePhotoToInternalStorage(context, uri)` на `Dispatchers.IO` і передавати у `onAddMedication` абсолютний шлях файлу (`File.absolutePath`).
- У `rememberSampledBitmap` отримувати `Uri` за допомогою виклику `PhotoStorage.getUriFromPathOrContent(uriString)`.

### 3) [MODIFY] `app/src/main/java/com/example/meditrackai/ui/SymptomViewModel.kt`
- У `deleteMedication(id: String)` перед видаленням препарату зі списку знаходити об'єкт за `id` та викликати `PhotoStorage.deletePhotoFromInternalStorage(itemToDelete?.photoUri)` на `Dispatchers.IO` для очищення дискової пам'яті.

---

## 3. Список файлів, які НЕ будуть змінюватись
- `app/src/main/java/com/example/meditrackai/network/SymptomModels.kt` (модель `MedicationItem` залишається без змін).
- `app/src/main/java/com/example/meditrackai/network/ApiClient.kt`
- `app/src/main/java/com/example/meditrackai/ui/SymptomCheckScreen.kt`
- Усі файли бекенду у `backend/`
- Усі файли тем у `ui/theme/`

---

## 4. Нові залежності
- **Нові залежності відсутні.** Використовуються лише стандартні засоби Android SDK (`Context.filesDir`, `InputStream`, `File`, `FileOutputStream`, `Uri.fromFile`, `Dispatchers.IO`).

---

## 5. Як перевірити вручну
1. Запустити додаток у режимі Debug або Release.
2. Перейти на вкладку «Мої ліки».
3. Додати новий препарат, натиснути «Обрати фото» і вибрати будь-яке фото з галереї.
4. Натиснути «Зберегти». Переконатися, що фото з'явилося у списку.
5. Повністю закрити додаток (Force Stop у налаштуваннях або вивантажити з меню останніх задач).
6. Запустити додаток знову.
7. **Очікуваний результат:** Фотографія ліків залишається на місці і коректно відображається через `Uri.fromFile`.
8. Видалити препарат — переконатися, що файл видаляється з локальної папки `med_photos`, а препарат зникає зі списку без помилок.

---

## 6. Ризики та зворотна сумісність
- **Старі ліки з `content://` URI:** Старі записи у `SharedPreferences` з застарілими `content://` URI не змінюються. При спробі їх зчитування `PhotoStorage.getUriFromPathOrContent` та `loadSampledBitmapFromUri` перехоплюють `SecurityException` / `FileNotFoundException` та безпечно повертають `null` (показується заглушка 💊) без крашу додатка.
