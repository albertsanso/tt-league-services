"""Match-day selector parsing (``3``, ``1,4`` or ``2-5``)."""

from __future__ import annotations

import re

_TOKEN_RE = re.compile(r"(\d+)(?:\s*-\s*(\d+))?")


def parse_match_days(value: str) -> frozenset[int]:
    days: set[int] = set()
    for token in filter(None, (part.strip() for part in value.split(","))):
        found = _TOKEN_RE.fullmatch(token)
        if not found:
            raise ValueError(f"invalid match day '{token}' (use e.g. 3, 1,4 or 2-5)")
        first, last = int(found.group(1)), int(found.group(2) or found.group(1))
        if first < 1 or last < first:
            raise ValueError(f"invalid match day range '{token}'")
        days.update(range(first, last + 1))
    if not days:
        raise ValueError("no match days given")
    return frozenset(days)
