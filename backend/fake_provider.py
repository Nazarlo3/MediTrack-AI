"""Explicit synthetic demo/test provider, never an automatic medical fallback."""

from domain import AnalysisInput, AnalysisResult, ProviderUnavailableError


class FakeProvider:
    def __init__(self, *, fail: bool = False):
        self.fail = fail
        self.calls: list[AnalysisInput] = []

    def generate(self, request: AnalysisInput) -> AnalysisResult:
        self.calls.append(request)
        if self.fail:
            raise ProviderUnavailableError()
        return AnalysisResult(
            category="інше",
            explanation="СИНТЕТИЧНА ДЕМО-ВІДПОВІДЬ. Зовнішній AI не викликався; це перевірка архітектури.",
            urgency="звичайне",
            follow_up_question="Чи потрібно уточнити синтетичний тестовий опис?",
        )
