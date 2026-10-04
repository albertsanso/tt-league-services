# Build Plan

## Acceptance Criteria
- [x] The `tt-league-ingest` module is created with the specified folder structure and submodules.
- [x] Each submodule has its own `pyproject.toml` and `__init__.py` files.
- [x] The root `pyproject.toml` correctly declares the workspace members.
- [x] The ingestion logic for each federation is implemented in the respective submodules.
- [x] The common ingestion logic and utilities are implemented in the `tt-league-ingest-common` submodule.
- [x] The CLI and REST API for triggering ingestion and monitoring progress are implemented in the respective submodules.
- [x] All submodules are correctly integrated and can be built and run using the workspace tooling.
- [x] The workspace tooling correctly resolves dependencies and allows running commands in specific submodules.
- [ ] All submodules have been tested and verified to work as expected.

## What "ingestion" means here (context, 2026-10-04)

The `actas-json` and `equipos-json` exports that the Java import consumes are produced today by
three standalone script repositories outside this repo: `C:\git\rfetm-extract-2`,
`C:\git\bcnesa-extract-2` and `C:\git\fctt-extract`. They download federation web pages/PDFs,
parse them into JSON that follows `docs/acta-model-definition.json` /
`docs/team-model-definition.json`, and package ZIPs with a `manifest.json` that
`POST /api/v1/administration/import/upload` accepts (`ResourceZipService`).

This feature moves the **current-season (2026-2027 onward) incremental pipelines** of those three
repositories into one uv workspace in this repository, with shared utilities, one CLI and one REST
API. The output files, folder layouts and file names must stay byte-compatible with what the
extractors produce today, so the Java import (`RfetmActasDirectoryNavigator`,
`BcnesaActasDirectoryNavigator`, `FcttActasDirectoryNavigator`) and the upload endpoint need no
change.

Source scripts to port (the "legacy scripts" below):

| Source | Download | Parse | Package |
| --- | --- | --- | --- |
| RFETM actas | `src/actas-html-pdf/download_actas_from_rfetm.py` | `src/actas-html-pdf/convert_actas_to_json.py` + `src/actas-pdf/parser-acta-pdf-2025-2026.py` (loaded via `importlib`) | `src/packager/package_actas.py`, `packager-both.py` |
| RFETM teams | `src/equipos-html/web-downloader-equipos-rfetm.py` | `src/equipos-html/parser-equipos-rfetm.py` | `src/packager/package_teams.py` |
| BCNESA | `src/incremental/download_actas_content_incremental.py` | `src/incremental/parse_actas_content_incremental.py` + `src/actas-html/parse_actas_from_html_to_json.py` (loaded via `importlib`) | `src/incremental/package_actas_content_incremental.py` |
| FCTT | `src/incremental/download_actas_content_incremental.py` | `src/incremental/parse_actas_content_incremental.py` | `src/incremental/package_actas_content_incremental.py` |

Output layouts the Java import expects (unchanged by this feature):

- RFETM: `actas-json/<season>/<category>/<day>/<sex>/acta_<localId>_<awayId>.json`; teams
  `equipos-json/<season>.json`.
- BCNESA: `actas-json/<season>/<rtb-competition>/<G<n>|Other>/<phase>/jornada_<NN>_local_team_<id>_away_team_<id>.json`.
- FCTT: `actas-json/<season>/<male|female>/<competition>/[G<n>/]jornada-<d>-partido-<m>.json`.

## Target layout

```
tt-league-ingest/                         (repository-root directory, sibling of the Maven modules)
├── pyproject.toml                        (workspace root, virtual: package = false)
├── uv.lock                               (committed)
├── .python-version                       (3.12)
├── README.md
├── AGENTS.md
├── tests/test_workspace_layout.py        (workspace dependency-direction test)
└── packages/
    ├── tt-league-ingest-common/  src/ingest_common/  tests/
    ├── tt-league-ingest-rfetm/   src/ingest_rfetm/   tests/
    ├── tt-league-ingest-bcnesa/  src/ingest_bcnesa/  tests/
    ├── tt-league-ingest-fctt/    src/ingest_fctt/    tests/
    ├── tt-league-ingest-cli/     src/ingest_cli/     tests/
    └── tt-league-ingest-rest/    src/ingest_rest/    tests/
```

