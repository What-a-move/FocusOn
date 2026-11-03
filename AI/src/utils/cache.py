from __future__ import annotations

import math
import time
from collections import OrderedDict
from threading import RLock
from typing import Callable, Generic, TypeVar

T = TypeVar("T")


class TTLCache(Generic[T]):
    """Bounded process-local LRU cache. Callers store only vectors/minimal results."""

    def __init__(self, capacity: int, ttl_seconds: float, clock: Callable[[], float] = time.monotonic):
        if isinstance(capacity, bool) or not isinstance(capacity, int) or capacity < 1 or not math.isfinite(ttl_seconds) or ttl_seconds <= 0:
            raise ValueError("INVALID_CACHE_LIMITS")
        self.capacity, self.ttl_seconds, self.clock = capacity, ttl_seconds, clock
        self._items: OrderedDict[str, tuple[float, T]] = OrderedDict()
        self._lock = RLock()

    def _expire(self) -> None:
        now = self.clock()
        for key in tuple(self._items):
            if self._items[key][0] <= now:
                del self._items[key]

    def get(self, key: str) -> T | None:
        with self._lock:
            self._expire()
            item = self._items.get(key)
            if item is None:
                return None
            self._items.move_to_end(key)
            return item[1]

    def put(self, key: str, value: T) -> None:
        with self._lock:
            self._expire()
            self._items[key] = (self.clock() + self.ttl_seconds, value)
            self._items.move_to_end(key)
            while len(self._items) > self.capacity:
                self._items.popitem(last=False)

    def clear(self) -> None:
        with self._lock:
            self._items.clear()

    def __len__(self) -> int:
        with self._lock:
            self._expire()
            return len(self._items)
