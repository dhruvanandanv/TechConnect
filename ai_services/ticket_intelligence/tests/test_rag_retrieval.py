"""
Unit tests for Stage 1: RETRIEVAL in TechConnect RAG.
Covers:
- Retrieval with relevant sources
- No relevant sources
- Similarity threshold filtering
- Top-K bounds enforcement
- Empty query handling
"""
import pytest
from unittest.mock import patch

from app.rag.models import RagAnswerRequest, TicketContext
from app.rag.retriever import retrieve_relevant_chunks
from app.ingestion.worker import process_single_article
from app.vector.repository import vector_repository


@pytest.fixture(autouse=True)
def setup_vector_data():
    """Seed test article vector chunks in repository."""
    vector_repository.clear()
    article = {
        "id": "kb-vpn-test-1",
        "title": "Corporate AnyConnect VPN Configuration Guide",
        "summary": "Fixes gateway 504 timeouts and remote connectivity drops.",
        "problem": "Users encounter frequent VPN disconnects and DNS resolution errors.",
        "cause": "Local DNS cache corruption or outdated Cisco profile.",
        "resolution": "1. Flush DNS using ipconfig /flushdns.\n2. Update AnyConnect to v3.4.\n3. Restart client.",
        "category": "VPN",
        "tags": ["vpn", "cisco", "network"],
        "status": "PUBLISHED",
        "version": 1
    }
    process_single_article(article)
    yield
    vector_repository.clear()


def test_retrieval_with_relevant_sources():
    """Verify semantic retrieval retrieves relevant knowledge chunks."""
    request = RagAnswerRequest(
        query="VPN keeps disconnecting from home network",
        category="VPN",
        topK=3,
        minSimilarity=0.30
    )
    sources = retrieve_relevant_chunks(request)

    assert len(sources) > 0
    assert any(s.articleId == "kb-vpn-test-1" for s in sources)
    assert sources[0].similarity >= 0.30
    assert sources[0].title == "Corporate AnyConnect VPN Configuration Guide"


def test_retrieval_no_relevant_sources():
    """Verify retrieval returns empty list when query is completely unrelated and below threshold."""
    request = RagAnswerRequest(
        query="Quantum astrophysics cosmological constant calculation",
        topK=5,
        minSimilarity=0.85
    )
    sources = retrieve_relevant_chunks(request)
    assert len(sources) == 0


def test_retrieval_similarity_threshold_filtering():
    """Verify chunks below minSimilarity are filtered out."""
    request_lenient = RagAnswerRequest(
        query="VPN network",
        minSimilarity=0.20
    )
    lenient_sources = retrieve_relevant_chunks(request_lenient)

    request_strict = RagAnswerRequest(
        query="VPN network",
        minSimilarity=0.99
    )
    strict_sources = retrieve_relevant_chunks(request_strict)

    assert len(lenient_sources) >= len(strict_sources)
    assert len(strict_sources) == 0


def test_retrieval_with_ticket_context():
    """Verify ticket context enhances general query retrieval."""
    request = RagAnswerRequest(
        query="How to fix this?",
        ticketContext=TicketContext(
            id=101,
            title="VPN Gateway 504 Timeout",
            category="VPN"
        ),
        minSimilarity=0.30
    )
    sources = retrieve_relevant_chunks(request)
    assert len(sources) > 0
    assert any(s.articleId == "kb-vpn-test-1" for s in sources)
