from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)


def test_model_info_endpoint():
    response = client.get("/api/v1/ticket-intelligence/model-info")
    assert response.status_code == 200
    data = response.json()
    assert "model_name" in data
    assert "model_version" in data
    assert "supported_categories" in data
    assert "supported_priorities" in data
    assert len(data["supported_categories"]) >= 8
    assert data["loaded_model_status"] == "LOADED"


def test_valid_ticket_analysis():
    payload = {
        "title": "VPN is not connecting",
        "description": "I am unable to connect to the company VPN from my laptop after changing my password.",
    }
    response = client.post("/api/v1/ticket-intelligence/analyze", json=payload)
    assert response.status_code == 200
    data = response.json()

    # Category verification
    assert "category" in data
    assert data["category"]["value"] in [
        "HARDWARE", "SOFTWARE", "NETWORK", "SECURITY",
        "ACCESS_MANAGEMENT", "EMAIL", "VPN", "OTHER"
    ]
    assert 0.0 <= data["category"]["confidence"] <= 1.0

    # Priority verification
    assert "priority" in data
    assert data["priority"]["value"] in ["LOW", "MEDIUM", "HIGH", "CRITICAL"]
    assert 0.0 <= data["priority"]["confidence"] <= 1.0

    # Team recommendation
    assert "suggested_team" in data
    assert isinstance(data["suggested_team"]["value"], str)
    assert 0.0 <= data["suggested_team"]["confidence"] <= 1.0

    # Summary and reasons
    assert "summary" in data
    assert len(data["summary"]) > 5
    assert "reasons" in data
    assert len(data["reasons"]) > 0

    assert data["model_version"] == "ticket-intelligence-v1"
    assert data["processing_time_ms"] >= 0


def test_user_provided_category_and_priority_respected():
    payload = {
        "title": "Monitor is flickering",
        "description": "Display goes dark when cable is touched",
        "category": "HARDWARE",
        "priority": "LOW",
    }
    response = client.post("/api/v1/ticket-intelligence/analyze", json=payload)
    assert response.status_code == 200
    data = response.json()

    assert data["category"]["value"] == "HARDWARE"
    assert data["category"]["confidence"] == 1.0
    assert data["priority"]["value"] == "LOW"
    assert data["priority"]["confidence"] == 1.0


def test_missing_title_rejected():
    payload = {
        "description": "Only description provided without title",
    }
    response = client.post("/api/v1/ticket-intelligence/analyze", json=payload)
    assert response.status_code == 422


def test_missing_description_rejected():
    payload = {
        "title": "Title with missing description",
    }
    response = client.post("/api/v1/ticket-intelligence/analyze", json=payload)
    assert response.status_code == 422


def test_empty_string_rejected():
    payload = {
        "title": "   ",
        "description": "Valid description text here",
    }
    response = client.post("/api/v1/ticket-intelligence/analyze", json=payload)
    assert response.status_code == 422


def test_critical_security_incident_detection():
    payload = {
        "title": "Ransomware warning notification on desktop background",
        "description": "Desktop wallpaper changed to a ransom note demanding bitcoin payment and all files encrypted.",
    }
    response = client.post("/api/v1/ticket-intelligence/analyze", json=payload)
    assert response.status_code == 200
    data = response.json()

    assert data["priority"]["value"] == "CRITICAL"
    assert data["priority"]["confidence"] >= 0.85
    assert any("critical" in r.lower() or "signals" in r.lower() for r in data["reasons"])
