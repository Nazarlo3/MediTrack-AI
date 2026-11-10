import json
from pathlib import Path
import sqlite3
import sys
import tempfile
import time
import unittest
from contextlib import closing
from unittest.mock import patch
from uuid import uuid4

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fastapi.testclient import TestClient
from collection_repository import CollectionRepository, RETENTION_SECONDS
from collection_service import CollectionService
from config import Settings, get_settings
from dependencies import get_provider, get_collection_service
from fake_provider import FakeProvider
from main import app


class CollectionTests(unittest.TestCase):
    def setUp(self):
        app.state.api_protection.reset_window()
        self.temp = tempfile.TemporaryDirectory()
        self.repo = CollectionRepository(str(Path(self.temp.name)/'data.sqlite3'))
        self.service = CollectionService(self.repo, 'synthetic-model')
        self.previous = app.dependency_overrides.copy()
        self.provider = FakeProvider()
        app.dependency_overrides[get_provider] = lambda: self.provider
        app.dependency_overrides[get_collection_service] = lambda: self.service
        app.dependency_overrides[get_settings] = lambda: Settings(collection_admin_token='a'*32)
        self.client = TestClient(app, raise_server_exceptions=False)
        self.token = 'b'*64
        self.admin = {'Authorization': 'Bearer '+'a'*32}

    def tearDown(self):
        self.client.close()
        app.dependency_overrides.clear()
        app.dependency_overrides.update(self.previous)
        self.temp.cleanup()

    def send(self, token=None, request_id=None, consent=True, **extra):
        payload = {'description': 'Синтетичний опис', **extra}
        if consent:
            payload['collection'] = {'consent_version': 1, 'request_id': str(request_id or uuid4())}
        return self.client.post('/analyze-symptom', json=payload,
                                headers={'X-Collection-Token': token or self.token})

    def test_legacy_and_declined_do_not_store(self):
        r = self.send(consent=False)
        self.assertEqual(200, r.status_code)
        self.assertEqual('not_requested', r.headers['X-Collection-Status'])
        self.assertEqual([], self.repo.records())

    def test_consent_records_real_provider_result_and_parameters_not_context_or_token(self):
        r = self.send(duration_days=2, intensity=5,
                      context=[{'role':'user','content':'Private previous turn'},
                               {'role':'assistant','content':'Previous answer'}])
        self.assertEqual('saved', r.headers.get('X-Collection-Status'))
        row = self.repo.records()[0]
        self.assertEqual(r.json(), row['response'])
        self.assertEqual(2, row['request']['duration_days'])
        self.assertEqual('synthetic-model', row['model'])
        self.assertNotIn('context', row['request'])
        self.assertNotIn(self.token, Path(self.repo.path).read_bytes().decode('latin1'))

    def test_duplicate_event_is_not_counted_twice(self):
        id = uuid4()
        self.send(request_id=id); self.send(request_id=id)
        self.assertEqual(1, self.repo.stats()['total'])

    def test_delete_scopes_owner_and_blocks_late_writes(self):
        self.send(); self.send(token='c'*64)
        r = self.client.delete('/collection/mine', headers={'X-Collection-Token': self.token})
        self.assertEqual({'deleted':1}, r.json())
        self.assertEqual('revoked', self.send().headers.get('X-Collection-Status'))
        self.assertEqual(1, self.repo.stats()['total'])
        self.assertEqual(401, self.client.delete('/collection/mine').status_code)

    def test_read_and_statistics_require_admin_and_pagination_is_bounded(self):
        self.send()
        for path in ['/collection/admin/records','/collection/admin/stats']:
            self.assertEqual(401, self.client.get(path).status_code)
            self.assertEqual(401, self.client.get(path, headers={'Authorization':'Bearer '+self.token}).status_code)
            self.assertEqual(200, self.client.get(path, headers=self.admin).status_code)
        r = self.client.get('/collection/admin/stats', headers=self.admin)
        self.assertEqual(1, r.json()['total'])
        self.assertIsNotNone(r.json()['average_elapsed_ms'])
        self.assertEqual('no-store', r.headers['Cache-Control'])
        self.assertEqual(422, self.client.get('/collection/admin/records?limit=101', headers=self.admin).status_code)

    def test_expiry_applies_to_reads_and_statistics(self):
        self.send()
        with closing(sqlite3.connect(self.repo.path)) as db:
            with db:
                db.execute('UPDATE records SET created_at=?', (time.time()-RETENTION_SECONDS-1,))
        self.assertEqual(0, self.repo.stats()['total'])
        self.assertEqual([], self.repo.records())

    def test_disabled_bad_capability_and_write_failure_do_not_break_ai(self):
        self.assertEqual('rejected', self.send(token='invalid').headers.get('X-Collection-Status'))
        self.service.repository = None
        self.assertEqual('disabled', self.send().headers.get('X-Collection-Status'))
        self.service.repository = self.repo
        with patch.object(self.repo, 'save', side_effect=OSError('private text')):
            with self.assertLogs('meditrack.collection') as log:
                r = self.send()
            self.assertNotIn('private text', str(log.output))
        self.assertEqual(200, r.status_code)
        self.assertEqual('unavailable', r.headers.get('X-Collection-Status'))

    def test_failed_ai_and_invalid_consent_do_not_create_records(self):
        self.provider.fail = True
        self.assertEqual(503, self.send().status_code)
        self.assertEqual([], self.repo.records())
        r = self.client.post('/analyze-symptom', json={'description':'synthetic', 'collection':{'consent_version':3,'request_id':str(uuid4())}})
        self.assertEqual(422, r.status_code)

    def test_no_result_is_stored_for_quality_review(self):
        from domain import AnalysisResult
        class NoResultProvider:
            def generate(self, request):
                return AnalysisResult('інше','Бракує даних','звичайне',follow_up_question='Уточніть?',has_usable_result=False)
        app.dependency_overrides[get_provider] = lambda: NoResultProvider()
        self.send()
        self.assertFalse(self.repo.records()[0]['response']['has_usable_result'])
