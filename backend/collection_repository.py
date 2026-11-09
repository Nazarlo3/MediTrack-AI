"""Private SQLite collection. No raw capability tokens, prompts or responses in logs."""
import hashlib
import json
from pathlib import Path
import sqlite3
import time
from contextlib import contextmanager

RETENTION_SECONDS = 90 * 24 * 3600


class CollectionRepository:
    def __init__(self, path: str):
        self.path = path

    @contextmanager
    def _connect(self):
        Path(self.path).parent.mkdir(parents=True, exist_ok=True)
        db = sqlite3.connect(self.path, timeout=2)
        db.row_factory = sqlite3.Row
        try:
            db.execute('PRAGMA secure_delete=ON')
            db.executescript('''
                CREATE TABLE IF NOT EXISTS records (
                  owner TEXT NOT NULL, request_id TEXT NOT NULL, created_at REAL NOT NULL,
                  request_json TEXT NOT NULL, response_json TEXT NOT NULL,
                  model TEXT NOT NULL, elapsed_ms REAL NOT NULL, consent_version INTEGER NOT NULL,
                  PRIMARY KEY(owner, request_id));
                CREATE INDEX IF NOT EXISTS records_date ON records(created_at);
                CREATE TABLE IF NOT EXISTS revoked (owner TEXT PRIMARY KEY);
            ''')
            with db:
                yield db
        finally:
            db.close()

    @staticmethod
    def owner(token):
        return hashlib.sha256(token.encode('ascii')).hexdigest()

    def save(self, token, request_id, request, response, model, elapsed_ms, consent_version=1):
        now = time.time()
        with self._connect() as db:
            db.execute('BEGIN IMMEDIATE')
            db.execute('DELETE FROM records WHERE created_at <= ?', (now - RETENTION_SECONDS,))
            owner = self.owner(token)
            if db.execute('SELECT 1 FROM revoked WHERE owner=?', (owner,)).fetchone():
                return 'revoked'
            db.execute('INSERT OR IGNORE INTO records VALUES (?,?,?,?,?,?,?,?)',
                       (owner, request_id, now, json.dumps(request, ensure_ascii=False),
                        json.dumps(response, ensure_ascii=False), model, elapsed_ms, consent_version))
        return 'saved'

    def delete(self, token):
        owner = self.owner(token)
        with self._connect() as db:
            db.execute('BEGIN IMMEDIATE')
            db.execute('INSERT OR IGNORE INTO revoked VALUES (?)', (owner,))
            count = db.execute('DELETE FROM records WHERE owner=?', (owner,)).rowcount
        return count

    def purge(self):
        with self._connect() as db:
            return db.execute('DELETE FROM records WHERE created_at <= ?',
                              (time.time() - RETENTION_SECONDS,)).rowcount

    def records(self, limit=50, offset=0):
        with self._connect() as db:
            db.execute('DELETE FROM records WHERE created_at <= ?', (time.time() - RETENTION_SECONDS,))
            rows = db.execute('SELECT request_id,created_at,request_json,response_json,model,elapsed_ms '
                              'FROM records ORDER BY created_at DESC,request_id LIMIT ? OFFSET ?',
                              (limit, offset)).fetchall()
        return [dict(request_id=r['request_id'], created_at=r['created_at'],
                     request=json.loads(r['request_json']), response=json.loads(r['response_json']),
                     model=r['model'], elapsed_ms=r['elapsed_ms']) for r in rows]

    def stats(self):
        with self._connect() as db:
            db.execute('DELETE FROM records WHERE created_at <= ?', (time.time() - RETENTION_SECONDS,))
            rows = db.execute('SELECT created_at,response_json,elapsed_ms FROM records').fetchall()
        from collections import Counter
        from datetime import datetime, timezone
        days, categories, urgencies = Counter(), Counter(), Counter()
        for r in rows:
            days[datetime.fromtimestamp(r['created_at'], timezone.utc).date().isoformat()] += 1
            response = json.loads(r['response_json'])
            categories[response['category']] += 1
            urgencies[response['urgency']] += 1
        return dict(total=len(rows), by_day=dict(days), by_category=dict(categories),
                    by_urgency=dict(urgencies),
                    average_elapsed_ms=sum(r['elapsed_ms'] for r in rows)/len(rows) if rows else None)
