# План реалізації: Додавання тривалості симптому (у днях)

## 1. Результат перевірки наявності docs/
Папка `docs/` **знайдена** у репозиторії (`C:/Users/Nazar/AndroidStudioProjects/MediTrackAI/docs`).

Прочитані файли з `docs/`, які стосуються цієї функції:
- `docs/api-contract.md` (контракт API запиту та відповіді)
- `docs/requirements.md` (вимоги та функціональні критерії)
- `docs/ai-component.md` (конфігурація AI та системний промпт)
- `docs/architecture.md` (опис архітектури mobile -> backend -> AI)

---

## 2. План реалізації

### Покроковий план
1. **Backend (FastAPI & Pydantic):**
   - Оновити Pydantic-модель `SymptomRequest` у `backend/models.py`: додати необов'язкове поле `duration_days: Optional[int] = Field(default=None, ge=1, le=30, description="Тривалість симптому у днях (1..30)")`.
   - Оновити `backend/ai_service.py`:
     - Модифікувати сигнатуру `categorize_symptom(description: String, duration_days: Optional[int] = None)`.
     - Оновити `SYSTEM_PROMPT` або динамічний текст повідомлення користувача: якщо `duration_days` вказано, додавати інформацію для AI (наприклад, `"Симптом турбує користувача 10 днів."`). Додати в системний промпт вказівку: "Тривалий симптом (понад 7–14 днів) може підвищувати рівень терміновості з 'звичайне' до 'увага' або 'терміново'".
   - Оновити ендпоінт `POST /analyze-symptom` у `backend/main.py`: передавати `request.duration_days` у функцію `categorize_symptom()`.

2. **Android Client (Jetpack Compose & Retrofit):**
   - Оновити DTO `SymptomRequest` у `app/src/main/java/com/example/meditrackai/network/SymptomModels.kt`: додати `val durationDays: Int? = null` (з анотацією `@SerializedName("duration_days")` або підтримкою Gson).
   - Оновити `SymptomHistoryItem` у `SymptomModels.kt`: додати `val durationDays: Int? = null`.
   - Оновити `SymptomViewModel.kt`:
     - Розширити метод `checkSymptom(description: String, durationDays: Int? = null)`.
     - При формуванні `SymptomHistoryItem` передавати значення `durationDays` та зберігати його в історії.
   - Оновити Compose UI у `app/src/main/java/com/example/meditrackai/ui/SymptomCheckScreen.kt`:
     - У `SymptomInputSection`: додати додаткове текстове поле для вводу кількості днів (опціональне, тільки цифри від 1 до 30).
     - У `HistoryCardItem`: додано відображення тривалості (наприклад, `"Тривалість: 5 дн."`), якщо `durationDays != null`.

3. **Забезпечення зворотної сумісності:**
   - Запити без поля `duration_days` отримують `duration_days = None` на backend та обробляються за стандартним сценарієм.
   - У мобільному застосунку старі збережені у SharedPreferences записи історії десеріалізуються Gson з `durationDays = null` без помилок.

### Файли, що зміняться
- `backend/models.py`: додавання поля `duration_days` до `SymptomRequest`.
- `backend/ai_service.py`: врахування тривалості симптому при формуванні промпту для Groq LLM.
- `backend/main.py`: передача `request.duration_days` до AI-сервісу.
- `app/src/main/java/com/example/meditrackai/network/SymptomModels.kt`: додавання `durationDays` у models `SymptomRequest` та `SymptomHistoryItem`.
- `app/src/main/java/com/example/meditrackai/ui/SymptomViewModel.kt`: підтримка `durationDays` у бізнес-логіці та збереженні історії.
- `app/src/main/java/com/example/meditrackai/ui/SymptomCheckScreen.kt`: відображення поля вводу в UI та відображення тривалості в картці історії.

### Файли, які НЕ чіпатимеш
- `docs/*` (будь-які документаційні файли згідно із забороною)
- `backend/.env`
- `app/src/main/java/com/example/meditrackai/network/ApiClient.kt`
- `app/src/main/java/com/example/meditrackai/MainActivity.kt`
- `app/src/main/java/com/example/meditrackai/ui/MedicationSection.kt`
- `app/src/main/java/com/example/meditrackai/ui/DebugMenu.kt`
- `app/src/main/java/com/example/meditrackai/ui/PhotoStorage.kt`
- `app/src/main/java/com/example/meditrackai/ui/theme/*`

### Ризики
1. **Зворотна сумісність API:**
   - Якщо зробити `duration_days` обов'язковим, старі версії Android-клієнта чи прямі API запити отримуватимуть помилку `422 Unprocessable Entity`. *Мітигація:* зробити поле `Optional[int] = Field(default=None)`.
2. **Валідація меж (1..30):**
   - Користувач може ввести некоректне значення (наприклад, 0, -5, 100 або літери). *Мітигація:* Pydantic `ge=1, le=30` на бекенді та відсікання некоректного вводу в Android UI.
3. **Збережена історія (Gson & SharedPreferences):**
   - Старий заклішований JSON в SharedPreferences пристрою не містить `durationDays`. *Мітигація:* використовувати дефолтний `null` в Kotlin data class.
