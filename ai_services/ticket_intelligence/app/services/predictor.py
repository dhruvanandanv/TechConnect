import logging
import time
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import joblib
import numpy as np

from app.config import settings
from app.ml.train import train_category_model
from app.schemas.ticket import (
    ModelInfoResponse,
    PredictionScore,
    TicketAnalysisRequest,
    TicketAnalysisResponse,
)
from app.services.rules_engine import evaluate_priority_rules, evaluate_team_routing
from app.services.summarizer import generate_deterministic_summary
from app.utils.text_cleaning import combine_ticket_text

logger = logging.getLogger(__name__)

CATEGORY_KEYWORD_INDICATORS = {
    "VPN": ["vpn", "tunnel", "anyconnect", "forticlient", "pulse secure", "globalprotect", "wireguard", "openvpn"],
    "NETWORK": ["wifi", "wi-fi", "internet", "ethernet", "subnet", "dns", "dhcp", "latency", "packet loss", "router", "switch", "gateway"],
    "HARDWARE": ["laptop", "keyboard", "monitor", "screen", "mouse", "charger", "docking station", "battery", "printer", "headset"],
    "SOFTWARE": ["crash", "freeze", "excel", "docker", "intellij", "vscode", "zoom", "teams", "browser", "install", "git", "extension"],
    "SECURITY": ["phishing", "malware", "ransomware", "trojan", "antivirus", "compromised", "stolen", "unauthorized", "bitlocker", "ddos"],
    "ACCESS_MANAGEMENT": ["access", "permission", "password reset", "unlock", "mfa", "okta", "active directory", "account", "onboarding", "role"],
    "EMAIL": ["outlook", "email", "mailbox", "spam", "bounce", "calendar", "exchange", "signature", "attachment", "quota"],
    "OTHER": ["inquiry", "question", "feedback", "ergonomic", "chair", "temperature", "merchandise", "parking", "desk"],
}


class TicketIntelligencePredictor:
    """Core predictive service managing ML model inference, rules engine, and explainability."""

    def __init__(self):
        self.model = None
        self.vectorizer = None
        self.metadata = {}
        self.model_loaded = False
        self._load_or_train_model()

    def _load_or_train_model(self):
        artifacts_dir = Path(settings.model_artifacts_dir)
        model_path = artifacts_dir / "category_model.joblib"
        vec_path = artifacts_dir / "category_vectorizer.joblib"
        meta_path = artifacts_dir / "model_metadata.json"

        if not (model_path.exists() and vec_path.exists()):
            logger.warning("Model artifacts missing in %s. Triggering training baseline...", artifacts_dir)
            try:
                train_category_model(output_dir=str(artifacts_dir))
            except Exception as e:
                logger.error("Failed to train model on initialization: %s", str(e), exc_info=True)
                return

        try:
            self.model = joblib.load(model_path)
            self.vectorizer = joblib.load(vec_path)
            if meta_path.exists():
                import json
                with open(meta_path, "r", encoding="utf-8") as f:
                    self.metadata = json.load(f)
            self.model_loaded = True
            logger.info("Successfully loaded Ticket Intelligence model (classes: %s)", list(self.model.classes_))
        except Exception as e:
            logger.error("Failed to load model artifacts: %s", str(e), exc_info=True)
            self.model_loaded = False

    def is_loaded(self) -> bool:
        return self.model_loaded and self.model is not None and self.vectorizer is not None

    def analyze(self, request: TicketAnalysisRequest) -> TicketAnalysisResponse:
        start_time = time.perf_counter()
        combined_text = combine_ticket_text(request.title, request.description)
        reasons: List[str] = []

        # 1. Category Classification
        if request.category and request.category.strip().upper() in self.metadata.get("classes", []):
            cat_value = request.category.strip().upper()
            cat_confidence = 1.0
            reasons.append(f"Category '{cat_value}' provided directly by author")
        elif self.is_loaded():
            vec = self.vectorizer.transform([combined_text])
            probs = self.model.predict_proba(vec)[0]
            best_idx = int(np.argmax(probs))
            cat_value = str(self.model.classes_[best_idx])
            cat_confidence = round(float(probs[best_idx]), 2)

            # Extract matched category keywords for explainability
            matched_kw = [
                kw for kw in CATEGORY_KEYWORD_INDICATORS.get(cat_value, [])
                if kw in combined_text.lower()
            ]
            if matched_kw:
                reasons.append(f"Category {cat_value} predicted from salient terminology: {', '.join(matched_kw[:3])}")
            else:
                reasons.append(f"Category {cat_value} predicted by TF-IDF statistical text model ({int(cat_confidence * 100)}% probability)")
        else:
            cat_value = "OTHER"
            cat_confidence = 0.50
            reasons.append("Default fallback category applied (ML model offline)")

        # 2. Priority Intelligence
        if request.priority and request.priority.strip().upper() in ["LOW", "MEDIUM", "HIGH", "CRITICAL"]:
            prio_value = request.priority.strip().upper()
            prio_confidence = 1.0
            reasons.append(f"Priority '{prio_value}' provided directly by author")
        else:
            prio_value, prio_confidence, prio_reasons = evaluate_priority_rules(combined_text)
            reasons.extend(prio_reasons)

        # 3. Team Routing Recommendation
        team_value, team_confidence, team_reason = evaluate_team_routing(cat_value, cat_confidence)
        reasons.append(team_reason)

        # 4. Summary Generation
        summary_text = generate_deterministic_summary(request.title, request.description)

        elapsed_ms = max(1, int((time.perf_counter() - start_time) * 1000))

        return TicketAnalysisResponse(
            category=PredictionScore(value=cat_value, confidence=cat_confidence),
            priority=PredictionScore(value=prio_value, confidence=prio_confidence),
            suggested_team=PredictionScore(value=team_value, confidence=team_confidence),
            summary=summary_text,
            reasons=reasons,
            model_version=settings.model_version,
            processing_time_ms=elapsed_ms,
        )

    def get_model_info(self) -> ModelInfoResponse:
        supported_cats = self.metadata.get("classes", [
            "HARDWARE", "SOFTWARE", "NETWORK", "SECURITY",
            "ACCESS_MANAGEMENT", "EMAIL", "VPN", "OTHER"
        ])
        return ModelInfoResponse(
            model_name=self.metadata.get("model_name", "TF-IDF + LogisticRegression Category Classifier"),
            model_version=settings.model_version,
            supported_categories=supported_cats,
            supported_priorities=["LOW", "MEDIUM", "HIGH", "CRITICAL"],
            loaded_model_status="LOADED" if self.is_loaded() else "NOT_LOADED",
            trained_at=self.metadata.get("trained_at"),
            classes_count=len(supported_cats),
        )


# Singleton predictor instance
predictor = TicketIntelligencePredictor()