Dependency direction (inward only, enforced by step 12):

```
ingest_cli ──┐
             ├──> ingest_rfetm / ingest_bcnesa / ingest_fctt ──> ingest_common
ingest_rest ─┘                                                     ^
             └─────────────────────────────────────────────────────┘
```

Federation packages never import each other, and nothing imports `ingest_cli` or `ingest_rest`.

## Steps

1. **Workspace skeleton (`tt-league-ingest/`).**
   - Root `pyproject.toml`:
     ```toml
     [project]
     name = "tt-league-ingest"
     version = "0.1.0"
     requires-python = ">=3.12"
     dependencies = [
         "tt-league-ingest-common", "tt-league-ingest-rfetm", "tt-league-ingest-bcnesa",
         "tt-league-ingest-fctt", "tt-league-ingest-cli", "tt-league-ingest-rest",
     ]

     [tool.uv]
     package = false

     [tool.uv.workspace]
     members = ["packages/*"]

     [tool.uv.sources]
     tt-league-ingest-common = { workspace = true }
     tt-league-ingest-rfetm = { workspace = true }
     tt-league-ingest-bcnesa = { workspace = true }
     tt-league-ingest-fctt = { workspace = true }
     tt-league-ingest-cli = { workspace = true }
     tt-league-ingest-rest = { workspace = true }

     [dependency-groups]
     dev = ["pytest>=8", "httpx>=0.27"]

     [tool.pytest.ini_options]
     testpaths = ["tests", "packages"]
     addopts = "--import-mode=importlib"
     ```
   - Each member `pyproject.toml` uses `hatchling` with an explicit wheel package
     (`[tool.hatch.build.targets.wheel] packages = ["src/ingest_<name>"]`) because the import names
     (`ingest_common`, …) differ from the distribution names. Workspace dependencies are declared by
     distribution name plus `[tool.uv.sources] <name> = { workspace = true }`, for example
     `tt-league-ingest-rest` depends on `tt-league-ingest-common` (the snippet in the registry
     description is illustrative only; see Notes).
   - Every package starts with `src/ingest_<name>/__init__.py` exposing `__version__` and an empty
     `tests/` directory; then `uv lock` and commit `uv.lock`.
   - Add to the root `.gitignore`: `tt-league-ingest/.venv/`, `__pycache__/`, `*.egg-info/`,
     `.pytest_cache/`.
   - Check: `uv sync` from `tt-league-ingest/` installs all members editable;
     `uv run --package tt-league-ingest-common python -c "import ingest_common"` works.

