from fastapi import APIRouter
from app.config import settings
from app.schemas.ticket import HealthResponse

router = APIRouter(tags=["Health"])


@router.get("/health", response_model=HealthResponse)
def get_health() -> HealthResponse:
    """Return health status of the ticket intelligence microservice."""
    return HealthResponse(
        status="UP",
        service=settings.service_name,
    )
