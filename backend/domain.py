"""Application contracts: no FastAPI, Pydantic, requests or environment access."""

from dataclasses import dataclass
from typing import Protocol, runtime_checkable


MEDICAL_DISCLAIMER = "Це не медичний діагноз. У разі проблем зі здоров'ям зверніться до лікаря."


@dataclass(frozen=True)
class ConversationMessage:
    role: str
    content: str


@dataclass(frozen=True)
class AnalysisInput:
    description: str
    context: tuple[ConversationMessage, ...] = ()
    duration_days: int | None = None
    intensity: int | None = None


@dataclass(frozen=True)
class AnalysisResult:
    category: str
    explanation: str
    urgency: str
    follow_up_question: str | None = None
    context_used: bool = False
    disclaimer: str = MEDICAL_DISCLAIMER
    details_used: bool = False
    has_usable_result: bool = True


class AnalysisError(Exception):
    public_message = "AI-сервіс тимчасово недоступний"


class InvalidInputError(AnalysisError):
    public_message = "Перевірте опис та контекст уточнення"


class EmptyDescriptionError(InvalidInputError):
    public_message = "Опис не може бути порожнім"


class ProviderUnavailableError(AnalysisError):
    pass


class ProviderTimeoutError(ProviderUnavailableError):
    public_message = "AI-сервіс не відповів вчасно, спробуйте ще раз"


class InvalidProviderResponseError(ProviderUnavailableError):
    public_message = "AI повернув некоректну відповідь"


class ConfigurationError(ProviderUnavailableError):
    pass


@runtime_checkable
class AIProvider(Protocol):
    def generate(self, request: AnalysisInput) -> AnalysisResult: ...
