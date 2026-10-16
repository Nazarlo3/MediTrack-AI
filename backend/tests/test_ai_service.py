import json
from pathlib import Path
import re
import sys
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import ai_service


class AIServiceTests(unittest.TestCase):
    def setUp(self):
        self.valid = {
            "category": "травми та ушкодження",
            "explanation": "Можливе ушкодження після удару; симптоми потрібно уточнити.",
            "urgency": "увага",
        }

    def test_all_sixteen_categories_are_supported(self):
        self.assertEqual(16, len(set(ai_service.ALLOWED_CATEGORIES)))
        for category in ai_service.ALLOWED_CATEGORIES:
            with self.subTest(category=category):
                value = ai_service._validate_ai_result({**self.valid, "category": category})
                self.assertEqual(category, value["category"])

    def test_android_accepts_exactly_the_same_categories(self):
        root = Path(__file__).resolve().parents[2]
        source = (root / "app/src/main/java/com/example/meditrackai/validation/SymptomResponseValidator.kt").read_text(encoding="utf-8")
        category_block = source.split("private val categories = setOf(", 1)[1].split(")", 1)[0]
        android_categories = set(re.findall(r'"([^"]+)"', category_block))
        self.assertEqual(set(ai_service.ALLOWED_CATEGORIES), android_categories)

    def test_lor_case_is_normalized_to_contract_value(self):
        result = ai_service._validate_ai_result({**self.valid, "category": " лор ", "urgency": " УВАГА "})
        self.assertEqual("ЛОР", result["category"])
        self.assertEqual("увага", result["urgency"])

    def test_unknown_category_uses_existing_fallback(self):
        self.assertEqual("інше", ai_service._validate_ai_result({**self.valid, "category": "невідома"})["category"])

    def test_unknown_urgency_is_not_downgraded_to_normal(self):
        for urgency in ("не терміново", "maybe", "терміново чи ні", "", None, 1):
            with self.subTest(urgency=urgency):
                with self.assertRaises(ValueError):
                    ai_service._validate_ai_result({**self.valid, "urgency": urgency})

    def test_missing_or_blank_fields_are_rejected(self):
        for field in self.valid:
            for value in (None, "", "   ", [], 2):
                with self.subTest(field=field, value=value):
                    with self.assertRaises(ValueError):
                        ai_service._validate_ai_result({**self.valid, field: value})
            missing = dict(self.valid)
            del missing[field]
            with self.assertRaises(ValueError):
                ai_service._validate_ai_result(missing)

    def test_non_object_results_are_rejected(self):
        for value in (None, [], "text", 1):
            with self.assertRaises(ValueError):
                ai_service._validate_ai_result(value)

    @patch.dict("os.environ", {"GROQ_API_KEY": "test-key-not-a-real-secret"})
    @patch("ai_service.requests.post")
    def test_request_contains_context_policy_and_keeps_description_as_data(self, post):
        response = Mock()
        response.json.return_value = {"choices": [{"message": {"content": json.dumps(self.valid)}}]}
        post.return_value = response
        description = "розбив монітор ногою"
        self.assertEqual(self.valid, ai_service.categorize_symptom(description))
        payload = post.call_args.kwargs["json"]
        self.assertEqual("system", payload["messages"][0]["role"])
        self.assertIn("травма можлива, але не підтверджена", payload["messages"][0]["content"])
        self.assertEqual({"role": "user", "content": description}, payload["messages"][1])
        self.assertEqual(400, payload["max_tokens"])

    @patch.dict("os.environ", {"GROQ_API_KEY": "test-key-not-a-real-secret"})
    @patch("ai_service.requests.post")
    def test_malformed_provider_results_raise_controlled_error(self, post):
        for content in ("{", "[]", json.dumps({**self.valid, "urgency": "maybe"}), json.dumps({**self.valid, "explanation": ""})):
            response = Mock()
            response.json.return_value = {"choices": [{"message": {"content": content}}]}
            post.return_value = response
            with self.assertRaises(ai_service.AIServiceError):
                ai_service.categorize_symptom("Синтетичний опис")

    @patch.dict("os.environ", {"GROQ_API_KEY": "test-key-not-a-real-secret"})
    @patch("ai_service.requests.post", side_effect=ai_service.requests.exceptions.Timeout)
    def test_timeout_is_controlled(self, post):
        with self.assertRaises(ai_service.AIServiceError):
            ai_service.categorize_symptom("Синтетичний опис")


if __name__ == "__main__":
    unittest.main()
