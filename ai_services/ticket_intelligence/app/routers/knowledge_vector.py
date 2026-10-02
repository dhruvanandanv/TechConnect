"""
FastAPI Router for Knowledge Embeddings, Ingestion, and Semantic Vector Search.
Provides microservice endpoints for Spring Boot to trigger ingestion batches, reindexing,
and semantic query retrieval.
"""
import logging
from typing import List, Optional, Dict, Any
from fastapi import APIRouter, HTTPException, Query, status
from pydantic import BaseModel, Field

from app.embeddings.config import embedding_settings
from app.embeddings.generator import generate_embeddings
from app.embeddings.model import get_actual_dimensions
from app.ingestion.worker import process_single_article, process_article_batch
from app.vector.repository import vector_repository
from app.vector.search import perform_semantic_search

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/knowledge", tags=["Knowledge Vector Intelligence"])


# ==============================================================================
# Request & Response Schemas
# ==============================================================================

class EmbedRequest(BaseModel):
    texts: List[str] = Field(..., min_length=1, description="List of strings to embed")


class EmbedResponse(BaseModel):
    model: str
    dimensions: int
    embeddings: List[List[float]]


class ArticlePayload(BaseModel):
    id: Optional[str] = None
    articleId: Optional[str] = None
    title: Optional[str] = ""
    summary: Optional[str] = ""
    problem: Optional[str] = ""
    cause: Optional[str] = ""
    resolution: Optional[str] = ""
    content: Optional[str] = ""
    category: Optional[str] = "GENERAL"
    tags: Optional[List[str]] = Field(default_factory=list)
    status: Optional[str] = "PUBLISHED"
    version: Optional[int] = 1


class IngestionBatchRequest(BaseModel):
    articles: List[ArticlePayload] = Field(..., description="List of articles to ingest")


class SemanticSearchRequest(BaseModel):
    query: str = Field(..., min_length=1, max_length=1000, description="Natural language search query")
    category: Optional[str] = Field(default=None, description="Optional TicketCategory filter")
    topK: Optional[int] = Field(default=5, ge=1, le=20, description="Top-K nearest chunks to return")
    minSimilarity: Optional[float] = Field(default=0.50, ge=0.0, le=1.0, description="Minimum cosine similarity")
    allowedStatuses: Optional[List[str]] = Field(default=None, description="RBAC allowed statuses")
    allowedArticleIds: Optional[List[str]] = Field(default=None, description="RBAC allowed article IDs")


class UpdateArticleStatusRequest(BaseModel):
    status: str = Field(..., description="New status (e.g. PUBLISHED, DRAFT, ARCHIVED)")


# ==============================================================================
# Endpoints
# ==============================================================================

@router.post("/embed", response_model=EmbedResponse)
def embed_texts(request: EmbedRequest):
    """Generates dense vector embeddings for input texts."""
    try:
        vectors = generate_embeddings(request.texts)
        return EmbedResponse(
            model=embedding_settings.model_name,
            dimensions=get_actual_dimensions(),
            embeddings=vectors,
        )
    except Exception as exc:
        logger.error("Embedding generation failed: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to generate embeddings",
        )


@router.post("/ingest")
def ingest_articles(request: IngestionBatchRequest):
    """Processes a batch of articles: chunking, embedding, and vector persistence."""
    try:
        articles_dicts = [a.model_dump() for a in request.articles]
        batch_summary = process_article_batch(articles_dicts)
        return batch_summary
    except Exception as exc:
        logger.error("Ingestion batch failed: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to process ingestion batch",
        )


@router.post("/reindex")
def reindex_article(article: ArticlePayload):
    """Reindexes a single article version and invalidates older chunks."""
    try:
        result = process_single_article(article.model_dump())
        return result
    except Exception as exc:
        logger.error("Reindexing failed: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to reindex article",
        )


@router.post("/semantic-search")
def semantic_search(request: SemanticSearchRequest):
    """Performs cosine vector search against active knowledge chunks."""
    try:
        return perform_semantic_search(
            query=request.query,
            top_k=request.topK,
            min_similarity=request.minSimilarity,
            category=request.category,
            allowed_statuses=request.allowedStatuses,
            allowed_article_ids=request.allowedArticleIds,
        )
    except Exception as exc:
        logger.error("Semantic search failed: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Semantic search is temporarily unavailable",
        )


@router.patch("/articles/{article_id}/status")
def update_status(article_id: str, request: UpdateArticleStatusRequest):
    """Updates chunk status (e.g. when an article transitions to ARCHIVED or DRAFT)."""
    count = vector_repository.update_article_status(article_id, request.status)
    return {"articleId": article_id, "status": request.status, "updatedChunks": count}


@router.delete("/articles/{article_id}")
def delete_article_chunks(article_id: str):
    """Deletes all vector chunks associated with an article."""
    deleted = vector_repository.delete_chunks_by_article(article_id)
    return {"articleId": article_id, "deletedChunks": deleted}


@router.get("/stats")
def get_vector_stats():
    """Returns vector repository metadata and active chunk statistics."""
    stats = vector_repository.get_stats()
    stats["embeddingModel"] = embedding_settings.model_name
    stats["dimensions"] = get_actual_dimensions()
    stats["distanceMetric"] = embedding_settings.distance_metric
    return stats
