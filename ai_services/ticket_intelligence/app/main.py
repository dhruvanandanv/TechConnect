import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.routers import health, intelligence
from app.services.predictor import predictor

logging.basicConfig(
    level=getattr(logging, settings.log_level.upper(), logging.INFO),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Initializing TechConnect Ticket Intelligence Service (%s)...", settings.model_version)
    if not predictor.is_loaded():
        logger.warning("Predictor model not loaded. Attempting initialization...")
        predictor._load_or_train_model()
    yield
    logger.info("Shutting down Ticket Intelligence Service...")


app = FastAPI(
    title="TechConnect Ticket Intelligence API",
    description="Microservice providing supervised ML ticket categorization, priority prediction, team routing, and automated summarization for TechConnect ITSM.",
    version="1.0.0",
    lifespan=lifespan,
)

# CORS configuration for internal microservice / local inspection
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health.router)
app.include_router(intelligence.router)


@app.get("/")
def root():
    return {
        "service": settings.service_name,
        "version": settings.model_version,
        "docs_url": "/docs",
        "health_url": "/health",
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host=settings.host, port=settings.port, reload=True)
