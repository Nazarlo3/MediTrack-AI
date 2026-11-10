"""Best effort persistence, independent of the AI result and application logger."""
import logging
import re
import threading
from collections import Counter
from collection_repository import CollectionRepository
from collection_dispatcher import Job

logger = logging.getLogger('meditrack.collection')


def valid_token(token):
    return bool(token and re.fullmatch(r'[a-f0-9]{64}', token))


class CollectionService:
    def __init__(self, repository: CollectionRepository | None, model: str, dispatcher=None, unavailable=False):
        self.repository = repository
        self.model = model
        self.dispatcher = dispatcher
        self.unavailable = unavailable
        self.lock = threading.RLock()
        self.active = Counter()

    def begin(self, metadata, token):
        if metadata is not None and self.repository is not None and valid_token(token):
            with self.lock:
                self.active[token] += 1
            return token
        return None

    def end(self, ticket):
        if ticket is not None:
            with self.lock:
                self.active[ticket] -= 1
                if self.active[ticket] == 0:
                    del self.active[ticket]

    def delete(self, token):
        if self.unavailable:
            raise OSError('Collection outbox unavailable')
        with self.lock:
            pending = self.active[token] > 0 or (self.dispatcher is not None and self.dispatcher.has_owner(token))
            if self.dispatcher is not None:
                pending = self.dispatcher.revoke(token, pending=pending) or pending
            return self.repository.delete(token, pending=pending)

    def record(self, metadata, token, request, response, elapsed_ms):
        if metadata is None:
            return 'not_requested'
        if self.unavailable:
            return 'unavailable'
        if self.repository is None:
            return 'disabled'
        if metadata.consent_version < getattr(self.repository, 'required_consent_version', 1):
            return 'consent_required'
        if not valid_token(token):
            return 'rejected'
        try:
            # Persist only this turn, never the potentially non-consented earlier context.
            payload = request.model_dump(include={'description', 'duration_days', 'intensity'})
            if self.dispatcher is not None:
                return self.dispatcher.submit(Job(self.repository.owner(token), str(metadata.request_id), payload,
                    response.model_dump(), self.model, elapsed_ms, metadata.consent_version))
            return self.repository.save(token, str(metadata.request_id), payload,
                                        response.model_dump(), self.model, elapsed_ms,
                                        consent_version=metadata.consent_version)
        except Exception:
            logger.warning('collection_write_failed')
            return 'unavailable'
