"""Ingest scopes: the ``{"scopes": [...]}`` contract shared by the REST body and the CLI ``--scope-file``.

A scope has the optional keys ``category``, ``group``, ``phase``, ``territory``, ``gender`` (text, the same
vocabulary as the filters) and ``matchDays`` (an array of positive integers or a selector such as ``3``, ``1,4``
or ``2-5``). Keys inside a scope are AND-combined; scopes are OR-combined.
"""

from __future__ import annotations

from collections.abc import Iterable, Mapping

from ingest_common.match_days import parse_match_days
from ingest_common.run import IngestFilters

SCOPE_KEYS = ("category", "group", "phase", "territory", "gender", "matchDays")


def _text(name: str, value: object) -> str | None:
    if value is None:
        return None
    if not isinstance(value, str):
        raise ValueError(f"{name} must be text")
    return value.strip() or None


def _match_days(value: object) -> frozenset[int] | None:
    if value is None:
        return None
    if isinstance(value, str):
        return parse_match_days(value) if value.strip() else None
    if isinstance(value, list):
        if not value:
            raise ValueError("matchDays must not be empty")
        if any(isinstance(day, bool) or not isinstance(day, int) or day < 1 for day in value):
            raise ValueError("matchDays must be positive integers")
        return frozenset(value)
    raise ValueError("matchDays must be an array of integers or a selector such as 1,4 or 2-5")


def scope_from_values(*, category: object = None, group: object = None, phase: object = None,
                      territory: object = None, gender: object = None, match_days: object = None) -> IngestFilters:
    """One validated scope; blank text counts as unset and a scope must set at least one key."""
    scope = IngestFilters(_text("category", category), _text("group", group), _text("phase", phase),
                          _match_days(match_days), _text("gender", gender), _text("territory", territory))
    if scope.is_empty:
        raise ValueError("a scope must set at least one of " + ", ".join(SCOPE_KEYS))
    return scope


def scopes_from_values(values: Iterable[Mapping[str, object]]) -> tuple[IngestFilters, ...]:
    """Validate scope objects (keys already restricted to ``SCOPE_KEYS``); errors name the scope position."""
    scopes = []
    for index, value in enumerate(values, 1):
        try:
            scopes.append(scope_from_values(category=value.get("category"), group=value.get("group"),
                                            phase=value.get("phase"), territory=value.get("territory"),
                                            gender=value.get("gender"), match_days=value.get("matchDays")))
        except ValueError as error:
            raise ValueError(f"scope {index}: {error}") from None
    if not scopes:
        raise ValueError("scopes must not be empty")
    return tuple(dict.fromkeys(scopes))


def parse_scopes(raw: object) -> tuple[IngestFilters, ...]:
    """Parse a decoded ``{"scopes": [...]}`` document (the CLI ``--scope-file``)."""
    if not isinstance(raw, dict) or set(raw) != {"scopes"}:
        raise ValueError('the scope document must be an object with the single key "scopes"')
    items = raw["scopes"]
    if not isinstance(items, list):
        raise ValueError("scopes must be an array")
    for index, item in enumerate(items, 1):
        if not isinstance(item, dict):
            raise ValueError(f"scope {index}: must be an object")
        unknown = sorted(set(item) - set(SCOPE_KEYS))
        if unknown:
            raise ValueError(f"scope {index}: unknown key(s) {', '.join(unknown)} (allowed: {', '.join(SCOPE_KEYS)})")
    return scopes_from_values(items)
