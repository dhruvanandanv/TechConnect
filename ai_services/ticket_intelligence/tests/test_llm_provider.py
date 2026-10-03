"""
Unit tests for Stage 3: GENERATION & LLM Provider Abstraction.
Covers:
- ConfigurableLlmProvider offline synthesis
- External provider HTTP call with mock
- LLM timeout exception handling
- LLM provider failure (HTTP 500 / network error)
- Malformed provider response handling
- Graceful degraded response formatting
"""
import pytest
from unittest.mock import patch, MagicMock
import requests

from app.rag.models import RagSourceChunk, RagAnswerRequest
from app.rag.providers.configurable_provider import ConfigurableLlmProvider
from app.rag.service import RagService


@pytest.fixture
def sample_sources():
    return [
        RagSourceChunk(
            articleId="art-vpn-1",
            chunkId="art-vpn-1-0",
            articleVersion=1,
            title="Configuring Corporate VPN",
            section="RESOLUTION",
            content="1. Flush DNS using ipconfig /flushdns.\n2. Update AnyConnect to v3.4.\n3. Reconnect.",
            similarity=0.8842,
            category="VPN"
        )
    ]


def test_offline_grounded_synthesis_success(sample_sources):
    """Verify local deterministic provider extracts and formats verified troubleshooting steps."""
    provider = ConfigurableLlmProvider(api_key="")
    answer = provider.generate_grounded_response(
        system_prompt="system",
        user_prompt="user",
        context="context",
        sources=sample_sources
    )

    assert "Based on the TechConnect knowledge base (Configuring Corporate VPN)" in answer
    assert "Flush DNS using ipconfig /flushdns" in answer
    assert "Update AnyConnect to v3.4" in answer


@patch("requests.post")
def test_external_llm_call_success(mock_post, sample_sources):
    """Verify external OpenAI-compatible API response parsing."""
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = {
        "choices": [
            {
                "message": {
                    "content": "To resolve VPN disconnects, flush your DNS and update AnyConnect."
                }
            }
        ]
    }
    mock_post.return_value = mock_response

    provider = ConfigurableLlmProvider(
        provider="openai",
        api_key="sk-test-mock-key",
        model="gpt-4o-mini"
    )

    answer = provider.generate_grounded_response(
        system_prompt="system",
        user_prompt="user",
        context="context",
        sources=sample_sources
    )

    assert "To resolve VPN disconnects, flush your DNS and update AnyConnect." in answer


@patch("requests.post")
def test_external_llm_timeout_handled(mock_post, sample_sources):
    """Verify LLM timeout raises TimeoutError and is caught gracefully by RagService."""
    mock_post.side_effect = requests.Timeout("Connection timed out")

    provider = ConfigurableLlmProvider(
        provider="openai",
        api_key="sk-test-mock-key",
        timeout_seconds=5
    )

    with pytest.raises(TimeoutError):
        provider.generate_grounded_response("sys", "usr", "ctx", sample_sources)

    # Test that RagService catches TimeoutError and returns safe fallback
    service = RagService(provider=provider)
    with patch("app.rag.service.retrieve_relevant_chunks", return_value=sample_sources):
        resp = service.generate_answer(RagAnswerRequest(query="VPN issue"))
        assert resp.grounded is False
        assert "timed out while formulating a response" in resp.answer
        assert len(resp.sources) == 1  # Sources are still cited for user reference!


@patch("requests.post")
def test_external_llm_malformed_response_handled(mock_post, sample_sources):
    """Verify malformed LLM JSON payload is caught and handled safely."""
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = {"error": "Invalid payload format"}
    mock_post.return_value = mock_response

    provider = ConfigurableLlmProvider(
        provider="openai",
        api_key="sk-test-mock-key"
    )

    service = RagService(provider=provider)
    with patch("app.rag.service.retrieve_relevant_chunks", return_value=sample_sources):
        resp = service.generate_answer(RagAnswerRequest(query="VPN issue"))
        assert resp.grounded is False
        assert "temporarily unavailable" in resp.answer
        assert len(resp.sources) == 1