4. **Галюцинації AI:**
   - Вказування тривалості може надмірно підвищувати терміновість для безпечних симптомів. *Мітигація:* чітке фокусування у системному промпті AI.

### Як перевірити вручну
1. **Запит без `duration_days` (старий клієнт/потік):**
   - Виконати `POST /analyze-symptom` з тілом `{"description": "болить голова"}`.
   - Очікується `200 OK` і коректна відповідь AI без збоїв.
2. **Запит з valid `duration_days` (1..30):**
   - Виконати `POST /analyze-symptom` з тілом `{"description": "болить голова", "duration_days": 14}`.
   - Перевірити, що AI враховує тривалість і може підвищити терміновість до `увага` чи `терміново`.
3. **Перевірка межевих значень (0, 31):**
   - Надіслати `{"description": "кашель", "duration_days": 0}` або `31`.
   - Переконатися, що backend повертає `422 Unprocessable Entity`.
4. **Перевірка Android UI та історії:**
   - Запустити додаток, ввести симптом "біль у спині", ввести тривалість "10" днів.
   - Натиснути "Перевірити", переконатися у відображенні результату та наявності "10 дн." у картці історії.
   - Очистити поле днів, відправити новий запит — переконатися, що у картці історії тривалість не відображається.
   - Перезапустити додаток і перевірити збереження історії між сесіями.

---

## 3. Джерела тверджень та список припущень

### Джерела тверджень про проєкт:
1. `backend/models.py:8-15`: `SymptomRequest` містить лише поле `description: str = Field(..., min_length=3, max_length=500)`.
2. `backend/models.py:18-29`: `SymptomResponse` повертає `category`, `explanation`, `urgency`, `disclaimer`.
3. `backend/main.py:38-59`: Ендпоінт `POST /analyze-symptom` приймає `SymptomRequest`, передає `request.description` у `categorize_symptom(description)`.
4. `backend/ai_service.py:36-47`: `SYSTEM_PROMPT` визначає правила оцінки `urgency` ("терміново", "увага", "звичайне").
5. `backend/ai_service.py:58-109`: Функція `categorize_symptom(description: str)` приймає один параметр `description: str` та формує `messages` для Groq API.
6. `app/src/main/java/com/example/meditrackai/network/SymptomModels.kt:6-8`: Data-клас `SymptomRequest(val description: String)`.
7. `app/src/main/java/com/example/meditrackai/network/SymptomModels.kt:18-23`: Data-клас `SymptomHistoryItem(val timestamp: String, val description: String, val response: SymptomResponse)`.
8. `app/src/main/java/com/example/meditrackai/network/ApiClient.kt:13-16`: `MediTrackApi` має метод `analyzeSymptom(@Body request: SymptomRequest)`.
9. `app/src/main/java/com/example/meditrackai/ui/SymptomViewModel.kt:152-192`: Метод `checkSymptom(description: String)` приймає тільки `description`.
10. `app/src/main/java/com/example/meditrackai/ui/SymptomViewModel.kt:45-75`: Збереження історії у SharedPreferences ("meditrack_prefs", ключ "symptom_history") у вигляді JSON-масиву `SymptomHistoryItem`.
11. `app/src/main/java/com/example/meditrackai/ui/SymptomCheckScreen.kt:169-217`: `SymptomInputSection` відображає лише одне поле `OutlinedTextField` для опису симптому.
12. `app/src/main/java/com/example/meditrackai/ui/SymptomCheckScreen.kt:319-383`: `HistoryCardItem` показує опис, час, категорію, рівень терміновості та пояснення, але не відображає тривалість симптому.
13. `docs/api-contract.md:21-25`: Поточний контракт API передбачає тіло запиту `{"description": "болить голова і нудить"}`.
14. `docs/requirements.md:19-21`: У вимогах описана функціональність терміновості та історії.
15. `docs/ai-component.md:8-12`: AI компонент використовує Groq Cloud API з моделлю `qwen/qwen3.8-27b`.
16. `docs/architecture.md:5-27`: Архітектура описує потік Mobile (Compose -> ViewModel -> Retrofit) -> Backend (FastAPI -> Pydantic -> ai_service) -> Groq API.

### Список припущень (Assumptions):
1. **Припущення:** Поле `duration_days` на бекенді має бути оголошене як `Optional[int] = Field(default=None, ge=1, le=30)`, щоб забезпечити повну зворотну сумісність.
2. **Припущення:** Передача тривалості симптому у промпт AI (наприклад, "Симптом триває 14 днів") може коригувати `urgency` (наприклад, тривалий кашель чи біль підвищує терміновість з "звичайне" на "увага" або "терміново").
3. **Припущення:** У мобільному Compose UI поле вводу тривалості повинно бути опціональним текстовим/числовим полем у межах 1..30.
4. **Припущення:** У Kotlin data-класах `SymptomRequest` та `SymptomHistoryItem` поле `durationDays: Int? = null` з дефолтним значенням `null` дозволить Gson прозоро десеріалізувати збережені раніше записи історії без втрати чи пошкодження даних.
5. **Припущення:** Рендеринг у картці історії повинен показувати тривалість у вигляді зручного тексту (наприклад, "Тривалість: 5 дн."), тільки якщо `durationDays != null`.