2. **Common: values, configuration and run model (`ingest_common`).**
   - `source.py`: `Source` enum `RFETM | BCNESA | FCTT` whose values are the exact
     `ImportSource` names the upload manifest accepts; parsing is case-insensitive and fails on any
     other value.
   - `season.py`: `Season` value (`YYYY-YYYY`, consecutive years, validated);
     `Season.current(today)` using the same cut-over rule the legacy `current_season()` functions
     use (verify both legacy implementations agree before porting; record the rule in the
     docstring); `Season.slash_form()` → `2026/2027` for payload `temporada`.
   - `match_days.py`: `parse_match_days("3" | "1,4" | "2-5")` → `frozenset[int]`, rejecting empty,
     zero, negative or reversed ranges (ported from both incremental scripts).
   - `text.py`: `clean_text`, `fold` (accent folding), `slugify`/`kebab_case`, `parse_score`; only
     helpers that at least two legacy scripts duplicate. Source-specific parsing stays in the
     federation packages.
   - `settings.py`: `IngestSettings` built from CLI arguments or the environment.
     `TT_INGEST_DATA_DIR` (required; no default inside the repository) is the root under which each
     source writes `<data_dir>/<source-lower>/{content,actas-json,equipos-json,packages,logs}`.
     Missing or non-directory values fail with a clear message naming the variable.
   - `run.py`: `IngestStage` enum (`DOWNLOAD`, `PARSE`, `TEAMS`, `PACKAGE`, `UPLOAD`);
     `IngestRequest` (source, season, stages, filters: category, group, phase, match_days, gender;
     `force`, `delay_seconds`); `StageReport` (counters: `seen`, `downloaded`, `skipped_existing`,
     `parsed`, `published`, `unpublished`, `written`, `unchanged`, `invalid`, `failed`, plus an
     `issues` list of `(path_or_url, message)`); `RunReport` (stage reports, start/end timestamps,
     final `RunStatus` `SUCCEEDED | COMPLETED_WITH_ISSUES | FAILED`, output paths);
     `ProgressListener` protocol (`stage_started`, `item_processed`, `stage_finished`) with a no-op
     default. All are frozen dataclasses except the mutable report builders.

3. **Common: HTTP, JSON IO and schema validation (`ingest_common`).**
   - `http.py`: `PoliteHttpClient` on `requests` with a fixed browser-like User-Agent, minimum delay
     between requests (`RequestPacer`), retries with exponential backoff + jitter honouring
     `Retry-After` (from FCTT `retry_delay`/`retry_after_seconds`), optional `robots.txt` check, and
     `decode_html(bytes)` with utf-8 → iso-8859-1 fallback (RFETM). Per-source quirks are options,
     not branches: RFETM passes `accept_status={200, 500}` because its server returns parseable
     HTML with 500 and 500 must not be retried, while BCNESA/FCTT pass `retry_status={404, 500, …}`
     because `fctt.cat` answers 404/500 intermittently for pages that exist.
   - `json_io.py`: `write_json_atomically(path, payload)` (utf-8, `ensure_ascii=False`, `indent=2`,
     trailing newline, temp file + replace) and `write_if_changed(...)` returning
     `WRITTEN | UNCHANGED`, matching the legacy incremental "rewrite only when content changed or
     `--force`" rule. Confirm the legacy indent/newline choices per source and keep each source's
     exact formatting (byte-compatibility is checked in step 11).
   - `schema/acta-model-definition.json` and `schema/team-model-definition.json` as package data
     (copies of `docs/*.json`); `validation.py`: `ActaValidator` / `TeamsValidator` wrapping
     `jsonschema.Draft202012Validator` and returning sorted error messages.
   - Tests: season parsing/current-season boundaries, match-day parsing, pacer and retry timing with
     a fake clock and fake transport (no network), encoding fallback, atomic/unchanged writes,
     validator accepts a published, an unpublished and a legacy acta fixture and rejects a broken
     one, and a **schema drift test** asserting the packaged schemas are byte-identical to
     `<repo>/docs/acta-model-definition.json` and `docs/team-model-definition.json`.

