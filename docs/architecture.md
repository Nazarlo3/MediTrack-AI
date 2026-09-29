# Architecture — MediTrack AI

## Компоненти системи

```text
[ Android App (Compose UI + ViewModel + Retrofit) ]
                       │
                       │ HTTP POST /analyze-symptom (JSON)
                       ▼
[ FastAPI Backend on Render.com (main.py + models.py + ai_service.py) ]
                       │
                       │ HTTPS REST API
                       ▼
[ Groq Cloud API (Qwen 27B LLM) ]
```

### 1. Mobile (Android)
- **`SymptomCheckScreen.kt`**: Головний Composable екран користувача, показує стан вводу, завантаження, результат або помилку.
- **`SymptomViewModel.kt`**: Бізнес-логіка, збереження стану екрана (`StateFlow`), обробка винятків мережі.
- **`ApiClient.kt`**: Налаштування Retrofit та OkHttp клієнта з таймаутом 60 секунд.
- **`SymptomModels.kt`**: Data-класи запиту `SymptomRequest` та відповіді `SymptomResponse`.

### 2. Backend (FastAPI)
- **`main.py`**: Точка входу FastAPI сервера з ендпоінтами `/health` та `/analyze-symptom`.
- **`models.py`**: Pydantic-моделі для валідації вхідних даних та форматування відповіді.
- **`ai_service.py`**: Модуль взаємодії з Groq API, завантаження `.env` ключів, системні промпти та відловлювання помилок.

### 3. AI Component
- **Groq Cloud API** з моделлю `qwen/qwen3.8-27b`, яка повертає гарантовану JSON-структуру `{"category": "...", "explanation": "..."}`.
