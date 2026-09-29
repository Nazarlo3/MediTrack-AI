# API Contract — MediTrack AI

## Base URL
Production: `https://meditrack-ai-xbk5.onrender.com/`

---

## 1. Health Check
Перевірка доступності сервера.

- **Method:** `GET`
- **Path:** `/health`
- **Response `200 OK`:**
  ```json
  {
    "status": "ok"
  }
  ```

---

## 2. Analyze Symptom
Основний ендпоінт аналізу та категоризації симптому.

- **Method:** `POST`
- **Path:** `/analyze-symptom`
- **Headers:** `Content-Type: application/json`

### Request Body
```json
{
  "description": "болить голова і нудить"
}
```

### Success Response (`200 OK`)
```json
{
  "category": "загальне нездужання",
  "explanation": "Поєднання головного болю та нудоти є неспецифічними симптомами, які часто вказують на загальний стан нездужання.",
  "urgency": "звичайне",
  "disclaimer": "Це не медичний діагноз. У разі проблем зі здоров'ям зверніться до лікаря."
}
```

### Error Responses

#### `400 Bad Request` (Валідаційна помилка вводу)
```json
{
  "detail": "Опис не може бути порожнім"
}
```

#### `503 Service Unavailable` (Збій AI-сервісу)
```json
{
  "detail": "AI-сервіс тимчасово недоступний"
}
```
