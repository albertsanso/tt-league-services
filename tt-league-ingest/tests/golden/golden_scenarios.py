"""The golden scenarios: which saved pages are served, what goes wrong, and what each round runs."""

from __future__ import annotations

from golden_harness import BASE_URL, BLOCKED, EMPTY, TIMEOUT, Round, Scenario


def league(slug: str) -> str:
    return f"{BASE_URL}/lligues/{slug}/"


def day(slug: str, number: int) -> str:
    return f"{BASE_URL}/lligues/{slug}/?jornada={number}"


SCENARIOS = (
    # BCNESA: one league per territory, the real markup (veterans), a play-off phase, a partial jornada that is
    # completed in round 2, a future jornada, a forced refresh of one territory and filtered runs.
    Scenario("bcnesa", "territories", (
        "rtb-preferent-g1", "rtb-veterans-1a-g1", "rtb-preferent-g3-playoff", "rtg-preferent", "rtl-preferent",
        "rtt-preferent"), (
        Round(),
        Round(),
        Round(download=("--territory", "Girona", "--force"), parse=("--force",)),
        Round(download=("--category", "RTB PREFERENT", "--group", "G1", "--match_day", "1-2"),
              parse=("--category", "RTB PREFERENT", "--group", "G1", "--match_day", "1-2")),
    )),
    # BCNESA: comarcal league with a walkover (no results table) and a swapped table, server faults, validation
    # issues that persist across runs (tolerated from the third run on), region type filter.
    Scenario("bcnesa", "faults", ("rtb-preferent-g2", "rtb-1a-comarcal-g1"), (
        Round(download=("--region_type", "comarcal"), parse=()),
        Round(faults={day("rtb-preferent-g2", 1): (500, EMPTY), league("rtb-preferent-g2"): (TIMEOUT,)}),
        Round(),
        Round(),
        Round(download=("--region_type", "provincial"), parse=()),
    )),
    # BCNESA: PDFs, and the published-acta guard when the site drops the results of a published match.
    Scenario("bcnesa", "published-guard", ("rtb-preferent-g4",), (
        Round(download=("--no-skip_pdf",)),
        Round(download=("--force", "--no-skip_pdf")),
        Round(parse=("--force",), site_round=2),
    ), pdfs={"6801": (200, "text/html", b"<html>not a pdf</html>")}),
    # FCTT: the real saved pages (TDM G1), walkover, duplicated pairing, swapped table, a partial jornada whose
    # pairing changes, group pages without league title, faults, forced refresh and a second parse.
    Scenario("fctt", "tdm", ("grup-1", "grup-2", "grup-3"), (
        Round(faults={day("grup-2", 1): (500, 404), league("grup-3"): (503,)}),
        Round(),
        Round(download=("--force",), parse=("--force",)),
        Round(parse=("--category", "tdm", "--group", "g2", "--match_day", "1-2")),
    )),
    # FCTT: women's cup (unlinked doubles, final phase in the heading), PDFs, a blocked page, filters.
    Scenario("fctt", "femenina", ("1a-divisio", "2a-divisio"), (
        Round(download=("--no-skip_pdf", "--category", "female"),
              faults={day("2a-divisio", 1): (( 200, "text/html", BLOCKED),)}),
        Round(download=("--no-skip_pdf",)),
        Round(download=("--territory", "catalunya", "--group", "1a-divisio", "--match_day", "1", "--force")),
    ), pdfs={"7202": (200, "text/html", b"<html>not a pdf</html>")}),
)
