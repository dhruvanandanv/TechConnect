"""
Unit tests for knowledge article text normalization.
Verifies clean whitespace handling, deterministic canonical format,
and preservation of technical commands and markdown syntax.
"""
from app.ingestion.normalizer import normalize_article, clean_section_text


def test_clean_section_text():
    raw = "Line 1   \r\nLine 2 \r\n\r\n\r\n\r\nLine 3"
    cleaned = clean_section_text(raw)
    assert "\r" not in cleaned
    assert "Line 1\nLine 2\n\nLine 3" == cleaned


def test_normalize_article_structure():
    normalized = normalize_article(
        title="Corporate VPN Setup",
        summary="Steps to connect to company VPN",
        problem="Cannot connect from home WiFi",
        cause="Outdated certificates or blocked UDP port 1194",
        resolution="Run 'sudo systemctl restart openvpn' and verify credentials",
        category="VPN",
        tags=["VPN", "Remote", "vpn", "network"],
    )

    assert "TITLE:\nCorporate VPN Setup" in normalized
    assert "SUMMARY:\nSteps to connect to company VPN" in normalized
    assert "PROBLEM:\nCannot connect from home WiFi" in normalized
    assert "CAUSE:\nOutdated certificates or blocked UDP port 1194" in normalized
    assert "RESOLUTION:\nRun 'sudo systemctl restart openvpn' and verify credentials" in normalized
    assert "CATEGORY:\nVPN" in normalized
    assert "TAGS:\nnetwork, remote, vpn" in normalized


def test_deterministic_normalization():
    # Calling twice with same inputs in different order of tags or with extra whitespace
    res1 = normalize_article(
        title="  Fix WiFi Driver  ",
        summary="Fixing intel wifi driver",
        problem="Driver missing after Windows 11 update",
        cause="Corrupt INF cache",
        resolution="pnputil /add-driver oem.inf /install",
        category="HARDWARE",
        tags=["wifi", "hardware", "intel"],
    )

    res2 = normalize_article(
        title="Fix WiFi Driver",
        summary="Fixing intel wifi driver",
        problem="Driver missing after Windows 11 update",
        cause="Corrupt INF cache",
        resolution="pnputil /add-driver oem.inf /install",
        category="HARDWARE",
        tags=["intel", "wifi", "hardware"],
    )

    assert res1 == res2


def test_preserves_technical_commands_and_code():
    code_block = "```bash\ncurl -X POST https://api.corp.local/vpn/auth -H 'Bearer token'\n```"
    normalized = normalize_article(
        title="VPN CLI Auth",
        summary="Authenticate via terminal",
        problem="GUI client fails",
        cause=None,
        resolution=code_block,
        category="VPN",
        tags=["cli", "curl"],
    )

    assert code_block in normalized
    assert "CAUSE:" not in normalized  # Missing section skipped cleanly
