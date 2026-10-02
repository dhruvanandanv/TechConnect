import os
from pathlib import Path
from pydantic import BaseModel, Field

BASE_DIR = Path(__file__).resolve().parent


class Settings(BaseModel):
    service_name: str = Field(default_factory=lambda: os.getenv("AI_SERVICE_NAME", "ticket-intelligence"))
    host: str = Field(default_factory=lambda: os.getenv("AI_SERVICE_HOST", "0.0.0.0"))
    port: int = Field(default_factory=lambda: int(os.getenv("AI_SERVICE_PORT", "8000")))
    env: str = Field(default_factory=lambda: os.getenv("AI_SERVICE_ENV", "development"))
    model_version: str = Field(default_factory=lambda: os.getenv("AI_MODEL_VERSION", "ticket-intelligence-v1"))
    log_level: str = Field(default_factory=lambda: os.getenv("AI_LOG_LEVEL", "INFO"))
    model_artifacts_dir: str = Field(
        default_factory=lambda: os.getenv("MODEL_ARTIFACTS_DIR", str(BASE_DIR / "models"))
    )
    data_file_path: str = Field(
        default_factory=lambda: os.getenv("DATA_FILE_PATH", str(BASE_DIR.parent / "data" / "tickets_training_data.csv"))
    )


settings = Settings()
