"""
Configurable LLM Provider implementation for TechConnect RAG.
Supports external OpenAI-compatible endpoints when an API key is provided,
and seamlessly falls back to a deterministic, zero-cost, local grounded synthesizer
for local development and automated testing environments.
"""
import logging
from typing import List
import requests

from app.rag.config import rag_settings
from app.rag.llm_provider import LlmProvider
from app.rag.models import RagSourceChunk

logger = logging.getLogger(__name__)


class ConfigurableLlmProvider(LlmProvider):
    """
    Production-ready configurable LLM provider.
    - If external API key is provided: Calls OpenAI-compatible /chat/completions API.
    - If offline / no API key: Synthesizes directly from retrieved knowledge chunks.
    """

    def __init__(
        self,
        provider: str = rag_settings.techconnect_llm_provider,
        api_key: str = rag_settings.techconnect_llm_api_key,
        model: str = rag_settings.techconnect_llm_model,
        base_url: str = rag_settings.techconnect_llm_base_url,
        timeout_seconds: int = rag_settings.rag_llm_timeout_seconds
    ):
        self._provider = provider.lower()
        self._api_key = api_key
        self._model = model
        self._base_url = base_url.rstrip("/")
        self._timeout = timeout_seconds

    @property
    def provider_name(self) -> str:
        if self._api_key and self._provider in ["openai", "openai_compatible", "azure"]:
            return self._provider
        return "techconnect-grounded-synthesizer"

    @property
    def model_name(self) -> str:
        if self._api_key and self._provider in ["openai", "openai_compatible", "azure"]:
            return self._model
        return "grounded-extractive-v1"

    def generate_grounded_response(
        self,
        system_prompt: str,
        user_prompt: str,
        context: str,
        sources: List[RagSourceChunk]
    ) -> str:
        """
        Generates a grounded technical answer.
        Uses external API if credentials exist; otherwise uses local deterministic synthesis.
        """
        if self._api_key and self._provider in ["openai", "openai_compatible", "azure"]:
            return self._call_external_api(system_prompt, user_prompt)
        
        return self._synthesize_local_grounded_answer(sources)

    def _call_external_api(self, system_prompt: str, user_prompt: str) -> str:
        """
        Dispatches request to an OpenAI-compatible /chat/completions endpoint with strict timeout.
        """
        endpoint = f"{self._base_url}/chat/completions"
        headers = {
            "Authorization": f"Bearer {self._api_key}",
            "Content-Type": "application/json"
        }
        payload = {
            "model": self._model,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt}
            ],
            "temperature": 0.1,
            "max_tokens": 800
        }

        logger.debug("Calling external LLM endpoint %s with model %s", endpoint, self._model)
        try:
            response = requests.post(endpoint, json=payload, headers=headers, timeout=self._timeout)
            response.raise_for_status()
            data = response.json()
            choices = data.get("choices", [])
            if not choices or "message" not in choices[0]:
                raise ValueError("Malformed response payload from LLM provider")
            return choices[0]["message"]["content"].strip()
        except requests.Timeout:
            logger.error("External LLM provider timed out after %ds", self._timeout)
            raise TimeoutError(f"LLM provider request timed out after {self._timeout}s")
        except requests.RequestException as e:
            logger.error("External LLM provider error: %s", str(e))
            raise RuntimeError(f"LLM provider returned error: {str(e)}")

    def _synthesize_local_grounded_answer(self, sources: List[RagSourceChunk]) -> str:
        """
        Deterministic, local rule-based grounded synthesizer for offline and testing use.
        Extracts verified steps directly from the highest-ranked retrieved sources.
        """
        if not sources:
            return (
                "I couldn't find a sufficiently relevant troubleshooting article in the "
                "TechConnect knowledge base. Please contact an IT support engineer."
            )

        # Prioritize RESOLUTION chunks, otherwise take highest similarity chunk
        primary_chunk = next((c for c in sources if c.section.upper() == "RESOLUTION"), sources[0])

        lines: List[str] = [
            f"Based on the TechConnect knowledge base ({primary_chunk.title}):",
            ""
        ]

        # Extract and format the content lines cleanly
        content_lines = [l.strip() for l in primary_chunk.content.split("\n") if l.strip()]
        for line in content_lines:
            # Avoid repeating internal section headers
            if line.upper().startswith(("RESOLUTION:", "PROBLEM:", "CAUSE:", "SUMMARY:", "TITLE:")):
                continue
            lines.append(line)

        # If there are additional resolution or cause chunks, append relevant context
        other_chunks = [c for c in sources if c.chunkId != primary_chunk.chunkId]
        for c in other_chunks[:2]:
            if c.section.upper() in ["CAUSE", "RESOLUTION"]:
                snippet = c.content.strip().split("\n")[0]
                if len(snippet) > 120:
                    snippet = snippet[:120] + "..."
                lines.append(f"\nAdditional context from {c.title} ({c.section}):")
                lines.append(snippet)

        return "\n".join(lines).strip()
