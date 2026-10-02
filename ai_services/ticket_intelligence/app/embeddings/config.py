"""
TechConnect Knowledge Embeddings Configuration.
Defines parameters for model loading, dimension verification, chunking thresholds,
and vector similarity scoring.
"""
import os
from pydantic import BaseModel, Field


class EmbeddingSettings(BaseModel):
    model_name: str = Field(
        default_factory=lambda: os.getenv("TECHCONNECT_EMBEDDING_MODEL", "sentence-transformers/all-MiniLM-L6-v2")
    )
    model_version: str = Field(
        default_factory=lambda: os.getenv("TECHCONNECT_EMBEDDING_MODEL_VERSION", "1.0.0")
    )
    expected_dimensions: int = 384
    distance_metric: str = "cosine"
    
    # Chunking parameters
    max_chunk_chars: int = Field(default_factory=lambda: int(os.getenv("TECHCONNECT_MAX_CHUNK_CHARS", "500")))
    chunk_overlap_chars: int = Field(default_factory=lambda: int(os.getenv("TECHCONNECT_CHUNK_OVERLAP_CHARS", "80")))
    
    # Retrieval parameters
    default_top_k: int = 5
    max_top_k: int = 20
    default_min_similarity: float = Field(
        default_factory=lambda: float(os.getenv("TECHCONNECT_SIMILARITY_THRESHOLD", "0.50"))
    )


embedding_settings = EmbeddingSettings()
