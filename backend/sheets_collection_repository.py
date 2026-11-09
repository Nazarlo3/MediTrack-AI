"""Small single-worker collection on private Sheets. All writes use literal values."""
from collections import Counter
from datetime import datetime, timezone
import json
import threading
import time

from collection_repository import CollectionRepository, RETENTION_SECONDS
from sheets_transport import SheetsError

LOCK = threading.RLock()
DATA = 'Звернення'
STATS = 'Статистика'
KEYS = '_MediTrack_keys'
HEADERS = ['ID запиту', 'Дата UTC', 'Запит', 'Відповідь AI', 'Категорія', 'Терміновість',
           'Тривалість, днів', 'Інтенсивність', 'Модель', 'Час, мс', 'Ключ SHA-256',
           'Повна відповідь JSON', 'Версія згоди', 'Epoch UTC']


def cells(values):
    return {'values': [{'userEnteredValue': {'numberValue': v} if type(v) in (int, float)
                       else {'stringValue': '' if v is None else str(v)}} for v in values]}


def append(sheet_id, values):
    return {'appendCells': {'sheetId': sheet_id, 'rows': [cells(values)], 'fields': 'userEnteredValue'}}


class SheetsCollectionRepository:
    required_consent_version = 2
    owner = staticmethod(CollectionRepository.owner)

    def __init__(self, transport):
        self.api = transport
        self.ids = None

    def _batch(self, requests):
        if requests:
            self.api('POST', ':batchUpdate', {'requests': requests})

    def _ensure(self):
        if self.ids is not None:
            return
        meta = self.api('GET', params={'fields': 'sheets(properties(sheetId,title))'})
        ids = {s['properties']['title']: s['properties']['sheetId'] for s in meta['sheets']}
        requests = []
        new_ids = set(ids.values())
        for title, header in [(DATA, HEADERS), (STATS, ['Показник', 'Значення']), (KEYS, ['Ключ SHA-256'])]:
            if title in ids:
                continue
            id = max(new_ids, default=0) + 1
            ids[title] = id
            new_ids.add(id)
            requests += [{'addSheet': {'properties': {'sheetId': id, 'title': title,
                          'hidden': title == KEYS, 'gridProperties': {'rowCount': 1000, 'columnCount': 14,
                                                                    'frozenRowCount': 1}}}}, append(id, header)]
            requests += [{'repeatCell': {'range': {'sheetId': id, 'startRowIndex': 0, 'endRowIndex': 1},
                          'cell': {'userEnteredFormat': {'textFormat': {'bold': True},
                                  'backgroundColor': {'red': .85, 'green': .92, 'blue': .96}}},
                          'fields': 'userEnteredFormat'}}]
            if title == DATA:
                requests += [{'updateDimensionProperties': {'range': {'sheetId': id, 'dimension': 'COLUMNS',
                             'startIndex': 10, 'endIndex': 14}, 'properties': {'hiddenByUser': True},
                             'fields': 'hiddenByUser'}},
                             {'updateDimensionProperties': {'range': {'sheetId': id, 'dimension': 'COLUMNS',
                             'startIndex': 2, 'endIndex': 4}, 'properties': {'pixelSize': 360}, 'fields': 'pixelSize'}},
                             {'repeatCell': {'range': {'sheetId': id},
                             'cell': {'userEnteredFormat': {'wrapStrategy': 'WRAP'}}, 'fields': 'userEnteredFormat.wrapStrategy'}}]
        self._batch(requests)
        self.ids = ids

    def _load(self):
        self._ensure()
        result = self.api('GET', '/values:batchGet', params={'ranges': [f"'{DATA}'!A:N", f"'{KEYS}'!A:A"],
                                                          'valueRenderOption': 'UNFORMATTED_VALUE'})
        data, keys = [r.get('values', []) for r in result['valueRanges']]
        if not data or data[0] != HEADERS or not keys or keys[0] != ['Ключ SHA-256']:
            raise SheetsError('Unexpected collection headers')
        if len(data) > 10001:
            raise SheetsError('Collection capacity reached; operator action required')
        if any(len(row) != 14 for row in data[1:]):
            raise SheetsError('Unexpected collection row')
        return data[1:], {r[0] for r in keys[1:] if r}

    def _delete_rows(self, indexes):
        return [{'deleteDimension': {'range': {'sheetId': self.ids[DATA], 'dimension': 'ROWS',
                 'startIndex': i + 1, 'endIndex': i + 2}}} for i in sorted(indexes, reverse=True)]

    @staticmethod
    def _summary(rows):
        days, categories, urgencies = Counter(), Counter(), Counter()
        for r in rows:
            days[str(r[1])[:10]] += 1
            categories[r[4]] += 1
            urgencies[r[5]] += 1
        return dict(total=len(rows), by_day=dict(days), by_category=dict(categories), by_urgency=dict(urgencies),
                    average_elapsed_ms=sum(float(r[9]) for r in rows)/len(rows) if rows else None)

    def _stats_request(self, rows):
        stats = self._summary(rows)
        values = [['Показник', 'Значення'], ['Звернень', stats['total']],
                  ['Середній час AI, мс', stats['average_elapsed_ms']],
                  ['Оновлено UTC', datetime.now(timezone.utc).isoformat()]]
        for group, label in [('by_day', 'День'), ('by_category', 'Категорія'), ('by_urgency', 'Терміновість')]:
            values += [[label + ': ' + k, v] for k, v in sorted(stats[group].items())]
        # Bounded output area is owned by this adapter; clearing excess old values is deliberate.
        return {'updateCells': {'range': {'sheetId': self.ids[STATS], 'startRowIndex': 0,
                'endRowIndex': 1000, 'startColumnIndex': 0, 'endColumnIndex': 2},
                'rows': [cells(v) for v in values], 'fields': 'userEnteredValue'}}

    def _fresh(self):
        rows, keys = self._load()
        expired = [i for i, r in enumerate(rows) if float(r[13]) <= time.time() - RETENTION_SECONDS]
        expired_set = set(expired)
        fresh = [r for i, r in enumerate(rows) if i not in expired_set]
        if expired:
            self._batch(self._delete_rows(expired) + [self._stats_request(fresh)])
        return fresh, keys

    def save(self, token, request_id, request, response, model, elapsed_ms, consent_version=2):
        with LOCK:
            rows, keys = self._fresh()
            owner = self.owner(token)
            if owner in keys:
                return 'revoked'
            if any(r[0] == request_id and r[10] == owner for r in rows):
                return 'saved'
            if len(rows) >= 10000 or len(keys) >= 10000:
                raise SheetsError('Collection capacity reached')
            now = time.time()
            row = [request_id, datetime.fromtimestamp(now, timezone.utc).isoformat(), request['description'],
                   response['explanation'], response['category'], response['urgency'],
                   request.get('duration_days'), request.get('intensity'), model, elapsed_ms,
                   owner, json.dumps(response, ensure_ascii=False), consent_version, now]
            self._batch([append(self.ids[DATA], row), self._stats_request(rows + [row])])
            return 'saved'

    def delete(self, token):
        with LOCK:
            rows, keys = self._fresh()
            owner = self.owner(token)
            indexes = [i for i, r in enumerate(rows) if r[10] == owner]
            requests = self._delete_rows(indexes)
            if owner not in keys:
                requests += [append(self.ids[KEYS], [owner])]
            requests += [self._stats_request([r for r in rows if r[10] != owner])]
            self._batch(requests)
            return len(indexes)

    def purge(self):
        with LOCK:
            before, _ = self._load()
            after, _ = self._fresh()
            return len(before) - len(after)

    def records(self, limit=50, offset=0):
        with LOCK:
            rows, _ = self._fresh()
            rows.sort(key=lambda r: (float(r[13]), r[0]), reverse=True)
            return [dict(request_id=r[0], created_at=float(r[13]),
                         request=dict(description=r[2], duration_days=r[6] if r[6] != '' else None,
                                      intensity=r[7] if r[7] != '' else None), response=json.loads(r[11]),
                         model=r[8], elapsed_ms=float(r[9])) for r in rows[offset:offset+limit]]

    def stats(self):
        with LOCK:
            rows, _ = self._fresh()
            return self._summary(rows)
