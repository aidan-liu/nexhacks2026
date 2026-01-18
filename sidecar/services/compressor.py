"""Bear-1 compression service wrapper."""
import time
from typing import Optional
from services.cache import get_cache, CacheEntry


class Compressor:
    """Wrapper around Token Company bear-1 compression API."""

    def __init__(self):
        self.api_key = None
        self._mock_mode = True  # Start in mock mode until API key is provided
        self._call_count = 0

    def set_api_key(self, api_key: str):
        """Set the API key and disable mock mode."""
        self.api_key = api_key
        self._mock_mode = False

    def compress(self, text: str, context_type: str = "general") -> tuple[str, int, int, float, bool]:
        """
        Compress text using bear-1.

        Returns:
            (compressed_text, original_tokens, compressed_tokens, compression_ratio, cache_hit)
        """
        cache = get_cache()
        cached = cache.get(text)

        if cached:
            return (
                cached.compressed_text,
                cached.original_tokens,
                cached.compressed_tokens,
                cached.compressed_tokens / cached.original_tokens if cached.original_tokens > 0 else 0,
                True,
            )

        start = time.time()

        if self._mock_mode:
            # Mock compression: remove filler words and redundant phrases
            compressed, orig_toks, comp_toks = self._mock_compress(text)
        else:
            # Real bear-1 API call would go here
            # For now, using mock as fallback
            compressed, orig_toks, comp_toks = self._mock_compress(text)

        latency_ms = int((time.time() - start) * 1000)
        ratio = comp_toks / orig_toks if orig_toks > 0 else 0

        # Cache the result
        cache.put(text, compressed, orig_toks, comp_toks)

        self._call_count += 1
        return compressed, orig_toks, comp_toks, ratio, False

    def _mock_compress(self, text: str) -> tuple[str, int, int]:
        """
        Mock compression for demo purposes.

        Removes common filler patterns to simulate 40-60% compression.
        """
        import re

        original_tokens = self._estimate_tokens(text)

        # Remove redundant phrases
        patterns_to_remove = [
            r"\b(in order to)\b",
            r"\b(at this point in time)\b",
            r"\b(for the purpose of)\b",
            r"\b(in the event that)\b",
            r"\b(it should be noted that)\b",
            r"\b(it is important to note that)\b",
            r"\b(due to the fact that)\b",
            r"\b(in terms of)\b",
            r"\b(on the basis of)\b",
            r"\b(with regard to)\b",
            r"\b(in the case of)\b",
            r"\b(for the most part)\b",
            r"\b(as a matter of)\b",
        ]

        compressed = text
        for pattern in patterns_to_remove:
            compressed = re.sub(pattern, "", compressed, flags=re.IGNORECASE)

        # Remove excessive whitespace
        compressed = re.sub(r"\s+", " ", compressed)
        compressed = re.sub(r"\n\s*\n", "\n", compressed)

        # If compression didn't achieve 40% reduction, apply more aggressive
        current_ratio = len(compressed) / len(text) if text else 1
        if current_ratio > 0.7:
            # Remove minor words (articles, some prepositions)
            compressed = re.sub(r"\b(the|a|an)\b", "", compressed, flags=re.IGNORECASE)
            compressed = re.sub(r"\s+", " ", compressed)

        # Calculate mock tokens
        compressed_tokens = self._estimate_tokens(compressed)

        return compressed.strip(), original_tokens, compressed_tokens

    def _estimate_tokens(self, text: str) -> int:
        """Estimate token count (rough approximation: 4 chars per token)."""
        return max(1, len(text) // 4)

    def expand(self, compressed_text: str, context_type: str = "general") -> tuple[str, int]:
        """
        Expand compressed text (for accuracy verification).

        Returns:
            (expanded_text, latency_ms)
        """
        start = time.time()

        if self._mock_mode:
            # In mock mode, just return the compressed text as-is
            # since we can't truly expand without the original
            expanded = compressed_text
        else:
            # Real bear-1 expansion would go here
            expanded = compressed_text

        latency_ms = int((time.time() - start) * 1000)
        return expanded, latency_ms


# Global compressor instance
_compressor: Optional[Compressor] = None


def get_compressor() -> Compressor:
    """Get the global compressor instance."""
    global _compressor
    if _compressor is None:
        _compressor = Compressor()
    return _compressor


def initialize_compressor(api_key: str):
    """Initialize the compressor with an API key."""
    compressor = get_compressor()
    compressor.set_api_key(api_key)
    return compressor
