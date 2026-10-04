"""Federation sources handled by the ingestion pipelines."""

from __future__ import annotations

from enum import Enum


class Source(Enum):
    """Values are the exact ``ImportSource`` names accepted by the upload manifest."""

    RFETM = "RFETM"
    BCNESA = "BCNESA"
    FCTT = "FCTT"

    @classmethod
    def parse(cls, value: str) -> "Source":
        try:
            return cls(value.strip().upper())
        except ValueError:
            allowed = ", ".join(member.value for member in cls)
            raise ValueError(f"unknown source '{value}' (expected one of: {allowed})") from None

    @property
    def slug(self) -> str:
        """Lower-case name used for folders and entry-point names."""
        return self.value.lower()
