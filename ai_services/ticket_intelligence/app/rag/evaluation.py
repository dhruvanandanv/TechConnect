"""
Development Evaluation Module for TechConnect RAG.
Evaluates retrieval hit rate, top-1 retrieval, top-3 retrieval, and no-answer behavior
on a curated enterprise benchmark dataset.

NOTE: This is a development benchmark dataset used to evaluate retrieval precision and
grounding calibration during continuous integration and local development.
"""
from typing import List, Dict, Any
from app.rag.models import RagAnswerRequest
from app.rag.service import rag_service

DEV_BENCHMARK_DATASET = [
    {
        "id": "eval-1",
        "question": "My corporate VPN keeps disconnecting when I work from home",
        "category": "VPN",
        "expected_article_id": "kb-vpn-test-1",
        "expected_keyword": "VPN",
        "should_be_grounded": True,
    },
    {
        "id": "eval-2",
        "question": "How do I reset my MFA authenticator after getting a new phone?",
        "category": "ACCESS_MANAGEMENT",
        "expected_article_id": "kb-mfa-1",
        "expected_keyword": "Authenticator",
        "should_be_grounded": True,
    },
    {
        "id": "eval-3",
        "question": "Outlook mailbox is full and cannot send or receive emails",
        "category": "SOFTWARE",
        "expected_article_id": "kb-outlook-quota-1",
        "expected_keyword": "Mailbox",
        "should_be_grounded": True,
    },
    {
        "id": "eval-4",
        "question": "What is the recipe for baking chocolate brownies?",
        "category": None,
        "expected_article_id": None,
        "expected_keyword": None,
        "should_be_grounded": False,
    },
]


def run_development_evaluation(dataset: List[Dict[str, Any]] = None) -> Dict[str, Any]:
    """
    Runs automated evaluation across test queries and computes:
    - retrieval hit rate
    - top-1 accuracy
    - top-3 accuracy
    - no-answer behavior accuracy
    """
    eval_set = dataset or DEV_BENCHMARK_DATASET
    total = len(eval_set)
    hit_count = 0
    top_1_count = 0
    top_3_count = 0
    correct_no_answer_count = 0
    total_negative_queries = 0

    results = []

    for item in eval_set:
        req = RagAnswerRequest(
            query=item["question"],
            category=item["category"],
            topK=3,
            minSimilarity=0.30,
        )
        response = rag_service.generate_answer(req)

        expected_id = item["expected_article_id"]
        should_ground = item["should_be_grounded"]

        item_result = {
            "id": item["id"],
            "question": item["question"],
            "expected_article_id": expected_id,
            "grounded": response.grounded,
            "sources_count": len(response.sources),
            "sources": [s.articleId for s in response.sources],
            "passed": False,
        }

        if not should_ground:
            total_negative_queries += 1
            if not response.grounded and len(response.sources) == 0:
                correct_no_answer_count += 1
                item_result["passed"] = True
        else:
            retrieved_ids = [s.articleId for s in response.sources]
            if expected_id in retrieved_ids:
                hit_count += 1
                if retrieved_ids and retrieved_ids[0] == expected_id:
                    top_1_count += 1
                if expected_id in retrieved_ids[:3]:
                    top_3_count += 1
                item_result["passed"] = True

        results.append(item_result)

    positive_queries = total - total_negative_queries
    hit_rate = (hit_count / positive_queries) if positive_queries > 0 else 1.0
    top_1_rate = (top_1_count / positive_queries) if positive_queries > 0 else 1.0
    top_3_rate = (top_3_count / positive_queries) if positive_queries > 0 else 1.0
    no_answer_rate = (correct_no_answer_count / total_negative_queries) if total_negative_queries > 0 else 1.0

    return {
        "totalEvaluated": total,
        "positiveQueries": positive_queries,
        "negativeQueries": total_negative_queries,
        "retrievalHitRate": round(hit_rate, 4),
        "top1Accuracy": round(top_1_rate, 4),
        "top3Accuracy": round(top_3_rate, 4),
        "noAnswerAccuracy": round(no_answer_rate, 4),
        "details": results,
    }
