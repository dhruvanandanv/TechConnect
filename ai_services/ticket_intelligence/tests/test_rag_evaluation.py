"""
Tests for RAG Development Evaluation benchmark.
Evaluates:
- Retrieval hit rate
- Top-1 retrieval
- Top-3 retrieval
- Grounded accuracy
- No-answer / out-of-domain behavior
"""
import pytest
from app.ingestion.worker import process_single_article
from app.vector.repository import vector_repository
from app.rag.evaluation import run_development_evaluation, DEV_BENCHMARK_DATASET


@pytest.fixture(autouse=True)
def seed_benchmark_corpus():
    vector_repository.clear()

    # Seed Article 1: VPN
    process_single_article({
        "id": "kb-vpn-test-1",
        "title": "Corporate AnyConnect VPN Configuration Guide",
        "summary": "Fixes gateway 504 timeouts and remote connectivity drops.",
        "problem": "Users encounter frequent VPN disconnects and DNS resolution errors.",
        "cause": "Local DNS cache corruption or outdated Cisco profile.",
        "resolution": "1. Flush DNS using ipconfig /flushdns.\n2. Update AnyConnect to v3.4.\n3. Restart client.",
        "category": "VPN",
        "tags": ["vpn", "cisco", "network"],
        "status": "PUBLISHED",
        "version": 1
    })

    # Seed Article 2: MFA
    process_single_article({
        "id": "kb-mfa-1",
        "title": "Microsoft Authenticator MFA Reset Procedure",
        "summary": "Steps to register and restore multi-factor authentication tokens.",
        "problem": "User changed mobile phone and cannot receive MFA approval push notifications.",
        "cause": "Old device token registration prevents new authenticator pairing.",
        "resolution": "1. Log into aka.ms/mysecurityinfo.\n2. Delete old phone device registration.\n3. Add new Microsoft Authenticator app and scan QR code.",
        "category": "ACCESS_MANAGEMENT",
        "tags": ["mfa", "azure", "security", "authenticator"],
        "status": "PUBLISHED",
        "version": 1
    })

    # Seed Article 3: Outlook Quota
    process_single_article({
        "id": "kb-outlook-quota-1",
        "title": "Exchange Online Mailbox Quota Cleanup & Archival",
        "summary": "Resolves mailbox full bouncebacks and Outlook send receive errors.",
        "problem": "Mailbox has exceeded 50GB storage limit. Incoming emails bounce with NDR 554 5.2.2.",
        "cause": "Excessive attachments in Deleted Items and Sent Items folders.",
        "resolution": "1. Empty Deleted Items and Junk Email.\n2. Enable Exchange Online Online Archive.\n3. Run Mailbox Cleanup Tool in Outlook.",
        "category": "SOFTWARE",
        "tags": ["outlook", "email", "exchange", "mailbox"],
        "status": "PUBLISHED",
        "version": 1
    })

    yield
    vector_repository.clear()


def test_development_evaluation_metrics():
    """Run development evaluation and verify performance thresholds."""
    metrics = run_development_evaluation(DEV_BENCHMARK_DATASET)

    assert metrics["totalEvaluated"] == 4
    assert metrics["positiveQueries"] == 3
    assert metrics["negativeQueries"] == 1
    assert metrics["retrievalHitRate"] == 1.0  # 100% on dev benchmark
    assert metrics["top1Accuracy"] >= 0.66
    assert metrics["top3Accuracy"] == 1.0
    assert metrics["noAnswerAccuracy"] == 1.0  # Out-of-domain properly rejected
