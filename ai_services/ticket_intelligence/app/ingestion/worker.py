"""
Knowledge Ingestion Worker.
Coordinates deterministic chunking, batch vector embedding, and state-safe persistence
for pending and re-indexed knowledge articles.
"""
import logging
from typing import List, Dict, Any

from app.embeddings.config import embedding_settings
from app.embeddings.generator import generate_embeddings
from app.ingestion.chunker import chunk_article
from app.vector.repository import vector_repository

logger = logging.getLogger(__name__)


def process_single_article(article_data: Dict[str, Any]) -> Dict[str, Any]:
    """
    Ingests or reindexes a single article:
    1. Breaks down content into section-aware chunks.
    2. Batch-generates dense vector embeddings.
    3. Invalidates/removes stale version vectors.
    4. Persists active chunks in vector repository.
    """
    article_id = str(article_data.get("id") or article_data.get("articleId"))
    if not article_id:
        raise ValueError("Article must possess a valid 'id' or 'articleId'")

    title = article_data.get("title", "")
    summary = article_data.get("summary", "")
    problem = article_data.get("problem", "")
    cause = article_data.get("cause", "")
    resolution = article_data.get("resolution", "")
    content = article_data.get("content", "")
    category = article_data.get("category", "GENERAL")
    tags = article_data.get("tags") or []
    status = article_data.get("status", "PUBLISHED")
    version = int(article_data.get("version", 1))

    logger.info("Ingesting article '%s' (v%d, status: %s)...", article_id, version, status)

    try:
        # Step 1: Chunk article
        chunks = chunk_article(
            article_id=article_id,
            title=title,
            summary=summary,
            problem=problem,
            cause=cause,
            resolution=resolution,
            content=content,
            category=category,
            tags=tags,
            article_version=version,
        )

        if not chunks:
            logger.warning("No chunks generated for article '%s'", article_id)
            return {
                "success": True,
                "articleId": article_id,
                "version": version,
                "chunksCreated": 0,
                "chunksEmbedded": 0,
            }

        # Step 2: Batch embed chunks
        chunk_texts = [c["text"] for c in chunks]
        embeddings = generate_embeddings(chunk_texts)

        for i, c in enumerate(chunks):
            c["embedding"] = embeddings[i]
            c["embeddingModel"] = embedding_settings.model_name
            c["status"] = status

        # Step 3: Invalidate older versions in vector store
        vector_repository.delete_chunks_by_article(article_id, keep_version=version)

        # Step 4: Persist chunks
        saved_count = vector_repository.save_chunks(chunks)

        logger.info(
            "Successfully embedded article '%s' (v%d): %d chunks stored.",
            article_id,
            version,
            saved_count,
        )

        return {
            "success": True,
            "articleId": article_id,
            "version": version,
            "chunksCreated": len(chunks),
            "chunksEmbedded": saved_count,
        }

    except Exception as exc:
        safe_msg = f"Failed to ingest article: {type(exc).__name__} - {str(exc)}"
        logger.error("Ingestion failed for article '%s': %s", article_id, exc, exc_info=True)
        return {
            "success": False,
            "articleId": article_id,
            "version": version,
            "chunksCreated": 0,
            "chunksEmbedded": 0,
            "error": safe_msg,
        }


def process_article_batch(articles: List[Dict[str, Any]]) -> Dict[str, Any]:
    """
    Ingests a batch of knowledge articles, tracking discovery, processing,
    chunk counts, embeddings, and failure tallies.
    """
    discovered = len(articles)
    processed = 0
    chunks_created = 0
    chunks_embedded = 0
    failures = 0
    results: List[Dict[str, Any]] = []

    for item in articles:
        outcome = process_single_article(item)
        results.append(outcome)
        if outcome["success"]:
            processed += 1
            chunks_created += outcome["chunksCreated"]
            chunks_embedded += outcome["chunksEmbedded"]
        else:
            failures += 1

    return {
        "articlesDiscovered": discovered,
        "articlesProcessed": processed,
        "chunksCreated": chunks_created,
        "chunksEmbedded": chunks_embedded,
        "failures": failures,
        "results": results,
    }
