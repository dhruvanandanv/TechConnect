"""
TechConnect RAG Support Copilot package.
"""
from app.rag.config import rag_settings
from app.rag.models import RagAnswerRequest, RagAnswerResponse, RagSourceChunk, TicketContext
from app.rag.service import rag_service, RagService

__all__ = [
    "rag_settings",
    "RagAnswerRequest",
    "RagAnswerResponse",
    "RagSourceChunk",
    "TicketContext",
    "rag_service",
    "RagService",
]
