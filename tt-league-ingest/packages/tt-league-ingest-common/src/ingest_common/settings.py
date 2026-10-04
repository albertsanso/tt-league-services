"""Ingestion settings built from CLI arguments or the environment."""

from __future__ import annotations

import os
from collections.abc import Mapping
from dataclasses import dataclass
from pathlib import Path

from ingest_common.source import Source

DATA_DIR_VARIABLE = "TT_INGEST_DATA_DIR"
API_URL_VARIABLE = "TT_LEAGUE_API_URL"
API_TOKEN_VARIABLE = "TT_LEAGUE_API_TOKEN"


class ConfigurationError(Exception):
    """A required setting is missing or invalid."""


@dataclass(frozen=True)
class IngestSettings:
    data_dir: Path
    api_url: str | None = None
    api_token: str | None = None

    @classmethod
    def build(cls, data_dir: Path | str | None = None, env: Mapping[str, str] | None = None) -> "IngestSettings":
        env = os.environ if env is None else env
        raw = str(data_dir) if data_dir else env.get(DATA_DIR_VARIABLE, "").strip()
        if not raw:
            raise ConfigurationError(f"{DATA_DIR_VARIABLE} (or --data-dir) is required")
        path = Path(raw)
        if not path.is_dir():
            raise ConfigurationError(f"{DATA_DIR_VARIABLE} '{raw}' is not an existing directory")
        return cls(path, env.get(API_URL_VARIABLE, "").strip() or None, env.get(API_TOKEN_VARIABLE, "").strip() or None)

    def source_dir(self, source: Source) -> Path:
        return self.data_dir / source.slug

    def content_dir(self, source: Source) -> Path:
        return self.source_dir(source) / "content"

    def actas_json_dir(self, source: Source) -> Path:
        return self.source_dir(source) / "actas-json"

    def equipos_json_dir(self, source: Source) -> Path:
        return self.source_dir(source) / "equipos-json"

    def packages_dir(self, source: Source) -> Path:
        return self.source_dir(source) / "packages"

    def logs_dir(self, source: Source) -> Path:
        return self.source_dir(source) / "logs"

    def require_upload(self) -> tuple[str, str]:
        missing = [name for name, value in ((API_URL_VARIABLE, self.api_url), (API_TOKEN_VARIABLE, self.api_token))
                   if not value]
        if missing:
            raise ConfigurationError(f"{' and '.join(missing)} must be set for the upload stage")
        return self.api_url, self.api_token  # type: ignore[return-value]
