from app.schemas.ticket import TicketAnalysisRequest
from app.services.predictor import TicketIntelligencePredictor


def test_predictor_offline_fallback():
    """Verify that when ML model is offline, predictor falls back gracefully without raising exceptions."""
    predictor = TicketIntelligencePredictor()
    # Simulate model uninitialized/unloaded
    predictor.model = None
    predictor.vectorizer = None
    predictor.model_loaded = False

    req = TicketAnalysisRequest(
        title="Custom network issue",
        description="Unable to connect to internal website while traveling."
    )
    res = predictor.analyze(req)

    assert res.category.value in ["OTHER", "NETWORK"]
    assert res.category.confidence >= 0.0
    assert res.priority.value in ["HIGH", "MEDIUM", "LOW", "CRITICAL"]
    assert res.suggested_team.value is not None
    assert len(res.summary) > 0
    assert any("fallback" in r.lower() or "offline" in r.lower() or "priority" in r.lower() for r in res.reasons)
