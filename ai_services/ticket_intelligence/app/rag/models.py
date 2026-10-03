"""
Pydantic Data Transfer Objects and schemas for TechConnect RAG Support Copilot.
"""
from typing import List, Optional, Dict, Any
from pydantic import BaseModel, Field


class TicketContext(BaseModel):
    """
    Advisory ticket information used to ground troubleshooting recommendations.
    Never causes automated changes to the ticket.
    """
    id: Optional[int] = Field(default=None, description="Ticket ID")
    title: Optional[str] = Field(default=None, description="Ticket title")
    description: Optional[str] = Field(default=None, description="Ticket description")
    category: Optional[str] = Field(default=None, description="Ticket category")
    priority: Optional[str] = Field(default=None, description="Ticket priority")
    status: Optional[str] = Field(default=None, description="Ticket current status")


class RagAnswerRequest(BaseModel):
    """
    Request payload sent to POST /api/v1/rag/answer
    """
    query: str = Field(..., min_length=1, max_length=1000, description="User IT support inquiry")
    category: Optional[str] = Field(default=None, description="Optional TicketCategory filter")
    topK: Optional[int] = Field(default=5, ge=1, le=10, description="Top-K vector chunks to retrieve")
    minSimilarity: Optional[float] = Field(default=0.30, ge=0.0, le=1.0, description="Minimum cosine similarity")
    ticketContext: Optional[TicketContext] = Field(default=None, description="Optional active ticket metadata")
    allowedStatuses: Optional[List[str]] = Field(default=None, description="RBAC allowed article publication statuses")
    allowedArticleIds: Optional[List[str]] = Field(default=None, description="Optional RBAC authorized article IDs")


class RagSourceChunk(BaseModel):
    """
    Structured provenance metadata for each retrieved knowledge chunk.
    """
    articleId: str
    chunkId: str
    articleVersion: int
    title: str
    section: str
    content: str
    similarity: float
    category: Optional[str] = None
    tags: Optional[List[str]] = Field(default_factory=list)


class RagRetrievalMeta(BaseModel):
    """
    Retrieval audit statistics.
    """
    topK: int
    resultsUsed: int
    bestSimilarity: float


class RagAnswerResponse(BaseModel):
    """
    Structured RAG response payload.
    """
    answer: str = Field(..., description="Grounded troubleshooting synthesis or safe fallback message")
    grounded: bool = Field(..., description="True if answer is backed by relevant knowledge base articles")
    confidence: Optional[float] = Field(default=None, description="Retrieval quality / best similarity score")
    sources: List[RagSourceChunk] = Field(default_factory=list, description="Citations to knowledge base articles")
    retrieval: Optional[RagRetrievalMeta] = Field(default=None, description="Retrieval metrics")
    retrievedChunks: int = Field(default=0, description="Number of source chunks utilized")
    model: str = Field(..., description="Model identifier used for synthesis")
    provider: str = Field(..., description="Provider identifier used")
    processingTimeMs: int = Field(default=0, description="Roundtrip processing time in milliseconds")