4. **Common: packaging, upload client and pipeline (`ingest_common`).**
   - `packaging.py`: `build_manifest(source, seasons, assets, mode)` producing exactly the contract
     in `tt-data-league-api-runtime/README.md` ("ZIP import upload contract"): keys `source`,
     `seasons`, `assets` (`ACTAS`/`TEAMS` → `{"files": [...]}`), optional `mode`
     (`snapshot` | `delta`, lowercase), no other keys. `package_season(...)` writes the ZIP
     atomically with entries under `actas-json/<season>/...` and `equipos-json/...`.
     Snapshot packages every file of the season. Delta packages only actas whose **payload**
     `jornada` is in the requested match days (read from JSON, so RFETM paths need no parsing),
     and fails when the selection is empty. Supports `--dry-run`, `--include`/`--exclude`
     patterns and refuses to overwrite an existing ZIP without `force` (ported from the incremental
     packagers).
   - `upload.py`: `PlatformUploader` posting the ZIP as multipart `file` (and
     `allowPublishedShrink` when requested) to `<TT_LEAGUE_API_URL>/api/v1/administration/import/upload`
     with `Authorization: Bearer <TT_LEAGUE_API_TOKEN>`. Both variables are required only for the
     upload stage and fail clearly when missing. 400 and 409 responses surface the server message
     (409 = published-acta shrink check) and fail the stage; no automatic retry with the shrink
     override. Never log the token.
   - `pipeline.py`: `SourceIngestor` protocol (`source`, `supported_stages`,
     `download(request, settings, listener)`, `parse(...)`, optional `teams(...)`), discovered
     through the entry-point group `tt_league_ingest.sources`, so `ingest_common` does not depend
     on federation packages. `IngestPipeline.run(request)` executes the requested stages in order
     (`DOWNLOAD → PARSE → TEAMS → PACKAGE → UPLOAD`), stops at the first stage that fails, and
     returns the `RunReport`. An unregistered source or an unsupported stage (for example `TEAMS`
     for FCTT) fails before any network call.
   - Tests: manifest key set and values for snapshot/delta, ACTAS and TEAMS; manifest/ZIP layout
     round-trip; delta selection by payload `jornada`; empty delta fails; uploader against a local
     fake HTTP server (success, 400, 409, missing env); pipeline ordering, stop-on-failure, and
     entry-point resolution with a fake ingestor.

5. **RFETM package (`ingest_rfetm`).** Depends on `tt-league-ingest-common`, `requests`,
   `beautifulsoup4`, `pdfplumber`. Port, do not rewrite:
   - `categories.py`: the liga-code → category-slug map of `download_actas_from_rfetm.py`
     (the actas-html-pdf map, not the legacy `web_downloader_rfetm.py` one); unknown codes fail.
   - `download.py`: competition discovery, jornada HTML and acta PDF download into
     `content/<season>/<category>/<day>/<sex>/{grupo_<N>.html, acta_<id>.pdf}` via
     `PoliteHttpClient` (≈2 s default delay, `accept_status={200,500}`); `--no-pdf` kept.
   - `pdf_acta.py`: `parser-acta-pdf-2025-2026.py` as a normal module (removes the `importlib`
     loading and the hyphenated file name).
   - `parse.py`: `convert_actas_to_json.py` logic writing
     `actas-json/<season>/<category>/<day>/<sex>/acta_<localId>_<awayId>.json`, with
     `acta_publicada` false (HTML minimum) or true (PDF-enriched) exactly as today, schema
     validation always on and failures counted as `invalid` with the file path in `issues`.
   - `teams.py`: equipos HTML download (`view.php?listaeq=eq`) and parse into
     `equipos-json/<season>.json`, validated with `TeamsValidator`.
   - `ingestor.py`: `RfetmIngestor` registered under `tt_league_ingest.sources` as `rfetm`;
     supports `DOWNLOAD`, `PARSE`, `TEAMS`, `PACKAGE`, `UPLOAD`.
   - Fix while porting: the legacy `WORKSPACE_ROOT` path bug disappears because every path comes
     from `IngestSettings`.
   - Tests (new; the legacy repo has none): category mapping incl. unknown code, discovery and link
     extraction on saved HTML fixtures, PDF parse of one or two small fixture PDFs, HTML-only
     unpublished acta, PDF-enriched published acta, teams parse; all offline.

