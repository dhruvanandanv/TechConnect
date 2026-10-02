"""
Embedding vector generator.
Generates normalized, dense vector representations for individual texts and batches.
"""
import logging
from typing import List
import numpy as np

from app.embeddings.config import embedding_settings
from app.embeddings.model import get_embedding_model, get_actual_dimensions

logger = logging.getLogger(__name__)


def generate_embedding(text: str) -> List[float]:
    """
    Generates a dense vector embedding for a single text string.
    Normalizes whitespace and verifies vector dimension.
    """
    cleaned = (text or "").strip()
    if not cleaned:
        cleaned = "empty content"

    model = get_embedding_model()
    vectors = list(model.embed([cleaned]))
    vec = vectors[0]

    if isinstance(vec, np.ndarray):
        vec = vec.tolist()

    if len(vec) != embedding_settings.expected_dimensions:
        raise ValueError(
            f"Generated vector dimension {len(vec)} does not match expected {embedding_settings.expected_dimensions}"
        )

    return [float(x) for x in vec]


def generate_embeddings(texts: List[str]) -> List[List[float]]:
    """
    Generates dense vector embeddings for a list of text strings in batch.
    """
    if not texts:
        return []

    cleaned_texts = [(t or "").strip() or "empty content" for t in texts]
    model = get_embedding_model()
    raw_vectors = list(model.embed(cleaned_texts))

    results: List[List[float]] = []
    expected_dim = embedding_settings.expected_dimensions

    for idx, vec in enumerate(raw_vectors):
        if isinstance(vec, np.ndarray):
            vec = vec.tolist()
        if len(vec) != expected_dim:
            raise ValueError(
                f"Generated vector at index {idx} has dimension {len(vec)}, expected {expected_dim}"
            )
        results.append([float(x) for x in vec])

    return results
