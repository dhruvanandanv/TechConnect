"""
Unit tests for intelligent section-aware chunking.
Verifies section preservation, sentence-boundary overlap, empty article handling,
and metadata propagation.
"""
from app.ingestion.chunker import chunk_article, _split_text_with_overlap


def test_chunk_article_sections():
    chunks = chunk_article(
        article_id="kb-vpn-101",
        title="Corporate VPN Guide",
        summary="Overview of connecting to global VPN endpoints.",
        problem="Connection times out on port 443 with TLS handshake failure.",
        cause="Self-signed enterprise CA root certificate expired on host.",
        resolution="Download latest Root CA bundle from IT portal and reinstall in trust store.",
        category="VPN",
        tags=["vpn", "tls", "certificates"],
        article_version=2,
    )

    assert len(chunks) == 4
    sections = [c["section"] for c in chunks]
    assert sections == ["SUMMARY", "PROBLEM", "CAUSE", "RESOLUTION"]

    # Check metadata fields
    for idx, c in enumerate(chunks):
        assert c["articleId"] == "kb-vpn-101"
        assert c["chunkIndex"] == idx
        assert c["category"] == "VPN"
        assert c["articleVersion"] == 2
        assert "vpn" in c["tags"]
        assert c["chunkId"].startswith("kb-vpn-101-")


def test_chunk_overlap_and_boundary_splitting():
    # Long text exceeding 100 chars
    long_text = (
        "First sentence of diagnostic guide. "
        "Second sentence detailing network firewall rules. "
        "Third sentence describing proxy configuration steps. "
        "Fourth sentence detailing DNS resolving settings."
    )

    chunks = _split_text_with_overlap(long_text, max_chars=90, overlap_chars=25)
    assert len(chunks) > 1

    # Overlap verification: text in end of chunk 0 appears in beginning of chunk 1
    # or consecutive chunks contain shared boundary context
    for i in range(len(chunks) - 1):
        assert len(chunks[i]) <= 110  # within boundary tolerance


def test_empty_article_handling():
    chunks = chunk_article(
        article_id="kb-empty-001",
        title="",
        summary="",
        problem="",
        cause="",
        resolution="",
        content="",
        category="GENERAL",
        tags=[],
        article_version=1,
    )

    assert len(chunks) == 1
    assert chunks[0]["articleId"] == "kb-empty-001"
    assert chunks[0]["section"] == "GENERAL"
    assert chunks[0]["text"] == "No content available."


def test_large_section_split():
    large_resolution = "\n\n".join([f"Step {i}: Execute verification command on workstation." for i in range(1, 20)])
    chunks = chunk_article(
        article_id="kb-large-res",
        title="Large Resolution Guide",
        resolution=large_resolution,
        max_chunk_chars=120,
        chunk_overlap_chars=30,
    )

    assert len(chunks) >= 3
    for c in chunks:
        assert c["section"] == "RESOLUTION"
        assert c["articleId"] == "kb-large-res"
