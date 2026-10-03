"""
Stage 2 (Historical Tickets): Similar Resolved Ticket Retrieval Module.
Computes semantic vector similarity between current ticket query and historical
resolved tickets using the existing 384-dimensional all-MiniLM-L6-v2 embedding model.
Strictly preserves ticket IDs and provenance.
"""
import logging
import numpy as np
from typing import List, Optional

from app.embeddings.generator import generate_embedding, generate_embeddings
from app.rag.models import HistoricalTicketCandidate, SimilarTicketSource

logger = logging.getLogger(__name__)


def retrieve_similar_tickets(
    query_text: str,
    candidate_tickets: Optional[List[HistoricalTicketCandidate]] = None,
    top_k: int = 3,
    min_similarity: float = 0.30
) -> List[SimilarTicketSource]:
    """
    Ranks candidate historical resolved tickets against the current ticket's query text
    using dense semantic embedding cosine similarity.
    Only returns tickets with similarity >= min_similarity.
    """
    if not candidate_tickets:
        logger.debug("No candidate historical tickets provided for similar ticket matching.")
        return []

    cleaned_query = query_text.strip()
    if not cleaned_query:
        return []

    # Filter candidates to only those with non-empty resolution descriptions
    valid_candidates = [
        c for c in candidate_tickets
        if c.resolutionDescription and c.resolutionDescription.strip()
    ]

    if not valid_candidates:
        logger.debug("No valid historical tickets with non-empty resolution descriptions found.")
        return []

    try:
        query_vec = np.array(generate_embedding(cleaned_query), dtype=np.float32)
        q_norm = np.linalg.norm(query_vec)
        if q_norm == 0:
            return []

        # Formulate candidate textual representation from title and description
        candidate_texts = [
            f"{c.title}\n{c.description or ''}".strip()
            for c in valid_candidates
        ]

        candidate_embeddings = [
            np.array(e, dtype=np.float32)
            for e in generate_embeddings(candidate_texts)
        ]

        scored_tickets: List[SimilarTicketSource] = []
        for c, emb in zip(valid_candidates, candidate_embeddings):
            emb_norm = np.linalg.norm(emb)
            if emb_norm == 0:
                continue

            cosine_sim = float(np.dot(query_vec, emb) / (q_norm * emb_norm))
            if cosine_sim >= min_similarity:
                scored_tickets.append(
                    SimilarTicketSource(
                        ticketId=c.id,
                        title=c.title,
                        similarity=round(cosine_sim, 4),
                        resolutionSummary=c.resolutionDescription.strip(),
                        category=c.category,
                        priority=c.priority
                    )
                )

        # Sort descending by similarity
        scored_tickets.sort(key=lambda t: t.similarity, reverse=True)
        selected = scored_tickets[:top_k]

        logger.info(
            "Similar Ticket Retrieval: Found %d matching historical ticket(s) (threshold >= %.2f). Best similarity: %.4f",
            len(selected),
            min_similarity,
            selected[0].similarity if selected else 0.0
        )
        return selected

    except Exception as exc:
        logger.error("Failed to compute similar historical tickets: %s", exc)
        return []
