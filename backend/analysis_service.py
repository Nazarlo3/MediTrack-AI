"""One application scenario, independent of transport and concrete AI provider."""

from dataclasses import replace

from domain import AIProvider, AnalysisInput, AnalysisResult, EmptyDescriptionError, InvalidInputError


class AnalysisService:
    def __init__(self, provider: AIProvider):
        self.provider = provider

    def analyze(self, request: AnalysisInput) -> AnalysisResult:
        if not 3 <= len(request.description) <= 500:
            raise InvalidInputError()
        description = request.description.strip()
        if not description:
            raise EmptyDescriptionError()
        for value, minimum, maximum in ((request.duration_days, 1, 30), (request.intensity, 0, 10)):
            if value is not None and (type(value) is not int or not minimum <= value <= maximum):
                raise InvalidInputError()
        if len(request.context) > 6 or len(request.context) % 2:
            raise InvalidInputError()
        for index, turn in enumerate(request.context):
            if (turn.role != ("user" if index % 2 == 0 else "assistant")
                    or not turn.content.strip() or len(turn.content) > 1500):
                raise InvalidInputError()
        # Preserve the pre-refactor length semantics; changing trim validation is separate.
        result = self.provider.generate(replace(request, description=description))
        return replace(result, context_used=bool(request.context),
                       details_used=request.duration_days is not None or request.intensity is not None)
