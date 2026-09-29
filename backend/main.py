"""
MediTrack AI — backend (Лабораторна робота 2)

Запуск:
    python main.py
    або
    python -m uvicorn main:app --host 0.0.0.0 --port 8000

Swagger UI для ручної перевірки: http://127.0.0.1:8000/docs
"""

import logging
import uvicorn

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse

load_dotenv()  # підхоплює GROQ_API_KEY з файлу .env, якщо він є

from models import SymptomRequest, SymptomResponse
from ai_service import categorize_symptom, AIServiceError

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("meditrack.api")

app = FastAPI(
    title="MediTrack AI backend",
    description="Мінімальний backend для категоризації неідентифікаційного опису симптомів.",
    version="0.1.0",
)


@app.get("/health")
def health_check():
    """Перевірка, що сервіс запущений і відповідає."""
    return {"status": "ok"}


@app.post("/analyze-symptom", response_model=SymptomResponse)
def analyze_symptom(request: SymptomRequest):
    """
    Основний сценарій лабораторної роботи:
    приймає опис симптому -> валідує -> викликає AI -> повертає категорію.
    """
    description = request.description.strip()

    if not description:
        # Pydantic вже блокує порожній рядок через min_length,
        # але додаткова перевірка на самі пробіли — про всяк випадок.
        raise HTTPException(status_code=400, detail="Опис не може бути порожнім")

    try:
        result = categorize_symptom(description)
    except AIServiceError as exc:
        # Контрольована відповідь без технічних деталей (stack trace тощо).
        logger.error("AI service error: %s", exc)
        raise HTTPException(status_code=503, detail=str(exc))

    return SymptomResponse(
        category=result["category"],
        explanation=result["explanation"],
        urgency=result["urgency"],
    )


@app.exception_handler(Exception)
async def unhandled_exception_handler(request, exc):
    """
    Останній рубіж захисту: будь-яка неочікувана помилка повертається
    користувачу як загальне повідомлення, а не як внутрішній stack trace.
    """
    logger.exception("Unhandled exception: %s", exc)
    return JSONResponse(
        status_code=500,
        content={"detail": "Внутрішня помилка сервера. Спробуйте пізніше."},
    )


if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8000)
