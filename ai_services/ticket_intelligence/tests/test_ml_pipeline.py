from app.schemas.ticket import TicketAnalysisRequest
from app.services.predictor import TicketIntelligencePredictor
from app.services.rules_engine import evaluate_priority_rules, evaluate_team_routing
from app.services.summarizer import generate_deterministic_summary
from app.utils.text_cleaning import clean_text, combine_ticket_text


def test_text_cleaning():
    raw = "  VPN   \t\n is NOT connecting!!!  "
    cleaned = clean_text(raw)
    assert cleaned == "VPN is NOT connecting!"


def test_combine_ticket_text():
    title = "Wi-Fi dropping"
    desc = "Signal is weak on 5th floor"
    combined = combine_ticket_text(title, desc)
    assert combined.startswith("Wi-Fi dropping Wi-Fi dropping Signal is weak")


def test_priority_rules_engine():
    # Critical signal
    prio, conf, reasons = evaluate_priority_rules("Server down and ransomware alert")
    assert prio == "CRITICAL"
    assert conf >= 0.85

    # High signal
    prio, conf, reasons = evaluate_priority_rules("Cannot connect to VPN from home")
    assert prio == "HIGH"
    assert conf >= 0.80

    # Low signal
    prio, conf, reasons = evaluate_priority_rules("General inquiry on office chair settings")
    assert prio == "LOW"
    assert conf >= 0.80

    # Medium fallback
    prio, conf, reasons = evaluate_priority_rules("Need assistance updating project spreadsheet")
    assert prio in ["MEDIUM", "LOW"]


def test_team_routing():
    team_code, conf, reason = evaluate_team_routing("VPN", 0.92)
    assert team_code == "NETWORK_SUPPORT"
    assert conf >= 0.70
    assert "Network Support" in reason

    team_code, conf, reason = evaluate_team_routing("HARDWARE", 0.85)
    assert team_code == "HARDWARE_SUPPORT"

    team_code, conf, reason = evaluate_team_routing("SECURITY", 0.88)
    assert team_code == "SECURITY_TEAM"


def test_summary_generation():
    title = "Email not working"
    desc = "I am unable to send emails to external clients."
    summary = generate_deterministic_summary(title, desc)
    assert "User is unable to" in summary
    assert summary.endswith(".")


def test_predictor_instance_inference():
    predictor = TicketIntelligencePredictor()
    assert predictor.is_loaded() is True

    req = TicketAnalysisRequest(
        title="Laptop keyboard not responding",
        description="Keys spacebar and enter stopped working completely on my Thinkpad."
    )
    result = predictor.analyze(req)
    assert result.category.value == "HARDWARE"
    assert result.suggested_team.value == "HARDWARE_SUPPORT"
    assert result.priority.value in ["HIGH", "MEDIUM", "LOW", "CRITICAL"]
    assert len(result.reasons) > 0
    assert result.processing_time_ms >= 0
