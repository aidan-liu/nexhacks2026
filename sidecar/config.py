"""Configuration for bear-1 compression sidecar."""
import os
from typing import Optional


class Config:
    """Sidecar configuration from environment variables."""

    @staticmethod
    def bearer_token() -> Optional[str]:
        """Token Company API bearer token."""
        return os.getenv("BEAR1_API_KEY", "")

    @staticmethod
    def bearer_token_required() -> str:
        """Token Company API bearer token (raises if missing)."""
        token = Config.bearer_token()
        if not token:
            raise ValueError("BEAR1_API_KEY environment variable is required")
        return token

    @staticmethod
    def port() -> int:
        """Port to listen on."""
        return int(os.getenv("SIDECAR_PORT", "8001"))

    @staticmethod
    def model() -> str:
        """Compression model to use."""
        return os.getenv("BEAR1_MODEL", "bear-1")

    @staticmethod
    def cache_enabled() -> bool:
        """Whether compression caching is enabled."""
        return os.getenv("CACHE_ENABLED", "true").lower() == "true"

    @staticmethod
    def cache_ttl_seconds() -> int:
        """Cache TTL in seconds."""
        return int(os.getenv("CACHE_TTL_SECONDS", "3600"))

    @staticmethod
    def log_level() -> str:
        """Log level."""
        return os.getenv("LOG_LEVEL", "info")
