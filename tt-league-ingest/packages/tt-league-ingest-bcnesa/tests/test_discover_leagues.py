"""League discovery on the fctt.cat index: territory sections, including Barcelona's per-category sub-headings."""

from __future__ import annotations

from bs4 import BeautifulSoup

from ingest_bcnesa import download as dl

PAGE_URL = "https://fctt.cat/competicions-estatals/"


def card(slug, title):
    return (f'<div class="jet-engine-listing-overlay-wrap" data-url="https://fctt.cat/lligues/{slug}/">'
            f'<h3>{title}</h3></div>')


INDEX = (
    "<h2>Lligues estatals (grups catalans)</h2>" + card("grup-1", "TDM G1")
    + "<h2>Lligues de la representació territorial de Barcelona</h2>"
    + "<h2>Preferent</h2>" + card("rtb-pref", "RTB PREFERENT G1") + card("rtb-pref-2", "RTB PREFERENT G2")
    + "<h2>Primera</h2>" + card("rtb-prim", "RTB PRIMERA G1")
    + "<h2>Lligues de la representació territorial de Girona</h2>" + card("rtg-pref", "RTG PREFERENT")
    + "<h2>Federació Catalana de Tennis de Taula</h2>"
)


def test_barcelona_cards_under_category_sub_headings_are_discovered():
    leagues = dl.discover_leagues(BeautifulSoup(INDEX, "html.parser"), PAGE_URL)

    barcelona = [(league.category, league.group) for league in leagues if league.territory == "Barcelona"]
    assert barcelona == [("RTB PREFERENT", "G1"), ("RTB PREFERENT", "G2"), ("RTB PRIMERA", "G1")]


def test_single_group_territory_and_non_territorial_sections():
    leagues = dl.discover_leagues(BeautifulSoup(INDEX, "html.parser"), PAGE_URL)

    assert [(league.territory, league.category, league.group) for league in leagues if league.territory == "Girona"] \
        == [("Girona", "RTG PREFERENT", "G1")]
    assert all(league.title != "TDM G1" for league in leagues)
    assert len(leagues) == 4
