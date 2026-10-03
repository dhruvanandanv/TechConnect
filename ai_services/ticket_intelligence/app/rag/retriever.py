"""
Stage 1: RETRIEVAL Module
Reuses Phase 11 semantic search to retrieve top-K knowledge chunks from the vector database.
Enforces quality gates, similarity thresholds, and RBAC authorization filters.
"""
import logging
from typing import List, Optional

from app.rag.config import rag_settings
from app.rag.models import RagAnswerRequest, RagSourceChunk
from app.vector.search import perform_semantic_search

logger = logging.getLogger(__name__)


def retrieve_relevant_chunks(request: RagAnswerRequest) -> List[RagSourceChunk]:
    """
    Retrieves grounded knowledge chunks matching the user's query and optional ticket context.
    Filters weak matches below minSimilarity and strictly respects RBAC allowedStatuses.
    """
    effective_top_k = min(
        request.topK if request.topK and request.topK > 0 else rag_settings.rag_top_k,
        rag_settings.rag_max_top_k
    )
    effective_min_sim = (
        request.minSimilarity if request.minSimilarity is not None
        else rag_settings.rag_min_similarity
    )

    # Formulate effective search query incorporating advisory ticket context if present
    search_query = request.query.strip()
    if request.ticketContext and request.ticketContext.title:
        # If query is short or generic, append ticket title for higher domain relevance
        if len(search_query.split()) <= 4:
            search_query = f"{search_query} {request.ticketContext.title.strip()}"

    # Category filter
    category = request.category
    if not category and request.ticketContext and request.ticketContext.category:
        category = request.ticketContext.category

    logger.debug(
        "RAG Retrieval: query='%s', category=%s, top_k=%d, min_sim=%.2f",
        search_query, category, effective_top_k, effective_min_sim
    )

    try:
        search_res = perform_semantic_search(
            query=search_query,
            category=category,
            top_k=effective_top_k,
            min_similarity=effective_min_sim,
            allowed_statuses=request.allowedStatuses,
            allowed_article_ids=request.allowedArticleIds
        )
    except Exception as e:
        logger.error("Vector retrieval failed in RAG pipeline: %s", e)
        return []

    raw_results = search_res.get("results", []) if isinstance(search_res, dict) else (search_res or [])

    if not raw_results:
        logger.info("RAG Retrieval: 0 chunks met similarity threshold (%.2f)", effective_min_sim)
        return []

    # Map raw dictionary results to structured RagSourceChunk objects
    chunks: List[RagSourceChunk] = []
    for r in raw_results:
        if not isinstance(r, dict):
            continue
        similarity = float(r.get("similarity", 0.0))
        if similarity < effective_min_sim:
            continue

        # Extract title cleanly from chunk or first line header
        title = str(r.get("title") or "")
        content = str(r.get("content") or "").strip()
        if not title and content:
            first_line = content.split("\n")[0].strip()
            if first_line.startswith("[") and "]" in first_line:
                title = first_line.split("]", 1)[1].strip()
        if not title:
            title = f"Article #{r.get('articleId', '')}"

        chunks.append(
            RagSourceChunk(
                articleId=str(r.get("articleId", "")),
                chunkId=str(r.get("chunkId", "")),
                articleVersion=int(r.get("articleVersion", 1)),
                title=title,
                section=str(r.get("section", "GENERAL")),
                content=content,
                similarity=round(similarity, 4),
                category=r.get("category"),
                tags=r.get("tags") or []
            )
        )

    logger.info("RAG Retrieval: Selected %d grounded chunk(s). Best similarity: %.4f",
                len(chunks), chunks[0].similarity if chunks else 0.0)
    return chunks
