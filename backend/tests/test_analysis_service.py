from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from analysis_service import AnalysisService
from config import Settings
from dependencies import get_provider
from domain import AIProvider, AnalysisInput, ConversationMessage, EmptyDescriptionError, InvalidInputError, ProviderUnavailableError
from fake_provider import FakeProvider
from ai_service import GroqProvider


class AnalysisServiceTests(unittest.TestCase):
    def test_fake_success_normalizes_input_without_network(self):
        fake = FakeProvider()
        result = AnalysisService(fake).analyze(AnalysisInput("  Синтетичний опис  "))
        self.assertIn("СИНТЕТИЧНА", result.explanation)
        self.assertFalse(result.context_used)
        self.assertEqual("Синтетичний опис", fake.calls[0].description)

    def test_fake_error_propagates_as_domain_error(self):
        with self.assertRaises(ProviderUnavailableError):
            AnalysisService(FakeProvider(fail=True)).analyze(AnalysisInput("Синтетичний опис"))

    def test_whitespace_does_not_call_provider(self):
        fake = FakeProvider()
        with self.assertRaises(EmptyDescriptionError):
            AnalysisService(fake).analyze(AnalysisInput("   "))
        self.assertEqual([], fake.calls)

    def test_context_is_forwarded_and_acknowledged(self):
        fake = FakeProvider()
        context = (ConversationMessage("user", "Тестовий опис"), ConversationMessage("assistant", "Питання"))
        result = AnalysisService(fake).analyze(AnalysisInput("Уточнення", context))
        self.assertEqual(context, fake.calls[0].context)
        self.assertTrue(result.context_used)

    def test_incomplete_pairs_and_length_limits_never_call_provider(self):
        fake = FakeProvider()
        service = AnalysisService(fake)
        for request in (AnalysisInput("ab"), AnalysisInput("x" * 501),
                        AnalysisInput("Уточнення", (ConversationMessage("user", "Опис"),))):
            with self.assertRaises(InvalidInputError):
                service.analyze(request)
        self.assertEqual([], fake.calls)

    def test_composition_selects_two_implementations_of_same_protocol(self):
        real = get_provider(Settings(api_key="synthetic-test-key"))
        fake = get_provider(Settings(provider="fake"))
        self.assertIsInstance(real, GroqProvider)
        self.assertIsInstance(fake, FakeProvider)
        self.assertIsInstance(real, AIProvider)
        self.assertIsInstance(fake, AIProvider)
        self.assertNotIn("synthetic-test-key", repr(real.settings))
