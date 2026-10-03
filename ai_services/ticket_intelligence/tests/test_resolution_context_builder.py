"""
Tests for Resolution Context Builder (Stage 3).
"""
from app.rag.models import (
    TicketContext,
    RagSourceChunk,
    SimilarTicketSource,
)
from app.rag.resolution_context_builder import build_resolution_context


def test_build_resolution_context_structure():
    ticket = TicketContext(
        id=55,
        title="Cannot connect to corporate VPN after Windows 11 update",
        description="AnyConnect client stalls on 'Connecting' state.",
        category="VPN",
        priority="HIGH",
        status="IN_PROGRESS"
    )

    kb_sources = [
        RagSourceChunk(
            articleId="kb-vpn-1",
            chunkId="kb-vpn-1-res",
            articleVersion=2,
            title="Corporate AnyConnect Troubleshooting Guide",
            section="RESOLUTION",
            content="1. Flush DNS cache via ipconfig /flushdns.\n2. Reinstall Root CA certificate.",
            similarity=0.88,
            category="VPN"
        )
    ]

    similar_tickets = [
        SimilarTicketSource(
            ticketId=12,
            title="AnyConnect connection stalls on Windows 11",
            similarity=0.82,
            resolutionSummary="Re-installed AnyConnect v4.10 and restarted base filtering engine.",
            category="VPN",
            priority="HIGH"
        )
    ]

    context = build_resolution_context(ticket, kb_sources, similar_tickets, max_context_chars=3000)

    # Verifications
    assert "[CURRENT ACTIVE TICKET FOR RESOLUTION]" in context
    assert "Ticket ID: #55" in context
    assert "=== BEGIN OFFICIAL KNOWLEDGE BASE SOURCES" in context
    assert "[KNOWLEDGE SOURCE 1]" in context
    assert "Article ID: kb-vpn-1" in context
    assert "=== BEGIN SIMILAR HISTORICAL RESOLVED TICKETS" in context
    assert "[HISTORICAL TICKET 1]" in context
    assert "Ticket ID: #12" in context
    assert "Re-installed AnyConnect v4.10" in context


def test_build_resolution_context_budget_enforcement():
    ticket = TicketContext(id=1, title="Test Ticket", description="Short description")
    kb_sources = [
        RagSourceChunk(
            articleId=f"kb-{i}",
            chunkId=f"chunk-{i}",
            articleVersion=1,
            title=f"Article Title {i}",
            section="RESOLUTION",
            content="A" * 300,
            similarity=0.85
        )
        for i in range(10)
    ]

    # Set tight budget
    context = build_resolution_context(ticket, kb_sources, [], max_context_chars=800)
    assert len(context) <= 1200  # Respects budget and does not explode
