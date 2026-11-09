"""Best effort persistence, independent of the AI result and application logger."""
import logging
import re
from collection_repository import CollectionRepository

logger = logging.getLogger('meditrack.collection')


def valid_token(token):
    return bool(token and re.fullmatch(r'[a-f0-9]{64}', token))


class CollectionService:
    def __init__(self, repository: CollectionRepository | None, model: str):
        self.repository = repository
        self.model = model

    def record(self, metadata, token, request, response, elapsed_ms):
        if metadata is None:
            return 'not_requested'
        if self.repository is None:
            return 'disabled'
        if metadata.consent_version < getattr(self.repository, 'required_consent_version', 1):
            return 'consent_required'
        if not valid_token(token):
            return 'rejected'
        try:
            # Persist only this turn, never the potentially non-consented earlier context.
            payload = request.model_dump(include={'description', 'duration_days', 'intensity'})
            return self.repository.save(token, str(metadata.request_id), payload,
                                        response.model_dump(), self.model, elapsed_ms,
                                        consent_version=metadata.consent_version)
        except Exception:
            logger.warning('collection_write_failed')
            return 'unavailable'
