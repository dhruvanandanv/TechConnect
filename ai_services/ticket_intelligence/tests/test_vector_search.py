"""
Integration and unit tests for knowledge vector ingestion, versioning,
and semantic search retrieval API.
"""
import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.vector.repository import vector_repository
from app.ingestion.worker import process_single_article, process_article_batch
from app.vector.search import perform_semantic_search

client = TestClient(app)


@pytest.fixture(autouse=True)
def clean_vector_repo():
    """Ensures a clean repository before each test."""
    vector_repository.clear()
    yield
    vector_repository.clear()


def test_ingestion_and_semantic_retrieval():
    # 1. Ingest article
    article = {
        "id": "art-vpn-01",
        "title": "Corporate VPN Connection Troubleshooting",
        "summary": "Guide to resolving home WiFi VPN disconnection issues.",
        "problem": "Cannot connect to the corporate VPN when working remotely from home.",
        "cause": "DNS cache corruption or expired client certificates.",
        "resolution": "Flush DNS cache via ipconfig /flushdns and re-authenticate Cisco AnyConnect.",
        "category": "NETWORK",
        "tags": ["vpn", "network", "remote"],
        "status": "PUBLISHED",
        "version": 1,
    }

    result = process_single_article(article)
    assert result["success"] is True
    assert result["chunksCreated"] > 0
    assert result["chunksEmbedded"] > 0

    # 2. Search semantically
    search_res = perform_semantic_search(
        query="I cannot connect to the company VPN from home",
        top_k=5,
        min_similarity=0.40,
    )

    assert search_res["searchType"] == "SEMANTIC"
    assert search_res["totalHits"] > 0
    top_hit = search_res["results"][0]
    assert top_hit["articleId"] == "art-vpn-01"
    assert top_hit["similarity"] > 0.50
    assert top_hit["category"] == "NETWORK"


def test_version_consistency_and_stale_chunk_invalidation():
    # Ingest v1
    v1 = {
        "id": "art-printer-01",
        "title": "Old Printer Setup",
        "summary": "Legacy printer drivers",
        "problem": "Printer paper jam error 404",
        "resolution": "Replace drum unit v1",
        "category": "HARDWARE",
        "version": 1,
    }
    process_single_article(v1)

    # Search finds v1
    res_v1 = perform_semantic_search("printer paper jam", top_k=5, min_similarity=0.3)
    assert len(res_v1["results"]) > 0
    assert res_v1["results"][0]["articleVersion"] == 1

    # Update to v2
    v2 = {
        "id": "art-printer-01",
        "title": "Modern Printer Setup",
        "summary": "Modern cloud printer setup",
        "problem": "Printer paper jam error 404",
        "resolution": "Upgraded wireless printing service v2",
        "category": "HARDWARE",
        "version": 2,
    }
    process_single_article(v2)

    # Search now returns ONLY v2, v1 must NOT be returned!
    res_v2 = perform_semantic_search("printer paper jam", top_k=5, min_similarity=0.3)
    assert len(res_v2["results"]) > 0
    for r in res_v2["results"]:
        if r["articleId"] == "art-printer-01":
            assert r["articleVersion"] == 2, f"Found stale version {r['articleVersion']}!"


def test_category_and_rbac_status_filtering():
    art_published = {
        "id": "pub-01",
        "title": "Email Setup",
        "problem": "Cannot access Microsoft 365 inbox",
        "category": "SOFTWARE",
        "status": "PUBLISHED",
        "version": 1,
    }
    art_draft = {
        "id": "draft-01",
        "title": "Secret Password Reset",
        "problem": "Cannot access Microsoft 365 admin panel",
        "category": "SECURITY",
        "status": "DRAFT",
        "version": 1,
    }
    process_article_batch([art_published, art_draft])

    # 1. Search with category filter: SOFTWARE
    res_software = perform_semantic_search("Microsoft 365", category="SOFTWARE", min_similarity=0.3)
    for r in res_software["results"]:
        assert r["category"] == "SOFTWARE"

    # 2. Search with allowedStatuses=['PUBLISHED'] (Employee access pattern)
    res_emp = perform_semantic_search("Microsoft 365", allowed_statuses=["PUBLISHED"], min_similarity=0.3)
    for r in res_emp["results"]:
        assert r["articleId"] != "draft-01", "Draft article leaked to employee query!"


def test_top_k_limits():
    # Request top_k = 1000000 -> should be capped at max_top_k (20)
    res = perform_semantic_search("test query", top_k=1000000)
    assert len(res["results"]) <= 20


def test_fastapi_endpoints():
    # 1. /api/v1/knowledge/embed
    resp = client.post("/api/v1/knowledge/embed", json={"texts": ["hello world"]})
    assert resp.status_code == 200
    data = resp.json()
    assert data["dimensions"] == 384
    assert len(data["embeddings"]) == 1

    # 2. /api/v1/knowledge/ingest
    resp = client.post(
        "/api/v1/knowledge/ingest",
        json={
            "articles": [
                {
                    "id": "api-art-01",
                    "title": "VPN Guide",
                    "problem": "VPN client failure",
                    "resolution": "Restart openvpn service",
                    "category": "NETWORK",
                    "status": "PUBLISHED",
                    "version": 1,
                }
            ]
        },
    )
    assert resp.status_code == 200
    batch_res = resp.json()
    assert batch_res["articlesProcessed"] == 1
    assert batch_res["chunksEmbedded"] > 0

    # 3. /api/v1/knowledge/semantic-search
    resp = client.post(
        "/api/v1/knowledge/semantic-search",
        json={"query": "how to restart VPN", "topK": 3, "minSimilarity": 0.3},
    )
    assert resp.status_code == 200
    search_data = resp.json()
    assert search_data["searchType"] == "SEMANTIC"
    assert search_data["totalHits"] >= 1

    # 4. /api/v1/knowledge/stats
    resp = client.get("/api/v1/knowledge/stats")
    assert resp.status_code == 200
    stats = resp.json()
    assert stats["dimensions"] == 384
    assert stats["activeMemoryChunks"] > 0
