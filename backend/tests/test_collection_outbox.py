"""Persistence and privacy regressions; synthetic local files, no Google calls."""
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from collection_dispatcher import CollectionDispatcher
from collection_outbox import CollectionOutbox, Job, OUTBOX_RETENTION
from collection_service import CollectionService
from dependencies import collection_service_for
from config import Settings
from sheets_collection_repository import SheetsCollectionRepository
from sheets_transport import SheetsError
from test_sheets_collection import MemorySheets
from models import SymptomRequest, SymptomResponse
from uuid import uuid4


class OutboxTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = str(Path(self.temp.name)/'outbox.sqlite3')
        self.api = MemorySheets()
        self.repo = SheetsCollectionRepository(self.api)
        self.token = 'b'*64
        self.queue = CollectionDispatcher(self.repo, start=False, path=self.path)
        self.service = CollectionService(self.repo, 'synthetic', self.queue)

    def tearDown(self):
        self.queue.close()
        self.temp.cleanup()

    def enqueue(self):
        request = SymptomRequest(description='synthetic', collection={'consent_version':2, 'request_id':str(uuid4())})
        return self.service.record(request.collection, self.token, request,
                                   SymptomResponse(category='інше', explanation='synthetic'), 1)

    def restart(self):
        self.queue.close()
        self.queue = CollectionDispatcher(self.repo, start=False, path=self.path)
        self.service = CollectionService(self.repo, 'synthetic', self.queue)

    def test_queued_survives_restart_and_contains_hash_not_capability(self):
        self.assertEqual('queued', self.enqueue())
        self.assertNotIn(self.token.encode(), Path(self.path).read_bytes())
        self.restart()
        self.assertEqual(1, self.queue.outbox.counts()['pending'])
        self.queue.process_one(now=0)
        self.assertEqual(1, self.repo.stats()['total'])
        self.assertEqual(0, self.queue.outbox.counts()['pending'])

    def test_google_committed_before_crash_replay_is_deduplicated(self):
        self.enqueue()
        save = self.repo.save_owner
        def committed_but_timed_out(*args, **kwargs):
            save(*args, **kwargs)
            raise SheetsError(status=503)
        with patch.object(self.repo, 'save_owner', side_effect=committed_but_timed_out):
            self.queue.process_one(now=0)
        self.restart()
        self.assertFalse(self.queue.process_one(now=1))
        self.queue.process_one(now=2)
        self.assertEqual(1, self.repo.stats()['total'])
        self.assertEqual(0, self.queue.outbox.counts()['pending'])

    def test_deletion_cancels_durable_job_even_if_google_unavailable(self):
        self.enqueue()
        self.api.fail = True
        with self.assertRaises(SheetsError):
            self.service.delete(self.token)
        self.restart()
        self.api.fail = False
        self.assertEqual('revoked', self.enqueue())
        self.assertFalse(self.queue.process_one(now=0))
        self.assertEqual(0, self.repo.stats()['total'])
        self.assertEqual(0, self.service.delete(self.token))
        self.assertEqual('revoked', self.repo.save(self.token, 'late', {'description':'synthetic'},
            {'explanation':'synthetic','category':'інше','urgency':'звичайне'}, 'synthetic', 1))

    def test_ack_failure_keeps_job_for_replay(self):
        self.enqueue()
        with patch.object(self.queue.outbox, 'finish', side_effect=OSError('synthetic')):
            with self.assertRaises(OSError):
                self.queue.process_one(now=0)
        self.restart()
        self.queue.process_one(now=0)
        self.assertEqual(1, self.repo.stats()['total'])

    def test_disk_failure_never_returns_queued(self):
        with patch.object(self.queue.outbox, 'put', side_effect=OSError('synthetic')):
            self.assertEqual('unavailable', self.enqueue())

    def test_expired_and_permanently_failed_payloads_are_bounded(self):
        self.enqueue()
        with patch.object(self.repo, 'save_owner', side_effect=SheetsError(status=403)):
            self.queue.process_one(now=0)
        self.restart()
        self.assertEqual({'pending':0, 'failed':1}, self.queue.outbox.counts())
        self.assertFalse(self.queue.process_one(now=0))
        import time
        with patch('collection_outbox.time.time', return_value=time.time()+OUTBOX_RETENTION+1):
            self.assertEqual({'pending':0, 'failed':0}, self.queue.outbox.counts())

    def test_no_disk_configuration_uses_confirmed_sync_google_instead_of_ram(self):
        service = collection_service_for(Settings(google_sheets_id='synthetic', google_credentials_file='unused'))
        self.assertIsNone(service.dispatcher)

    def test_unknown_deletions_do_not_fill_local_registry(self):
        for i in range(20):
            self.assertFalse(self.queue.revoke(f'{i:064x}'))
        self.assertEqual(0, self.queue.outbox.db.execute('SELECT COUNT(*) FROM outbox_revoked').fetchone()[0])

    def test_invalid_disk_configuration_does_not_break_ai_service_dependency(self):
        with patch('collection_dispatcher.CollectionDispatcher', side_effect=OSError('synthetic')):
            service = collection_service_for(Settings(google_sheets_id='synthetic-disk-error',
                google_credentials_file='unused', collection_outbox_path=self.path))
        self.assertIsNone(service.dispatcher)
        self.assertIsNotNone(service.repository)
        self.assertTrue(service.unavailable)
        with self.assertRaises(OSError):
            service.delete(self.token)
