"""
Integration tests for FastAPI endpoint POST /api/v1/rag/answer.
Covers:
- Valid query handling
- Pydantic schema validations (empty query, query length limit > 1000)
- End-to-end citation structure
- Ticket context passing
"""
import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.ingestion.worker import process_single_article
from app.vector.repository import vector_repository

client = TestClient(app)


@pytest.fixture(autouse=True)
def seed_knowledge():
    vector_repository.clear()
    article = {
        "id": "kb-mfa-1",
        "title": "Microsoft Authenticator MFA Reset Procedure",
        "summary": "Steps to register and restore multi-factor authentication tokens.",
        "problem": "User changed mobile phone and cannot receive MFA approval push notifications.",
        "cause": "Old device token registration prevents new authenticator pairing.",
        "resolution": "1. Log into aka.ms/mysecurityinfo.\n2. Delete old phone device registration.\n3. Add new Microsoft Authenticator app and scan QR code.",
        "category": "ACCESS_MANAGEMENT",
        "tags": ["mfa", "azure", "security", "authenticator"],
        "status": "PUBLISHED",
        "version": 1
    }
    process_single_article(article)
    yield
    vector_repository.clear()


def test_rag_endpoint_valid_query():
    """Verify POST /api/v1/rag/answer returns 200 with structured grounded answer and citations."""
    response = client.post(
        "/api/v1/rag/answer",
        json={
            "query": "I got a new phone and my MFA push notification is not working",
            "category": "ACCESS_MANAGEMENT",
            "topK": 3,
            "minSimilarity": 0.25
        }
    )
    assert response.status_code == 200
    data = response.json()

    assert data["grounded"] is True
    assert "Microsoft Authenticator" in data["answer"]
    assert len(data["sources"]) > 0
    assert data["sources"][0]["articleId"] == "kb-mfa-1"
    assert data["sources"][0]["section"] in ["RESOLUTION", "PROBLEM", "TITLE"]
    assert data["retrieval"]["bestSimilarity"] >= 0.25
    assert data["processingTimeMs"] >= 0


def test_rag_endpoint_empty_query_rejected():
    """Verify empty query string is rejected with HTTP 422 Unprocessable Entity."""
    response = client.post(
        "/api/v1/rag/answer",
        json={
            "query": "",
            "topK": 5
        }
    )
    assert response.status_code == 422


def test_rag_endpoint_query_too_long_rejected():
    """Verify queries exceeding 1000 characters are rejected with HTTP 422."""
    long_query = "A" * 1001
    response = client.post(
        "/api/v1/rag/answer",
        json={
            "query": long_query,
            "topK": 5
        }
    )
    assert response.status_code == 422


def test_rag_endpoint_ticket_context():
    """Verify advisory ticket context is accepted and processed cleanly."""
    response = client.post(
        "/api/v1/rag/answer",
        json={
            "query": "How do I fix this MFA error?",
            "ticketContext": {
                "id": 89,
                "title": "MFA Authenticator registration failed",
                "category": "ACCESS_MANAGEMENT",
                "priority": "HIGH",
                "status": "OPEN"
            },
            "minSimilarity": 0.25
        }
    )
    assert response.status_code == 200
    data = response.json()
    assert data["grounded"] is True
    assert len(data["sources"]) > 0
