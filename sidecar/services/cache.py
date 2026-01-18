"""In-memory compression cache."""
import hashlib
import time
from typing import Optional, Tuple
from config import Config


class CacheEntry:
    """A cached compression result."""

    def __init__(self, compressed_text: str, original_tokens: int, compressed_tokens: int):
        self.compressed_text = compressed_text
        self.original_tokens = original_tokens
        self.compressed_tokens = compressed_tokens
        self.timestamp = time.time()

    def is_expired(self, ttl_seconds: int) -> bool:
        """Check if this entry has expired."""
        return time.time() - self.timestamp > ttl_seconds


class CompressionCache:
    """Thread-safe in-memory cache for compression results."""

    def __init__(self):
        self._cache: dict[str, CacheEntry] = {}
        self._enabled = Config.cache_enabled()
        self._ttl = Config.cache_ttl_seconds()

    def _hash_key(self, text: str) -> str:
        """Generate a hash key for the text."""
        return hashlib.sha256(text.encode()).hexdigest()

    def get(self, text: str) -> Optional[CacheEntry]:
        """Get cached result if available and not expired."""
        if not self._enabled:
            return None

        key = self._hash_key(text)
        entry = self._cache.get(key)

        if entry is None:
            return None

        if entry.is_expired(self._ttl):
            del self._cache[key]
            return None

        return entry

    def put(self, text: str, compressed_text: str, original_tokens: int, compressed_tokens: int):
        """Store a compression result in the cache."""
        if not self._enabled:
            return

        key = self._hash_key(text)
        self._cache[key] = CacheEntry(compressed_text, original_tokens, compressed_tokens)

    def clear(self):
        """Clear all cache entries."""
        self._cache.clear()

    def size(self) -> int:
        """Return the number of cached entries."""
        return len(self._cache)


# Global cache instance
_cache: Optional[CompressionCache] = None


def get_cache() -> CompressionCache:
    """Get the global cache instance."""
    global _cache
    if _cache is None:
        _cache = CompressionCache()
    return _cache
