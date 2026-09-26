"""
Pydantic-моделі вхідних та вихідних даних для MediTrack AI backend.
"""

from pydantic import BaseModel, Field


class SymptomRequest(BaseModel):
    """Вхідні дані від мобільного застосунку."""

    description: str = Field(
        ...,
        min_length=3,
        max_length=500,
        description="Опис симптому від користувача (неідентифікаційний, без персональних даних).",
    )


class SymptomResponse(BaseModel):
    """Відповідь backend після AI-аналізу."""

    category: str = Field(..., description="Визначена категорія звернення.")
    explanation: str = Field(..., description="Коротке пояснення, чому обрано цю категорію.")
    disclaimer: str = Field(
        default="Це не медичний діагноз. У разі проблем зі здоров'ям зверніться до лікаря.",
        description="Обов'язкове застереження.",
    )


class ErrorResponse(BaseModel):
    """Контрольована відповідь про помилку (без внутрішніх деталей backend)."""

    detail: str
