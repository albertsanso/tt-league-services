"""League discovery on the competitions index (``https://fctt.cat/competicions-estatals/``).

The index lists the territorial leagues of BCNESA as cards under ``Lligues de la representació territorial de
<territory>`` headings, and the Catalonia-wide FCTT leagues (``TDM`` and ``Copa Catalana Femenina``) as headed
links, with the previous seasons under ``Altres temporades`` (skipped). Which one a profile reads is data
(``SiteProfile.sections``).
"""

from __future__ import annotations

import re
from dataclasses import dataclass, replace
from typing import cast
from urllib.parse import urljoin, urlparse

from bs4 import BeautifulSoup

from ingest_common.region import region_values
from ingest_fcttweb.markup import HEADING_TAGS, Reader, fold, own_text, slugify
from ingest_fcttweb.profile import INDEX_URL, SiteProfile

OTHER_SEASONS_HEADING = "altres temporades"
SEASON_RE = re.compile(r"Temporada\s+(\d{4})\s*[/-]\s*(\d{4})", re.I)
PHASE_PATTERNS = (
    (r"play\s*-?\s*off\s+(?:de\s+)?t[ií]tol", "Play Off Títol"),
    (r"play\s*-?\s*off(?:\s+(?:d'|de\s+)?ascens)?", "Play Off Ascens"),
    (r"\bt[ií]tol\b", "TITOL"),
    (r"\bascens\b", "ASCENS"),
    (r"\bdescens\b", "DESCENS"),
    (r"\b([1-4])\s*a\s+fase\b", "{0}a Fase"),
)


@dataclass(frozen=True)
class League:
    """One league (a category and group) listed on the index."""

    territory: str  # Barcelona, Girona... (BCNESA) or catalunya (FCTT)
    title: str  # card title or heading label
    category: str  # display name (RTB PREFERENT) or category slug (tdm)
    group: str  # G1 or g1
    phase: str | None  # None when the index does not name a phase: it is read from the league page
    url: str
    gender: str | None = None  # male / female (FCTT)
    season: str = ""  # season the index suggests (FCTT)

    @property
    def key(self) -> str:
        return f"{self.season}/{self.category}/{self.group}"

    def with_season(self, season: str) -> "League":
        return replace(self, season=season)


@dataclass(frozen=True)
class Discovery:
    season: str | None  # the current season when the index tells it (FCTT)
    leagues: list[League]


def league_region(profile: SiteProfile, league: League) -> tuple[str, str] | None:
    """``(region, regionType)`` of a league, ``None`` for a BCNESA category without a known prefix."""
    return region_values(profile.source, league.category)


def next_season(season: str) -> str:
    start, end = (int(part) for part in season.split("-"))
    return f"{start + 1}-{end + 1}"


def discover(profile: SiteProfile, soup: BeautifulSoup, page_url: str = INDEX_URL) -> Discovery:
    if profile.sections.kind == "territorial-cards":
        return Discovery(None, _territorial_cards(soup, page_url))
    return _headed_links(profile, soup, page_url)


# --------------------------------------------------------------------------- territorial cards (BCNESA)


def phase_from_text(text: str) -> tuple[str | None, str]:
    """Return the phase named in ``text`` (or None) and ``text`` without it."""
    for pattern, phase in PHASE_PATTERNS:
        match = re.search(pattern, text, re.I)
        if match:
            rest = re.sub(r"\s+", " ", (text[:match.start()] + text[match.end():]).strip(" -"))
            return phase.format(*match.groups()), rest.strip()
    return None, text


def split_card_title(title: str) -> tuple[str, str, str | None]:
    """Split a card title such as ``RTB PREFERENT G1`` into category, group and phase."""
    phase, rest = phase_from_text(title)
    match = re.fullmatch(r"(.+?)\s+(?:G|GRUP\s*)(\d+)", rest, re.I)
    if match:
        return match.group(1).strip(), f"G{match.group(2)}", phase
    # Leagues with a single group (Girona, Tarragona, Lleida) have no group suffix.
    return rest.strip(), "G1", phase


def _territorial_cards(soup: BeautifulSoup, page_url: str) -> list[League]:
    """The league cards listed under each territorial heading."""
    territory_re = re.compile(r"representacio territorial de (\w+)")
    leagues: list[League] = []
    territory: str | None = None
    for element in soup.find_all(["h2", "div"]):
        if element.name == "h2":
            heading = fold(element.get_text(" ", strip=True))
            match = territory_re.search(heading)
            if match:
                territory = match.group(1).capitalize()
            elif heading.startswith("lligues"):
                territory = None  # another section (e.g. "Lligues estatals"), not a territorial one
            # Any other h2 is a category sub-heading (Barcelona lists "Preferent", "Primera", ...) inside the section.
            continue
        if territory is None or "jet-engine-listing-overlay-wrap" not in (element.get("class") or []):
            continue
        link = element.find("a", href=True)
        url = cast(str, element.get("data-url") or (link["href"] if link else ""))
        heading_tag = element.find(["h1", "h2", "h3", "h4", "h5", "h6"])
        title = re.sub(r"\s+", " ", heading_tag.get_text(" ", strip=True)) if heading_tag else ""
        if not url or not title:
            continue
        category, group, phase = split_card_title(title)
        leagues.append(League(territory, title, category, group, phase, urljoin(page_url, url)))
    return list(dict.fromkeys(leagues))


# --------------------------------------------------------------------------- headed links (FCTT)


def _headed_links(profile: SiteProfile, soup: BeautifulSoup, page_url: str) -> Discovery:
    """The current season and its leagues: links under the ``TDM`` / ``Copa Catalana Femenina`` headings."""
    reader = Reader(profile.text_mode)
    sections = dict(profile.sections.headings)
    section: tuple[str, str, str, str] | None = None
    other_seasons = False
    label: str | None = None
    listed_seasons: set[str] = set()
    leagues: list[League] = []
    for node in soup.find_all(True):
        if node.name in HEADING_TAGS:
            text = reader.text(node)
            key = text.casefold()
            if section and other_seasons and (found := SEASON_RE.search(text)):
                listed_seasons.add(f"{found.group(1)}-{found.group(2)}")
            elif key in sections:
                section, other_seasons, label = sections[key], False, None
            elif key == OTHER_SEASONS_HEADING:
                other_seasons, label = True, None
            elif node.name in ("h1", "h2"):
                section, label = None, None
            elif section and text:
                label = text
            continue
        if section and other_seasons and (found := SEASON_RE.search(own_text(node))):
            listed_seasons.add(f"{found.group(1)}-{found.group(2)}")
        if node.name != "a" or not section or other_seasons or not label:
            continue
        url = urljoin(page_url, str(node.get("href", ""))).split("?")[0].split("#")[0]
        if not urlparse(url).path.startswith("/lligues/"):
            continue
        territory, gender, category, prefix = section
        name = label[len(prefix):].strip() if prefix and label.casefold().startswith(prefix) else label
        leagues.append(League(territory, label, category, slugify(name) or slugify(label),
                              None, url if url.endswith("/") else url + "/", gender=gender))
        label = None
    current = next_season(max(listed_seasons)) if listed_seasons else None
    return Discovery(current, [league.with_season(current or "") for league in leagues])

