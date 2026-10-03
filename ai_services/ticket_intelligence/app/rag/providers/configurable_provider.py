"""
Configurable LLM Provider implementation for TechConnect RAG.
Supports external OpenAI-compatible endpoints when an API key is provided,
and seamlessly falls back to a deterministic, zero-cost, local grounded synthesizer
for local development and automated testing environments.
"""
import logging
from typing import List, Any
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

    def generate_resolution_suggestion(
        self,
        system_prompt: str,
        user_prompt: str,
        context: str,
        knowledge_sources: List[RagSourceChunk],
        similar_tickets: List[Any]
    ) -> tuple[str, List[str]]:
        """
        Generates a grounded technical resolution proposal and structured steps
        using knowledge articles and similar historical tickets.
        """
        if self._api_key and self._provider in ["openai", "openai_compatible", "azure"]:
            raw_text = self._call_external_api(system_prompt, user_prompt)
            steps = self._extract_steps_from_text(raw_text)
            return raw_text, steps

        return self._synthesize_local_resolution(knowledge_sources, similar_tickets)

    def _synthesize_local_resolution(
        self,
        knowledge_sources: List[RagSourceChunk],
        similar_tickets: List[Any]
    ) -> tuple[str, List[str]]:
        """
        Deterministic local resolution synthesizer for Phase 13 offline & test execution.
        Combines actionable resolution procedures from knowledge chunks and historical tickets.
        """
        steps: List[str] = []
        narrative_parts: List[str] = []

        primary_kb = None
        if knowledge_sources:
            primary_kb = next((c for c in knowledge_sources if c.section.upper() == "RESOLUTION"), knowledge_sources[0])

        primary_ticket = similar_tickets[0] if similar_tickets else None

        # Build introduction narrative
        if primary_kb and primary_ticket:
            narrative_parts.append(
                f"Resolution proposal grounded in TechConnect Knowledge Base ('{primary_kb.title}') "
                f"and verified resolution from similar historical ticket #{primary_ticket.ticketId}:"
            )
        elif primary_kb:
            narrative_parts.append(
                f"Resolution proposal grounded in TechConnect Knowledge Base ('{primary_kb.title}'):"
            )
        elif primary_ticket:
            narrative_parts.append(
                f"Resolution proposal grounded in similar historical resolved ticket #{primary_ticket.ticketId} "
                f"('{primary_ticket.title}'):"
            )
        else:
            return (
                "Insufficient knowledge base or historical ticket evidence is available to formulate a reliable resolution suggestion.",
                []
            )

        narrative_parts.append("")

        # Extract steps from Knowledge Base
        if primary_kb:
            kb_lines = [l.strip() for l in primary_kb.content.split("\n") if l.strip()]
            for line in kb_lines:
                if line.upper().startswith(("RESOLUTION:", "PROBLEM:", "CAUSE:", "SUMMARY:", "TITLE:")):
                    continue
                # If line is already a step
                clean_line = line.lstrip("0123456789.-*•) ")
                if clean_line and len(clean_line) > 5 and clean_line not in steps:
                    steps.append(clean_line)

        # Extract steps from Historical Ticket resolution
        if primary_ticket and primary_ticket.resolutionSummary:
            t_lines = [l.strip() for l in primary_ticket.resolutionSummary.split("\n") if l.strip()]
            for line in t_lines:
                clean_line = line.lstrip("0123456789.-*•) ")
                if clean_line and len(clean_line) > 5 and clean_line not in steps:
                    steps.append(clean_line)

        # Format numbered narrative
        if steps:
            for idx, s in enumerate(steps, start=1):
                narrative_parts.append(f"{idx}. {s}")
        else:
            default_step = "Review configuration parameters and verify network/system connectivity."
            steps.append(default_step)
            narrative_parts.append(f"1. {default_step}")

        # Add verification note
        narrative_parts.append("")
        narrative_parts.append(
            "Advisory Note: Review and verify these steps in the test environment before applying to customer tickets."
        )

        suggestion = "\n".join(narrative_parts).strip()
        return suggestion, steps

    def _extract_steps_from_text(self, text: str) -> List[str]:
        """
        Extracts discrete numbered or bulleted troubleshooting steps from LLM output.
        """
        steps: List[str] = []
        for line in text.split("\n"):
            stripped = line.strip()
            # Match 1., 2., Step 1:, - , etc.
            if stripped and (
                (len(stripped) > 2 and stripped[0].isdigit() and stripped[1] in ".):")
                or stripped.startswith(("- ", "* ", "• "))
                or stripped.lower().startswith("step ")
            ):
                cleaned = stripped.lstrip("0123456789.-*•): ")
                if cleaned.lower().startswith("step "):
                    # Strip 'Step 1:' etc
                    parts = cleaned.split(":", 1)
                    cleaned = parts[1].strip() if len(parts) > 1 else cleaned
                if cleaned and cleaned not in steps:
                    steps.append(cleaned)

        if not steps:
            # Fallback: Split non-empty lines
            for line in text.split("\n"):
                s = line.strip()
                if len(s) > 20 and not s.endswith(":") and s not in steps:
                    steps.append(s)
                    if len(steps) >= 5:
                        break

        return steps
