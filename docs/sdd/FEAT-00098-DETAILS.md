# Build Plan
**Scope contract (shared by REST, CLI and pipeline).** A scope is an object with the optional keys `category`,
`group`, `phase`, `territory`, `gender` (non-blank strings, same vocabulary as the existing filters) and `matchDays`
(an array of positive integers, or the existing selector string `3`, `1,4`, `2-5`). Unknown keys and a scope with no
key set are rejected. Fields inside one scope are AND-combined (exactly what one `filters` object means today);
scopes are OR-combined. Identical scopes are de-duplicated, keeping first-seen order. Omitting both `filters` and
`scopes` keeps today's meaning (the whole season).

1. **Run model (`tt-league-ingest/packages/tt-league-ingest-common/src/ingest_common/run.py`).**
   - Reuse `IngestFilters` as the per-scope value (no second type with the same fields) and add
     `IngestRequest.scopes: tuple[IngestFilters, ...] = ()`.
   - `IngestRequest.effective_scopes() -> tuple[IngestFilters, ...]`: `scopes` when set, else `(filters,)`.
   - `IngestRequest.__post_init__` raises `ValueError` when `scopes` is set together with non-default `filters`,
     when a scope has no field set, and when `scopes` is set, `PACKAGE` is a stage and `mode` is `snapshot`
     (a snapshot is the whole season by definition; see Implementation Guidelines).
   - `RunReport.to_dict()` adds `"scopes"`: the effective scopes as `{category, group, phase, territory, gender,
     matchDays}` (sorted int list or `null`), so callers can correlate a run with what it covered.
2. **Scope parsing (new `ingest_common/scopes.py`).** `scope_from_values(category, group, phase, territory, gender,
   match_days) -> IngestFilters` (strips strings, turns blanks into `None`, accepts `list[int]` or a selector string
   via `match_days.parse_match_days`, rejects an empty scope) and `parse_scopes(raw: object) ->
   tuple[IngestFilters, ...]` (expects `{"scopes": [...]}`, rejects unknown keys, empty lists and non-objects,
   de-duplicates). Plus `describe_scope(scope) -> str` (`territory=Girona category=PREFERENT group=G2 days=3,4`) for
   issue labels and `scope_to_dict(scope)` used by `RunReport.to_dict()`. All raise `ValueError` with the offending
   scope index.
3. **Pipeline validation (`ingest_common/pipeline.py`).** The unsupported-filter check loops over
   `request.effective_scopes()` and reports `<source> does not support filter(s): ... (scope N)` through
   `_fail_early`, before any stage runs, so no network call happens. Fingerprinting, the `NO_CHANGES` skip and the
   `SOURCE_UNAVAILABLE` rule stay as they are (they already work on the whole season folder).
