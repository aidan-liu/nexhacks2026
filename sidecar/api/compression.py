"""Compression API endpoints."""
import time
from typing import Dict
from fastapi import APIRouter, HTTPException, status
from models.schemas import (
    CompressRequest,
    CompressResponse,
    ExpandRequest,
    ExpandResponse,
    MetricsResponse,
    ContextMetrics,
)
from services.compressor import get_compressor, initialize_compressor
from services.cache import get_cache
from config import Config

router = APIRouter()

# Metrics tracking
_metrics: Dict[str, Dict] = {
    "total_compressions": 0,
    "total_original_tokens": 0,
    "total_compressed_tokens": 0,
    "total_latency_ms": 0,
    "cache_hits": 0,
    "by_context_type": {},
}


def _update_metrics(
    original_tokens: int,
    compressed_tokens: int,
    latency_ms: int,
    cache_hit: bool,
    context_type: str,
):
    """Update global metrics."""
    _metrics["total_compressions"] += 1
    _metrics["total_original_tokens"] += original_tokens
    _metrics["total_compressed_tokens"] += compressed_tokens
    _metrics["total_latency_ms"] += latency_ms
    if cache_hit:
        _metrics["cache_hits"] += 1

    # Track by context type
    if context_type not in _metrics["by_context_type"]:
        _metrics["by_context_type"][context_type] = {
            "count": 0,
            "original_tokens": 0,
            "compressed_tokens": 0,
        }

    _metrics["by_context_type"][context_type]["count"] += 1
    _metrics["by_context_type"][context_type]["original_tokens"] += original_tokens
    _metrics["by_context_type"][context_type]["compressed_tokens"] += compressed_tokens


@router.post("/compress", response_model=CompressResponse)
async def compress(request: CompressRequest):
    """Compress text using bear-1 model."""
    if not request.text:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST, detail="text is required"
        )

    try:
        compressor = get_compressor()
        compressed, orig_toks, comp_toks, ratio, cache_hit = compressor.compress(
            request.text, request.context_type
        )

        _update_metrics(orig_toks, comp_toks, 0, cache_hit, request.context_type)

        return CompressResponse(
            compressed_text=compressed,
            original_tokens=orig_toks,
            compressed_tokens=comp_toks,
            compression_ratio=ratio,
            latency_ms=0,  # Already tracked in compressor
            cache_hit=cache_hit,
            request_id=request.request_id,
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(e)
        )


@router.post("/expand", response_model=ExpandResponse)
async def expand(request: ExpandRequest):
    """Expand compressed text (for accuracy verification)."""
    if not request.compressed_text:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST, detail="compressed_text is required"
        )

    try:
        compressor = get_compressor()
        expanded, latency_ms = compressor.expand(
            request.compressed_text, request.context_type
        )

        return ExpandResponse(expanded_text=expanded, latency_ms=latency_ms)
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(e)
        )


@router.get("/metrics", response_model=MetricsResponse)
async def get_metrics():
    """Get compression metrics."""
    total = _metrics["total_compressions"]

    if total == 0:
        return MetricsResponse(
            total_compressions=0,
            total_original_tokens=0,
            total_compressed_tokens=0,
            average_compression_ratio=0,
            average_latency_ms=0,
            cache_hit_rate=0,
            by_context_type={},
        )

    # Build context type breakdown
    by_context = {}
    for ctx, data in _metrics["by_context_type"].items():
        count = data["count"]
        by_context[ctx] = ContextMetrics(
            count=count,
            avg_ratio=data["compressed_tokens"] / data["original_tokens"]
            if data["original_tokens"] > 0
            else 0,
            total_original_tokens=data["original_tokens"],
            total_compressed_tokens=data["compressed_tokens"],
        )

    return MetricsResponse(
        total_compressions=total,
        total_original_tokens=_metrics["total_original_tokens"],
        total_compressed_tokens=_metrics["total_compressed_tokens"],
        average_compression_ratio=_metrics["total_compressed_tokens"]
        / _metrics["total_original_tokens"],
        average_latency_ms=_metrics["total_latency_ms"] / total,
        cache_hit_rate=_metrics["cache_hits"] / total,
        by_context_type=by_context,
    )


@router.post("/reset-metrics")
async def reset_metrics():
    """Reset all metrics."""
    global _metrics
    _metrics = {
        "total_compressions": 0,
        "total_original_tokens": 0,
        "total_compressed_tokens": 0,
        "total_latency_ms": 0,
        "cache_hits": 0,
        "by_context_type": {},
    }
    return {"status": "metrics reset"}


@router.get("/health")
async def health():
    """Health check endpoint."""
    return {
        "status": "healthy",
        "model": Config.model(),
        "cache_enabled": Config.cache_enabled(),
    }
