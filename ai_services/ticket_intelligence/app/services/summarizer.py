import re
from app.utils.text_cleaning import clean_text


def generate_deterministic_summary(title: str, description: str) -> str:
    """
    Generate a clean, professional, concise ITSM summary combining title and description
    using deterministic normalization without requiring an external LLM.
    """
    cleaned_title = clean_text(title)
    cleaned_desc = clean_text(description)

    # Normalize first-person references to standard ITSM incident format
    summary_text = cleaned_desc

    replacements = [
        (r"\b(i am unable to|i cannot|can't)\b", "User is unable to", re.IGNORECASE),
        (r"\b(i have not received|haven't received)\b", "User has not received", re.IGNORECASE),
        (r"\b(my corporate laptop|my laptop|my workstation)\b", "the assigned workstation", re.IGNORECASE),
        (r"\b(my|mine)\b", "the user's", re.IGNORECASE),
        (r"\b(i need|i require|i am requesting)\b", "User requests", re.IGNORECASE),
        (r"\b(we are experiencing|we have)\b", "Team is experiencing", re.IGNORECASE),
    ]

    for pattern, repl, flags in replacements:
        summary_text = re.sub(pattern, repl, summary_text, flags=flags)

    # Capitalize first letter
    if summary_text:
        summary_text = summary_text[0].upper() + summary_text[1:]

    # Ensure period at the end
    if summary_text and not summary_text.endswith((".", "!", "?")):
        summary_text += "."

    # If title already provides the main context, construct cohesive summary
    if not summary_text.lower().startswith(cleaned_title.lower()):
        # Check if the title is short enough to prepend
        return f"{cleaned_title}: {summary_text}"
    return summary_text
