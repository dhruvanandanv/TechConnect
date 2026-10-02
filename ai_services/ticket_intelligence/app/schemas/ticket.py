from typing import List, Optional
from pydantic import BaseModel, Field, field_validator


class TicketAnalysisRequest(BaseModel):
    title: str = Field(
        ...,
        min_length=3,
        max_length=255,
        description="Ticket title summarizing the issue",
        examples=["VPN is not connecting"],
    )
    description: str = Field(
        ...,
        min_length=5,
        max_length=5000,
        description="Detailed description of the incident or request",
        examples=["I am unable to connect to the company VPN from my laptop"],
    )
    category: Optional[str] = Field(
        None,
        description="Optional human-entered category",
        examples=["VPN"],
    )
    priority: Optional[str] = Field(
        None,
        description="Optional human-entered priority",
        examples=["HIGH"],
    )

    @field_validator("title", "description")
    @classmethod
    def strip_and_validate_non_empty(cls, v: str) -> str:
        stripped = v.strip()
        if not stripped:
            raise ValueError("Field cannot be empty or only whitespace")
        return stripped


class PredictionScore(BaseModel):
    value: str
    confidence: float = Field(..., ge=0.0, le=1.0)


class TicketAnalysisResponse(BaseModel):
    category: PredictionScore
    priority: PredictionScore
    suggested_team: PredictionScore
    summary: str
    reasons: List[str]
    model_version: str
    processing_time_ms: int


class ModelInfoResponse(BaseModel):
    model_name: str
    model_version: str
    supported_categories: List[str]
    supported_priorities: List[str]
    loaded_model_status: str
    trained_at: Optional[str] = None
    classes_count: int


class HealthResponse(BaseModel):
    status: str
    service: str
