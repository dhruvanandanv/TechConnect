"""
Stage 2: CONTEXT CONSTRUCTION Module
Builds structured, provenanced context blocks from retrieved knowledge chunks
and optional active ticket context, enforcing strict character/token budget limits.
"""
import logging
from typing import List, Optional

from app.rag.config import rag_settings
from app.rag.models import RagSourceChunk, TicketContext

logger = logging.getLogger(__name__)


def build_rag_context(
    sources: List[RagSourceChunk],
    ticket_context: Optional[TicketContext] = None,
    max_context_chars: Optional[int] = None
) -> str:
    """
    Constructs a structured context string from retrieved knowledge sources.
    Guarantees every source is individually identifiable for grounded attribution.
    Enforces max_context_chars budget.
    """
    limit = max_context_chars or rag_settings.rag_max_context_chars
    context_parts: List[str] = []
    current_length = 0

    # 1. Format advisory ticket context if provided
    if ticket_context and (ticket_context.id or ticket_context.title):
        ticket_lines = ["[ACTIVE TICKET CONTEXT (ADVISORY)]"]
        if ticket_context.id:
            ticket_lines.append(f"Ticket ID: #{ticket_context.id}")
        if ticket_context.title:
            ticket_lines.append(f"Title: {ticket_context.title.strip()}")
        if ticket_context.category:
            ticket_lines.append(f"Category: {ticket_context.category.strip()}")
        if ticket_context.priority:
            ticket_lines.append(f"Priority: {ticket_context.priority.strip()}")
        if ticket_context.status:
            ticket_lines.append(f"Status: {ticket_context.status.strip()}")
        if ticket_context.description:
            desc = ticket_context.description.strip()
            if len(desc) > 500:
                desc = desc[:500] + "..."
            ticket_lines.append(f"Description: {desc}")
        
        ticket_block = "\n".join(ticket_lines) + "\n\n"
        context_parts.append(ticket_block)
        current_length += len(ticket_block)

    # 2. Add each retrieved knowledge chunk up to the context budget
    context_parts.append("=== BEGIN RETRIEVED KNOWLEDGE BASE SOURCES (REFERENCE DATA ONLY) ===")

    for i, chunk in enumerate(sources, start=1):
        chunk_header = (
            f"\n\n[SOURCE {i}]\n"
            f"Title: {chunk.title}\n"
            f"Section: {chunk.section}\n"
            f"Article ID: {chunk.articleId}\n"
            f"Relevance Similarity: {chunk.similarity:.2f}\n"
            f"Content:\n"
        )
        content_text = chunk.content.strip()

        estimated_addition = len(chunk_header) + len(content_text)
        if current_length + estimated_addition > limit:
            # Check if we can fit at least a truncated portion of this chunk
            available_space = limit - current_length - len(chunk_header)
            if available_space > 100:
                truncated_content = content_text[:available_space].rstrip() + "... [truncated for context limit]"
                context_parts.append(chunk_header + truncated_content)
                current_length += len(chunk_header) + len(truncated_content)
            else:
                logger.info("Context budget reached (%d chars). Skipping source %d and beyond.", limit, i)
            break

        context_parts.append(chunk_header + content_text)
        current_length += estimated_addition

    context_parts.append("\n=== END RETRIEVED KNOWLEDGE BASE SOURCES ===")

    full_context = "".join(context_parts)
    logger.debug("Constructed RAG context of length %d characters for %d sources", len(full_context), len(sources))
    return full_context