4. **Ingestors iterate scopes (`ingest_bcnesa/ingestor.py`, `ingest_fctt/ingestor.py`, `ingest_rfetm/ingestor.py`).**
   - Turn `_filter_args(request)` / `_jornada_args(request)` into `(request, scope)` builders; `download` and `parse`
     build one argument list per effective scope, drop duplicate argument lists (for example two BCNESA scopes that
     differ only by territory give one parse call), and call the legacy `main` once per list.
   - Label each `record_exit_code` call with `describe_scope`; stop the loop when a call fails the stage (exit code
     other than 0/1), continue after exit code 1 (as today, the issue is recorded).
   - Between consecutive download calls, wait through `ingest_common.http.RequestPacer` built with
     `request.delay_seconds` or the script's own default (BCNESA 1.0 s, FCTT 3.0 s, RFETM `DEFAULT_DELAY`), so a new
     scope never starts its first request sooner than the legacy pace allows. Parse calls need no pacing.
   - `count_files` and `write_match_day_status` run once after the loop, not per scope.
   - A single-scope request (today's `filters`) produces exactly the same argument lists as now.
5. **Scope membership per source.** Add `scope_matches(scope: IngestFilters, relative: PurePosixPath,
   match_day: int | None) -> bool` to the `SourceIngestor` protocol: whether an `actas-json/<season>/...` file
   (path relative to the season folder, payload `jornada`) belongs to the scope. Each source reuses the normalisation
   its parser already applies to the same filter, so parse and package never disagree:
   - RFETM, `<category>/<day>/<sex>/acta_*.json`: category equals the folder, day in `match_days`.
   - BCNESA, `<category>/<group>/<phase>/jornada_*.json`: `parse.category_matches`, `parse.normalise_group`,
     `parse.fold` for phase; territory through the `rtb`/`rtg`/`rtl`/`rtt` category prefix (Barcelona, Girona,
     Lleida, Tarragona). Verify the prefix mapping against the downloader's territory headings and a fixture before
     relying on it.
   - FCTT, `<category>/<group>/<phase>/jornada_*.json`: the slug comparison of `parse.discover_pages` (extract a small
     predicate if needed, no behaviour change) with the category/gender expansion `_filter_args` already builds;
     territory is accepted and ignored (FCTT has only `catalunya`).
6. **Scoped packaging (`ingest_common/packaging.py`, `ingest_common/pipeline.py`).**
   - `package_season(..., select: Callable[[PurePosixPath, int | None], bool] | None = None)`: in delta mode a given
     `select` replaces the `match_days` rule (path relative to the season folder, payload `jornada` from
     `_payload_match_day`), and "delta requires match days" applies only when `select` is `None`. Snapshot mode
     ignores `select`. Empty selection still raises `PackagingError` ("no actas selected").
   - `IngestPipeline._package`: when `request.scopes` is set, pass
     `select=lambda rel, day: any(ingestor.scope_matches(s, rel, day) for s in request.scopes)` and no
     `match_days`; otherwise keep today's call unchanged.
7. **REST (`tt-league-ingest/packages/tt-league-ingest-rest/src/ingest_rest/app.py`).**
   - `ScopeBody(BaseModel)` with `model_config = ConfigDict(extra="forbid")` and the six fields (`matchDays:
     str | list[int] | None`); `RunBody.scopes: list[ScopeBody] | None = None`.
   - `to_request`: 400 when `"filters" in body.model_fields_set` and `scopes` is not `None`, when `scopes` is an
     empty list, and for any `ValueError` from `scope_from_values`/`IngestRequest`. Keep "mode delta requires
     filters.matchDays" for filter requests only.
   - `RunRecord.to_dict()` adds `scopes` (same shape as the run report).
   - `GET /api/v1/ingest/sources/{source}/match-days-status?season=` (`X-API-Key`): 400 for an unknown source or a
     malformed season; reads `settings.content_dir(source) / STATUS_FILE` (written atomically, so safe while a run is
     active); 404 when the file does not exist. Without `season` it returns the file as is. With `season` it returns
     `ingest_common.match_day_status.report_for_season(report, season)`: `seasons` set to that season, `matchDays`
     filtered, the row-derived `summary` counters recomputed (`emptyPages`/`unreadablePages` stay file-wide); 404 when
     the season has no rows.
8. **Status filter helper (`ingest_common/match_day_status.py`).** `report_for_season(report: dict, season: str) ->
   dict | None`, reusing `STATUSES` so the recomputed summary has the same keys as `build_report`.
9. **CLI (`tt-league-ingest/packages/tt-league-ingest-cli/src/ingest_cli/main.py`).** `run --scope-file PATH`: reads
   UTF-8 JSON `{"scopes": [...]}` (the REST body fragment) through `parse_scopes`. Combined with any of
   `--category/--group/--phase/--match-day/--gender/--territory`, an unreadable file, invalid JSON or a scope error
   it exits 2 with a usage error. `ValueError` from `IngestRequest` maps to `UsageError`. `download`/`parse`/`package`
   keep their filter options only. `format_summary` prints one `scopes:` line when the request has scopes.
10. **Tests (no network, fake ingestors/legacy `main` replaced with recorders).**
    - `tt-league-ingest-common/tests/`: new `test_scopes.py` (parsing, list vs selector `matchDays`, unknown key,
      empty scope, de-duplication, `IngestRequest` invariants); `test_packaging_pipeline.py` (union selection with
      `select`, snapshot ignores it, empty scoped selection fails, unsupported field in scope 2 fails before any
      stage with the fake ingestor never called, a filters-only request behaves as before);
      `test_match_day_status.py` (`report_for_season` filtering and summary).
    - `tt-league-ingest-bcnesa/tests/test_ingestor.py`, FCTT and RFETM tests: one legacy call per distinct scope with
      the expected arguments, duplicate parse argument lists collapsed, pacer waits between download calls
      (`delay_seconds=0` or an injected clock), status file written once, `scope_matches` on fixture paths
      including the BCNESA territory prefix.
    - A round-trip test per source: a row of a fixture `match-days-status.json` turned into a scope selects that row's
      saved page in the parser's filter (so the orchestrator can build scopes from status rows).
    - `tt-league-ingest-rest/tests/test_rest.py`: scopes accepted (202 and recorded scopes), filters+scopes 400, empty
      list 400, unknown key 400, snapshot+PACKAGE+scopes 400; status endpoint 200 (whole file and per season), 404
      (no file, unknown season), 400 (unknown source, bad season), 401 without key.
    - `tt-league-ingest-cli/tests/test_cli.py`: `--scope-file` happy path, conflicting filter option, bad file → 2.
11. **Docs (`tt-league-ingest/README.md`).** "Scoped runs" section: the scope contract, union semantics, supported
    fields per source (RFETM `category`, `matchDays`; BCNESA adds `group`, `phase`, `territory`; FCTT all six), delta
    selection per source layout, snapshot rejection, pacing between scopes, REST and `--scope-file` examples; the
    endpoint list gains the match-days-status endpoint with its status codes and season filtering.
12. **Validation.** From `tt-league-ingest/`: `uv lock --check`, `uv sync --all-packages`, `uv run pytest`
    (including `tests/test_workspace_layout.py`). No Java change, so `mvn test` is unaffected.

## Acceptance Criteria

- [x] `POST /api/v1/ingest/runs` accepts `scopes: [{category, group, phase, territory, gender, matchDays}]`, and a run downloads, parses and packages only the union of the scopes
- [x] The existing single `filters` body stays valid; sending both `filters` and `scopes` is a 400
- [x] Unsupported scope fields for a source fail the run before any network call, as filters do today
- [x] `GET /api/v1/ingest/sources/{source}/match-days-status?season=` returns the current `match-days-status.json` (404 when none exists)
- [x] The CLI `run` accepts a `--scope-file` JSON with the same shape
- [x] Tests cover scope union, validation and the status endpoint; README documents both

# Implementation Guidelines

- Python only. Scraping etiquette and the incremental skip rules (complete/future jornadas) stay unchanged.
- Respect the dependency direction in `tt-league-ingest/AGENTS.md`: scope parsing lives in `ingest_common`, the
  per-source path rules live in each federation package, CLI and REST only call them.
- Port, do not rewrite: the legacy download/parse scripts keep their single-valued options and are called once per
  scope; their output files and JSON stay byte-identical. Extracting an existing predicate into a function is
  allowed; changing its matching rules is not.
- Never run scopes in parallel and never shorten delays; the REST service keeps one ingestion at a time and the
  one-active-run-per-source 409.
- A run with `scopes` and `PACKAGE` must use `mode=delta`; a scoped snapshot is rejected (400 / exit 2) instead of
  silently widening to the whole season. Requests using `filters` keep today's packaging behaviour, including the
  match-day-only delta selection.
- Overlapping scopes are not merged beyond exact duplicates; callers (FEAT-00108's scope builder) should send
  disjoint scopes. Overlap only costs extra requests that the incremental skip rules mostly avoid.
- The status endpoint is read-only and never triggers a scan; the file is rebuilt only by `DOWNLOAD`.
- No manifest or Java change; FEAT-00099 owns the manifest contract.
- Out of scope: scopes for `download`/`parse`/`package` CLI subcommands, the TEAMS stage (RFETM teams are
  season-wide and ignore scopes), persisting runs, and building scopes from platform data (FEAT-00108).

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal item 3 (scoped, incremental runs). BCNESA has four territory pipelines (Barcelona, Girona, Lleida, Tarragona); scopes must carry the territory.

- 2026-10-04: Build plan written against the current workspace (after FEAT-00097). Decisions: `IngestFilters` is
  reused as the scope value; scoped delta packaging selects by each source's `actas-json` folder layout through a
  new `SourceIngestor.scope_matches`, because acta payload names (`competicion`, `grupo`, `fase`) do not use the
  filter vocabulary; scoped snapshot packages are rejected; `matchDays` accepts an int array or the selector string;
  `?season=` filters the status file and recomputes its row-derived summary.
- Open: confirm the BCNESA `rt[bglt]` category prefix maps one-to-one to the four territories for every league
  (step 5). If it does not, BCNESA scoped packaging must ignore `territory` and rely on category/group/phase, and the
  README must say so.
- Coordination: FEAT-00099 also adds a field to `IngestRequest` and touches `IngestPipeline._package` and
  `ingest_rest/app.py`; whichever lands second rebases onto the other. Neither depends on the other.
- Consumers: FEAT-00108 builds `scopes` from open match days and can use the status endpoint; the round-trip test in
  step 10 guarantees a status row is a valid scope.
- 2026-10-04: Marked `ready` on explicit user request. The BCNESA territory-prefix question above is to be verified during step 5 of the plan, not before.
- 2026-10-04: Implemented (after one restart from a clean tree on user request). Validation: `uv lock --check`,
  `uv sync --all-packages` and `uv run pytest` in `tt-league-ingest/` (273 passed, including the workspace-layout
  test). No Java change, so `mvn test` is unaffected. Deviations and findings:
  - Pacing between scope downloads is a plain sleep of the script's delay (`ingest_common.scan.run_per_scope`)
    instead of `RequestPacer`: the pacer measures from the previous call's start, while the gap must follow the
    previous script's last request.
  - An unknown key inside a REST scope is rejected by request validation with `422` (pydantic `extra="forbid"`);
    every other scope error is `400`. The CLI rejects unknown keys as a usage error.
  - BCNESA territory prefix (step 5 open question): only `rtb-*` categories exist in the repository data
    (`BcnesaCompetitionNames`), and the downloader already strips `rt[bglt]` prefixes. The prefix is used when
    present; a category folder without a known prefix is matched on its other keys only. README documents it.
    Re-check against a real Girona/Lleida/Tarragona download.
  - Status rows round-trip as scopes for BCNESA and FCTT (tests). RFETM rows carry `group`/`gender`, which RFETM
    scopes do not support, so RFETM scopes use `category` and `matchDay` only (README, test).
  - Pre-existing, not changed: the FCTT parser compares `--category` values only with category folder slugs, so
    a gender-only FCTT scope (or filter) downloads the league but parses nothing new; packaging matches the gender
    like the downloader does.
