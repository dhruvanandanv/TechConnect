import re
from typing import List


def clean_text(text: str) -> str:
    """Normalize input text: remove control characters, normalize whitespace, lowercase."""
    if not text:
        return ""
    # Replace non-printable characters
    cleaned = re.sub(r"[\r\n\t]+", " ", text)
    # Remove excessive punctuation repetitions
    cleaned = re.sub(r"([!?,;.]){2,}", r"\1", cleaned)
    # Strip non-ASCII characters to standard ASCII printable
    cleaned = re.sub(r"[^\x00-\x7F]+", " ", cleaned)
    # Normalize multiple spaces
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    return cleaned


def combine_ticket_text(title: str, description: str) -> str:
    """
    Combine title and description into a unified weighted text string for TF-IDF feature extraction.
    The title is repeated twice to place higher priority on the headline summary.
    """
    cleaned_title = clean_text(title)
    cleaned_desc = clean_text(description)
    return f"{cleaned_title} {cleaned_title} {cleaned_desc}".strip()
