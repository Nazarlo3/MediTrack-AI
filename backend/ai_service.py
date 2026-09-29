"""
AI-компонент MediTrack AI.

Викликає безкоштовний зовнішній AI API (Groq — https://console.groq.com),
щоб категоризувати неідентифікаційний опис симптому користувача.

ВАЖЛИВО: цей компонент НЕ ставить медичний діагноз. Він лише відносить
текст до однієї із загальних категорій (наприклад "неврологічне",
"шлунково-кишкове" тощо), щоб продемонструвати повний потік
mobile -> backend -> AI -> mobile.
"""

import os
import json
import logging
from dotenv import load_dotenv
import requests

# Завантажуємо .env безпосередньо з директорії біля ai_service.py
load_dotenv(os.path.join(os.path.dirname(__file__), ".env"))

logger = logging.getLogger("meditrack.ai")

GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions"
GROQ_MODEL = "qwen/qwen3.8-27b"

# Дозволені категорії — обмежуємо AI конкретним списком,
# щоб відповідь була передбачуваною і стабільною для мобільного інтерфейсу.
ALLOWED_CATEGORIES = [
    "неврологічне",
    "шлунково-кишкове",
    "респіраторне",
    "опорно-руховий апарат",
    "шкірне",
    "загальне нездужання",
    "інше",
]

ALLOWED_URGENCIES = ["звичайне", "увага", "терміново"]

SYSTEM_PROMPT = (
    "Ти — асистент, який лише КАТЕГОРИЗУЄ загальний опис самопочуття людини. "
    "Ти НЕ ставиш діагноз і не даєш медичних порад. "
    f"Обери рівно одну категорію зі списку: {', '.join(ALLOWED_CATEGORIES)}. "
    "Визнач рівень терміновості з трьох варіантів: 'звичайне' (плановий прийом/самопочуття в нормі), "
    "'увага' (потрібна консультація найближчими днями) або 'терміново' (сильний біль чи небезпечний стан). "
    "Дай коротке (1-2 речення) нейтральне пояснення, чому обрано цю категорію. "
    "Відповідай СУВОРО у форматі JSON без жодного додаткового тексту: "
    '{"category": "<категорія>", "explanation": "<коротке пояснення>", "urgency": "<звичайне|увага|терміново>"}'
)


class AIServiceError(Exception):
    """Піднімається, коли зовнішній AI API недоступний або повернув помилку."""


def _get_api_key() -> str:
    api_key = os.environ.get("GROQ_API_KEY")
    if not api_key:
        logger.error("GROQ_API_KEY не встановлено в environment variables")
        raise AIServiceError("AI-сервіс тимчасово недоступний")
    return api_key


def categorize_symptom(description: str) -> dict:
    """
    Надсилає опис симптому до Groq API та повертає категорію + пояснення.

    Повертає dict: {"category": str, "explanation": str}
    Кидає AIServiceError, якщо AI недоступний або повернув некоректну відповідь.
    """
    api_key = _get_api_key()

    payload = {
        "model": GROQ_MODEL,
        "response_format": {"type": "json_object"},
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": description},
        ],
        "temperature": 0.2,
        "max_tokens": 200,
    }

    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }

    try:
        response = requests.post(GROQ_API_URL, headers=headers, json=payload, timeout=15)
        response.raise_for_status()
    except requests.exceptions.Timeout:
        logger.error("Groq API: timeout")
        raise AIServiceError("AI-сервіс не відповів вчасно, спробуйте ще раз")
    except requests.exceptions.RequestException as exc:
        logger.error("Groq API request failed: %s", exc)
        raise AIServiceError("AI-сервіс тимчасово недоступний")

    try:
        raw_content = response.json()["choices"][0]["message"]["content"].strip()
        parsed = json.loads(raw_content)
        category = parsed.get("category", "інше").strip().lower()
        explanation = parsed.get("explanation", "").strip()
        urgency = parsed.get("urgency", "звичайне").strip().lower()
    except (KeyError, IndexError, json.JSONDecodeError, AttributeError) as exc:
        logger.error("Groq API: неочікуваний формат відповіді (%s)", exc)
        raise AIServiceError("AI повернув некоректну відповідь")

    if category not in ALLOWED_CATEGORIES:
        logger.warning("Groq повернув категорію поза списком: %s", category)
        category = "інше"

    if urgency not in ALLOWED_URGENCIES:
        logger.warning("Groq повернув терміновість поза списком: %s", urgency)
        urgency = "звичайне"

    return {"category": category, "explanation": explanation, "urgency": urgency}
