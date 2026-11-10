"""Bounded private SQLite outbox. Raw installation capabilities are never persisted."""
from contextlib import contextmanager
from dataclasses import asdict, dataclass, field
import json
import os
from pathlib import Path
import sqlite3
import threading
import time

OUTBOX_RETENTION = 24 * 3600


@dataclass
class Job:
    owner: str
    request_id: str
    request: dict = field(repr=False)
    response: dict = field(repr=False)
    model: str
    elapsed_ms: float
    consent_version: int
    attempts: int = 0
    due: float = 0
    created_at: float = field(default_factory=time.time)


class CollectionOutbox:
    def __init__(self, path=':memory:', capacity=64):
        self.path, self.capacity = path, capacity
        self.lock = threading.RLock()
        if path != ':memory:':
            parent = Path(path).resolve().parent
            parent.mkdir(mode=0o700, parents=True, exist_ok=True)
            descriptor = os.open(str(Path(path)), os.O_CREAT | os.O_RDWR, 0o600)
            os.close(descriptor)
        self.db = sqlite3.connect(path, timeout=2, check_same_thread=False)
        self.db.execute('PRAGMA secure_delete=ON')
        self.db.execute('PRAGMA synchronous=FULL')
        self.db.executescript('''
            CREATE TABLE IF NOT EXISTS outbox (
              owner TEXT NOT NULL, request_id TEXT NOT NULL, payload TEXT NOT NULL,
              created_at REAL NOT NULL, due REAL NOT NULL, failed INTEGER NOT NULL DEFAULT 0,
              PRIMARY KEY(owner, request_id));
            CREATE TABLE IF NOT EXISTS outbox_revoked (owner TEXT PRIMARY KEY, created_at REAL NOT NULL);
        ''')
        self.purge()

    @contextmanager
    def transaction(self):
        with self.lock:
            with self.db:
                self.db.execute('BEGIN IMMEDIATE')
                yield self.db

    def purge(self):
        with self.transaction() as db:
            db.execute('DELETE FROM outbox WHERE created_at <= ?', (time.time() - OUTBOX_RETENTION,))
            db.execute('DELETE FROM outbox_revoked WHERE created_at <= ?', (time.time() - 90*24*3600,))

    def put(self, job):
        self.purge()
        with self.transaction() as db:
            if db.execute('SELECT 1 FROM outbox_revoked WHERE owner=?', (job.owner,)).fetchone():
                return 'revoked'
            if db.execute('SELECT 1 FROM outbox WHERE owner=? AND request_id=?', (job.owner, job.request_id)).fetchone():
                return 'queued'
            if db.execute('SELECT COUNT(*) FROM outbox').fetchone()[0] >= self.capacity:
                return 'unavailable'
            db.execute('INSERT INTO outbox(owner,request_id,payload,created_at,due) VALUES (?,?,?,?,?)',
                       (job.owner, job.request_id, json.dumps(asdict(job), ensure_ascii=False), job.created_at, job.due))
        return 'queued'

    def ready(self, now):
        self.purge()
        with self.lock:
            row = self.db.execute('SELECT payload FROM outbox WHERE failed=0 AND due<=? ORDER BY due,created_at LIMIT 1',
                                  (now,)).fetchone()
        return Job(**json.loads(row[0])) if row else None

    def pending(self):
        self.purge()
        with self.lock:
            return {(r[0], r[1]): Job(**json.loads(r[2])) for r in self.db.execute(
                'SELECT owner,request_id,payload FROM outbox WHERE failed=0')}

    def finish(self, job, retry=False, failed=False):
        with self.transaction() as db:
            if retry or failed:
                # UPDATE only: concurrent deletion must not resurrect a job.
                db.execute('UPDATE outbox SET payload=?,due=?,failed=? WHERE owner=? AND request_id=?',
                           (json.dumps(asdict(job), ensure_ascii=False), job.due, int(failed), job.owner, job.request_id))
            else:
                db.execute('DELETE FROM outbox WHERE owner=? AND request_id=?', (job.owner, job.request_id))

    def revoke(self, owner, pending=False):
        self.purge()
        with self.transaction() as db:
            known = db.execute('SELECT 1 FROM outbox WHERE owner=? LIMIT 1', (owner,)).fetchone()
            revoked = db.execute('SELECT 1 FROM outbox_revoked WHERE owner=?', (owner,)).fetchone()
            if known or pending or revoked:
                db.execute('INSERT OR IGNORE INTO outbox_revoked VALUES (?,?)', (owner, time.time()))
                db.execute('DELETE FROM outbox WHERE owner=?', (owner,))
            return bool(known or pending or revoked)

    def has_owner(self, owner):
        self.purge()
        with self.lock:
            return self.db.execute('SELECT 1 FROM outbox WHERE owner=? LIMIT 1', (owner,)).fetchone() is not None

    def is_revoked(self, owner):
        with self.lock:
            return self.db.execute('SELECT 1 FROM outbox_revoked WHERE owner=?', (owner,)).fetchone() is not None

    def counts(self):
        self.purge()
        with self.lock:
            count, failed = self.db.execute('SELECT COUNT(*),COALESCE(SUM(failed),0) FROM outbox').fetchone()
            return {'pending': count-failed, 'failed': failed}

    def close(self):
        with self.lock:
            self.db.close()
