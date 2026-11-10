"""Synthetic regressions: no network, secrets or medical records."""
from pathlib import Path
import asyncio
import sqlite3
import sys
import tempfile
from contextlib import closing
import unittest
from uuid import uuid4
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from fastapi import FastAPI
from fastapi.testclient import TestClient
from api_protection import ApiProtection, ApiProtectionMiddleware
from collection_dispatcher import CollectionDispatcher, Job
from collection_repository import CollectionRepository
from collection_service import CollectionService
from models import SymptomRequest, SymptomResponse
from sheets_collection_repository import SheetsCollectionRepository, KEYS
from sheets_transport import SheetsError
from test_sheets_collection import MemorySheets


class ProtectionTests(unittest.TestCase):
    def test_rate_expiry_global_bound_and_concurrency(self):
        now = [0]
        guard = ApiProtection(per_client=2, global_limit=3, concurrent=1, clock=lambda: now[0])
        self.assertTrue(guard.admit('one', '/analyze-symptom'))
        self.assertTrue(guard.admit('one', '/analyze-symptom'))
        self.assertFalse(guard.admit('one', '/analyze-symptom'))
        self.assertTrue(guard.admit('two', '/analyze-symptom'))
        self.assertFalse(guard.admit('three', '/analyze-symptom'))
        self.assertTrue(guard.slots.acquire(False))
        self.assertFalse(guard.slots.acquire(False))
        guard.slots.release()
        now[0] = 61
        self.assertTrue(guard.admit('one', '/analyze-symptom'))

    def test_http_rejects_oversize_and_limit_before_handler(self):
        app = FastAPI()
        app.add_middleware(ApiProtectionMiddleware, guard=ApiProtection(per_client=2), max_body=16)
        calls = []
        @app.post('/analyze-symptom')
        def analyze():
            calls.append(1)
            return {'ok': True}
        with TestClient(app) as client:
            self.assertEqual(413, client.post('/analyze-symptom', content='x'*17).status_code)
            self.assertEqual(200, client.post('/analyze-symptom', content='{}').status_code)
            r = client.post('/analyze-symptom', content='{}', headers={'X-Forwarded-For': '203.0.113.55'})
            self.assertEqual(429, r.status_code)
            self.assertEqual('60', r.headers['Retry-After'])
            self.assertEqual([1], calls)

    def test_slow_body_times_out_and_releases_admission_slot(self):
        guard = ApiProtection(concurrent=1)
        messages = []
        async def unused(scope, receive, send):
            raise AssertionError('Slow body must not reach handler')
        async def receive():
            raise TimeoutError()
        async def send(message):
            messages.append(message)
        scope = {'type': 'http', 'path': '/analyze-symptom', 'client': ('synthetic', 1)}
        asyncio.run(ApiProtectionMiddleware(unused, guard)(scope, receive, send))
        self.assertEqual(408, messages[0]['status'])
        self.assertTrue(guard.slots.acquire(False))
        guard.slots.release()


class CollectionReliabilityTests(unittest.TestCase):
    def setUp(self):
        self.api = MemorySheets()
        self.repo = SheetsCollectionRepository(self.api)
        self.queue = CollectionDispatcher(self.repo, capacity=2, start=False)
        self.service = CollectionService(self.repo, 'synthetic', self.queue)
        self.token = 'b'*64

    def tearDown(self):
        self.queue.close()

    def request(self):
        return SymptomRequest(description='synthetic', collection={'consent_version': 2, 'request_id': str(uuid4())})

    def enqueue(self, request=None):
        req = request or self.request()
        return self.service.record(req.collection, self.token, req,
                                   SymptomResponse(category='інше', explanation='synthetic'), 1)

    def test_google_failure_does_not_block_enqueue_and_retry_is_idempotent(self):
        req = self.request()
        self.api.fail = True
        self.assertEqual('queued', self.enqueue(req))
        self.assertEqual('queued', self.enqueue(req))
        self.queue.process_one(now=0)
        self.assertEqual(1, len(self.queue.jobs))
        self.api.fail = False
        self.assertFalse(self.queue.process_one(now=1))
        self.assertTrue(self.queue.process_one(now=2))
        self.assertEqual(1, self.repo.stats()['total'])

    def test_queue_capacity_and_backoff_after_three_failures(self):
        self.assertEqual('queued', self.enqueue())
        self.assertEqual('queued', self.enqueue())
        self.assertEqual('unavailable', self.enqueue())
        self.api.fail = True
        for now in [0, 2, 6]:
            self.queue.process_one(now=now)
            self.queue.process_one(now=now)
        self.assertEqual(2, len(self.queue.jobs))
        self.assertFalse(self.queue.process_one(now=7))
        self.assertTrue(all(j.due == 906 for j in self.queue.jobs.values()))

    def test_delete_during_ai_and_queued_write_cannot_resurrect_data(self):
        req = self.request()
        ticket = self.service.begin(req.collection, self.token)
        self.assertEqual(0, self.service.delete(self.token))
        self.enqueue(req)
        self.service.end(ticket)
        self.queue.process_one(now=0)
        self.assertEqual(0, self.repo.stats()['total'])
        self.assertEqual(1, len(self.api.tables[KEYS])-1)

    def test_delete_while_waiting_in_queue_revokes_late_retry(self):
        self.enqueue()
        self.service.delete(self.token)
        self.queue.process_one(now=0)
        self.assertEqual(0, self.repo.stats()['total'])

    def test_unknown_deletions_do_not_grow_google_or_sqlite_registry(self):
        for i in range(20):
            self.assertEqual(0, self.repo.delete(f'{i:064x}'))
        self.assertEqual(1, len(self.api.tables[KEYS]))
        with tempfile.TemporaryDirectory() as temp:
            repo = CollectionRepository(str(Path(temp)/'synthetic.sqlite3'))
            for i in range(20):
                self.assertEqual(0, repo.delete(f'{i:064x}'))
            with closing(sqlite3.connect(repo.path)) as db:
                self.assertEqual(0, db.execute('SELECT COUNT(*) FROM revoked').fetchone()[0])
