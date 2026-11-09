"""Google OAuth + REST; tokens are memory-only, never included in exception text."""
import threading
import json
import subprocess
from types import SimpleNamespace
from urllib.parse import quote, urlencode


class SheetsError(RuntimeError):
    def __init__(self, message='Google Sheets unavailable', status=None, detail=None):
        super().__init__(message)
        self.status = status
        self.detail = detail


class SheetsTransport:
    def __init__(self, spreadsheet_id, credential_file, transport='requests'):
        self.base = 'https://sheets.googleapis.com/v4/spreadsheets/' + quote(spreadsheet_id, safe='')
        self.credential_file = credential_file
        self.credentials = None
        self.lock = threading.Lock()
        self.transport = transport

    def _http(self, method, url, headers, body=None):
        if self.transport == 'curl':
            # Secrets and request bodies go only to stdin, never command arguments/files.
            config = '\n'.join('header = ' + json.dumps(k + ': ' + v) for k, v in headers.items())
            if body is not None:
                config += '\ndata-binary = ' + json.dumps(body.decode('utf-8'), ensure_ascii=False)
            result = subprocess.run(['curl', '--silent', '--max-time', '20', '--request', method,
                        '--config', '-', '--write-out', '\n%{http_code}', url], input=config.encode('utf-8'),
                        capture_output=True, timeout=25)
            if result.returncode:
                raise SheetsError('Google connection failed')
            data, status = result.stdout.rsplit(b'\n', 1)
            return SimpleNamespace(status=int(status), data=data, headers={})
        import requests
        r = requests.request(method, url, headers=headers, data=body, timeout=(5, 15))
        return SimpleNamespace(status=r.status_code, data=r.content, headers=r.headers)

    def __call__(self, method, suffix='', body=None, params=None):
        try:
            # Lazy credentials: no collection configuration must not affect legacy AI.
            from google.oauth2.service_account import Credentials
            with self.lock:
                if self.credentials is None:
                    self.credentials = Credentials.from_service_account_file(
                        self.credential_file, scopes=['https://www.googleapis.com/auth/spreadsheets'])
                if not self.credentials.valid:
                    def request(url, method='GET', body=None, headers=None, **kwargs):
                        return self._http(method, url, headers or {}, body)
                    self.credentials.refresh(request)
                token = self.credentials.token
            # Explicit REST avoids unrelated regional-boundary discovery calls in AuthorizedSession.
            url = self.base + suffix + ('?' + urlencode(params, doseq=True) if params else '')
            response = self._http(method, url, {'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'},
                                  json.dumps(body, ensure_ascii=False).encode('utf-8') if body is not None else None)
            if response.status >= 400:
                if response.status == 401:
                    with self.lock:
                        self.credentials = None
                raise SheetsError('Google Sheets request failed', response.status,
                                  json.loads(response.data).get('error', {}).get('message'))
            return json.loads(response.data)
        except SheetsError:
            raise
        except Exception:
            raise SheetsError('Google Sheets unavailable') from None
