"""
Tests for Resolution Assistant FastAPI endpoint POST /api/v1/rag/resolution-suggestion.
"""
import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.vector.repository import vector_repository
from app.ingestion.worker import process_single_article

client = TestClient(app)


@pytest.fixture(autouse=True)
def seed_knowledge():
    vector_repository.clear()
    process_single_article({
        "id": "kb-vpn-ep",
        "title": "Corporate AnyConnect VPN Setup",
        "summary": "Fixes gateway 504 timeouts and remote connectivity drops.",
        "problem": "Frequent VPN disconnects on home wireless.",
        "cause": "DNS cache issues.",
        "resolution": "1. Flush DNS using ipconfig /flushdns.\n2. Restart AnyConnect.",
        "category": "VPN",
        "tags": ["vpn"],
        "status": "PUBLISHED",
        "version": 1
    })
    yield
    vector_repository.clear()


def test_endpoint_resolution_suggestion_success():
    payload = {
        "ticketId": 100,
        "ticket": {
            "id": 100,
            "title": "VPN disconnects every 15 minutes",
            "description": "User loses connectivity after Windows sleep.",
            "category": "VPN",
            "priority": "HIGH",
            "status": "IN_PROGRESS"
        },
        "candidateTickets": [
            {
                "id": 40,
                "title": "VPN disconnects on home wireless",
                "description": "AnyConnect timeout",
                "category": "VPN",
                "priority": "MEDIUM",
                "resolutionDescription": "Flushed DNS cache and verified gateway profile URL."
            }
        ],
        "topK": 3,
        "minSimilarity": 0.30
    }

    res = client.post("/api/v1/rag/resolution-suggestion", json=payload)
    assert res.status_code == 200
    data = res.json()
    assert data["ticketId"] == 100
    assert data["grounded"] is True
    assert len(data["steps"]) > 0
    assert len(data["sources"]) > 0
    assert len(data["similarTickets"]) > 0
    assert data["similarTickets"][0]["ticketId"] == 40


def test_endpoint_resolution_suggestion_no_evidence():
    payload = {
        "ticketId": 200,
        "ticket": {
            "id": 200,
            "title": "Superconductor calibration failure on rig 4",
            "description": "Exotic laboratory equipment",
            "category": "HARDWARE"
        },
        "candidateTickets": [],
        "minSimilarity": 0.30
    }

    res = client.post("/api/v1/rag/resolution-suggestion", json=payload)
    assert res.status_code == 200
    data = res.json()
    assert data["ticketId"] == 200
    assert data["grounded"] is False
    assert len(data["steps"]) == 0
