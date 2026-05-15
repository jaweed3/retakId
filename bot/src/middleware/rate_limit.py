import time
import logging
from collections import defaultdict

logger = logging.getLogger(__name__)


class RateLimiter:
    def __init__(self, max_requests: int = 10, window: int = 60):
        self.max_requests = max_requests
        self.window = window
        self._buckets: dict[int, list[float]] = defaultdict(list)

    def is_limited(self, user_id: int) -> bool:
        now = time.time()
        cutoff = now - self.window
        bucket = [t for t in self._buckets[user_id] if t > cutoff]
        self._buckets[user_id] = bucket
        if len(bucket) >= self.max_requests:
            return True
        bucket.append(now)
        return False

    @property
    def stats(self) -> dict:
        return {
            "max_requests": self.max_requests,
            "window_seconds": self.window,
            "active_users": len(self._buckets),
        }
