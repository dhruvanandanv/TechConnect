"""
FastAPI Router for AI Support Copilot (RAG Answer Endpoint).
Exposes the RAG retrieval and synthesis pipeline to the Spring Boot backend.
"""
import logging
from fastapi import APIRouter, HTTPException, status

from app.rag.models import RagAnswerRequest, RagAnswerResponse
from app.rag.service import rag_service

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/rag", tags=["AI Support Copilot"])


@router.post("/answer", response_model=RagAnswerResponse, status_code=status.HTTP_200_OK)
def answer_support_question(request: RagAnswerRequest):
    """
    RAG Support Copilot Endpoint:
    1. Validates user technical inquiry.
    2. Performs semantic retrieval against knowledge base vector chunks.
    3. Builds grounded context with provenance metadata and budget caps.
    4. Generates an advisory troubleshooting answer using the configured LLM provider.
    5. Returns citations and structured confidence metrics.
    """
    try:
        response = rag_service.generate_answer(request)
        return response
    except Exception as e:
        logger.error("RAG pipeline encountered an unexpected error: %s", str(e), exc_info=False)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="An error occurred while processing the support inquiry."
        )
