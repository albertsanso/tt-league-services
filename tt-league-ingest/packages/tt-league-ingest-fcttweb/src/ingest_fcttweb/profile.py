"""The per-source facts of the fctt.cat scraper: ``BCNESA_PROFILE`` and ``FCTT_PROFILE``.

Both sources scrape the same website with the same markup and emit the same acta model; they started as two
independent ports whose output must stay byte-compatible (``tt-league-ingest/AGENTS.md``), so every legacy
difference is declared here as data and the shared code only branches on these values, never on the source.

``source`` stays the segregation key (data directories, manifests, ``federated_*`` scoping, ``id_partido``
identity). Region support lives in ``ingest_common.region``; a profile only says where its leagues come from.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Literal

from ingest_common.source import Source

INDEX_URL = "https://fctt.cat/competicions-estatals/"
FEDERATION = "Federació Catalana de Tennis Taula"

# --------------------------------------------------------------------------- HTTP etiquette


@dataclass(frozen=True)
class RobotsPolicy:
    attempts: int  # requests made to read robots.txt before giving up
    # BCNESA treats any status >= 400 as "no robots.txt"; FCTT only a 4xx other than 429 (5xx and 429 are retried).
    any_error_means_none: bool
    # What an unreadable robots.txt means: "allow" (BCNESA) or "cached-or-disallow": reuse the copy stored in the
    # metrics file, and with none ever seen disallow everything (FCTT).
    unreadable: Literal["allow", "cached-or-disallow"]
    accept: str | None  # Accept header of the robots.txt request (None: the profile's HTML one)


@dataclass(frozen=True)
class HttpPolicy:
    delay: float  # default seconds between requests
    backoff: float  # default base seconds of the exponential retry backoff
    timeout: float
    retry_statuses: frozenset[int]
    user_agents: tuple[str, ...]
    # "shuffled": a random permutation cycled across all requests (BCNESA, browser agents only);
    # "by-attempt": agent ``attempt - 1`` of the list, so the identifying tool agent goes first (FCTT).
    user_agent_order: Literal["shuffled", "by-attempt"]
    accept_html: str
    accept_pdf: str | None  # None: the HTML Accept header is used for PDFs too
    accept_language: str
    backoff_cap: float | None  # upper bound of the computed backoff
    retry_after_cap: float
    retry_after_mode: Literal["at-least", "instead"]  # a Retry-After value raises the backoff, or replaces it
    retry_after_dates: bool  # Retry-After may be an HTTP date
    message_includes_reason: bool  # "HTTP 500 Internal Server Error" instead of "HTTP 500"
    exception_in_message: bool  # network errors read "ConnectionError: ..." instead of the bare exception text
    health_on_rejected_content: bool  # a page that never validated counts as an HTTP error in the health counters
    robots: RobotsPolicy


# --------------------------------------------------------------------------- output layout


@dataclass(frozen=True)
class MetaKeys:
    """Names of the ``<meta>`` tags of a saved match day page (written by the downloader, read by parse/status)."""

    status: str
    season: str
    territory: str
    region: str
    region_type: str
    category: str
    group: str
    phase: str
    match_day: str
    source_url: str
    downloaded_at: str
    # BCNESA only
    content_hash: str | None = None
    issues: str | None = None
    content_source: str | None = None
    # FCTT only
    origin: str | None = None
    gender: str | None = None
    matches: str | None = None
    matches_played: str | None = None
    matches_incomplete: str | None = None
    content_fingerprint: str | None = None
    calendar_fingerprint: str | None = None


@dataclass(frozen=True)
class OutputLayout:
    default_phase: str
    html_name: str  # "jornada_{day:02d}.html"
    html_regex: str  # the same name as a regular expression with the day as group 1
    html_glob: str
    pdf_folder: str  # folder of the PDF actas of a day
    # Download folder values: "clean-name" keeps the display names (file-system safe), "slug" expects slugs.
    folder_values: Literal["clean-name", "slug"]
    json_name: str  # "jornada_{day:02d}_local_team_{home}_away_team_{away}.json"
    json_category: Literal["kebab", "slug"]  # BCNESA kebab-cases the category only; FCTT slugifies every segment
    json_folder_slugs: bool  # group and phase folders are slugified as well
    meta: MetaKeys


# --------------------------------------------------------------------------- the profile


@dataclass(frozen=True)
class SectionSpec:
    """How the leagues are found on the competitions index."""

    kind: Literal["territorial-cards", "headed-links"]
    # headed-links: heading text (casefolded) -> (territory, gender, category slug, group label prefix to drop)
    headings: tuple[tuple[str, tuple[str, str, str, str]], ...] = ()
    territories: tuple[str, ...] = ()


@dataclass(frozen=True)
class IngestorSpec:
    """How the ingestor drives the scripts (CLI option names, scoping)."""

    delay_option: str
    supported_filters: frozenset[str]
    gender_in_category: bool  # the scripts' --category also selects a league by its gender
    territory_from_region: bool  # a ``region`` filter becomes the downloader's --territory
    region_type_option: str | None  # downloader option that narrows by regionType
    scope: Literal["territory-prefix", "category-or-gender"]


@dataclass(frozen=True)
class SiteProfile:
    source: Source
    sections: SectionSpec
    layout: OutputLayout
    http: HttpPolicy
    ingestor: IngestorSpec
    # "validated-pages": a page is validated on its own and rebuilt (BCNESA); "calendar-checked": it is checked
    # against the league calendar and saved as served, annotated (FCTT).
    download_model: Literal["validated-pages", "calendar-checked"]
    # "match-status": one status per match (complete / partial / incomplete, a walkover is complete) decides what is
    # parsed (FCTT); "table-present": a match is published when it has a results table (BCNESA).
    status_model: Literal["match-status", "table-present"]
    # "published-guard": a published JSON is never replaced by an unpublished one; "parse-state": a state file
    # skips unchanged pages and removes the JSON a page no longer produces (FCTT).
    parse_policy: Literal["published-guard", "parse-state"]
    fixture_id: Literal["compact", "slug"]
    emits_gender: bool
    text_mode: Literal["spaced", "joined"]  # how element text is gathered (see markup.Reader)
    aware_clock: bool  # match start times are Europe/Madrid-aware (FCTT) or naive local (BCNESA)
    decode: Literal["bytes", "text"]  # BeautifulSoup parses the raw bytes (BCNESA) or the decoded text (FCTT)
    download_logger: str  # logger names of the scripts (kept: operators grep and the REST service logs them)
    parse_logger: str

    @property
    def name(self) -> str:
        return self.source.slug


# --------------------------------------------------------------------------- BCNESA

BCNESA_USER_AGENTS = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Safari/605.1.15",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36 Edg/129.0.0.0",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
)

BCNESA_PROFILE = SiteProfile(
    source=Source.BCNESA,
    sections=SectionSpec("territorial-cards", territories=("Barcelona", "Girona", "Lleida", "Tarragona")),
    layout=OutputLayout(
        default_phase="1a Fase",
        html_name="jornada_{day:02d}.html",
        html_regex=r"jornada_(\d+)\.html",
        html_glob="jornada_*.html",
        pdf_folder="jornada_{day:02d}",
        folder_values="clean-name",
        json_name="jornada_{day:02d}_local_team_{home}_away_team_{away}.json",
        json_category="kebab",
        json_folder_slugs=False,
        meta=MetaKeys(
            status="acta-content-status", season="season", territory="territory", region="region",
            region_type="region-type", category="category", group="group", phase="phase", match_day="match-day",
            source_url="source-url", downloaded_at="downloaded-at",
            content_hash="acta-content-hash", issues="acta-content-issues", content_source="content-source"),
    ),
    http=HttpPolicy(
        delay=1.0, backoff=2.0, timeout=60.0,
        # fctt.cat answers 404 and 500 intermittently for pages that exist, so both are retried.
        retry_statuses=frozenset({403, 404, 408, 425, 429, 500, 502, 503, 504}),
        # Full browser user agents: fctt.cat serves empty league pages to short or bot-like ones.
        user_agents=BCNESA_USER_AGENTS, user_agent_order="shuffled",
        accept_html="text/html,application/xhtml+xml,application/xml;q=0.9,application/pdf;q=0.9,*/*;q=0.8",
        accept_pdf=None, accept_language="ca,es;q=0.8,en;q=0.5",
        backoff_cap=None, retry_after_cap=300.0, retry_after_mode="at-least", retry_after_dates=False,
        message_includes_reason=False, exception_in_message=False, health_on_rejected_content=False,
        robots=RobotsPolicy(attempts=1, any_error_means_none=True, unreadable="allow", accept=None),
    ),
    ingestor=IngestorSpec(
        delay_option="--delay",
        supported_filters=frozenset({"category", "group", "phase", "match_days", "territory", "region", "region_type"}),
        gender_in_category=False, territory_from_region=True, region_type_option="--region_type",
        scope="territory-prefix"),
    download_model="validated-pages", status_model="table-present", parse_policy="published-guard",
    fixture_id="compact", emits_gender=False, text_mode="spaced", aware_clock=False, decode="bytes",
    download_logger="download_actas_content_incremental", parse_logger="parse_actas_content_incremental",
)

# --------------------------------------------------------------------------- FCTT

FCTT_PROFILE = SiteProfile(
    source=Source.FCTT,
    sections=SectionSpec("headed-links", headings=(
        ("tdm", ("catalunya", "male", "tdm", "tdm")),
        ("copa catalana femenina", ("catalunya", "female", "copa-catalana-femenina", "")),
    )),
    layout=OutputLayout(
        default_phase="regular",
        html_name="jornada-{day}.html",
        html_regex=r"jornada-(\d+)\.html",
        html_glob="jornada-*.html",
        pdf_folder="jornada-{day}",
        folder_values="slug",
        json_name="jornada_{day}_local_team_{home}_away_team_{away}.json",
        json_category="slug",
        json_folder_slugs=True,
        meta=MetaKeys(
            status="acta-content-status", season="acta-season", territory="acta-territory", region="acta-region",
            region_type="acta-region-type", category="acta-category", group="acta-group", phase="acta-phase",
            match_day="acta-match-day", source_url="acta-source-url", downloaded_at="acta-downloaded-at",
            origin="acta-content-origin", gender="acta-gender", matches="acta-matches",
            matches_played="acta-matches-played", matches_incomplete="acta-matches-incomplete",
            content_fingerprint="acta-content-fingerprint", calendar_fingerprint="acta-calendar-fingerprint"),
    ),
    http=HttpPolicy(
        delay=3.0, backoff=5.0, timeout=30.0,
        # fctt.cat intermittently answers 404 for valid league pages, so 404 is retried too.
        # 401/403 are never retried: an explicit refusal is respected, not worked around.
        retry_statuses=frozenset({404, 408, 425, 429, 500, 502, 503, 504, 520, 521, 522, 523, 524}),
        # The first agent identifies the tool; the next ones are used on retries.
        user_agents=(
            "fctt-actas-downloader/1.0 (+https://fctt.cat/lligues/)",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0",
        ),
        user_agent_order="by-attempt",
        accept_html="text/html,application/xhtml+xml;q=0.9,*/*;q=0.8", accept_pdf="application/pdf,*/*;q=0.5",
        accept_language="ca,es;q=0.9,en;q=0.8",
        backoff_cap=120.0, retry_after_cap=300.0, retry_after_mode="instead", retry_after_dates=True,
        message_includes_reason=True, exception_in_message=True, health_on_rejected_content=True,
        robots=RobotsPolicy(attempts=3, any_error_means_none=False, unreadable="cached-or-disallow",
                            accept="text/plain"),
    ),
    ingestor=IngestorSpec(
        delay_option="--request_delay",
        supported_filters=frozenset({"category", "group", "phase", "match_days", "gender", "territory", "region",
                                     "region_type"}),
        gender_in_category=True, territory_from_region=False, region_type_option=None, scope="category-or-gender"),
    download_model="calendar-checked", status_model="match-status", parse_policy="parse-state",
    fixture_id="slug", emits_gender=True, text_mode="joined", aware_clock=True, decode="text",
    download_logger="fctt-incremental-download", parse_logger="fctt-incremental-parse",
)

PROFILES = {profile.name: profile for profile in (BCNESA_PROFILE, FCTT_PROFILE)}
