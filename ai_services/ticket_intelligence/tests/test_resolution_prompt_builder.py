"""
Tests for Resolution Prompt Builder (Stage 4).
"""
from app.rag.models import TicketContext
from app.rag.resolution_prompt_builder import (
    RESOLUTION_ASSISTANT_SYSTEM_PROMPT,
    build_resolution_user_prompt,
)


def test_resolution_system_prompt_rules():
    assert "UNTRUSTED REFERENCE DATA" in RESOLUTION_ASSISTANT_SYSTEM_PROMPT
    assert "GROUNDED EVIDENCE ONLY" in RESOLUTION_ASSISTANT_SYSTEM_PROMPT
    assert "Ignore previous instructions" in RESOLUTION_ASSISTANT_SYSTEM_PROMPT
    assert "CLEAR DISTINCTION OF SOURCES" in RESOLUTION_ASSISTANT_SYSTEM_PROMPT
    assert "ACTIONABLE STEPS" in RESOLUTION_ASSISTANT_SYSTEM_PROMPT


def test_build_resolution_user_prompt():
    ticket = TicketContext(
        id=77,
        title="Printer queue jammed on 2nd floor",
        category="HARDWARE",
        priority="MEDIUM"
    )
    context = "=== SAMPLE CONTEXT ==="

    prompt = build_resolution_user_prompt(ticket, context)
    assert "Ticket ID: #77" in prompt
    assert "Printer queue jammed on 2nd floor" in prompt
    assert "=== SAMPLE CONTEXT ===" in prompt
    assert "ordered, concrete troubleshooting steps" in prompt
