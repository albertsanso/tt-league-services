"""Closed ``region`` / ``regionType`` vocabularies and their deterministic derivation.

``source`` stays the segregation key; the region only describes where a competition belongs. Unknown input
raises ``ValueError``: no fuzzy matching and no default region.
"""

from __future__ import annotations

import re
import unicodedata
from enum import Enum

from ingest_common.source import Source


class Region(Enum):
    ESPANA = "espana"
    CATALUNYA = "catalunya"
    BARCELONA = "barcelona"
    GIRONA = "girona"
    LLEIDA = "lleida"
    TARRAGONA = "tarragona"

    @classmethod
    def parse(cls, value: str) -> "Region":
        return _parse(cls, "region", value)


class RegionType(Enum):
    NACIONAL = "nacional"
    AUTONOMICA = "autonomica"
    PROVINCIAL = "provincial"
    COMARCAL = "comarcal"

    @classmethod
    def parse(cls, value: str) -> "RegionType":
        return _parse(cls, "regionType", value)


def _parse(enum, name: str, value: str):
    try:
        return enum(value.strip().lower())
    except (ValueError, AttributeError):
        allowed = ", ".join(member.value for member in enum)
        raise ValueError(f"unknown {name} '{value}' (expected one of: {allowed})") from None


# BCNESA category folders start with the representation code of their territory (``rtb-preferent``).
PREFIX_REGIONS = {"rtb": Region.BARCELONA, "rtg": Region.GIRONA, "rtl": Region.LLEIDA, "rtt": Region.TARRAGONA}
TERRITORY_REGIONS = {"barcelona": Region.BARCELONA, "girona": Region.GIRONA, "lleida": Region.LLEIDA,
                     "tarragona": Region.TARRAGONA, "catalunya": Region.CATALUNYA}


def _fold(text: str) -> str:
    text = unicodedata.normalize("NFD", text.replace("ª", "a"))
    text = "".join(character for character in text if unicodedata.category(character) != "Mn")
    return re.sub(r"[^a-z0-9]+", "", text.casefold())


def region_for_territory(territory: str) -> Region:
    """``Barcelona`` -> ``barcelona``; used to map the legacy ``territory`` filter onto ``region``."""
    region = TERRITORY_REGIONS.get(_fold(territory))
    if region is None:
        raise ValueError(f"unknown territory '{territory}' (expected one of: "
                         + ", ".join(TERRITORY_REGIONS) + ")")
    return region


def region_for(source: Source, category: str | None = None) -> tuple[Region, RegionType]:
    """The ``(region, regionType)`` of a league.

    RFETM is Spain-wide, FCTT leagues (TDM, Copa Catalana Femenina) are Catalonia-wide and BCNESA leagues are
    read from the representation prefix of the category folder (``rtb``/``rtg``/``rtl``/``rtt``); a BCNESA
    category containing ``comarcal`` is a ``comarcal`` league, any other one ``provincial``.
    """
    if source is Source.RFETM:
        return Region.ESPANA, RegionType.NACIONAL
    if source is Source.FCTT:
        return Region.CATALUNYA, RegionType.AUTONOMICA
    folded = _fold(category or "")
    region = PREFIX_REGIONS.get(folded[:3])
    if region is None:
        raise ValueError(f"cannot derive the region of BCNESA category '{category}': "
                         f"expected a prefix among {', '.join(PREFIX_REGIONS)}")
    return region, RegionType.COMARCAL if "comarcal" in folded else RegionType.PROVINCIAL


def region_values(source: Source, category: str | None = None) -> tuple[str, str] | None:
    """``(region, regionType)`` values of a league, or ``None`` when the category has no known prefix.

    For status reports and page meta tags, which describe saved content and must not fail on one odd folder;
    the parsers use ``region_for`` and fail clearly.
    """
    try:
        region, region_type = region_for(source, category)
    except ValueError:
        return None
    return region.value, region_type.value


PROVINCE_NAMES = {Region.BARCELONA: "Barcelona", Region.GIRONA: "Girona", Region.LLEIDA: "Lleida",
                  Region.TARRAGONA: "Tarragona"}
BCNESA_REGION_TYPES = (RegionType.PROVINCIAL, RegionType.COMARCAL)


def scope_error(source: Source, region: str | None, region_type: str | None, territory: str | None = None) -> str | None:
    """Why a ``region`` / ``regionType`` / ``territory`` filter can never select a league of ``source``.

    Values are already validated vocabulary members (``IngestFilters`` rejects unknown ones).
    """
    leagues = {
        Source.RFETM: ({Region.ESPANA}, {RegionType.NACIONAL}),
        Source.FCTT: ({Region.CATALUNYA}, {RegionType.AUTONOMICA}),
        Source.BCNESA: (set(PROVINCE_NAMES), set(BCNESA_REGION_TYPES)),
    }
    regions, types = leagues[source]
    if region and Region.parse(region) not in regions:
        return f"has no leagues in region '{region}' (expected: {', '.join(sorted(r.value for r in regions))})"
    if region_type and RegionType.parse(region_type) not in types:
        return (f"has no leagues of regionType '{region_type}' "
                f"(expected: {', '.join(sorted(t.value for t in types))})")
    if territory and source is Source.BCNESA and region:
        try:
            if region_for_territory(territory) is not Region.parse(region):
                return f"territory '{territory}' conflicts with region '{region}'"
        except ValueError as error:
            return str(error)
    return None


def category_matches_region(source: Source, category: str, region: str | None, region_type: str | None) -> bool:
    """Whether the league of ``category`` satisfies the region filters; unknown categories never match one."""
    if not region and not region_type:
        return True
    values = region_values(source, category)
    return values is not None and region in (None, values[0]) and region_type in (None, values[1])
