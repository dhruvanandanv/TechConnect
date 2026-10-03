"""
LLM Provider Abstraction Module for TechConnect RAG.
Defines the abstract interface that decouples RAG synthesis from specific LLM vendors.
"""
from abc import ABC, abstractmethod
from typing import List, Any
from app.rag.models import RagSourceChunk


class LlmProvider(ABC):
    """
    Abstract base interface for LLM synthesis providers.
    Supports pluggable external providers (OpenAI, Ollama, vLLM, Azure OpenAI)
    as well as local deterministic grounded synthesizers.
    """

    @abstractmethod
    def generate_grounded_response(
        self,
        system_prompt: str,
        user_prompt: str,
        context: str,
        sources: List[RagSourceChunk]
    ) -> str:
        """
        Generates a grounded technical answer from retrieved sources.
        Must strictly adhere to system grounding rules.
        """
        pass

    @abstractmethod
    def generate_resolution_suggestion(
        self,
        system_prompt: str,
        user_prompt: str,
        context: str,
        knowledge_sources: List[RagSourceChunk],
        similar_tickets: List[Any]
    ) -> tuple[str, List[str]]:
        """
        Generates a grounded technical resolution suggestion and discrete steps
        using retrieved knowledge sources and similar historical tickets.
        """
        pass

    @property
    @abstractmethod
    def provider_name(self) -> str:
        """Returns the human-readable identifier of the provider."""
        pass

    @property
    @abstractmethod
    def model_name(self) -> str:
        """Returns the configured model name."""
        pass
