"""Composition root; tests override get_provider without patching the router."""

from typing import Annotated

from fastapi import Depends

from ai_service import GroqProvider
from analysis_service import AnalysisService
from config import Settings, get_settings
from domain import AIProvider
from fake_provider import FakeProvider
from collection_service import CollectionService
from collection_repository import CollectionRepository
from functools import lru_cache
import logging


def get_provider(settings: Annotated[Settings, Depends(get_settings)]) -> AIProvider:
    if settings.provider == "fake":
        return FakeProvider(fail=settings.fake_error)
    return GroqProvider(settings)


def get_analysis_service(provider: Annotated[AIProvider, Depends(get_provider)]) -> AnalysisService:
    return AnalysisService(provider)


def get_collection_service(settings: Annotated[Settings, Depends(get_settings)]) -> CollectionService:
    return collection_service_for(settings)


@lru_cache(maxsize=4)
def collection_service_for(settings: Settings):
    repo = get_collection_repository(settings)
    dispatcher = None
    unavailable = False
    if settings.google_sheets_id and settings.collection_outbox_path:
        from collection_dispatcher import CollectionDispatcher
        try:
            dispatcher = CollectionDispatcher(repo, path=settings.collection_outbox_path)
        except Exception:
            logging.getLogger('meditrack.collection').warning('collection_outbox_unavailable')
            unavailable = True
    return CollectionService(repo, settings.model if settings.provider == 'groq' else 'fake:' + settings.model,
                             dispatcher, unavailable=unavailable)


@lru_cache(maxsize=4)
def get_collection_repository(settings: Settings):
    if settings.google_sheets_id:
        from sheets_collection_repository import SheetsCollectionRepository
        from sheets_transport import SheetsTransport
        return SheetsCollectionRepository(SheetsTransport(settings.google_sheets_id, settings.google_credentials_file,
                                                         settings.google_http_transport))
    return CollectionRepository(settings.collection_db_path) if settings.collection_db_path else None
