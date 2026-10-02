import logging
from fastapi import APIRouter, HTTPException, status

from app.schemas.ticket import (
    ModelInfoResponse,
    TicketAnalysisRequest,
    TicketAnalysisResponse,
)
from app.services.predictor import predictor

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/ticket-intelligence", tags=["Ticket Intelligence"])


@router.get("/model-info", response_model=ModelInfoResponse)
def get_model_info() -> ModelInfoResponse:
    """Retrieve metadata about the currently deployed Ticket Intelligence ML models."""
    return predictor.get_model_info()


@router.post("/analyze", response_model=TicketAnalysisResponse, status_code=status.HTTP_200_OK)
def analyze_ticket(request: TicketAnalysisRequest) -> TicketAnalysisResponse:
    """
    Analyze IT service ticket text and generate predictions for:
    - Predicted Category + Statistical Confidence Score
    - Priority Assessment + Operational Urgency Confidence
    - Suggested Support Team Routing + Match Reason
    - Deterministic Problem Summary
    - Explainable Prediction Rationale
    """
    try:
        return predictor.analyze(request)
    except Exception as e:
        logger.error("Error during ticket analysis: %s", str(e), exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="An error occurred while processing ticket intelligence analysis."
        )
