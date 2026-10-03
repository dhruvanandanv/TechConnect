"""
Configuration settings for TechConnect RAG (Retrieval-Augmented Generation) Support Copilot.
All parameters are configurable via environment variables with safe enterprise defaults.
"""
import os
from pydantic import BaseModel, Field


class RagSettings(BaseModel):
    # Retrieval Configuration
    rag_top_k: int = Field(default_factory=lambda: int(os.getenv("RAG_TOP_K", "5")))
    rag_max_top_k: int = Field(default_factory=lambda: int(os.getenv("RAG_MAX_TOP_K", "10")))
    rag_min_similarity: float = Field(default_factory=lambda: float(os.getenv("RAG_MIN_SIMILARITY", "0.30")))
    rag_max_query_length: int = Field(default_factory=lambda: int(os.getenv("RAG_MAX_QUERY_LENGTH", "1000")))
    rag_max_context_chars: int = Field(default_factory=lambda: int(os.getenv("RAG_MAX_CONTEXT_CHARS", "4000")))

    # LLM Provider Configuration
    techconnect_llm_provider: str = Field(
        default_factory=lambda: os.getenv("TECHCONNECT_LLM_PROVIDER", "configurable")
    )
    techconnect_llm_api_key: str = Field(
        default_factory=lambda: os.getenv("TECHCONNECT_LLM_API_KEY", "")
    )
    techconnect_llm_model: str = Field(
        default_factory=lambda: os.getenv("TECHCONNECT_LLM_MODEL", "techconnect-grounded-v1")
    )
    techconnect_llm_base_url: str = Field(
        default_factory=lambda: os.getenv("TECHCONNECT_LLM_BASE_URL", "https://api.openai.com/v1")
    )
    rag_llm_timeout_seconds: int = Field(
        default_factory=lambda: int(os.getenv("RAG_LLM_TIMEOUT_SECONDS", "10"))
    )


rag_settings = RagSettings()
