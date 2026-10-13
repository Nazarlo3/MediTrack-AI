# Feature 1 — Історія змін (Changelog)

Повний деталізований перелік змін, внесених під час реалізації Feature 1.

---

## ⚙️ Серверна частина (Backend)
- **`backend/models.py`**:
  - Додано нове поле `urgency: str = Field(default="звичайне")` до моделі `SymptomResponse`.
- **`backend/ai_service.py`**:
  - Оновлено `SYSTEM_PROMPT` із суворими правилами для класифікації терміновості:
    - `терміново` — травми голови/хребта, колоті рани (цвяхи), біль у серці, висока температура, раптові травми.
    - `увага` — застуда, кашель, помірний біль, висип.
    - `звичайне` — легке тимчасове нездужання.
  - Додано обробку та нормалізацію рядків терміновості.
- **`backend/main.py`**:
  - Передано `urgency` у відповідь REST API.

---

## 📱 Мобільна частина (Android Client)
- **`SymptomModels.kt`**:
  - Додано поле `val urgency: String? = "звичайне"` до `SymptomResponse` із захистом від `null`.
  - Створено дата-клас `SymptomHistoryItem(val timestamp: String, val description: String, val response: SymptomResponse)`.
- **`SymptomViewModel.kt`**:
  - Переведено на `AndroidViewModel` для підтримки `SharedPreferences`.
  - Реалізовано завантаження історії з диска при старті (`loadHistory`) та синхронне збереження через `.commit()` після кожного успішного запиту (`saveHistory`).
  - Додано метод `clearHistory()` для повного очищення сховища.
- **`SymptomCheckScreen.kt`**:
  - Модифіковано `SuccessCard`: додано кольоровий значок статусу (зелений / помаранчевий / червоний).
  - Створено компонент `HistorySection`: виводить список перевірок із часом, текстом, категорією, кольоровим статусом та розгорнутим поясненням від ШІ.
  - Додано індикатор із текстовою підказкою про прокидання сервера у стані `Loading`.
- **`ApiClient.kt`**:
  - Збільшено таймаути OkHttp до 60 секунд (`connectTimeout` / `readTimeout`).
