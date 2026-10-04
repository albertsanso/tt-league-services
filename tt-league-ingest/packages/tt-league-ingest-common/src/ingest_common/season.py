"""Season value (``YYYY-YYYY``)."""

from __future__ import annotations

import re
from dataclasses import dataclass
from datetime import date

_SEASON_RE = re.compile(r"(\d{4})-(\d{4})")

# A season starts in August: both legacy ``current_season()`` implementations (BCNESA and the
# FCTT packager) use ``start = today.year if today.month >= 8 else today.year - 1``.
SEASON_START_MONTH = 8


@dataclass(frozen=True, order=True)
class Season:
    start_year: int

    def __post_init__(self) -> None:
        if not 1000 <= self.start_year <= 9998:
            raise ValueError(f"invalid season start year {self.start_year}")

    @classmethod
    def parse(cls, value: str) -> "Season":
        found = _SEASON_RE.fullmatch(value.strip())
        if not found or int(found.group(2)) != int(found.group(1)) + 1:
            raise ValueError(f"season '{value}' must look like 2026-2027 (consecutive years)")
        return cls(int(found.group(1)))

    @classmethod
    def current(cls, today: date | None = None) -> "Season":
        """Season in progress on ``today``: from August on it is the one starting that year."""
        today = today or date.today()
        return cls(today.year if today.month >= SEASON_START_MONTH else today.year - 1)

    def __str__(self) -> str:
        return f"{self.start_year}-{self.start_year + 1}"

    def slash_form(self) -> str:
        """``2026/2027``, the form used by the payload ``temporada`` field."""
        return f"{self.start_year}/{self.start_year + 1}"