6. **BCNESA package (`ingest_bcnesa`).** Depends on `tt-league-ingest-common`, `requests`,
   `beautifulsoup4`, `jsonschema` (via common). Port:
   - `download.py`: `incremental/download_actas_content_incremental.py` (territory, category, group,
     phase, match-day filters; html/pdf/both formats; robots and content-hash checks) writing into
     `content/<season>/...` with the same relative layout as today.
   - `acta_parser.py`: `actas-html/parse_actas_from_html_to_json.py` as a normal module (drop the
     `importlib` loader in the incremental parser).
   - `parse.py`: `incremental/parse_actas_content_incremental.py` writing
     `actas-json/<season>/<rtb-competition>/<G<n>|Other>/<phase>/jornada_<NN>_local_team_<id>_away_team_<id>.json`
     (`acta_filename` unchanged), with the legacy write-if-changed/published-is-final rules.
   - `ingestor.py`: `BcnesaIngestor` registered as `bcnesa`; stages `DOWNLOAD`, `PARSE`, `PACKAGE`,
     `UPLOAD` (no `TEAMS`).
   - Tests: port `tests/test_parse_actas_content_incremental.py` and
     `tests/test_parse_actas_from_html_to_json.py` from `bcnesa-extract-2` (replace `importlib`
     loading with package imports; keep assertions), plus offline download tests using the
     fake HTTP transport from step 3.

7. **FCTT package (`ingest_fctt`).** Depends on `tt-league-ingest-common` only (the legacy scripts
   use the stdlib `HTMLParser` tree; keep it in this package as `html_tree.py`, it is not shared).
   Port:
   - `download.py`: `incremental/download_actas_content_incremental.py` (league discovery, gender
     and group handling, failure log, robots, pacing via `PoliteHttpClient`).
   - `parse.py`: `incremental/parse_actas_content_incremental.py` writing
     `actas-json/<season>/<male|female>/<competition>/[G<n>/]jornada-<d>-partido-<m>.json`,
     including partial/pending acta handling and the placeholder-team cases the Java import
     reports (FEAT-00081).
   - `ingestor.py`: `FcttIngestor` registered as `fctt`; stages `DOWNLOAD`, `PARSE`, `PACKAGE`,
     `UPLOAD`.
   - Tests: port `src/incremental/test_download_actas_content_incremental.py`,
     `test_parse_actas_content_incremental.py` and `test_package_actas_content_incremental.py`
     (the packaging tests move to `ingest_common` tests where they exercise shared packaging).
     The legacy `RealContentTest` that reads `resources/` is converted to a small committed fixture.

8. **CLI package (`ingest_cli`).** Depends on common + the three federation packages. Stdlib
   `argparse` (no new CLI framework). Script `tt-league-ingest = "ingest_cli.main:main"`.
   Sub-commands, all taking `--source {rfetm,bcnesa,fctt}`, `--season YYYY-YYYY` (default: current
   season) and `--data-dir` (overrides `TT_INGEST_DATA_DIR`):
   - `download` / `parse` with `--category`, `--group`, `--phase`, `--match-day`, `--gender`
     (FCTT), `--force`, `--delay`; options a source does not support fail with a usage error.
   - `teams` (RFETM only).
   - `package [--mode snapshot|delta] [--match-day …] [--dry-run] [--force] [--output FILE]`.
   - `upload --zip FILE [--allow-published-shrink]`.
   - `run` = `download` + `parse` (+ `--package`, + `--upload`) through `IngestPipeline`.
   - Output: one progress line per stage and a final summary of counters and issues; `--json`
     prints the `RunReport` as JSON. Exit codes: `0` succeeded, `1` completed with issues or
     failed stage, `2` usage/configuration error.
   - Tests: argument parsing and validation per sub-command, unsupported-option errors, exit-code
     mapping, `run` delegating to the pipeline with a fake ingestor, `--json` output shape.

