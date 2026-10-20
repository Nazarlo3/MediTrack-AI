"""Runner accounting tests: no Groq calls, no claims about AI semantic accuracy."""
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from config import Settings

spec = importlib.util.spec_from_file_location("evaluation_runner", Path(__file__).resolve().parents[1] / "evaluation/run.py")
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class EvaluationTests(unittest.TestCase):
    def setUp(self):
        self.case = {"id": "synthetic", "group": "context", "description": "В обох.",
                     "context": [{"role": "user", "content": "Печуть очі"}, {"role": "assistant", "content": "В одному чи обох?"}],
                     "expected": {"category": "офтальмологічне"}}
        self.cfg = {"name": "current", "system_prompt": "test rules", "max_tokens": 400,
                    "supports_context": True, "categories": ["офтальмологічне"]}
        self.post = Mock()
        self.post.return_value.status_code = 200
        self.post.return_value.json.return_value = {"model": "synthetic-model", "choices": [{"finish_reason": "stop", "message": {"content": json.dumps({
            "category": "офтальмологічне", "explanation": "Синтетичний результат", "urgency": "звичайне"})}}]}

    def test_context_is_forwarded_and_success_is_recorded(self):
        row = runner.evaluate(self.case, self.cfg, Settings(api_key="test-only"), post=self.post)
        self.assertTrue(row["passed"])
        self.assertTrue(row["result"]["context_used"])
        self.assertEqual(4, len(self.post.call_args.kwargs["json"]["messages"]))
        self.assertEqual(1, len(row["attempts"]))
        self.assertNotIn("test-only", json.dumps(row))

    def test_baseline_does_not_claim_context_or_new_category(self):
        cfg = {**self.cfg, "name": "baseline", "supports_context": False, "categories": ["інше"], "max_tokens": 200}
        row = runner.evaluate(self.case, cfg, Settings(api_key="test-only"), post=self.post)
        self.assertFalse(row["passed"])
        self.assertFalse(row["result"]["context_used"])
        self.assertEqual("інше", row["result"]["category"])
        self.assertEqual(2, len(self.post.call_args.kwargs["json"]["messages"]))

    def test_timeout_is_counted_once_without_secret_or_retry(self):
        self.post.side_effect = runner.requests.exceptions.Timeout("private-request-marker")
        row = runner.evaluate(self.case, self.cfg, Settings(api_key="test-only"), post=self.post)
        self.assertFalse(row["passed"])
        self.assertFalse(row["valid_response"])
        self.post.assert_called_once()
        self.assertNotIn("private-request-marker", json.dumps(row))
        summary = runner.summarize([self.case], [row], {"context": 1, "valid_responses": 1})
        self.assertFalse(summary["current"]["accepted"])

    def test_invalid_json_is_not_counted_as_a_valid_response(self):
        self.post.return_value.json.return_value["choices"][0]["message"]["content"] = "{"
        row = runner.evaluate(self.case, self.cfg, Settings(api_key="test-only"), post=self.post)
        self.assertFalse(row["valid_response"])
        self.assertEqual("{", row["attempts"][0]["raw_content"])

    def test_resume_preserves_failures_and_does_not_retry_semantic_mismatches(self):
        dataset = json.loads((runner.HERE / "cases.json").read_text(encoding="utf-8"))
        configs = [json.loads((runner.HERE / (name + ".json")).read_text(encoding="utf-8")) for name in ("baseline", "current")]
        rows = [{"case_id": case["id"], "version": cfg["name"], "valid_response": True,
                 "passed": cfg["name"] == "current", "attempts": [{"number": 1, "http_status": 200}]}
                for case in dataset["cases"] for cfg in configs]
        failed = rows[1]
        failed.update(valid_response=False, passed=False, attempts=[{"number": 1, "http_status": 429}])
        previous = {"dataset_sha256": runner.hashlib.sha256((runner.HERE / "cases.json").read_bytes()).hexdigest(),
                    "configurations": configs, "model_requested": Settings().model, "temperature": 0.1,
                    "timeout_seconds": 15.0, "rows": rows,
                    "summary": runner.summarize(dataset["cases"], rows, dataset["acceptance"])}
        with tempfile.TemporaryDirectory() as directory:
            source, output = Path(directory) / "before.json", Path(directory) / "after.json"
            source.write_text(json.dumps(previous), encoding="utf-8")
            original = source.read_bytes()
            replacement = {**failed, "valid_response": True, "passed": True, "attempts": [{"number": 1, "http_status": 200}]}
            with patch.object(sys, "argv", ["run.py", "--suite", "legacy", "--live", "--resume", str(source), "--output", str(output), "--delay", "0"]), \
                    patch.object(runner, "get_settings", return_value=Settings(api_key="test-only")), \
                    patch.object(runner, "evaluate", return_value=replacement) as evaluate:
                self.assertEqual(0, runner.main())
            evaluate.assert_called_once()
            self.assertEqual(original, source.read_bytes())
            artifact = json.loads(output.read_text(encoding="utf-8"))
            retried = next(row for row in artifact["rows"] if row["case_id"] == failed["case_id"] and row["version"] == "current")
            self.assertEqual([429, 200], [attempt["http_status"] for attempt in retried["attempts"]])
            self.assertTrue(artifact["summary"]["current"]["accepted"])
            self.assertFalse(artifact["first_attempt_acceptance"]["current"])

    def test_details_check_requires_actual_explanation_not_only_acknowledgement(self):
        case = {**self.case, "duration_days": 3, "intensity": 0,
                "expected": {"details_used": True, "explanation_contains": ["3 дн.", "0/10"]}}
        cfg = {**self.cfg, "supports_details": True}
        row = runner.evaluate(case, cfg, Settings(api_key="test-only"), post=self.post)
        self.assertTrue(row["result"]["details_used"])
        self.assertFalse(row["passed"])
        self.assertIn("0/10", self.post.call_args.kwargs["json"]["messages"][-1]["content"])


if __name__ == "__main__":
    unittest.main()
