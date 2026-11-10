"""No-network golden harness for the fctt.cat ingest pipelines (BCNESA and FCTT download + parse).

A *scenario* serves saved pages (``sites/<site>/``) through a fake transport, runs the download and the parse of one
source for one or more *rounds* (a round is a download followed by a parse, optionally against a changed copy of
the site) and snapshots the produced trees: the downloaded content (HTML with its meta tags and PDFs), the JSON
actas (and the parser state file), the request sequence and the normalised metrics file. The snapshots stored under
``expected/`` were produced by the original ``ingest_bcnesa`` / ``ingest_fctt`` ports; the unified implementation
must reproduce them byte for byte (see ``tt-league-ingest/AGENTS.md``: an output difference is a bug).

The harness is implementation-agnostic: an *adapter* (``golden_adapters``) installs the fake transport and the frozen
clock for one implementation and exposes ``download(source, argv)`` / ``parse(source, argv)``.
"""

from __future__ import annotations

import json
import logging
import re
import shutil
import tempfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Mapping, Protocol

GOLDEN = Path(__file__).resolve().parent
SITES = GOLDEN / "sites"
EXPECTED = GOLDEN / "expected"
BASE_URL = "https://fctt.cat"
INDEX_URL = f"{BASE_URL}/competicions-estatals/"
SEASON = "2026-2027"
PDF = b"%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF\n"
BLOCKED = (b"<!DOCTYPE html><html><head><title>Access denied</title></head><body>blocked</body></html>")
EMPTY = (b"<!DOCTYPE html><html><head><title>Lligues</title></head><body><main>"
         b"<p>No s'han trobat resultats.</p><p>No s'han trobat partits.</p></main></body></html>")
TIMEOUT = "timeout"
HTML_TYPE = "text/html; charset=UTF-8"
# Metrics values that depend on the machine (timings, random ids) are replaced; everything else is compared.
VOLATILE_KEYS = {"duration_s", "latency_seconds", "run_id", "last_duration_s", "elapsed"}


# --------------------------------------------------------------------------- scenarios


@dataclass(frozen=True)
class Round:
    """One download followed by one parse; ``faults`` queue responses in front of a URL's normal one."""

    download: tuple[str, ...] = ()
    parse: tuple[str, ...] = ()
    faults: Mapping[str, tuple[Any, ...]] = field(default_factory=dict)
    # Serve the ``.rN`` variants of the site files up to this number (default: the round's own number).
    site_round: int | None = None


@dataclass(frozen=True)
class Scenario:
    source: str  # bcnesa | fctt
    name: str
    leagues: tuple[str, ...]  # site folders served (the index is filtered to them)
    rounds: tuple[Round, ...]
    pdfs: Mapping[str, Any] = field(default_factory=dict)  # acta id -> bytes / fault spec (default: a valid PDF)

    @property
    def key(self) -> str:
        return f"{self.source}/{self.name}"


# --------------------------------------------------------------------------- fake site


@dataclass
class Reply:
    status: int
    content_type: str
    body: bytes


def _reply(spec: Any) -> Reply | str:
    if spec == TIMEOUT:
        return TIMEOUT
    if isinstance(spec, int):
        return Reply(spec, HTML_TYPE, b"error")
    if isinstance(spec, tuple):
        return Reply(*spec)
    return Reply(200, HTML_TYPE, spec)


class Router:
    """URL -> queued replies (the last one repeats); records every requested URL in order."""

    def __init__(self, routes: Mapping[str, list[Any]]):
        self.routes = {url: list(queue) for url, queue in routes.items()}
        self.requests: list[str] = []

    def respond(self, url: str) -> Reply | str:
        self.requests.append(url)
        if url.endswith("/robots.txt"):
            return Reply(404, "text/plain", b"")
        queue = self.routes.get(url)
        if not queue:
            return Reply(404, HTML_TYPE, b"not found")
        spec = queue.pop(0) if len(queue) > 1 else queue[0]
        return _reply(spec)


def _variant(folder: Path, stem: str, suffix: str, round_no: int) -> Path | None:
    for number in range(round_no, 0, -1):
        path = folder / (f"{stem}{suffix}" if number == 1 else f"{stem}.r{number}{suffix}")
        if path.is_file():
            return path
    return None


def _filter_index(text: str, leagues: tuple[str, ...]) -> str:
    """Keep only the index entries of ``leagues`` (cards and links are one per line / one element)."""
    slugs = set(leagues)

    def keep(url: str) -> bool:
        match = re.search(r"/lligues/([^/]+)/", url)
        return not match or match.group(1) in slugs or match.group(1) in {"superdivisio", "altres", "suf"} \
            or match.group(1).endswith("-2025")

    text = re.sub(r'<div class="jet-engine-listing-overlay-wrap" data-url="([^"]*)">.*?</div></div>',
                  lambda m: m.group(0) if keep(m.group(1)) else "", text, flags=re.S)
    return re.sub(r'(<h3>[^<]*</h3><a[^>]*href="([^"]*)"[^>]*>(?:<span>[^<]*</span>)?</a>)',
                  lambda m: m.group(1) if keep(m.group(2)) else "", text)


