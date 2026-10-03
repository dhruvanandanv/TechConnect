"""
Resolution Suggestion Orchestration Service for Phase 13.
Coordinates:
1. Knowledge Article Retrieval (via pgvector semantic search)
2. Historical Resolved Ticket Retrieval (via dense semantic similarity)
3. Quality Gate (short-circuits LLM if insufficient evidence)
4. Context Construction (distinct KB vs Historical evidence blocks)
5. Grounded Generation (proposal narrative + discrete steps)
6. Structured Provenance Packaging
"""
import time
import logging
from typing import Optional, List

from app.rag.config import rag_settings
from app.rag.models import (
    RagAnswerRequest,
    ResolutionSuggestionRequest,
    ResolutionSuggestionResponse,
    ResolutionRetrievalMeta,
    KnowledgeSourceCitation,
    RagSourceChunk,
)
from app.rag.retriever import retrieve_relevant_chunks
from app.rag.similar_tickets import retrieve_similar_tickets
from app.rag.resolution_context_builder import build_resolution_context
from app.rag.resolution_prompt_builder import (
    RESOLUTION_ASSISTANT_SYSTEM_PROMPT,
    build_resolution_user_prompt,
)
from app.rag.prompt_builder import check_sensitive_or_harmful_request
from app.rag.llm_provider import LlmProvider
from app.rag.providers.configurable_provider import ConfigurableLlmProvider

logger = logging.getLogger(__name__)


class ResolutionAssistantService:
    """
    Core AI Resolution Assistant service for IT support engineers.
    """

    def __init__(self, provider: Optional[LlmProvider] = None):
        self.provider = provider or ConfigurableLlmProvider()

    def generate_resolution_suggestion(
        self,
        request: ResolutionSuggestionRequest
    ) -> ResolutionSuggestionResponse:
        """
        Executes the resolution suggestion pipeline.
        Grounded in both official knowledge articles and similar historical tickets.
        """
        start_time = time.perf_counter()

        # Step 0: Security & Harmful Intent Gate
        combined_text = f"{request.ticket.title or ''} {request.ticket.description or ''}".strip()
        security_refusal = check_sensitive_or_harmful_request(combined_text)
        if security_refusal:
            elapsed_ms = int((time.perf_counter() - start_time) * 1000)
            return ResolutionSuggestionResponse(
                ticketId=request.ticketId,
                suggestion=security_refusal,
                grounded=False,
                steps=[],
                sources=[],
                similarTickets=[],
                retrievalMeta=None,
                provider=self.provider.provider_name,
                model=self.provider.model_name,
                processingTimeMs=elapsed_ms
            )

        # STAGE 1: RETRIEVAL
        effective_query = (
            f"{request.ticket.title or ''} {request.ticket.description or ''}".strip()
            if request.ticket.title
            else f"Ticket #{request.ticketId}"
        )

        effective_top_k = min(
            request.topK if request.topK and request.topK > 0 else rag_settings.rag_top_k,
            rag_settings.rag_max_top_k
        )
        effective_min_sim = (
            request.minSimilarity if request.minSimilarity is not None
            else rag_settings.rag_min_similarity
        )

        # 1a. Retrieve Knowledge Base chunks
        kb_request = RagAnswerRequest(
            query=request.ticket.title or effective_query,
            category=request.ticket.category,
            topK=effective_top_k,
            minSimilarity=effective_min_sim,
            ticketContext=request.ticket,
            allowedStatuses=request.allowedStatuses,
            allowedArticleIds=request.allowedArticleIds
        )
        knowledge_chunks: List[RagSourceChunk] = retrieve_relevant_chunks(kb_request)

        # 1b. Retrieve Similar Historical Resolved Tickets
        similar_tickets = retrieve_similar_tickets(
            query_text=effective_query,
            candidate_tickets=request.candidateTickets,
            top_k=3,
            min_similarity=effective_min_sim
        )

        # Quality Check: If NO knowledge chunks AND NO similar tickets met threshold, do NOT call LLM
        if not knowledge_chunks and not similar_tickets:
            elapsed_ms = int((time.perf_counter() - start_time) * 1000)
            return ResolutionSuggestionResponse(
                ticketId=request.ticketId,
                suggestion=(
                    "I couldn't find sufficiently relevant knowledge articles or historical resolved "
                    "tickets to generate a reliable resolution suggestion. Please investigate manually "
                    "or escalate this incident."
                ),
                grounded=False,
                steps=[],
                sources=[],
                similarTickets=[],
                retrievalMeta=ResolutionRetrievalMeta(
                    topK=effective_top_k,
                    knowledgeChunksUsed=0,
                    similarTicketsUsed=0,
                    bestSimilarity=0.0
                ),
                provider=self.provider.provider_name,
                model=self.provider.model_name,
                processingTimeMs=elapsed_ms
            )

        # STAGE 2: CONTEXT CONSTRUCTION
        structured_context = build_resolution_context(
            ticket=request.ticket,
            knowledge_sources=knowledge_chunks,
            similar_tickets=similar_tickets
        )

        user_prompt = build_resolution_user_prompt(request.ticket, structured_context)

        # STAGE 3: GENERATION
        try:
            raw_suggestion, steps = self.provider.generate_resolution_suggestion(
                system_prompt=RESOLUTION_ASSISTANT_SYSTEM_PROMPT,
                user_prompt=user_prompt,
                context=structured_context,
                knowledge_sources=knowledge_chunks,
                similar_tickets=similar_tickets
            )
            grounded = True
        except TimeoutError:
            logger.error("LLM Provider timed out during resolution suggestion synthesis")
            raw_suggestion = (
                "The AI Resolution Assistant timed out while formulating a proposal. "
                "You can still review the cited knowledge articles and historical tickets below."
            )
            steps = []
            grounded = False
        except Exception as e:
            logger.error("LLM Provider error during resolution suggestion synthesis: %s", str(e))
            raw_suggestion = (
                "The AI Resolution Assistant is temporarily unavailable. "
                "You can still review the cited knowledge articles and historical tickets below."
            )
            steps = []
            grounded = False

        # STAGE 4: CITATION & PACKAGING
        elapsed_ms = int((time.perf_counter() - start_time) * 1000)

        knowledge_citations = [
            KnowledgeSourceCitation(
                type="KNOWLEDGE_ARTICLE",
                articleId=c.articleId,
                chunkId=c.chunkId,
                title=c.title,
                section=c.section,
                similarity=c.similarity,
                version=c.articleVersion
            )
            for c in knowledge_chunks
        ]

        best_kb_sim = knowledge_chunks[0].similarity if knowledge_chunks else 0.0
        best_ticket_sim = similar_tickets[0].similarity if similar_tickets else 0.0
        best_overall_sim = max(best_kb_sim, best_ticket_sim)

        return ResolutionSuggestionResponse(
            ticketId=request.ticketId,
            suggestion=raw_suggestion,
            grounded=grounded,
            steps=steps,
            sources=knowledge_citations,
            similarTickets=similar_tickets,
            retrievalMeta=ResolutionRetrievalMeta(
                topK=effective_top_k,
                knowledgeChunksUsed=len(knowledge_chunks),
                similarTicketsUsed=len(similar_tickets),
                bestSimilarity=best_overall_sim
            ),
            provider=self.provider.provider_name,
            model=self.provider.model_name,
            processingTimeMs=elapsed_ms
        )


resolution_service = ResolutionAssistantService()
