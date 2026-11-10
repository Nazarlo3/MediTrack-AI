"""FastAPI HTTP boundary. Run from backend/: python -m uvicorn main:app."""

import logging
from time import perf_counter
import asyncio
from contextlib import asynccontextmanager, suppress
from typing import Annotated

from fastapi import Depends, FastAPI, Request, Header, Response
from fastapi.responses import JSONResponse
import uvicorn

from analysis_service import AnalysisService
from dependencies import get_analysis_service, get_collection_service, get_collection_repository
from collection_service import CollectionService
from collection_routes import router as collection_router
from domain import AnalysisError, InvalidInputError
from models import SymptomRequest, SymptomResponse
from request_context import RequestContextMiddleware, current_request_id
from config import get_settings
from collection_repository import CollectionRepository
from api_protection import ApiProtection, ApiProtectionMiddleware

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("meditrack.api")

async def purge_collection_periodically():
    while True:
        try:
            repository = get_collection_repository(get_settings())
            if repository is not None:
                await asyncio.to_thread(repository.purge)
        except Exception:
            logger.warning('collection_cleanup_failed')
        await asyncio.sleep(3600)


@asynccontextmanager
async def lifespan(app):
    task = asyncio.create_task(purge_collection_periodically())
    try:
        yield
    finally:
        task.cancel()
        with suppress(asyncio.CancelledError):
            await task
        collection = get_collection_service(get_settings())
        if collection.dispatcher is not None:
            await asyncio.to_thread(collection.dispatcher.close)


app = FastAPI(
    title="MediTrack AI backend",
    description="Мінімальний backend для категоризації неідентифікаційного опису симптомів.",
    version="0.5.1",
    lifespan=lifespan,
)
app.state.api_protection = ApiProtection()
app.add_middleware(ApiProtectionMiddleware, guard=app.state.api_protection)
app.add_middleware(RequestContextMiddleware)
app.include_router(collection_router)


@app.get("/health")
def health_check():
    return {"status": "ok"}


@app.post("/analyze-symptom", response_model=SymptomResponse)
def analyze_symptom(request: SymptomRequest, response: Response,
                    service: Annotated[AnalysisService, Depends(get_analysis_service)],
                    collection: Annotated[CollectionService, Depends(get_collection_service)],
                    x_collection_token: Annotated[str | None, Header()] = None):
    started = perf_counter()
    ticket = collection.begin(request.collection, x_collection_token)
    try:
        result = SymptomResponse.from_domain(service.analyze(request.to_domain()))
        response.headers['X-Collection-Status'] = collection.record(
            request.collection, x_collection_token, request, result, (perf_counter()-started)*1000)
    finally:
        collection.end(ticket)
    response.headers['Cache-Control'] = 'no-store'
    return result


@app.exception_handler(AnalysisError)
async def domain_error_handler(request: Request, exc: AnalysisError):
    status = 400 if isinstance(exc, InvalidInputError) else 503
    logger.warning("analysis_failed request_id=%s kind=%s", current_request_id(), type(exc).__name__)
    return JSONResponse(status_code=status, content={"detail": exc.public_message})


@app.exception_handler(Exception)
async def unhandled_exception_handler(request: Request, exc: Exception):
    # Do not publish/log exception text that may contain provider or input data.
    request_id = getattr(request.state, "request_id", "-")
    logger.error("unexpected_failure request_id=%s kind=%s", request_id, type(exc).__name__)
    # ServerErrorMiddleware is outside user middleware, so add the same ID explicitly.
    return JSONResponse(status_code=500, headers={"X-Request-ID": request_id},
                        content={"detail": "Внутрішня помилка сервера. Спробуйте пізніше."})


if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8000)