def build_router(scenario: Scenario, round_no: int, round_: Round) -> Router:
    site = SITES / scenario.source
    served = round_.site_round or round_no
    routes: dict[str, list[Any]] = {}
    index = (site / "index.html").read_text(encoding="utf-8")
    routes[INDEX_URL] = [_filter_index(index, scenario.leagues).encode("utf-8")]
    for slug in scenario.leagues:
        folder = site / slug
        league = _variant(folder, "league", ".html", served)
        if league:
            routes[f"{BASE_URL}/lligues/{slug}/"] = [league.read_bytes()]
        for path in sorted(folder.glob("jornada-*.html")):
            day = re.fullmatch(r"jornada-(\d+)(?:\.r\d+)?\.html", path.name)
            if not day:
                continue
            chosen = _variant(folder, f"jornada-{day.group(1)}", ".html", served)
            if chosen:
                routes[f"{BASE_URL}/lligues/{slug}/?jornada={day.group(1)}"] = [chosen.read_bytes()]
    for acta in sorted(set(re.findall(r"partido/(\d+)/imprimir/acta", "".join(
            p.read_text(encoding="utf-8") for p in site.rglob("*.html"))))):
        routes[f"https://control.fctt.cat/partido/{acta}/imprimir/acta"] = [
            scenario.pdfs.get(acta, (200, "application/pdf", PDF))]
    for url, faults in round_.faults.items():
        routes[url] = [*faults, *routes.get(url, [])]
    return Router(routes)


# --------------------------------------------------------------------------- adapters


class Adapter(Protocol):
    name: str

    def installed(self, router: Router) -> Any:
        """Context manager that routes the implementation's HTTP traffic to ``router`` and freezes its clock."""

    def download(self, source: str, argv: list[str]) -> int: ...

    def parse(self, source: str, argv: list[str]) -> int: ...


# --------------------------------------------------------------------------- running


def _base_download_args(source: str, content: Path, logs: Path) -> list[str]:
    common = ["--season", SEASON, "--output_dir", str(content), "--log_file", str(logs / "download.log"),
              "--metrics_file", str(logs / "download_metrics.json"), "--backoff", "0", "--retry", "3"]
    return common + (["--delay", "0"] if source == "bcnesa" else ["--request_delay", "0"])


def _tree(root: Path) -> dict[str, bytes]:
    return {path.relative_to(root).as_posix(): path.read_bytes()
            for path in sorted(root.rglob("*")) if path.is_file() and not path.name.endswith((".part", ".tmp"))}


def _normalise(value: Any, tmp: str) -> Any:
    if isinstance(value, dict):
        return {key: "<volatile>" if key in VOLATILE_KEYS else _normalise(item, tmp) for key, item in value.items()}
    if isinstance(value, list):
        return [_normalise(item, tmp) for item in value]
    if isinstance(value, str):
        return value.replace(tmp, "<tmp>")
    return value


@dataclass
class Snapshot:
    """The files of one round: relative path (``content/...``, ``json/...``, ...) -> bytes."""

    files: dict[str, bytes]


def run_scenario(adapter: Adapter, scenario: Scenario) -> list[Snapshot]:
    snapshots: list[Snapshot] = []
    root_logger = logging.getLogger()
    saved_handlers, saved_level = list(root_logger.handlers), root_logger.level
    with tempfile.TemporaryDirectory(prefix="golden-") as tmp:
        work = Path(tmp) / "work"
        content, actas, logs = work / "content", work / "json", work / "logs"
        for number, round_ in enumerate(scenario.rounds, 1):
            router = build_router(scenario, number, round_)
            codes: dict[str, Any] = {}
            try:
                with adapter.installed(router):
                    codes["download"] = adapter.download(
                        scenario.source, _base_download_args(scenario.source, content, logs) + list(round_.download))
                    codes["parse"] = adapter.parse(scenario.source, [
                        "--season", SEASON, "--input_dir", str(content), "--output_dir", str(actas),
                        "--log_file", str(logs / "parse.log"), *round_.parse])
            finally:
                root_logger.handlers[:] = saved_handlers
                root_logger.setLevel(saved_level)
            files = {f"content/{rel}": data for rel, data in _tree(content).items()}
            files.update({f"json/{rel}": data for rel, data in _tree(actas).items()})
            metrics = json.loads((logs / "download_metrics.json").read_text(encoding="utf-8"))
            files["metrics.json"] = _dump(_normalise(metrics, tmp))
            files["requests.txt"] = ("\n".join(router.requests) + "\n").encode("utf-8")
            files["exit-codes.json"] = _dump(codes)
            snapshots.append(Snapshot(files))
    return snapshots


def _dump(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=1, sort_keys=True) + "\n").encode("utf-8")


# --------------------------------------------------------------------------- expected outputs


def expected_dir(scenario: Scenario, round_no: int) -> Path:
    return EXPECTED / scenario.source / scenario.name / f"round-{round_no}"


def write_expected(scenario: Scenario, snapshots: list[Snapshot]) -> None:
    target = EXPECTED / scenario.source / scenario.name
    shutil.rmtree(target, ignore_errors=True)
    for number, snapshot in enumerate(snapshots, 1):
        for rel, data in snapshot.files.items():
            path = expected_dir(scenario, number) / rel
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)


def read_expected(scenario: Scenario, round_no: int) -> dict[str, bytes]:
    root = expected_dir(scenario, round_no)
    return {path.relative_to(root).as_posix(): path.read_bytes() for path in sorted(root.rglob("*")) if path.is_file()}


def differences(expected: Mapping[str, bytes], actual: Mapping[str, bytes], limit: int = 12) -> list[str]:
    """Human-readable differences (missing, unexpected and changed files, with the first differing line)."""
    import difflib

    problems = [f"missing: {rel}" for rel in sorted(set(expected) - set(actual))]
    problems += [f"unexpected: {rel}" for rel in sorted(set(actual) - set(expected))]
    for rel in sorted(set(expected) & set(actual)):
        if expected[rel] == actual[rel]:
            continue
        lines = list(difflib.unified_diff(expected[rel].decode("utf-8", "replace").splitlines(),
                                          actual[rel].decode("utf-8", "replace").splitlines(),
                                          "expected", "actual", lineterm="", n=0))
        problems.append(f"changed: {rel}\n    " + "\n    ".join(line[:240] for line in lines[:8]))
    return problems[:limit]
