"""HTTP-only correlation IDs; never log bodies, keys or provider exception text."""

from contextvars import ContextVar
import logging
from uuid import uuid4

from starlette.datastructures import MutableHeaders
from starlette.types import ASGIApp, Message, Receive, Scope, Send

_request_id: ContextVar[str] = ContextVar("request_id", default="-")
logger = logging.getLogger("meditrack.http")


def current_request_id() -> str:
    return _request_id.get()


class RequestContextMiddleware:
    def __init__(self, app: ASGIApp):
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send):
        if scope["type"] != "http":
            return await self.app(scope, receive, send)
        # Generate on the server; do not reflect untrusted client strings into logs.
        request_id = uuid4().hex
        scope.setdefault("state", {})["request_id"] = request_id
        token = _request_id.set(request_id)
        status = 500

        async def send_with_id(message: Message):
            nonlocal status
            if message["type"] == "http.response.start":
                status = message["status"]
                MutableHeaders(scope=message)["X-Request-ID"] = request_id
            await send(message)

        try:
            await self.app(scope, receive, send_with_id)
        except Exception:
            logger.error("request_failed request_id=%s method=%s status=500", request_id, scope["method"])
            raise
        else:
            logger.info("request_finished request_id=%s method=%s status=%s", request_id, scope["method"], status)
        finally:
            _request_id.reset(token)
