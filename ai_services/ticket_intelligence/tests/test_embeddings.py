"""
Unit tests for embedding generation and model lifecycle.
Verifies singleton reuse, exact 384 dimensions, batch processing,
and resilient handling of unusual text inputs.
"""
from app.embeddings.model import get_embedding_model, get_actual_dimensions
from app.embeddings.generator import generate_embedding, generate_embeddings


def test_model_loading_and_reuse():
    m1 = get_embedding_model()
    m2 = get_embedding_model()
    assert m1 is m2  # Singleton: exact same instance in memory


def test_embedding_dimensions():
    dims = get_actual_dimensions()
    assert dims == 384

    vec = generate_embedding("TechConnect enterprise IT service management")
    assert isinstance(vec, list)
    assert len(vec) == 384
    assert all(isinstance(x, float) for x in vec)


def test_embedding_invalid_or_empty_text():
    # Empty string should not crash; defaults to fallback text representation
    vec_empty = generate_embedding("")
    assert len(vec_empty) == 384

    vec_spaces = generate_embedding("     \n\t   ")
    assert len(vec_spaces) == 384


def test_batch_embedding_generation():
    texts = [
        "How do I reset my Active Directory password?",
        "VPN connection timed out after 30 seconds",
        "Outlook crashes when opening encrypted emails",
    ]
    batch_vecs = generate_embeddings(texts)
    assert len(batch_vecs) == 3
    for v in batch_vecs:
        assert len(v) == 384

    # Verify individual vs batch produces near-identical vectors
    single_v0 = generate_embedding(texts[0])
    # Compare with small float tolerance
    diff = sum(abs(a - b) for a, b in zip(batch_vecs[0], single_v0))
    assert diff < 1e-4
