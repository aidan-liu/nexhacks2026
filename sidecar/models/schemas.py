"""Pydantic models for API requests and responses."""
from pydantic import BaseModel
from typing import Optional


class CompressRequest(BaseModel):
    """Request to compress text."""

    text: str
    context_type: str = "general"  # bill, facts, summary, public_comments, general
    request_id: Optional[str] = None


class CompressResponse(BaseModel):
    """Response from compression."""

    compressed_text: str
    original_tokens: int
    compressed_tokens: int
    compression_ratio: float
    latency_ms: int
    cache_hit: bool
    request_id: Optional[str] = None


class ExpandRequest(BaseModel):
    """Request to expand compressed text."""

    compressed_text: str
    context_type: str = "general"


class ExpandResponse(BaseModel):
    """Response from expansion."""

    expanded_text: str
    latency_ms: int


class ContextMetrics(BaseModel):
    """Metrics for a specific context type."""

    count: int
    avg_ratio: float
    total_original_tokens: int
    total_compressed_tokens: int


class MetricsResponse(BaseModel):
    """Overall compression metrics."""

    total_compressions: int
    total_original_tokens: int
    total_compressed_tokens: int
    average_compression_ratio: float
    average_latency_ms: float
    cache_hit_rate: float
    by_context_type: dict[str, ContextMetrics]


class HealthResponse(BaseModel):
    """Health check response."""

    status: str
    model: str
    cache_enabled: bool
