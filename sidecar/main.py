"""Bear-1 Compression Sidecar Service.

FastAPI service that wraps The Token Company's bear-1 compression model.
Provides compression endpoints for the GovSim Java application.
"""
import sys
import os

# Add the sidecar directory to the Python path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from config import Config
from api import compression
from services.compressor import initialize_compressor


def create_app() -> FastAPI:
    """Create and configure the FastAPI application."""
    app = FastAPI(
        title="Bear-1 Compression Sidecar",
        description="Compression service for GovSim using The Token Company bear-1 model",
        version="0.1.0",
    )

    # Add CORS middleware
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # Include routers
    app.include_router(compression.router, tags=["compression"])

    # Initialize compressor if API key is available
    try:
        api_key = Config.bearer_token_required()
        initialize_compressor(api_key)
    except ValueError:
        # API key not set, run in mock mode
        print("WARNING: BEAR1_API_KEY not set, running in mock mode")

    @app.get("/")
    async def root():
        """Root endpoint."""
        return {
            "service": "Bear-1 Compression Sidecar",
            "version": "0.1.0",
            "model": Config.model(),
            "endpoints": {
                "compress": "POST /compress",
                "expand": "POST /expand",
                "metrics": "GET /metrics",
                "health": "GET /health",
            },
        }

    return app


app = create_app()


if __name__ == "__main__":
    import uvicorn

    port = Config.port()
    log_level = Config.log_level()

    print(f"Starting Bear-1 Compression Sidecar on port {port}...")
    print(f"Model: {Config.model()}")
    print(f"Cache enabled: {Config.cache_enabled()}")

    uvicorn.run(app, host="0.0.0.0", port=port, log_level=log_level)
