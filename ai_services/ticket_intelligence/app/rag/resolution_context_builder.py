"""
Stage 3: RESOLUTION CONTEXT Module.
Constructs structured context containing:
1. Current Ticket Context
2. Retrieved Knowledge Base Sources
3. Similar Historical Resolved Tickets
Preserves provenance and enforces strict context budget limits.
"""
import logging
from typing import List, Optional

from app.rag.config import rag_settings
from app.rag.models import RagSourceChunk, SimilarTicketSource, TicketContext

logger = logging.getLogger(__name__)

DEFAULT_RESOLUTION_MAX_CHARS = 4500


def build_resolution_context(
    ticket: TicketContext,
    knowledge_sources: List[RagSourceChunk],
    similar_tickets: List[SimilarTicketSource],
    max_context_chars: Optional[int] = None
) -> str:
    """
    Constructs an isolated, structured context block for the Engineer Resolution Assistant.
    Clearly distinguishes official Knowledge Articles from Historical Resolved Tickets.
    """
    limit = max_context_chars or DEFAULT_RESOLUTION_MAX_CHARS
    context_parts: List[str] = []
    current_length = 0

    # 1. Format Current Ticket Context
    ticket_lines = ["[CURRENT ACTIVE TICKET FOR RESOLUTION]"]
    if ticket.id:
        ticket_lines.append(f"Ticket ID: #{ticket.id}")
    if ticket.title:
        ticket_lines.append(f"Title: {ticket.title.strip()}")
    if ticket.category:
        ticket_lines.append(f"Category: {ticket.category.strip()}")
    if ticket.priority:
        ticket_lines.append(f"Priority: {ticket.priority.strip()}")
    if ticket.status:
        ticket_lines.append(f"Status: {ticket.status.strip()}")
    if ticket.description:
        desc = ticket.description.strip()
        if len(desc) > 600:
            desc = desc[:600] + "... [truncated]"
        ticket_lines.append(f"Problem Description: {desc}")

    ticket_block = "\n".join(ticket_lines) + "\n\n"
    context_parts.append(ticket_block)
    current_length += len(ticket_block)

    # 2. Add Retrieved Knowledge Base Sources
    if knowledge_sources:
        kb_header = "=== BEGIN OFFICIAL KNOWLEDGE BASE SOURCES (AUTHORITATIVE REFERENCE DATA) ==="
        context_parts.append(kb_header)
        current_length += len(kb_header)

        for i, chunk in enumerate(knowledge_sources, start=1):
            chunk_header = (
                f"\n\n[KNOWLEDGE SOURCE {i}]\n"
                f"Title: {chunk.title}\n"
                f"Section: {chunk.section}\n"
                f"Article ID: {chunk.articleId}\n"
                f"Article Version: {chunk.articleVersion}\n"
                f"Relevance Similarity: {chunk.similarity:.2f}\n"
                f"Content:\n"
            )
            content_text = chunk.content.strip()
            addition = len(chunk_header) + len(content_text)

            if current_length + addition > limit:
                available = limit - current_length - len(chunk_header)
                if available > 100:
                    truncated = content_text[:available].rstrip() + "... [truncated]"
                    context_parts.append(chunk_header + truncated)
                    current_length += len(chunk_header) + len(truncated)
                break

            context_parts.append(chunk_header + content_text)
            current_length += addition

        kb_footer = "\n=== END OFFICIAL KNOWLEDGE BASE SOURCES ===\n\n"
        context_parts.append(kb_footer)
        current_length += len(kb_footer)

    # 3. Add Similar Historical Resolved Tickets
    if similar_tickets and current_length < limit:
        hist_header = "=== BEGIN SIMILAR HISTORICAL RESOLVED TICKETS (HISTORICAL EVIDENCE ONLY) ==="
        context_parts.append(hist_header)
        current_length += len(hist_header)

        for i, t in enumerate(similar_tickets, start=1):
            t_header = (
                f"\n\n[HISTORICAL TICKET {i}]\n"
                f"Ticket ID: #{t.ticketId}\n"
                f"Title: {t.title}\n"
                f"Category: {t.category or 'N/A'}\n"
                f"Similarity Score: {t.similarity:.2f}\n"
                f"Recorded Resolution:\n"
            )
            res_text = t.resolutionSummary.strip()
            addition = len(t_header) + len(res_text)

            if current_length + addition > limit:
                available = limit - current_length - len(t_header)
                if available > 80:
                    truncated = res_text[:available].rstrip() + "... [truncated]"
                    context_parts.append(t_header + truncated)
                    current_length += len(t_header) + len(truncated)
                break

            context_parts.append(t_header + res_text)
            current_length += addition

        hist_footer = "\n=== END SIMILAR HISTORICAL RESOLVED TICKETS ==="
        context_parts.append(hist_footer)
        current_length += len(hist_footer)

    full_context = "".join(context_parts)
    logger.debug(
        "Built resolution context of length %d chars (KB: %d, Historical Tickets: %d)",
        len(full_context), len(knowledge_sources), len(similar_tickets)
    )
    return full_context
