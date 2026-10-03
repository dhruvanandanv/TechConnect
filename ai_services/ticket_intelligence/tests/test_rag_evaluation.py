"""
Tests for RAG Development Evaluation benchmark across 12 representative IT support domains.
Evaluates:
- Retrieval hit rate
- Top-1 retrieval
- Top-3 retrieval
- Negative-query refusal
- Adversarial prompt injection defense
- Insufficient knowledge / out-of-domain refusal
"""
import pytest
from app.ingestion.worker import process_single_article
from app.vector.repository import vector_repository
from app.rag.evaluation import run_development_evaluation, DEV_BENCHMARK_DATASET


@pytest.fixture(autouse=True)
def seed_benchmark_corpus():
    vector_repository.clear()

    corpus = [
        {
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
        },
        {
            "id": "kb-mfa-1",
            "title": "Microsoft Authenticator MFA Reset Procedure",
            "summary": "Steps to register and restore multi-factor authentication tokens.",
            "problem": "User changed mobile phone and cannot receive MFA approval push notifications.",
            "cause": "Old device token registration prevents new authenticator pairing.",
            "resolution": "1. Log into aka.ms/mysecurityinfo.\n2. Delete old phone device registration.\n3. Add new Microsoft Authenticator app.",
            "category": "ACCESS_MANAGEMENT",
            "tags": ["mfa", "azure", "security", "authenticator"],
            "status": "PUBLISHED",
            "version": 1
        },
        {
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
        },
        {
            "id": "kb-pwd-1",
            "title": "Active Directory Self-Service Password Reset",
            "summary": "Guide for resetting expired or forgotten Active Directory domain passwords.",
            "problem": "User account password expired and cannot log onto Windows desktop.",
            "cause": "90-day password rotation policy expired account credentials.",
            "resolution": "1. Navigate to passwordreset.techconnect.internal.\n2. Verify identity via SMS/email OTP.\n3. Enter new compliant password.",
            "category": "ACCESS_MANAGEMENT",
            "tags": ["password", "ad", "activedirectory", "login"],
            "status": "PUBLISHED",
            "version": 1
        },
        {
            "id": "kb-network-1",
            "title": "Workstation Ethernet Default Gateway Connectivity Troubleshooting",
            "summary": "Resolves ethernet link disconnects and unreachable default gateway.",
            "problem": "Workstation displays No Internet and default gateway unreachable.",
            "cause": "DHCP lease expiration or network adapter driver stall.",
            "resolution": "1. Run ipconfig /release followed by ipconfig /renew.\n2. Reseat RJ45 ethernet patch cable.\n3. Restart network adapter.",
            "category": "NETWORK",
            "tags": ["network", "ethernet", "dhcp", "gateway"],
            "status": "PUBLISHED",
            "version": 1
        },
        {
            "id": "kb-software-1",
            "title": "Enterprise Software Center Request and Installation Elevation",
            "summary": "Installing approved enterprise applications without local admin rights.",
            "problem": "User prompted for administrator UAC credentials during installation.",
            "cause": "Least privilege security policy prohibits standard user installations.",
            "resolution": "1. Open Software Center or Company Portal.\n2. Search for the pre-approved package.\n3. Click Install to deploy with elevated system rights.",
            "category": "SOFTWARE",
            "tags": ["software", "admin", "uac", "install"],
            "status": "PUBLISHED",
            "version": 1
        },
        {
            "id": "kb-printer-1",
            "title": "Office Network Print Spooler Recovery",
            "summary": "Clearing stuck print queues and restarting Windows Print Spooler service.",
            "problem": "Print jobs stuck in spooling status and printer appears offline.",
            "cause": "Corrupted print spooler spool file in System32 spool folder.",
            "resolution": "1. Stop Print Spooler service via net stop spooler.\n2. Clear files in C:\\Windows\\System32\\spool\\PRINTERS.\n3. Start Print Spooler via net start spooler.",
            "category": "HARDWARE",
            "tags": ["printer", "hardware", "spooler", "print"],
            "status": "PUBLISHED",
            "version": 1
        },
        {
            "id": "kb-lockout-1",
            "title": "Domain Account Lockout Diagnosis and Unlocking",
            "summary": "Unlocking locked domain accounts and identifying cached credential lockouts.",
            "problem": "User account locked out after 5 consecutive incorrect passwords.",
            "cause": "Cached credentials on mobile device or mapped network drive repeatedly hammering Kerberos.",
            "resolution": "1. Open Active Directory Users and Computers.\n2. Locate user and check Unlock account.\n3. Verify mobile device WiFi credentials.",
            "category": "ACCESS_MANAGEMENT",
            "tags": ["lockout", "account", "active-directory", "unlock"],
            "status": "PUBLISHED",
            "version": 1
        },
        {
            "id": "kb-wifi-1",
            "title": "Corporate 802.1X Enterprise Wi-Fi Certificate Troubleshooting",
            "summary": "Resolves certificate rejection and WPA3 enterprise connection issues.",
            "problem": "Laptop cannot authenticate to TechConnect-Secure wireless SSID.",
            "cause": "Expired client certificate or untrusted Root CA in device store.",
            "resolution": "1. Forget TechConnect-Secure Wi-Fi network.\n2. Verify device certificate validity in certmgr.msc.\n3. Reconnect entering domain credentials.",
            "category": "NETWORK",
            "tags": ["wifi", "wireless", "802.1x", "certificate"],
            "status": "PUBLISHED",
            "version": 1
        }
    ]

    for article in corpus:
        process_single_article(article)

    yield
    vector_repository.clear()


def test_expanded_development_evaluation_metrics():
    """Run development evaluation across all 12 domains and verify performance thresholds."""
    metrics = run_development_evaluation(DEV_BENCHMARK_DATASET)

    assert metrics["totalEvaluated"] == 12
    assert metrics["positiveQueries"] == 9
    assert metrics["negativeQueries"] == 3
    assert metrics["retrievalHitRate"] >= 0.88  # High hit rate across 9 IT domains
    assert metrics["top1Accuracy"] >= 0.66
    assert metrics["top3Accuracy"] >= 0.88
    assert metrics["noAnswerAccuracy"] == 1.0  # 100% refusal on negative/injection/insufficient queries
