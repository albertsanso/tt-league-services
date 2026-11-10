"""BeautifulSoup extraction shared by the downloader, the parser and the status scan.

Element text can be gathered in two ways, one per legacy port (``SiteProfile.text_mode``): ``spaced`` joins the
text pieces with a space (``<b>a</b>b`` -> ``a b``, BCNESA) and ``joined`` concatenates them and turns ``<br>``
into a space (``a b`` -> ``ab``, FCTT). They only differ for text split over several inline elements.
"""

from __future__ import annotations

import re
import unicodedata
from typing import Literal
from urllib.parse import parse_qs, urlparse

from bs4 import BeautifulSoup, NavigableString, Tag

HEADING_TAGS = {"h1", "h2", "h3", "h4", "h5", "h6"}
RAW_TEXT_TAGS = {"script", "style", "noscript", "template"}
LINE_BREAK = "\x00"
SCORE_RE = re.compile(r"(\d+)\s*-\s*(\d+)")


def make_soup(data: bytes | str) -> BeautifulSoup:
    return BeautifulSoup(data, "html.parser")


# --------------------------------------------------------------------------- text


class Reader:
    """Reads the text of elements in the profile's text mode."""

    def __init__(self, mode: Literal["spaced", "joined"]):
        self.mode = mode

    def text(self, node: Tag | None) -> str:
        if node is None:
            return ""
        if self.mode == "spaced":
            return re.sub(r"\s+", " ", node.get_text(" ", strip=True)).strip()
        return " ".join(_raw_text(node).replace(LINE_BREAK, " ").split())

    def lines(self, node: Tag) -> list[str]:
        """Text split on ``<br>``, e.g. the two players of a doubles cell."""
        return [line for line in (" ".join(part.split()) for part in _raw_text(node).split(LINE_BREAK)) if line]


def _raw_text(node: Tag) -> str:
    if node.name == "br":
        return LINE_BREAK
    if node.name in RAW_TEXT_TAGS:
        return ""
    parts: list[str] = []

    def walk(element: Tag) -> None:
        for child in element.children:
            if isinstance(child, Tag):
                if child.name == "br":
                    parts.append(LINE_BREAK)
                elif child.name not in RAW_TEXT_TAGS:
                    walk(child)
            elif type(child) is NavigableString:  # comments, doctypes and CDATA are not text
                parts.append(str(child))

    walk(node)
    return "".join(parts)


def own_text(node: Tag) -> str:
    """The text directly inside ``node`` (not inside its child elements)."""
    return " ".join("".join(str(child) for child in node.children if type(child) is NavigableString).split())


def squash(value: str | None) -> str:
    return re.sub(r"\s+", " ", value or "").strip()


def fold(text: str) -> str:
    """Lower-case, accent-free, single-spaced text for comparisons."""
    text = unicodedata.normalize("NFD", text.replace("ª", "a"))
    text = "".join(character for character in text if unicodedata.category(character) != "Mn")
    return re.sub(r"\s+", " ", text).strip().casefold()


def fold_compact(text: str) -> str:
    """Lower-case, accent-free, alphanumeric-only text for comparisons."""
    text = unicodedata.normalize("NFD", text.replace("ª", "a"))
    text = "".join(character for character in text if unicodedata.category(character) != "Mn")
    return re.sub(r"[^a-z0-9]+", "", text.casefold())


def kebab_case(value: str) -> str:
    """``RTB VETERANS 1a`` -> ``rtb-veterans-1a``; accents and punctuation are dropped."""
    value = unicodedata.normalize("NFD", value.replace("ª", "a"))
    value = "".join(character for character in value if unicodedata.category(character) != "Mn")
    return re.sub(r"[^a-z0-9]+", "-", value.casefold()).strip("-") or "unknown"


def slugify(value: str) -> str:
    normalized = unicodedata.normalize("NFKD", value)
    ascii_text = "".join(char for char in normalized if not unicodedata.combining(char))
    return re.sub(r"[^a-z0-9]+", "-", ascii_text.lower()).strip("-")


def clean_name(value: str) -> str:
    """A display name made safe as a file-system name."""
    value = re.sub(r"\s+", " ", value).strip()
    value = re.sub(r'[<>:"/\\|?*\x00-\x1f]', "_", value)
    return value.rstrip(". ") or "unknown"


def normalise_group(value: str) -> str:
    match = re.fullmatch(r"(?:g|grup)?\s*(\d+)", value.strip(), re.I)
    return f"G{match.group(1)}" if match else value.strip().upper()


def compact(value: str) -> str:
    """``Play Off Títol`` -> ``PlayOffTitol``: accents and non-alphanumerics removed."""
    value = "".join(char for char in unicodedata.normalize("NFD", value) if unicodedata.category(char) != "Mn")
    return re.sub(r"[^A-Za-z0-9]+", "", value) or "Other"


# --------------------------------------------------------------------------- values


def query_value(href: str | None, key: str) -> str | None:
    values = parse_qs(urlparse(href or "").query).get(key)
    return values[0] if values else None


def parse_score(value: str | None) -> tuple[int, int] | None:
    match = SCORE_RE.search(squash(value))
    return (int(match.group(1)), int(match.group(2))) if match else None


def score_dict(score: tuple[int, int] | None) -> dict[str, int] | None:
    return {"local": score[0], "visitante": score[1]} if score else None


def has_class(node: Tag, name: str) -> bool:
    return name in (node.get("class") or [])


# --------------------------------------------------------------------------- page structure


def heading_number(heading: Tag, reader: Reader) -> int | None:
    """The jornada a ``.jornada-results-title`` names: ``Jornada N``, else its link's ``jornada`` parameter."""
    if found := re.search(r"Jornada\s+(\d+)", reader.text(heading), re.I):
        return int(found.group(1))
    link = heading.find("a")
    value = query_value(str(link.get("href", "")), "jornada") if isinstance(link, Tag) else None
    if value and value.isdigit():
        return int(value)
    found = re.search(r"(\d+)", reader.text(heading))
    return int(found.group(1)) if found else None


def day_sections(soup: BeautifulSoup, reader: Reader) -> tuple[dict[int | None, list[Tag]], dict[int, Tag]]:
    """Group the ``.match-container`` elements of a page by the ``Jornada N`` heading that precedes them.

    Containers before any heading, or after one without a number, go to the ``None`` section. The second result maps
    a jornada number to its (first) heading.
    """
    sections: dict[int | None, list[Tag]] = {}
    headings: dict[int, Tag] = {}
    current: int | None = None
    for node in soup.find_all(True):
        if has_class(node, "jornada-results-title"):
            current = heading_number(node, reader)
            sections.setdefault(current, [])
            if current is not None:
                headings.setdefault(current, node)
        elif has_class(node, "match-container"):
            sections.setdefault(current, []).append(node)
    return sections, headings


def selector_days(soup: BeautifulSoup) -> list[int]:
    """The jornadas offered by the jornada selector buttons (``data-jornada``)."""
    return sorted({int(str(node["data-jornada"])) for node in soup.find_all(attrs={"data-jornada": True})
                   if str(node["data-jornada"]).isdigit()})


def league_page_title(soup: BeautifulSoup) -> str:
    """The league heading of a page (``.br-apic-league-title``, else ``h1.entry-title``), empty when absent."""
    node = soup.select_one(".br-apic-league-title") or soup.select_one("h1.entry-title")
    return squash(node.get_text(" ", strip=True)) if node else ""
