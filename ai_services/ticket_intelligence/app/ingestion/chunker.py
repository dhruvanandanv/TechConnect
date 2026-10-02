"""
Intelligent Section-Aware Article Chunker.
Splits knowledge articles by structural domain sections (PROBLEM, CAUSE, RESOLUTION, SUMMARY)
with intelligent sentence-boundary splitting and calibrated overlap for retrieval accuracy.
"""
from typing import List, Dict, Any, Optional
from app.embeddings.config import embedding_settings
from app.ingestion.normalizer import clean_section_text


def _split_text_with_overlap(
    text: str, max_chars: int = 500, overlap_chars: int = 80
) -> List[str]:
    """
    Splits long section text into chunks respecting sentence/line boundaries where possible.
    """
    cleaned = clean_section_text(text)
    if not cleaned:
        return []

    if len(cleaned) <= max_chars:
        return [cleaned]

    chunks = []
    start = 0
    text_len = len(cleaned)

    while start < text_len:
        end = start + max_chars

        if end >= text_len:
            chunk = cleaned[start:].strip()
            if chunk:
                chunks.append(chunk)
            break

        # Look for natural breakpoint (newline, period, question mark, semicolon)
        boundary = -1
        for sep in ["\n\n", "\n", ". ", "? ", "! ", "; "]:
            pos = cleaned.rfind(sep, start + overlap_chars, end)
            if pos != -1:
                boundary = pos + len(sep)
                break

        if boundary == -1:
            # Fallback to last whitespace
            pos = cleaned.rfind(" ", start + overlap_chars, end)
            boundary = (pos + 1) if pos != -1 else end

        chunk = cleaned[start:boundary].strip()
        if chunk:
            chunks.append(chunk)

        # Advance start with overlap
        start = max(boundary - overlap_chars, start + 1)

    return chunks


def chunk_article(
    article_id: str,
    title: Optional[str] = None,
    summary: Optional[str] = None,
    problem: Optional[str] = None,
    cause: Optional[str] = None,
    resolution: Optional[str] = None,
    content: Optional[str] = None,
    category: Optional[str] = "GENERAL",
    tags: Optional[List[str]] = None,
    article_version: int = 1,
    max_chunk_chars: Optional[int] = None,
    chunk_overlap_chars: Optional[int] = None,
) -> List[Dict[str, Any]]:
    """
    Chunks a knowledge article into structural sections:
    1. PROBLEM
    2. CAUSE
    3. RESOLUTION
    4. SUMMARY / GENERAL
    
    Each chunk is enriched with article metadata for targeted semantic retrieval.
    """
    max_c = max_chunk_chars or embedding_settings.max_chunk_chars
    overlap_c = chunk_overlap_chars or embedding_settings.chunk_overlap_chars

    normalized_tags = [t.strip().lower() for t in (tags or []) if t and t.strip()]
    cat = (category or "GENERAL").strip().upper()

    sections_to_process = [
        ("SUMMARY", summary),
        ("PROBLEM", problem),
        ("CAUSE", cause),
        ("RESOLUTION", resolution),
    ]

    chunks: List[Dict[str, Any]] = []
    chunk_counter = 0

    # Process explicit structured sections
    for section_name, section_raw in sections_to_process:
        if not section_raw or not section_raw.strip():
            continue

        section_chunks = _split_text_with_overlap(section_raw, max_c, overlap_c)
        for sub_idx, chunk_text in enumerate(section_chunks):
            # Prepend context header for dense representation clarity
            header_prefix = f"[{section_name}] {title.strip() if title else ''}\n"
            full_chunk_text = f"{header_prefix}{chunk_text}".strip()

            chunk_id = f"{article_id}-{section_name.lower()}-{chunk_counter}"
            chunks.append(
                {
                    "chunkId": chunk_id,
                    "articleId": article_id,
                    "chunkIndex": chunk_counter,
                    "section": section_name,
                    "text": full_chunk_text,
                    "category": cat,
                    "tags": normalized_tags,
                    "articleVersion": article_version,
                }
            )
            chunk_counter += 1

    # If no structural sections existed, chunk generic content or title
    if not chunks:
        fallback_text = content or summary or title or "No content available."
        fallback_chunks = _split_text_with_overlap(fallback_text, max_c, overlap_c)
        for sub_idx, chunk_text in enumerate(fallback_chunks):
            chunk_id = f"{article_id}-general-{chunk_counter}"
            chunks.append(
                {
                    "chunkId": chunk_id,
                    "articleId": article_id,
                    "chunkIndex": chunk_counter,
                    "section": "GENERAL",
                    "text": chunk_text,
                    "category": cat,
                    "tags": normalized_tags,
                    "articleVersion": article_version,
                }
            )
            chunk_counter += 1

    return chunks
