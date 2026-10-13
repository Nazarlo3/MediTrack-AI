# План реалізації нової функції: Необов'язкове поле тривалості симптому у днях (без_docs)

## 1. Результат перевірки Step 1
`docs/ відсутня` (директорію перейменовано на `docs_hidden/`).

---

## 2. Запропоновані зміни по файлах

### 1) [MODIFY] `backend/models.py`
- Додати у Pydantic-модель `SymptomRequest` необейовазкове поле:
  ```python
  symptom_days: int | None = Field(
      default=None,
      ge=1,
      le=30,
      description="Кількість днів тривалості симптому (від 1 до 30). Необов'язкове поле."
  )
  ```

### 2) [MODIFY] `backend/ai_service.py`
- Оновити сигнатуру функції `categorize_symptom(description: str, symptom_days: int | None = None)`.
- Якщо `symptom_days` вказано (наприклад `10`), формувати повідомлення для AI з додатковим контекстом: `"Симптом триває 10 днів."`.
- Додати інструкцію в `SYSTEM_PROMPT`: `"Якщо тривалість симптому становить 7 днів і більше, враховуй хронічність стану, що може підвищувати рівень терміновості (urgency) до 'увага' або 'терміново'."`.

### 3) [MODIFY] `backend/main.py`
- Передавати `request.symptom_days` у виклик `categorize_symptom(description, request.symptom_days)`.

### 4) [MODIFY] `app/src/main/java/com/example/meditrackai/network/SymptomModels.kt`
- Оновити data-класи:
  ```kotlin
  @Immutable
  data class SymptomRequest(
      val description: String,
      val symptom_days: Int? = null
  )

  @Immutable
  data class SymptomHistoryItem(
      val timestamp: String,
      val description: String,
      val response: SymptomResponse,
      val symptomDays: Int? = null
  )
  ```

### 5) [MODIFY] `app/src/main/java/com/example/meditrackai/ui/SymptomViewModel.kt`
- Оновити функцію `checkSymptom(description: String, symptomDays: Int? = null)`:
  - Передавати `symptomDays` у `SymptomRequest(trimmed, symptomDays)`.
  - Записувати `symptomDays` у `SymptomHistoryItem(..., symptomDays = symptomDays)`.
- Зберегти `lastSymptomDays` у ViewModel для коректної роботи `retryLastCheck()`.

### 6) [MODIFY] `app/src/main/java/com/example/meditrackai/ui/SymptomCheckScreen.kt`
- У `SymptomInputSection`: Додати необейовазкове поле вводу `OutlinedTextField` для кількості днів (1..30).
- У `HistoryCardItem`: Якщо `item.symptomDays != null`, відображати додатковий рядок або бейдж: `"Тривалість: X дн."`.

---

## 3. Список файлів, які НЕ будуть змінюватись
- `app/src/main/java/com/example/meditrackai/network/ApiClient.kt`
- `app/src/main/java/com/example/meditrackai/ui/MedicationSection.kt`
- `app/src/main/java/com/example/meditrackai/ui/PhotoStorage.kt`
- `app/src/main/java/com/example/meditrackai/ui/DebugMenu.kt`
- `app/src/main/java/com/example/meditrackai/ui/DebugAlarmReceiver.kt`
- `app/src/main/AndroidManifest.xml`
- `build.gradle.kts`
- `backend/requirements.txt`
- Усі файли тем у `ui/theme/`

---

## 4. Ризики та зворотна сумісність
- **Зворотна сумісність API:** Якщо мобільний додаток надсилає запит без поля `symptom_days` (або `null`), Pydantic-модель на бекенді завдяки `default=None` приймає запит без помилок `422 Unprocessable Entity`.
- **Зворотна сумісність історії:** Старі записи у `SharedPreferences` не містять `symptomDays`. Kotlin-модель `symptomDays: Int? = null` десеріалізує старі записи з Gson без помилок (значення дорівнює `null`).

---

## 5. Як перевірити вручну
1. **Тест 1 (старий сценарій):** Ввести опис "болить голова" без вказання днів ➔ Надіслати запит ➔ Відповідь повертається успішно, в історії запис відображається без позначки днів.
2. **Тест 2 (новий сценарій):** Ввести опис "болить голова" та вказати `10` днів ➔ Надіслати запит ➔ AI враховує тривалість (підвищує терміновість) ➔ В історії показується "Тривалість: 10 дн.".
3. **Тест 3 (межі 1..30):** Ввести 0 або 35 ➔ Локальна валідація блокує відправку або бекенд повертає `422 Bad Request`.

---

## 6. Джерела та Припущення

### Джерела тверджень у коді:
- `SymptomRequest` у Python: `backend/models.py:8-15`.
- `SymptomRequest` у Kotlin: `app/src/main/java/com/example/meditrackai/network/SymptomModels.kt:6-8`.
- Обробник маршруту `/analyze-symptom`: `backend/main.py:38-57`.
- Формування промпту та виклик Groq API: `backend/ai_service.py:41-118`.
- Збереження історії у `SharedPreferences` через Gson: `app/src/main/java/com/example/meditrackai/ui/SymptomViewModel.kt:151-180`.
- Форма симптомів та картка історії: `app/src/main/java/com/example/meditrackai/ui/SymptomCheckScreen.kt:125-165, 235-290`.

### Окремий список припущень (Assumptions):
1. **Припущення 1:** Оскільки папка `docs/` перейменована на `docs_hidden/`, специфікації з `docs/` недоступні за стандартним шляхом `docs/`, тому весь аналіз побудовано виключно на основі фактичного коду проєкту.
2. **Припущення 2:** Назва поля у JSON між Kotlin та Python узгоджена як `symptom_days`.
3. **Припущення 3:** AI модель `qwen/qwen3.8-27b` коректно зреагує на інструкцію про вплив тривалості симптому від 7 днів на підвищення рівня терміновості.
