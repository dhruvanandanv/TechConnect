"""
Semantic Vector Search Engine for TechConnect Knowledge Chunks.
Translates text queries to dense vector embeddings and executes nearest-neighbor retrieval
with strict category boundaries, similarity filtering, and role-based access constraints.
"""
import logging
from typing import List, Dict, Any, Optional

from app.embeddings.config import embedding_settings
from app.embeddings.generator import generate_embedding
from app.vector.repository import vector_repository

logger = logging.getLogger(__name__)


def perform_semantic_search(
    query: str,
    top_k: Optional[int] = None,
    min_similarity: Optional[float] = None,
    category: Optional[str] = None,
    allowed_statuses: Optional[List[str]] = None,
    allowed_article_ids: Optional[List[str]] = None,
) -> Dict[str, Any]:
    """
    Performs vector similarity search against active knowledge chunks.
    Ensures input validation on top_k and similarity threshold.
    """
    cleaned_query = (query or "").strip()
    if not cleaned_query:
        return {
            "searchType": "SEMANTIC",
            "query": "",
            "totalHits": 0,
            "results": [],
        }

    # Validate and cap top_k
    k = top_k if top_k is not None else embedding_settings.default_top_k
    if k < 1:
        k = 1
    elif k > embedding_settings.max_top_k:
        k = embedding_settings.max_top_k

    # Validate min_similarity
    threshold = (
        min_similarity
        if min_similarity is not None
        else embedding_settings.default_min_similarity
    )
    if threshold < 0.0:
        threshold = 0.0
    elif threshold > 1.0:
        threshold = 1.0

    try:
        # Embed query text
        query_vector = generate_embedding(cleaned_query)

        # Retrieve matching chunks
        matched_chunks = vector_repository.search_vectors(
            query_vector=query_vector,
            top_k=k,
            min_similarity=threshold,
            category=category,
            allowed_statuses=allowed_statuses,
            allowed_article_ids=allowed_article_ids,
        )

        return {
            "searchType": "SEMANTIC",
            "query": cleaned_query,
            "totalHits": len(matched_chunks),
            "results": matched_chunks,
        }

    except Exception as exc:
        logger.error("Semantic search failed for query '%s': %s", cleaned_query, exc, exc_info=True)
        raise RuntimeError(f"Semantic search encountered an internal error: {exc}") from exc