9. **REST package (`ingest_rest`).** Depends on common + the three federation packages,
   `fastapi`, `uvicorn`. Script `tt-league-ingest-rest = "ingest_rest.main:main"`.
   - Configuration: `TT_INGEST_REST_HOST` (default `127.0.0.1`), `TT_INGEST_REST_PORT` (default
     `8090`), `TT_INGEST_REST_API_KEY` (required; startup fails without it) checked against the
     `X-API-Key` header on every endpoint except `GET /health`; plus the step 2/4 settings.
   - Endpoints (JSON):
     - `POST /api/v1/ingest/runs` — body `{source, season, stages, filters, force, mode}` →
       `202 {runId}`; `400` for invalid input; `409` when a run for the same source is active.
     - `GET /api/v1/ingest/runs/{runId}` — `QUEUED | RUNNING | SUCCEEDED | COMPLETED_WITH_ISSUES |
       FAILED`, current stage, per-stage counters, issues, timestamps, package path; `404` unknown.
     - `GET /api/v1/ingest/runs` — most recent runs first (bounded, default 50).
     - `GET /health`.
   - Execution: one `ThreadPoolExecutor(max_workers=1)` so the service never scrapes federation
     sites in parallel; an in-memory `RunRegistry` fed by a `ProgressListener`. Run history is
     not persisted across restarts (documented).
   - Tests with FastAPI `TestClient` and a fake ingestor: auth required, create/poll lifecycle,
     validation errors, same-source conflict, unknown run, failed stage reported as `FAILED`.

10. **Documentation and guidance.**
    - `tt-league-ingest/README.md`: purpose, prerequisites (uv, Python 3.12+), `uv sync`,
      environment variables table, data-directory layout, per-source output contracts (the three
      layouts above), CLI reference with examples (current-jornada delta flow: `run --match-day N
      --package --upload` with `--mode delta`), REST endpoints and curl examples, how to run tests
      for the whole workspace and for one package.
    - `tt-league-ingest/AGENTS.md`: module boundaries (dependency direction), "port-not-rewrite"
      and byte-compatible output rule, schema copy + drift test, no network in tests, polite
      scraping limits, no generated data or secrets committed.
    - Root `AGENTS.md`: add the workspace to "Mission and architecture" and to the
      nearest-`AGENTS.md` list, and add the Python validation commands to "Build and validation"
      (stating that `mvn test` does not cover the workspace).
    - `tt-data-league-import-runtime/README.md` and `tt-data-league-api-runtime/README.md`: one
      short pointer that `tt-league-ingest` produces the `actas-json` exports / upload ZIPs.

11. **Parity verification against the legacy extractors (manual, recorded in Notes).**
    For each source, copy the legacy downloaded content for 2026-2027 into a scratch
    `TT_INGEST_DATA_DIR`, run `tt-league-ingest parse --source <s> --season 2026-2027 --force`, and
    diff the produced `actas-json` tree against the legacy `resources/actas-json/2026-2027` (or
    `resources/actas-incremental/json/2026-2027`). Expected: no differences in file set or content.
    Then run one live `download` for a single small filter per source (one category/group/
    jornada) to confirm the network path. Then run the Java import in report mode over each
    produced tree (`tt-data-league-import-runtime --source=<s> --season=2026-2027
    --actas-folder=<data_dir>/<s>/actas-json …`) and, against a local `api-runtime`, upload one
    delta ZIP produced by `package` + `upload`. Record counts and outcomes in Notes.

12. **Workspace tests and validation.**
    - `tests/test_workspace_layout.py` reads every member `pyproject.toml` and asserts: the six
      expected members exist, each has `src/ingest_<name>/__init__.py`, every workspace dependency
      has a matching `{ workspace = true }` source, and the dependency direction diagram above
      holds (federations depend only on common; nothing depends on cli/rest).
    - Commands (from `tt-league-ingest/`): `uv lock --check`, `uv sync --all-packages`,
      `uv run pytest`, `uv run --package tt-league-ingest-fctt pytest packages/tt-league-ingest-fctt`,
      `uv run tt-league-ingest --help`, `uv run tt-league-ingest-rest` (smoke: `/health`).
    - From the repository root: `mvn test` stays green (no Java changes expected).

Acceptance-criteria mapping: AC1–3 → step 1; AC4 → steps 5–7; AC5 → steps 2–4; AC6 → steps 8–9;
AC7–8 → steps 1 and 12; AC9 → steps 3–9 tests, step 11 parity and step 12.

# Implementation Guidelines

