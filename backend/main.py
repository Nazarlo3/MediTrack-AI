"""FastAPI HTTP boundary. Run from backend/: python -m uvicorn main:app."""

import logging
from typing import Annotated

from fastapi import Depends, FastAPI, Request
from fastapi.responses import JSONResponse
import uvicorn

from analysis_service import AnalysisService
from dependencies import get_analysis_service
from domain import AnalysisError, InvalidInputError
from models import SymptomRequest, SymptomResponse
from request_context import RequestContextMiddleware, current_request_id

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("meditrack.api")

app = FastAPI(
    title="MediTrack AI backend",
    description="Мінімальний backend для категоризації неідентифікаційного опису симптомів.",
    version="0.1.0",
)
app.add_middleware(RequestContextMiddleware)


@app.get("/health")
def health_check():
    return {"status": "ok"}


@app.post("/analyze-symptom", response_model=SymptomResponse)
def analyze_symptom(request: SymptomRequest, service: Annotated[AnalysisService, Depends(get_analysis_service)]):
    return SymptomResponse.from_domain(service.analyze(request.to_domain()))


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
