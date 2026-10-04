"""Text helpers duplicated by several legacy scripts."""

from __future__ import annotations

import re
import unicodedata


def clean_text(value: str | None) -> str:
    """Collapse whitespace (including non-breaking spaces) and strip."""
    return " ".join((value or "").replace("\xa0", " ").split())


def fold(value: str) -> str:
    """Remove accents and case differences."""
    decomposed = unicodedata.normalize("NFKD", value)
    return "".join(ch for ch in decomposed if not unicodedata.combining(ch)).casefold()


def slugify(value: str) -> str:
    """Accent-free, lower-case, dash separated form of ``value``."""
    return re.sub(r"[^a-z0-9]+", "-", fold(value)).strip("-")