- **Location.** The workspace lives in a repository-root directory `tt-league-ingest/`, which holds
  the workspace-root `pyproject.toml` and `uv.lock`. It is not added to the Maven reactor and the
  parent POM is not touched.
- **Port, do not redesign, the parsers.** Federation parsing logic moves with its behaviour intact
  (field values, `acta_publicada`, `id_partido`, file names, folder names, JSON formatting).
  Refactoring is limited to: module/file renames, replacing `importlib` file loading with imports,
  replacing hard-coded paths with `IngestSettings`, and replacing duplicated helpers with
  `ingest_common`. Any output difference found in step 11 is a bug in the port unless it is a
  documented fix agreed with the user.
- **Contracts are owned elsewhere.** `docs/acta-model-definition.json` and
  `docs/team-model-definition.json` stay authoritative; the packaged copies are synchronized by the
  drift test, never edited independently. The manifest contract is owned by
  `ResourceZipService` / `tt-data-league-api-runtime/README.md`. The `actas-json` layouts are owned
  by the Java navigators. Do not change Java code in this feature.
- **Configuration.** All paths, URLs and credentials come from CLI arguments or environment
  variables (`TT_INGEST_DATA_DIR`, `TT_LEAGUE_API_URL`, `TT_LEAGUE_API_TOKEN`,
  `TT_INGEST_REST_HOST`, `TT_INGEST_REST_PORT`, `TT_INGEST_REST_API_KEY`). Missing required values
  fail clearly; no silent default season other than the documented current-season rule, no default
  data directory, no fallback to another source.
- **Failure behaviour.** No broad `except Exception` that turns failures into success. Per-file
  parse failures are counted and reported (the run ends `COMPLETED_WITH_ISSUES`, exit code 1);
  configuration, contract and HTTP-auth failures end the run `FAILED`.
- **Scraping etiquette.** Keep the legacy default delays (RFETM ≈2 s), retries and User-Agent;
  the REST service runs one ingestion at a time. Tests never touch the network.
- **Python conventions.** Python ≥3.12, 4-space indentation, type hints, `from __future__ import
  annotations`, frozen dataclasses for values, English identifiers/comments in new code (JSON field
  names remain Spanish as per the schema). Testing uses `pytest` (which also runs the ported
  `unittest` cases unchanged); no linter, formatter or type checker is added in this feature.
- **Repository hygiene.** Commit `uv.lock`; never commit `.venv`, downloaded content, generated
  JSON, ZIPs, logs or tokens. Test fixtures are small, hand-picked files under each package's
  `tests/fixtures/`.
- **Out of scope:** historical pipelines (RFETM `actas-html` 2019-2025 and `actas-pdf` 2025-2026
  standalone scripts, BCNESA `histo/`, `actas-pdf/`, `actas_from_rtbtt/` and the 2026-2027
  non-incremental `actas-html` downloader, FCTT `actas-html/` full-season scripts, the legacy
  multi-season packagers) — their exports are already archived; retiring the legacy repositories;
  scheduling; persistence of REST run history; container images; Java/API changes.

# Notes

- 2026-10-04 — Plan created. Decisions taken while planning (please confirm or adjust before
  moving to `ready`):
  - **Scope = current-season incremental pipelines** of the three legacy repositories (see the
    out-of-scope list). Porting every historical pipeline would multiply the work without changing
    any data the platform needs today.
  - **Upload stage included** (`upload` command / `UPLOAD` stage) using a bearer token from
    `TT_LEAGUE_API_TOKEN`; the tool never handles user passwords or calls `/auth/login`. Drop
    steps 4 (`upload.py`) and the upload options in 8–9 if ingestion should stop at the ZIP.
  - **REST framework = FastAPI + uvicorn**, API-key protected, bound to localhost by default.
  - **Packaging = hatchling** per member (explicit `src/ingest_<name>` wheel package, because import
    names differ from distribution names); **tests = pytest** in the root `dev` group.
  - **Effort raised from medium to large** in the registry: six packages, three ported scrapers
    (≈1,200–1,400 lines each for the BCNESA/FCTT downloaders) and parity verification exceed 8 h.
