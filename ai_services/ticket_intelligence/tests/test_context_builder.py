"""
Unit tests for Stage 2: CONTEXT CONSTRUCTION in TechConnect RAG.
Covers:
- Context formatting with distinct identifiable sources
- Context size limit enforcement
- Provenance preservation (article ID, title, section, similarity)
- Advisory ticket context integration
"""
import pytest

from app.rag.models import RagSourceChunk, TicketContext
from app.rag.context_builder import build_rag_context


def test_context_construction_preserves_provenance():
    """Verify each source chunk remains identifiable with metadata."""
    sources = [
        RagSourceChunk(
            articleId="art-101",
            chunkId="art-101-0",
            articleVersion=2,
            title="Configuring Cisco AnyConnect",
            section="RESOLUTION",
            content="Run ipconfig /flushdns and restart the network adapter.",
            similarity=0.8842,
            category="VPN"
        ),
        RagSourceChunk(
            articleId="art-102",
            chunkId="art-102-1",
            articleVersion=1,
            title="VPN Gateway Disconnect Guide",
            section="PROBLEM",
            content="Users lose connection every 15 minutes.",
            similarity=0.7521,
            category="VPN"
        )
    ]

    context = build_rag_context(sources)

    assert "[SOURCE 1]" in context
    assert "Title: Configuring Cisco AnyConnect" in context
    assert "Section: RESOLUTION" in context
    assert "Article ID: art-101" in context
    assert "0.88" in context
    assert "Run ipconfig /flushdns" in context

    assert "[SOURCE 2]" in context
    assert "Title: VPN Gateway Disconnect Guide" in context
    assert "Section: PROBLEM" in context
    assert "Article ID: art-102" in context


def test_context_size_limit_enforcement():
    """Verify max_context_chars limits the context payload."""
    sources = [
        RagSourceChunk(
            articleId=f"art-{i}",
            chunkId=f"art-{i}-0",
            articleVersion=1,
            title=f"Article Title {i}",
            section="RESOLUTION",
            content="X" * 200,
            similarity=0.80 - (i * 0.05)
        )
        for i in range(10)
    ]

    max_budget = 600
    context = build_rag_context(sources, max_context_chars=max_budget)

    assert len(context) <= max_budget + 150  # Allows slight margin for closing tags
    # Should not include all 10 sources
    assert "[SOURCE 10]" not in context


def test_context_construction_with_ticket_context():
    """Verify advisory ticket context is cleanly formatted."""
    sources = [
        RagSourceChunk(
            articleId="art-1",
            chunkId="art-1-0",
            articleVersion=1,
            title="Outlook Troubleshooting",
            section="RESOLUTION",
            content="Restart Outlook in safe mode.",
            similarity=0.85
        )
    ]
    ticket = TicketContext(
        id=456,
        title="Outlook Crashes on Startup",
        category="SOFTWARE",
        priority="HIGH",
        status="IN_PROGRESS",
        description="Application freezes when loading profile."
    )

    context = build_rag_context(sources, ticket_context=ticket)

    assert "[ACTIVE TICKET CONTEXT (ADVISORY)]" in context
    assert "Ticket ID: #456" in context
    assert "Title: Outlook Crashes on Startup" in context
    assert "Priority: HIGH" in context
    assert "Status: IN_PROGRESS" in context
    assert "Description: Application freezes when loading profile." in context
    assert "[SOURCE 1]" in context
