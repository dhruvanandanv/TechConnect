"""
Thread-safe singleton loader for the local embedding model (all-MiniLM-L6-v2).
Caches the loaded model in memory to prevent repeated disk I/O and re-initialization.
Programmatically validates output vector dimension.
"""
import logging
import threading
from typing import Optional
from app.embeddings.config import embedding_settings

logger = logging.getLogger(__name__)

_model_lock = threading.Lock()
_model_instance = None
_actual_dimensions: Optional[int] = None


def get_embedding_model():
    """
    Returns the loaded singleton embedding model instance.
    Initializes on first call and verifies output vector dimensions.
    """
    global _model_instance, _actual_dimensions

    if _model_instance is not None:
        return _model_instance

    with _model_lock:
        if _model_instance is not None:
            return _model_instance

        logger.info(
            "Loading local embedding model '%s' (version: %s)...",
            embedding_settings.model_name,
            embedding_settings.model_version,
        )

        try:
            from fastembed import TextEmbedding

            instance = TextEmbedding(model_name=embedding_settings.model_name)

            # Programmatically verify actual dimension
            test_vector = list(instance.embed(["techconnect test query"]))[0]
            _actual_dimensions = len(test_vector)

            if _actual_dimensions != embedding_settings.expected_dimensions:
                raise ValueError(
                    f"Model dimension mismatch: expected {embedding_settings.expected_dimensions}, "
                    f"got {_actual_dimensions}"
                )

            logger.info(
                "Embedding model '%s' initialized successfully. Verified vector dimension: %d",
                embedding_settings.model_name,
                _actual_dimensions,
            )
            _model_instance = instance
            return _model_instance

        except Exception as exc:
            logger.error("Failed to load fastembed TextEmbedding model: %s", exc, exc_info=True)
            raise RuntimeError(f"Could not initialize embedding model: {exc}") from exc


def get_actual_dimensions() -> int:
    """Returns the verified embedding dimensions."""
    global _actual_dimensions
    if _actual_dimensions is None:
        get_embedding_model()
    return _actual_dimensions or embedding_settings.expected_dimensions
