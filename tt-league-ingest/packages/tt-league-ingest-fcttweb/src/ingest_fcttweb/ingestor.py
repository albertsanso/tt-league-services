"""The ingestors of the fctt.cat sources: ``FcttWebIngestor`` bound to a ``SiteProfile``.

Registered under the unchanged entry points ``bcnesa`` and ``fctt`` (``BcnesaIngestor`` / ``FcttIngestor``).
"""

from __future__ import annotations

from collections.abc import Callable
from pathlib import PurePosixPath

from ingest_common.region import PROVINCE_NAMES, Region, category_matches_region, scope_error
from ingest_common.run import IngestFilters, IngestRequest, IngestStage, ProgressListener, StageReport
from ingest_common.scan import count_files, match_days_argument, run_per_scope, scan_actas, write_match_day_status
from ingest_common.settings import IngestSettings
from ingest_common.validation import ActaValidator
from ingest_fcttweb import download, parse, status
from ingest_fcttweb.markup import fold_compact, normalise_group, slugify
from ingest_fcttweb.parse import GuardedParser, parse_filter
from ingest_fcttweb.profile import BCNESA_PROFILE, FCTT_PROFILE, PROFILES, SiteProfile

# Category folders start with the territory's representation code (``rtb-preferent``, ``RTB PREFERENT``).
TERRITORY_PREFIXES = {"barcelona": "rtb", "girona": "rtg", "lleida": "rtl", "tarragona": "rtt"}