- Registry description discrepancies resolved by this plan:
  - "`pyproject.toml` and `uv.lock` at repository root level" is read as "in the
    repository-root directory `tt-league-ingest/`", matching the folder tree in the description;
    the repository root itself keeps no Python files.
  - The sub-module `pyproject.toml` snippet in the description (`name = "api"`,
    `dependencies = ["ingest_rest"]`, `core = { workspace = true }`) is illustrative and
    inconsistent; step 1 defines the real form (distribution names, matching `tool.uv.sources`).
  - `## Main index` title synchronized with the registry heading ("Workspace module").
- Prerequisite: `uv` is not installed on the current development machine (Python 3.13.6 is).
  Install uv before step 1 (for example `winget install --id=astral-sh.uv -e`).
- Open questions:
  - Should the BCNESA and FCTT incremental downloaders (both crawl `fctt.cat`) later share a
    crawler in `ingest_common`? Kept separate here to preserve behaviour; revisit after parity.
  - Should `tt-league-ingest` be wired into CI or the Maven build (e.g. an exec step running
    `uv run pytest`)? Not done here because the repository has no CI workflows and the parent POM
    must not change without need.

- 2026-10-04 — Implementation executed (status `in-progress` -> `in-review`). `uv run pytest` from
  `tt-league-ingest/`: all tests green (common, RFETM, BCNESA and FCTT ported/new tests, CLI, REST, workspace
  layout); `uv lock --check` ok; `uv run tt-league-ingest --help` and a `tt-league-ingest-rest` smoke
  (`/health` 200, API key enforced) ok.
  - **Parity (step 11, parse part) verified offline.** Legacy 2026-2027 content was copied to a scratch data dir and
    parsed with `tt-league-ingest parse --force`; `diff -r` against the legacy JSON trees shows no differences:
    RFETM 3,170 files (117 PDF-enriched), BCNESA 2,882 files, FCTT 375 files.
  - **Not done (pending manual verification, why AC "tested and verified" stays unchecked):** the live `download`
    per source (hits third-party websites), the Java import in report mode over the produced trees, and a
    `package` + `upload` against a local `api-runtime`. A delta `package` for FCTT jornada 1 was built and its
    manifest checked (`source`, `mode`, `seasons`, `assets.ACTAS.files`).
  - **Deviations from the plan, taken to keep the ports byte-compatible ("port, do not rewrite"):**
    - The ported legacy modules keep their own CLI `main(argv)`; the ingestors build `argv` from the request and
      settings (RFETM modules got a small `configure()` shim for the data paths). Hard-coded repo paths and the
      `importlib` file loading are gone.
    - The BCNESA/FCTT downloaders keep their own `urllib` HTTP clients (pacing, adaptive metrics, retries) and the
      RFETM downloader its `requests` session. `ingest_common.http.PoliteHttpClient` is implemented and tested
      but not yet used by the ports; migrating them is a follow-up once parity is confirmed live.
    - Per-stage counters are derived by scanning the produced files (`seen`, `parsed`, `published`,
      `unpublished`, `invalid`); the legacy script exit code maps to issues (`1`) or a failed stage (other).
    - RFETM unknown liga codes keep the legacy `liga-<code>` folder instead of failing (the plan asked for failing).
    - FCTT output file names are `jornada_<N>_local_team_<id>_away_team_<id>.json`, as the legacy parser writes them
      (the plan's `jornada-<d>-partido-<m>.json` was out of date).
    - No BCNESA offline download test exists: the legacy repository has none for the incremental downloader.
    - The FCTT packaging tests are replaced by the shared packaging tests in `ingest_common`; the FCTT
      `RealContentTest` now reads a committed one-page fixture.
  - Environment: uv 0.12.23 installed with winget; Python 3.12 comes from uv's managed download.

