"""
Deterministic Knowledge Article Normalizer for TechConnect.
Constructs canonical, structured representations of knowledge articles while
preserving technical keywords, code snippets, command syntax, and diagnostic terms.
"""
import re
from typing import List, Optional


def clean_section_text(text: Optional[str]) -> str:
    """
    Cleans individual section text by normalizing line breaks and excess horizontal whitespace,
    while preserving code blocks, commands, and markdown formatting.
    """
    if not text:
        return ""
    # Normalize Windows and Mac line endings to standard LF
    normalized = text.replace("\r\n", "\n").replace("\r", "\n")
    # Replace multiple trailing spaces on lines
    lines = [re.sub(r"[ \t]+$", "", line) for line in normalized.split("\n")]
    result = "\n".join(lines).strip()
    # Collapse 3 or more consecutive newlines into 2
    result = re.sub(r"\n{3,}", "\n\n", result)
    return result


def normalize_article(
    title: Optional[str],
    summary: Optional[str],
    problem: Optional[str],
    cause: Optional[str],
    resolution: Optional[str],
    category: Optional[str],
    tags: Optional[List[str]] = None,
) -> str:
    """
    Normalizes a knowledge article into a canonical, deterministic string.
    Structure:
    TITLE: ...
    SUMMARY: ...
    PROBLEM: ...
    CAUSE: ...
    RESOLUTION: ...
    CATEGORY: ...
    TAGS: ...
    """
    sections = []

    clean_title = clean_section_text(title)
    if clean_title:
        sections.append(f"TITLE:\n{clean_title}")

    clean_summary = clean_section_text(summary)
    if clean_summary:
        sections.append(f"SUMMARY:\n{clean_summary}")

    clean_problem = clean_section_text(problem)
    if clean_problem:
        sections.append(f"PROBLEM:\n{clean_problem}")

    clean_cause = clean_section_text(cause)
    if clean_cause:
        sections.append(f"CAUSE:\n{clean_cause}")

    clean_resolution = clean_section_text(resolution)
    if clean_resolution:
        sections.append(f"RESOLUTION:\n{clean_resolution}")

    clean_category = (category or "").strip().upper()
    if clean_category:
        sections.append(f"CATEGORY:\n{clean_category}")

    if tags:
        # Sort and deduplicate tags deterministically
        normalized_tags = sorted(list({t.strip().lower() for t in tags if t and t.strip()}))
        if normalized_tags:
            sections.append(f"TAGS:\n{', '.join(normalized_tags)}")

    return "\n\n".join(sections)