class FcttWebIngestor:
    supported_stages = frozenset({IngestStage.DOWNLOAD, IngestStage.PARSE, IngestStage.PACKAGE, IngestStage.UPLOAD})

    def __init__(self, profile: SiteProfile) -> None:
        self.profile = profile
        self.source = profile.source
        self.supported_filters = profile.ingestor.supported_filters

    # ---- arguments

    def _category_argument(self, filters: IngestFilters) -> str:
        """Category and gender go together in the scripts' ``--category`` option when the profile says so."""
        values = (filters.category, filters.gender) if self.profile.ingestor.gender_in_category else (filters.category,)
        return ",".join(value for value in values if value)

    def _filter_args(self, request: IngestRequest, filters: IngestFilters) -> list[str]:
        args: list[str] = ["--season", str(request.season)]
        for option, value in (("--category", self._category_argument(filters)), ("--group", filters.group),
                              ("--phase", filters.phase), ("--match_day", match_days_argument(filters.match_days))):
            if value:
                args += [option, value]
        if request.force:
            args.append("--force")
        return args

    # ---- stages

    def download(self, request: IngestRequest, settings: IngestSettings, listener: ProgressListener) -> StageReport:
        spec = self.profile.ingestor
        report = StageReport(IngestStage.DOWNLOAD)
        logs = settings.logs_dir(self.source)
        calls = []
        for scope in request.effective_scopes():
            args = self._filter_args(request, scope) + [
                "--output_dir", str(settings.content_dir(self.source)),
                "--log_file", str(logs / "download.log"),
                "--metrics_file", str(logs / "download_metrics.json")]
            territory = scope.territory
            if territory is None and spec.territory_from_region and scope.region:
                territory = PROVINCE_NAMES[Region.parse(scope.region)]  # a region of BCNESA is a territory
            if territory:  # only the downloader filters by territory
                args += ["--territory", territory]
            if scope.region_type and spec.region_type_option:  # parsing and packaging see it through scope_matches
                args += [spec.region_type_option, scope.region_type]
            if request.delay_seconds is not None:
                args += [spec.delay_option, str(request.delay_seconds)]
            calls.append((scope, args))
        delay = self.profile.http.delay if request.delay_seconds is None else request.delay_seconds
        run_per_scope(report, f"{self.profile.name} download", calls, self._main(download.main), delay)
        count_files(report, settings.content_dir(self.source) / str(request.season), ("*.html",))
        write_match_day_status(report, settings.content_dir(self.source), self.source,
                               lambda content_dir: status.scan(self.profile, content_dir))
        return report

    def parse(self, request: IngestRequest, settings: IngestSettings, listener: ProgressListener) -> StageReport:
        report = StageReport(IngestStage.PARSE)
        logs = settings.logs_dir(self.source)
        calls = [(scope, self._filter_args(request, scope) + [
                     "--input_dir", str(settings.content_dir(self.source)),
                     "--output_dir", str(settings.actas_json_dir(self.source)),
                     "--log_file", str(logs / "parse.log")])
                 for scope in request.effective_scopes()]
        run_per_scope(report, f"{self.profile.name} parse", calls, self._main(parse.main))
        scan_actas(report, settings.actas_json_dir(self.source) / str(request.season), ActaValidator())
        return report

    def _main(self, main: Callable[[SiteProfile, list[str] | None], int]) -> Callable[[list[str]], int]:
        return lambda argv: main(self.profile, argv)

    def teams(self, request: IngestRequest, settings: IngestSettings, listener: ProgressListener) -> StageReport:
        raise NotImplementedError(f"{self.profile.source.value} has no teams stage")

    # ---- scoping

    def scope_error(self, scope: IngestFilters) -> str | None:
        if self.profile.ingestor.scope == "territory-prefix":
            return scope_error(self.source, scope.region, scope.region_type, scope.territory)
        return scope_error(self.source, scope.region, scope.region_type)

    def scope_matches(self, scope: IngestFilters, relative: PurePosixPath, match_day: int | None) -> bool:
        if self.profile.ingestor.scope == "territory-prefix":
            return self._matches_territory_prefix(scope, relative, match_day)
        return self._matches_category_or_gender(scope, relative, match_day)

    def _matches_territory_prefix(self, scope: IngestFilters, relative: PurePosixPath, match_day: int | None) -> bool:
        """``<category>/<group>/<phase>/jornada_*.json``, compared the way the parser compares its filters.

        The territory is read from the category folder prefix (``rtb``, ``rtg``, ``rtl``, ``rtt``); a folder without a
        known prefix is matched on its other fields only.
        """
        if len(relative.parts) != 4:
            return False
        category, group, phase, _ = relative.parts
        if not GuardedParser.category_matches(category, scope.category):
            return False
        if scope.group and normalise_group(group) != normalise_group(scope.group):
            return False
        if scope.phase and fold_compact(phase) != fold_compact(scope.phase):
            return False
        if scope.match_days and match_day not in scope.match_days:
            return False
        if scope.territory:
            prefix = fold_compact(category)[:3]
            if prefix in TERRITORY_PREFIXES.values() and prefix != TERRITORY_PREFIXES.get(fold_compact(scope.territory)):
                return False
        return category_matches_region(self.source, category, scope.region, scope.region_type)

    def _matches_category_or_gender(self, scope: IngestFilters, relative: PurePosixPath, match_day: int | None) -> bool:
        """``<category>/<group>/<phase>/jornada_*.json`` with the scripts' slug filters.

        The category filter selects a league by its category or its gender (female for the ``femenina`` categories,
        as the parser decides). Territory is accepted and ignored: these leagues have one territory.
        """
        if len(relative.parts) != 4:
            return False
        category, group, phase = (slugify(part) for part in relative.parts[:3])
        categories = parse_filter(self._category_argument(scope))
        gender = "female" if "femen" in category else "male"
        if categories is not None and category not in categories and gender not in categories:
            return False
        groups, phases = parse_filter(scope.group), parse_filter(scope.phase)
        if (groups is not None and group not in groups) or (phases is not None and phase not in phases):
            return False
        return not scope.match_days or match_day in scope.match_days


class BcnesaIngestor(FcttWebIngestor):
    def __init__(self) -> None:
        super().__init__(BCNESA_PROFILE)


class FcttIngestor(FcttWebIngestor):
    def __init__(self) -> None:
        super().__init__(FCTT_PROFILE)


def download_main(source: str, argv: list[str]) -> int:
    """The download script of ``source`` (``bcnesa`` or ``fctt``) with script-style arguments."""
    return download.main(PROFILES[source], argv)


def parse_main(source: str, argv: list[str]) -> int:
    """The parse script of ``source`` (``bcnesa`` or ``fctt``) with script-style arguments."""
    return parse.main(PROFILES[source], argv)
