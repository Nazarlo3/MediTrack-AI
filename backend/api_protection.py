"""Bounded, single-process admission control; never trust raw forwarded headers."""
from collections import deque
import asyncio
import threading
import time
from fastapi.responses import JSONResponse


class ApiProtection:
    def __init__(self, per_client=30, global_limit=120, concurrent=4, clock=time.monotonic):
        self.per_client = per_client
        self.global_limit = global_limit
        self.clock = clock
        self.lock = threading.Lock()
        self.clients = {}
        self.all = deque()
        self.slots = threading.BoundedSemaphore(concurrent)

    def reset_window(self):
        """Test fixture isolation; not exposed through HTTP."""
        with self.lock:
            self.clients.clear()
            self.all.clear()

    def admit(self, client, path):
        now = self.clock()
        with self.lock:
            self.clients = {k: v for k, v in self.clients.items() if v and v[-1] > now - 60}
            while self.all and self.all[0] <= now - 60:
                self.all.popleft()
            key = (client, path)
            times = self.clients.get(key, deque())
            while times and times[0] <= now - 60:
                times.popleft()
            limit = self.per_client if path == '/analyze-symptom' else 10
            if len(times) >= limit or len(self.all) >= self.global_limit or (key not in self.clients and len(self.clients) >= 4096):
                return False
            times.append(now)
            self.clients[key] = times
            self.all.append(now)
            return True


class ApiProtectionMiddleware:
    def __init__(self, app, guard, max_body=65536):
        self.app, self.guard, self.max_body = app, guard, max_body

    async def __call__(self, scope, receive, send):
        path = scope.get('path')
        if scope['type'] != 'http' or path not in {'/analyze-symptom', '/collection/mine'}:
            return await self.app(scope, receive, send)
        client = (scope.get('client') or ('unknown', 0))[0]
        if not self.guard.admit(client, path):
            return await self.reject(429, scope, receive, send)
        if not self.guard.slots.acquire(blocking=False):
            return await self.reject(429, scope, receive, send)
        try:
            body = bytearray()
            deadline = time.monotonic() + 10
            while True:
                try:
                    message = await asyncio.wait_for(receive(), max(0.001, deadline - time.monotonic()))
                except TimeoutError:
                    return await self.reject(408, scope, receive, send)
                if message['type'] == 'http.disconnect':
                    return
                body.extend(message.get('body', b''))
                if len(body) > self.max_body:
                    return await self.reject(413, scope, receive, send)
                if not message.get('more_body', False):
                    break
            sent = False
            async def replay():
                nonlocal sent
                if not sent:
                    sent = True
                    return {'type': 'http.request', 'body': bytes(body), 'more_body': False}
                return await receive()
            await self.app(scope, replay, send)
        finally:
            self.guard.slots.release()

    @staticmethod
    async def reject(status, scope, receive, send):
        detail = {429: 'Забагато запитів. Спробуйте через хвилину.',
                  413: 'Запит завеликий.', 408: 'Перевищено час надсилання запиту.'}[status]
        response = JSONResponse({'detail': detail}, status_code=status,
                                headers={'Cache-Control': 'no-store', **({'Retry-After': '60'} if status == 429 else {})})
        await response(scope, receive, send)
