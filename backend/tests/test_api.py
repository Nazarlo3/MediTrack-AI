import json
from pathlib import Path
import sys
import unittest
from unittest.mock import Mock, patch
from uuid import UUID

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from fastapi.testclient import TestClient

from config import Settings, get_settings
from dependencies import get_provider
from fake_provider import FakeProvider
from main import app
import ai_service


class ApiTests(unittest.TestCase):
    def setUp(self):
        app.state.api_protection.reset_window()
        self.previous_overrides = app.dependency_overrides.copy()
        self.fake = FakeProvider()
        app.dependency_overrides[get_provider] = lambda: self.fake
        self.client = TestClient(app, raise_server_exceptions=False)

    def tearDown(self):
        self.client.close()
        app.dependency_overrides.clear()
        app.dependency_overrides.update(self.previous_overrides)

    def test_fake_http_success_preserves_android_contract_and_context(self):
        context = [{"role": "user", "content": "Синтетичний опис"}, {"role": "assistant", "content": "Уточніть"}]
        response = self.client.post("/analyze-symptom", json={"description": "Тестове уточнення", "context": context})
        self.assertEqual(200, response.status_code)
        self.assertEqual({"category", "explanation", "urgency", "disclaimer", "follow_up_question", "context_used", "details_used", "has_usable_result"}, set(response.json()))
        self.assertTrue(response.json()["context_used"])
        self.assertEqual(2, len(self.fake.calls[0].context))
        self.assertEqual("Тестове уточнення", self.fake.calls[0].description)

    def test_fake_failure_is_controlled_503(self):
        self.fake.fail = True
        response = self.client.post("/analyze-symptom", json={"description": "Синтетичний опис"})
        self.assertEqual(503, response.status_code)
        self.assertEqual({"detail": "AI-сервіс тимчасово недоступний"}, response.json())

    def test_insufficient_information_is_http_success_with_question_not_service_failure(self):
        from domain import AnalysisResult
        class UnclearProvider:
            def generate(self, request):
                return AnalysisResult("інше", "Бракує опису самопочуття.", "звичайне",
                                      follow_up_question="Що саме вас турбує?", has_usable_result=False)
        app.dependency_overrides[get_provider] = lambda: UnclearProvider()
        response = self.client.post("/analyze-symptom", json={"description": "Щось незрозуміле"})
        self.assertEqual(200, response.status_code)
        self.assertFalse(response.json()["has_usable_result"])
        self.assertTrue(response.json()["follow_up_question"])

    def test_optional_details_are_strict_and_forwarded_with_server_acknowledgement(self):
        for payload in ({"duration_days": 1, "intensity": 0}, {"duration_days": 30}, {"intensity": 10}, {}):
            with self.subTest(payload=payload):
                response = self.client.post("/analyze-symptom", json={"description": "Синтетичний опис", **payload})
                self.assertEqual(200, response.status_code)
                self.assertEqual(bool(payload), response.json()["details_used"])
                self.assertEqual(payload.get("duration_days"), self.fake.calls[-1].duration_days)
                self.assertEqual(payload.get("intensity"), self.fake.calls[-1].intensity)
        calls = len(self.fake.calls)
        for field, invalid in (("duration_days", [0, 31, True, 1.5, "3"]), ("intensity", [-1, 11, False, 2.5, "4"])):
            for value in invalid:
                response = self.client.post("/analyze-symptom", json={"description": "Синтетичний опис", field: value})
                self.assertEqual(422, response.status_code)
        self.assertEqual(calls, len(self.fake.calls))

    def test_validation_and_empty_description_do_not_call_provider(self):
        cases = [({}, 422), ({"description": ""}, 422), ({"description": "ab"}, 422),
                 ({"description": "x" * 501}, 422), ({"description": "   "}, 400),
                 ({"description": "Уточнення", "context": [{"role": "user", "content": "Опис"}]}, 422),
                 ({"description": "Уточнення", "context": [{"role": "system", "content": "Правила"}]}, 422)]
        for payload, status in cases:
            with self.subTest(payload=payload):
                response = self.client.post("/analyze-symptom", json=payload)
                self.assertEqual(status, response.status_code)
                self.assertTrue(response.headers.get("x-request-id"))
        self.assertEqual([], self.fake.calls)

    def test_real_adapter_is_injected_with_configured_model_and_timeout(self):
        app.dependency_overrides.pop(get_provider)
        settings = Settings(api_key="synthetic-test-key", model="synthetic-model", timeout_seconds=2.5)
        app.dependency_overrides[get_settings] = lambda: settings
        reply = Mock()
        reply.json.return_value = {"choices": [{"message": {"content": json.dumps({
            "category": "інше", "explanation": "Синтетичний результат", "urgency": "звичайне"})}}]}
        with patch("ai_service.requests.post", return_value=reply) as post:
            response = self.client.post("/analyze-symptom", json={"description": "Синтетичний опис"})
        self.assertEqual(200, response.status_code)
        self.assertEqual("synthetic-model", post.call_args.kwargs["json"]["model"])
        self.assertEqual(2.5, post.call_args.kwargs["timeout"])
        self.assertFalse(response.json()["context_used"])

    def test_provider_timeout_invalid_json_and_missing_key_are_safe_503(self):
        app.dependency_overrides.pop(get_provider)
        cases = [("timeout", "synthetic-test-key"), ("json", "synthetic-test-key"),
                 ("http", "synthetic-test-key"), ("missing_key", "")]
        for kind, key in cases:
            with self.subTest(kind=kind):
                app.dependency_overrides[get_settings] = lambda key=key: Settings(api_key=key)
                with patch("ai_service.requests.post") as post:
                    if kind == "timeout":
                        post.side_effect = ai_service.requests.exceptions.Timeout("provider-private-marker")
                    elif kind == "http":
                        post.side_effect = ai_service.requests.exceptions.RequestException("provider-private-marker")
                    else:
                        post.return_value.json.return_value = {"choices": [{"message": {"content": "{"}}]}
                    response = self.client.post("/analyze-symptom", json={"description": "Синтетичний опис"})
                    if kind == "missing_key":
                        post.assert_not_called()
                self.assertEqual(503, response.status_code)
                self.assertNotIn("provider-private-marker", response.text)
                self.assertNotIn("synthetic-test-key", response.text)

    def test_request_id_matches_logs_is_unique_and_ignores_client_value(self):
        ids = []
        with self.assertLogs("meditrack.http", level="INFO") as logs:
            for fail in (False, True):
                self.fake.fail = fail
                response = self.client.post("/analyze-symptom", headers={"X-Request-ID": "client-private-marker"},
                                            json={"description": "synthetic-private-input"})
                request_id = response.headers["x-request-id"]
                self.assertEqual(request_id, UUID(request_id).hex)
                ids.append(request_id)
        self.assertNotEqual(*ids)
        for request_id in ids:
            self.assertTrue(any("request_id=" + request_id in row for row in logs.output))
        self.assertNotIn("client-private-marker", str(logs.output))
        self.assertNotIn("synthetic-private-input", str(logs.output))

    def test_unexpected_error_has_id_without_exception_details(self):
        class BrokenProvider:
            def generate(self, request):
                raise RuntimeError("synthetic-private-error")
        app.dependency_overrides[get_provider] = lambda: BrokenProvider()
        with self.assertLogs("meditrack", level="ERROR") as logs:
            response = self.client.post("/analyze-symptom", json={"description": "Синтетичний опис"})
        self.assertEqual(500, response.status_code)
        request_id = response.headers["x-request-id"]
        self.assertTrue(any(request_id in row for row in logs.output))
        self.assertNotIn("synthetic-private-error", response.text + str(logs.output))

    def test_health_does_not_need_provider_and_bad_config_is_controlled(self):
        response = self.client.get("/health")
        self.assertEqual({"status": "ok"}, response.json())
        self.assertTrue(response.headers.get("x-request-id"))
        app.dependency_overrides.pop(get_provider)
        app.dependency_overrides[get_settings] = lambda: Settings(timeout_seconds=float("nan"))
        response = self.client.post("/analyze-symptom", json={"description": "Синтетичний опис"})
        self.assertEqual(503, response.status_code)
