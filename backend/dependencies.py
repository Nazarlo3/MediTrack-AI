"""Composition root; tests override get_provider without patching the router."""

from typing import Annotated

from fastapi import Depends

from ai_service import GroqProvider
from analysis_service import AnalysisService
from config import Settings, get_settings
from domain import AIProvider
from fake_provider import FakeProvider


def get_provider(settings: Annotated[Settings, Depends(get_settings)]) -> AIProvider:
    if settings.provider == "fake":
        return FakeProvider(fail=settings.fake_error)
    return GroqProvider(settings)


def get_analysis_service(provider: Annotated[AIProvider, Depends(get_provider)]) -> AnalysisService:
    return AnalysisService(provider)
