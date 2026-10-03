"""
Tests for Similar Historical Resolved Ticket Retrieval (Stage 2).
"""
from app.rag.models import HistoricalTicketCandidate
from app.rag.similar_tickets import retrieve_similar_tickets


def test_similar_ticket_ranking():
    candidates = [
        HistoricalTicketCandidate(
            id=101,
            title="VPN drops after 10 minutes on home network",
            description="User gets disconnected repeatedly when connected to Cisco AnyConnect from home WiFi.",
            category="VPN",
            priority="HIGH",
            resolutionDescription="Flushed DNS, updated AnyConnect client to v4.10, and updated wireless driver."
        ),
        HistoricalTicketCandidate(
            id=102,
            title="Outlook mailbox quota exceeded bounceback",
            description="Emails failing to deliver due to 50GB storage threshold.",
            category="SOFTWARE",
            priority="LOW",
            resolutionDescription="Cleaned deleted items folder and enabled online archive."
        ),
        HistoricalTicketCandidate(
            id=103,
            title="Printer paper jam on 3rd floor HP LaserJet",
            description="Hardware tray error 13.00.",
            category="HARDWARE",
            priority="MEDIUM",
            resolutionDescription="Cleared paper path and replaced roller kit."
        ),
    ]

    # Query for VPN issue
    results = retrieve_similar_tickets(
        query_text="Cisco AnyConnect VPN keeps dropping connection from home",
        candidate_tickets=candidates,
        top_k=2,
        min_similarity=0.30
    )

    assert len(results) > 0
    # Highest similarity should be the VPN ticket (101)
    assert results[0].ticketId == 101
    assert results[0].similarity > 0.40
    assert "Flushed DNS" in results[0].resolutionSummary


def test_similar_ticket_empty_candidates():
    results = retrieve_similar_tickets(
        query_text="Any question",
        candidate_tickets=[],
        top_k=3,
        min_similarity=0.30
    )
    assert results == []


def test_similar_ticket_no_resolution():
    candidates = [
        HistoricalTicketCandidate(
            id=201,
            title="Unresolved VPN Ticket",
            description="Still investigating",
            category="VPN",
            priority="HIGH",
            resolutionDescription=None  # No resolution provided
        )
    ]
    results = retrieve_similar_tickets(
        query_text="VPN issue",
        candidate_tickets=candidates,
        top_k=3,
        min_similarity=0.30
    )
    assert results == []
