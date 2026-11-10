"""Single writer: persist before queued, acknowledge only after Google confirms."""
import logging
import threading
import time
from collection_outbox import CollectionOutbox, Job
from sheets_transport import SheetsError

logger = logging.getLogger('meditrack.collection')


class CollectionDispatcher:
    def __init__(self, repository, capacity=64, start=True, path=':memory:'):
        self.repository = repository
        self.outbox = CollectionOutbox(path, capacity)
        self.condition = threading.Condition()
        self.active = None
        self.stopped = False
        self.worker = None
        if start:
            self.worker = threading.Thread(target=self._run, name='collection-writer', daemon=True)
            self.worker.start()

    @property
    def jobs(self):
        return self.outbox.pending()

    def submit(self, job):
        with self.condition:
            if self.stopped:
                return 'unavailable'
            status = self.outbox.put(job)
            self.condition.notify_all()
            return status

    def has_owner(self, token):
        with self.condition:
            owner = self.repository.owner(token)
            return self.outbox.has_owner(owner) or (self.active is not None and self.active[0] == owner)

    def revoke(self, token, pending=False):
        with self.condition:
            owner = self.repository.owner(token)
            active = self.active is not None and self.active[0] == owner
            return self.outbox.revoke(owner, pending or active)

    def process_one(self, now=None):
        real_time = now is None
        now = time.time() if real_time else now
        with self.condition:
            if self.active is not None:
                return False
            job = self.outbox.ready(now)
            if job is None:
                return False
            self.active = (job.owner, job.request_id)
        retry, failed = False, False
        try:
            if not self.outbox.is_revoked(job.owner):
                self.repository.save_owner(job.owner, job.request_id, job.request, job.response,
                    job.model, job.elapsed_ms, consent_version=job.consent_version)
        except SheetsError as exc:
            retry = exc.status is None or exc.status in {401, 408, 429} or exc.status >= 500
            failed = not retry
            logger.warning('collection_retry_scheduled' if retry else 'collection_write_failed')
        except Exception:
            failed = True
            logger.warning('collection_write_failed')
        finally:
            with self.condition:
                try:
                    if retry:
                        job.attempts += 1
                        delay = 2 ** job.attempts if job.attempts < 3 else 900
                        job.due = (time.time() if real_time else now) + delay
                    self.outbox.finish(job, retry, failed)
                finally:
                    self.active = None
                    self.condition.notify_all()
        return True

    def _run(self):
        while True:
            with self.condition:
                if self.stopped:
                    return
            try:
                if self.process_one():
                    continue
            except Exception:
                logger.warning('collection_outbox_unavailable')
            with self.condition:
                if not self.stopped:
                    self.condition.wait(timeout=1)

    def close(self, timeout=5):
        with self.condition:
            self.stopped = True
            self.condition.notify_all()
        if self.worker:
            self.worker.join(timeout=timeout)
        if self.worker is None or not self.worker.is_alive():
            self.outbox.close()
