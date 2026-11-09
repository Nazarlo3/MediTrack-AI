"""The only environment/.env reader; secrets never appear in Settings repr."""

from dataclasses import dataclass, field
from functools import lru_cache
import math
import os
from pathlib import Path

from dotenv import load_dotenv

from domain import ConfigurationError


@dataclass(frozen=True)
class Settings:
    provider: str = "groq"
    api_key: str = field(default="", repr=False)
    model: str = "qwen/qwen3.8-27b"
    timeout_seconds: float = 15.0
    fake_error: bool = False
    collection_db_path: str = ''
    collection_admin_token: str = field(default='', repr=False)
    google_sheets_id: str = ''
    google_credentials_file: str = ''
    google_http_transport: str = 'requests'

    def __post_init__(self):
        if self.provider not in {"groq", "fake"} or not self.model.strip():
            raise ConfigurationError()
        if not math.isfinite(self.timeout_seconds) or self.timeout_seconds <= 0:
            raise ConfigurationError()
        if self.google_http_transport not in {'requests', 'curl'}:
            raise ConfigurationError()


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    load_dotenv(Path(__file__).with_name(".env"), override=False)
    try:
        fake_error = os.getenv("FAKE_PROVIDER_ERROR", "false").strip().lower()
        if fake_error not in {"true", "false"}:
            raise ValueError("Invalid demo configuration")
        return Settings(
            provider=os.getenv("AI_PROVIDER", "groq").strip().lower(),
            api_key=os.getenv("GROQ_API_KEY", ""),
            model=os.getenv("GROQ_MODEL", "qwen/qwen3.8-27b"),
            timeout_seconds=float(os.getenv("AI_TIMEOUT_SECONDS", "15")),
            fake_error=fake_error == "true",
            collection_db_path=os.getenv('COLLECTION_DB_PATH', '').strip(),
            collection_admin_token=os.getenv('COLLECTION_ADMIN_TOKEN', ''),
            google_sheets_id=os.getenv('GOOGLE_SHEETS_ID', '').strip(),
            google_credentials_file=os.getenv('GOOGLE_APPLICATION_CREDENTIALS', '').strip(),
            google_http_transport=os.getenv('GOOGLE_HTTP_TRANSPORT', 'requests').strip(),
        )
    except (ValueError, TypeError) as exc:
        raise ConfigurationError() from exc
