"""
Pydantic-моделі вхідних та вихідних даних для MediTrack AI backend.
"""

from pydantic import BaseModel, Field, field_validator
from typing import Literal

from domain import AnalysisInput, AnalysisResult, ConversationMessage, MEDICAL_DISCLAIMER


class ConversationTurn(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(..., min_length=1, max_length=1500)


class SymptomRequest(BaseModel):
    """Вхідні дані від мобільного застосунку."""

    description: str = Field(
        ...,
        min_length=3,
        max_length=500,
        description="Опис симптому від користувача (неідентифікаційний, без персональних даних).",
    )
    context: list[ConversationTurn] = Field(default_factory=list, max_length=6)

    def to_domain(self) -> AnalysisInput:
        return AnalysisInput(self.description, tuple(ConversationMessage(turn.role, turn.content) for turn in self.context))

    @field_validator("context")
    @classmethod
    def validate_context(cls, turns):
        if len(turns) % 2:
            raise ValueError("Context must contain complete user/assistant pairs")
        for index, turn in enumerate(turns):
            if turn.role != ("user" if index % 2 == 0 else "assistant") or not turn.content.strip():
                raise ValueError("Invalid conversation order or blank content")
        return turns


class SymptomResponse(BaseModel):
    """Відповідь backend після AI-аналізу."""

    category: str = Field(..., description="Визначена категорія звернення.")
    explanation: str = Field(..., description="Коротке пояснення, чому обрано цю категорію.")
    urgency: str = Field(
        default="звичайне",
        description="Рівень терміновості звернення: звичайне, увага, терміново.",
    )
    disclaimer: str = Field(
        default=MEDICAL_DISCLAIMER,
        description="Обов'язкове застереження.",
    )
    follow_up_question: str | None = Field(default=None, max_length=300)
    context_used: bool = False

    @classmethod
    def from_domain(cls, result: AnalysisResult) -> "SymptomResponse":
        return cls(category=result.category, explanation=result.explanation, urgency=result.urgency,
                   disclaimer=result.disclaimer, follow_up_question=result.follow_up_question,
                   context_used=result.context_used)


class ErrorResponse(BaseModel):
    """Контрольована відповідь про помилку (без внутрішніх деталей backend)."""

    detail: str
