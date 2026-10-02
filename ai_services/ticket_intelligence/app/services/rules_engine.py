import re
from typing import Dict, List, Tuple


# Keyword dictionaries mapped to priority tiers with indicative weights
CRITICAL_SIGNALS = [
    "ransomware", "security breach", "data leak", "ddos", "trojan",
    "compromised", "unauthorized login", "production outage", "all servers down",
    "company-wide outage", "stolen company laptop", "malware infection",
    "bitcoin payment", "root access unauthorized"
]

HIGH_SIGNALS = [
    "vpn", "cannot connect", "not connecting", "connection drops", "fails to initialize",
    "crashes continuously", "packet loss", "high latency", "unreachable", "blocking",
    "quota exceeded", "infinite loop", "cannot send outbound", "license activation failed",
    "screen flickering", "charger cable frayed", "urgent", "power failure", "overheating",
    "cannot access internal", "handshake failed"
]

MEDIUM_SIGNALS = [
    "permission", "access request", "password reset", "install", "mfa",
    "invitation", "sync error", "intermittent", "slow", "vlookup", "indexing",
    "extension", "viewer license", "export", "formatting corrupted", "reassignment",
    "account provisioning"
]

LOW_SIGNALS = [
    "inquiry", "question", "feedback", "ergonomic", "chair", "whiteboard",
    "markers", "coffee machine", "recycling", "temperature", "parking permit",
    "merchandise", "advice", "key misplaced", "clean desk", "earbuds", "first aid"
]

# Team routing definition: (Team Name, Team Code, Rationale)
CATEGORY_TEAM_ROUTING = {
    "HARDWARE": ("Hardware & Workplace", "HARDWARE_SUPPORT", "Routed to Hardware & Workplace for physical equipment and workstation diagnostics"),
    "SOFTWARE": ("Application Support", "SOFTWARE_SUPPORT", "Routed to Application Support for software troubleshooting and application faults"),
    "NETWORK": ("Network Support", "NETWORK_SUPPORT", "Routed to Network Support for LAN/WAN, Wi-Fi, and connectivity management"),
    "SECURITY": ("Information Security", "SECURITY_TEAM", "Routed to Information Security for threat containment and compliance analysis"),
    "ACCESS_MANAGEMENT": ("Identity & Access", "ACCESS_SUPPORT", "Routed to Identity & Access for SSO, permissions, and directory provisioning"),
    "EMAIL": ("Network Support", "NETWORK_SUPPORT", "Routed to Network Support for Exchange and messaging gateway infrastructure"),
    "VPN": ("Network Support", "NETWORK_SUPPORT", "Routed to Network Support for secure remote tunnels and gateway firewalls"),
    "OTHER": ("IT Operations", "IT_SUPPORT", "Routed to IT Operations general service desk queue"),
}


def evaluate_priority_rules(text: str) -> Tuple[str, float, List[str]]:
    """
    Evaluate deterministic priority rules against input ticket text.
    Returns:
        (predicted_priority, confidence, matched_reasons)
    """
    lower_text = text.lower()
    matched_reasons: List[str] = []

    # Check CRITICAL
    crit_matches = [term for term in CRITICAL_SIGNALS if re.search(r"\b" + re.escape(term) + r"\b", lower_text)]
    if crit_matches:
        conf = min(0.96, 0.88 + 0.03 * len(crit_matches))
        matched_reasons.append(f"Critical urgency signals detected: {', '.join(crit_matches[:3])}")
        return "CRITICAL", round(conf, 2), matched_reasons

    # Check HIGH
    high_matches = [term for term in HIGH_SIGNALS if re.search(r"\b" + re.escape(term) + r"\b", lower_text)]
    if high_matches:
        conf = min(0.92, 0.82 + 0.03 * len(high_matches))
        matched_reasons.append(f"High-impact operational keywords identified: {', '.join(high_matches[:3])}")
        return "HIGH", round(conf, 2), matched_reasons

    # Check LOW
    low_matches = [term for term in LOW_SIGNALS if re.search(r"\b" + re.escape(term) + r"\b", lower_text)]
    if low_matches:
        conf = min(0.90, 0.80 + 0.03 * len(low_matches))
        matched_reasons.append(f"Non-urgent or informational inquiry signals detected: {', '.join(low_matches[:3])}")
        return "LOW", round(conf, 2), matched_reasons

    # Check MEDIUM
    med_matches = [term for term in MEDIUM_SIGNALS if re.search(r"\b" + re.escape(term) + r"\b", lower_text)]
    if med_matches:
        conf = min(0.88, 0.78 + 0.03 * len(med_matches))
        matched_reasons.append(f"Standard operational and service request signals detected: {', '.join(med_matches[:3])}")
        return "MEDIUM", round(conf, 2), matched_reasons

    # Baseline default
    return "MEDIUM", 0.72, ["Default baseline priority assigned based on standard incident classification"]


def evaluate_team_routing(category: str, category_confidence: float) -> Tuple[str, float, str]:
    """
    Route ticket category to support team.
    Returns:
        (team_code, confidence, routing_reason)
    """
    cat_upper = category.upper() if category else "OTHER"
    team_name, team_code, reason = CATEGORY_TEAM_ROUTING.get(
        cat_upper,
        ("IT Operations", "IT_SUPPORT", "Default support routing applied")
    )
    # Team routing confidence is proportional to category prediction confidence
    team_conf = round(max(0.70, min(0.98, category_confidence * 0.96)), 2)
    return team_code, team_conf, reason
