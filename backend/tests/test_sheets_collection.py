import copy
import json
from pathlib import Path
import sys
import time
import unittest
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from sheets_collection_repository import SheetsCollectionRepository, HEADERS, DATA, STATS, KEYS, cells
from sheets_transport import SheetsError
from collection_repository import RETENTION_SECONDS
from config import Settings
from dependencies import get_collection_repository


class MemorySheets:
    def __init__(self):
        self.tables = {DATA: [HEADERS[:]], STATS: [['Показник','Значення']], KEYS: [['Ключ SHA-256']]}
        self.ids = {title:i+1 for i,title in enumerate(self.tables)}
        self.fail = False
    def __call__(self, method, suffix='', body=None, params=None):
        if self.fail: raise SheetsError('synthetic failure')
        if method == 'GET' and not suffix:
            return {'sheets':[{'properties':{'sheetId':i,'title':t}} for t,i in self.ids.items()]}
        if suffix == '/values:batchGet':
            return {'valueRanges':[{'values':copy.deepcopy(self.tables[t])} for t in [DATA,KEYS]]}
        for r in body['requests']:
            if 'appendCells' in r:
                item=r['appendCells']; title=next(t for t,i in self.ids.items() if i==item['sheetId'])
                self.tables[title] += [[next(iter(c['userEnteredValue'].values())) for c in row['values']] for row in item['rows']]
            if 'deleteDimension' in r:
                item=r['deleteDimension']['range']; del self.tables[DATA][item['startIndex']:item['endIndex']]
            if 'updateCells' in r:
                item=r['updateCells']; self.tables[STATS]=[[next(iter(c['userEnteredValue'].values())) for c in row['values']] for row in item['rows']]
        return {}


class SheetsTests(unittest.TestCase):
    def setUp(self):
        self.api=MemorySheets(); self.repo=SheetsCollectionRepository(self.api)
        self.request={'description':'=SYNTHETIC()', 'duration_days':None, 'intensity':0}
        self.response={'category':'інше','explanation':'synthetic','urgency':'звичайне','has_usable_result':False}
    def save(self, token='b'*64, id='test'):
        return self.repo.save(token,id,self.request,self.response,'synthetic',12.5)
    def test_literal_text_and_json_round_trip(self):
        self.save()
        row=self.repo.records()[0]
        self.assertEqual(self.request,row['request']); self.assertEqual(self.response,row['response'])
        self.assertEqual({'stringValue':'=SYNTHETIC()'},cells(['=SYNTHETIC()'])['values'][0]['userEnteredValue'])
    def test_owner_scoped_delete_and_late_write(self):
        self.save(); self.save('c'*64)
        self.assertEqual(1,self.repo.delete('b'*64))
        self.assertEqual('revoked',self.save())
        self.assertEqual(1,self.repo.stats()['total'])
    def test_deduplication_and_stats(self):
        self.save(); self.save()
        self.assertEqual(1,self.repo.stats()['total'])
        self.assertEqual(12.5,self.repo.stats()['average_elapsed_ms'])
        self.assertEqual(['Звернень',1],self.api.tables[STATS][1])
    def test_expiry_cleans_data_and_stats(self):
        self.save()
        self.api.tables[DATA][1][13]=time.time()-RETENTION_SECONDS-1
        self.assertEqual(1,self.repo.purge()); self.assertEqual(0,self.repo.stats()['total'])
    def test_key_capacity_stops_new_collection_but_preserves_deletion(self):
        self.save()
        self.api.tables[KEYS] += [[str(i)] for i in range(10000)]
        with self.assertRaises(SheetsError): self.save('c'*64)
        self.assertEqual(1,self.repo.delete('b'*64))
        self.assertEqual(0,self.repo.stats()['total'])
    def test_invalid_schema_fails_closed(self):
        self.api.tables[DATA][0]=['manual change']
        with self.assertRaises(SheetsError): self.save()
    def test_failure_does_not_fall_back_to_unexpected_database(self):
        self.api.fail=True
        with self.assertRaises(SheetsError): self.save()
    def test_google_selected_only_when_configured(self):
        self.assertIsNone(get_collection_repository(Settings()))
        self.assertIsInstance(get_collection_repository(Settings(google_sheets_id='test',google_credentials_file='unused')),SheetsCollectionRepository)
    def test_old_consent_cannot_enable_google_and_new_consent_is_recorded(self):
        from collection_service import CollectionService
        from models import SymptomRequest, SymptomResponse
        from uuid import uuid4
        service=CollectionService(self.repo,'synthetic')
        response=SymptomResponse(category='інше',explanation='synthetic')
        for version,expected in [(1,'consent_required'),(2,'saved')]:
            request=SymptomRequest(description='synthetic',collection={'consent_version':version,'request_id':str(uuid4())})
            self.assertEqual(expected,service.record(request.collection,'b'*64,request,response,1))
        self.assertEqual(1,self.repo.stats()['total'])
        self.assertEqual(2,self.api.tables[DATA][1][12])
