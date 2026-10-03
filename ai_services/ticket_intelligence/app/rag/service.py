"""
RAG Orchestration Service Module.
Coordinates the four distinct stages of enterprise Retrieval-Augmented Generation:
1. RETRIEVAL (Semantic vector search & threshold gates)
2. CONTEXT CONSTRUCTION (Structured source provenance & budget caps)
3. GENERATION (LLM Provider synthesis with prompt injection defenses)
4. CITATION (Verifiable source references & response formatting)
"""
import time
import logging
from typing import Optional

from app.rag.config import rag_settings
from app.rag.models import (
    RagAnswerRequest,
    RagAnswerResponse,
    RagRetrievalMeta,
    RagSourceChunk,
)
from app.rag.retriever import retrieve_relevant_chunks
from app.rag.context_builder import build_rag_context
from app.rag.prompt_builder import (
    SYSTEM_PROMPT,
    build_user_prompt,
    check_sensitive_or_harmful_request,
)
from app.rag.llm_provider import LlmProvider
from app.rag.providers.configurable_provider import ConfigurableLlmProvider

logger = logging.getLogger(__name__)


class RagService:
    """
    Core RAG engine managing retrieval, guardrails, generation, and citation.
    """

    def __init__(self, provider: Optional[LlmProvider] = None):
        self.provider = provider or ConfigurableLlmProvider()

    def generate_answer(self, request: RagAnswerRequest) -> RagAnswerResponse:
        """
        Executes the 4-stage RAG workflow.
        Returns a structured answer guaranteed to be grounded in knowledge base sources.
        """
        start_time = time.perf_counter()

        # Step 0: Enterprise Security & Harmful Intent Gate
        security_refusal = check_sensitive_or_harmful_request(request.query)
        if security_refusal:
            elapsed_ms = int((time.perf_counter() - start_time) * 1000)
            return RagAnswerResponse(
                answer=security_refusal,
                grounded=False,
                confidence=0.0,
                sources=[],
                retrieval=None,
                retrievedChunks=0,
                model=self.provider.model_name,
                provider=self.provider.provider_name,
                processingTimeMs=elapsed_ms
            )

        # STAGE 1: RETRIEVAL
        sources = retrieve_relevant_chunks(request)

        # Quality Check: If no chunks met similarity threshold, do NOT call LLM
        if not sources:
            elapsed_ms = int((time.perf_counter() - start_time) * 1000)
            return RagAnswerResponse(
                answer=(
                    "I couldn't find a sufficiently relevant troubleshooting article in the "
                    "TechConnect knowledge base. Please contact an IT support engineer or "
                    "search the Knowledge Base directly."
                ),
                grounded=False,
                confidence=0.0,
                sources=[],
                retrieval=RagRetrievalMeta(
                    topK=request.topK or rag_settings.rag_top_k,
                    resultsUsed=0,
                    bestSimilarity=0.0
                ),
                retrievedChunks=0,
                model=self.provider.model_name,
                provider=self.provider.provider_name,
                processingTimeMs=elapsed_ms
            )

        # STAGE 2: CONTEXT CONSTRUCTION
        structured_context = build_rag_context(
            sources=sources,
            ticket_context=request.ticketContext,
            max_context_chars=rag_settings.rag_max_context_chars
        )

        user_prompt = build_user_prompt(request.query, structured_context)

        # STAGE 3: GENERATION
        try:
            raw_answer = self.provider.generate_grounded_response(
                system_prompt=SYSTEM_PROMPT,
                user_prompt=user_prompt,
                context=structured_context,
                sources=sources
            )
            grounded = True
        except TimeoutError:
            logger.error("LLM Provider timeout during RAG synthesis")
            raw_answer = (
                "The AI Support Copilot timed out while formulating a response. "
                "You can still review the relevant Knowledge Base articles cited below."
            )
            grounded = False
        except Exception as e:
            logger.error("LLM Provider error during RAG synthesis: %s", str(e))
            raw_answer = (
                "The AI Support Copilot is temporarily unavailable. "
                "You can still review the relevant Knowledge Base articles cited below."
            )
            grounded = False

        # STAGE 4: CITATION & RESPONSE PACKAGING
        elapsed_ms = int((time.perf_counter() - start_time) * 1000)
        best_similarity = sources[0].similarity if sources else 0.0

        return RagAnswerResponse(
            answer=raw_answer,
            grounded=grounded,
            confidence=best_similarity,
            sources=sources,
            retrieval=RagRetrievalMeta(
                topK=request.topK or rag_settings.rag_top_k,
                resultsUsed=len(sources),
                bestSimilarity=best_similarity
            ),
            retrievedChunks=len(sources),
            model=self.provider.model_name,
            provider=self.provider.provider_name,
            processingTimeMs=elapsed_ms
        )


# Singleton instance
rag_service = RagService()
