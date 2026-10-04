"""JSON-schema validation of actas and teams against the packaged model definitions."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator

SCHEMA_DIR = Path(__file__).resolve().parent / "schema"
ACTA_SCHEMA_PATH = SCHEMA_DIR / "acta-model-definition.json"
TEAM_SCHEMA_PATH = SCHEMA_DIR / "team-model-definition.json"


class _SchemaValidator:
    def __init__(self, schema_path: Path) -> None:
        self._validator = Draft202012Validator(json.loads(schema_path.read_text(encoding="utf-8")))

    def errors(self, payload: Any) -> list[str]:
        return sorted(f"{'/'.join(str(p) for p in error.absolute_path) or '<root>'}: {error.message}"
                      for error in self._validator.iter_errors(payload))

    def is_valid(self, payload: Any) -> bool:
        return not self.errors(payload)


class ActaValidator(_SchemaValidator):
    def __init__(self) -> None:
        super().__init__(ACTA_SCHEMA_PATH)


class TeamsValidator(_SchemaValidator):
    def __init__(self) -> None:
        super().__init__(TEAM_SCHEMA_PATH)
