# Build Plan
Steps 1-6 are implemented (landed with commit `3a5d1b5`) and were checked against the code on 2026-10-04.
Steps 7-9 remain. All paths are relative to `tt-league-ingest/packages/`.

1. **Fingerprints (`tt-league-ingest-common/src/ingest_common/fingerprint.py`) — done.**
   - `content_fingerprint(directory, patterns) -> dict[str, tuple[int, int]]`: relative POSIX path to `(size, mtime_ns)`
     for downloaded pages/PDFs (cheap; large PDFs are not hashed).
   - `json_fingerprint(directory) -> dict[str, str]`: relative path to SHA-256 of the bytes for `*.json`, built on
     `file_digest(path)` (chunked SHA-256, also used for the teams file).
   - `count_changes(before, after) -> int`: added + modified + removed entries.
2. **Run model (`ingest_common/run.py`) — done.**
   - `RunOutcome` enum (`SUCCEEDED`, `NO_CHANGES`, `COMPLETED_WITH_ISSUES`, `SOURCE_UNAVAILABLE`, `FAILED`) and
     `RETRYABLE_OUTCOMES = {SOURCE_UNAVAILABLE}`; `LEGACY_FAILURE_MESSAGE` marks a legacy exit-code-1 issue.
   - `StageReport` has `skipped: str | None`, `source_unavailable: bool` and the `legacy_failure` property.
   - `RunReport` has `changes` (`contentChanged`, `actasChanged`), `outcome` and the `retryable` property. `finish()`
     keeps the `status` logic and `_derive_outcome` applies: first failed stage is `DOWNLOAD` with
     `source_unavailable` -> `SOURCE_UNAVAILABLE`, any other failed stage -> `FAILED`; a skipped stage and no
     issues -> `NO_CHANGES`; issues/invalid/failed counters -> `COMPLETED_WITH_ISSUES`; else `SUCCEEDED`.
   - `to_dict()` emits `outcome`, `retryable`, `changes` and per-stage `skipped`.
3. **Pipeline (`ingest_common/pipeline.py`) — done.**
   - Before the first stage: `content_fingerprint(content_dir(source) / season, CONTENT_PATTERNS)` and
     `_json_snapshot(request)` (season `actas-json` plus `equipos-json/<season>.json` when present).
   - After `DOWNLOAD`: sets `changes["contentChanged"]`; when `legacy_failure`, the stage has not already failed and
     `contentChanged == 0`, sets `source_unavailable` and fails the stage, so later stages do not run.
   - After `PARSE`/`TEAMS`: sets `changes["actasChanged"]`.
   - `PACKAGE`/`UPLOAD` are appended as `StageReport(stage, skipped=NO_JSON_CHANGED)` when `_nothing_to_publish`
     (request has `PARSE`, `actasChanged == 0`, not `force`). Package-only runs are never skipped.
4. **Legacy exit-code mapping (`ingest_common/scan.py`) — done.** `record_exit_code` records code 1 as an issue with
   `LEGACY_FAILURE_MESSAGE` and returns `True`; other non-zero codes fail the stage.
5. **CLI (`tt-league-ingest-cli/src/ingest_cli/main.py`) — done.** `EXIT_NO_CHANGES = 3`,
   `EXIT_SOURCE_UNAVAILABLE = 4`; `exit_code(report)` maps `outcome` through `EXIT_CODES`; usage errors stay 2.
   `format_summary` prints outcome, retryable, change counts and `skipped (<reason>)` per stage.
6. **REST (`tt-league-ingest-rest/src/ingest_rest/app.py`) — done.** `RunRecord.to_dict()` emits `outcome` (null
   until the run ends), `retryable`, `changes` and per-stage `skipped`; an exception inside `execute` sets
   `outcome = FAILED` and leaves `retryable` false.
7. **Tests (no network; `tmp_path` only).**
   - New `tt-league-ingest-common/tests/test_fingerprint.py`: `count_changes` for added, modified and removed
     entries and for identical snapshots (0); `json_fingerprint` changes when bytes change and ignores non-JSON
     files; `content_fingerprint` only lists the requested patterns and returns `{}` for a missing directory.
   - `tt-league-ingest-common/tests/test_packaging_pipeline.py`: extend `FakeIngestor` with optional
     `download_exit_code`, `write_content` (writes an `.html` under `settings.content_dir(FCTT) / season`) and
     `write_actas` (JSON written under `settings.actas_json_dir(FCTT) / season`) behaviour, using
     `record_exit_code` for the legacy code. Add tests:
     - parse writes nothing with `DOWNLOAD, PARSE, PACKAGE` -> `NO_CHANGES`, `PACKAGE` skipped with
       `"no JSON changed"`, no ZIP, `status` `SUCCEEDED`, `retryable` false;
     - parse writes one JSON -> `SUCCEEDED`, ZIP in `outputs["package"]`, `actasChanged == 1`;
     - second identical run on the same data dir -> `NO_CHANGES` (no "already exists" packaging failure);
     - same second run with `force=True` -> `PACKAGE` executes;
     - `PACKAGE`-only request on existing actas -> not skipped;
     - download exit 1, no content -> `SOURCE_UNAVAILABLE`, `retryable` true, `status` `FAILED`, `PARSE` not called;
     - download exit 1 with content written -> `COMPLETED_WITH_ISSUES`, `contentChanged == 1`, `retryable` false;
     - a failing `PARSE` stage -> `FAILED`, `retryable` false;
     - `to_dict()` carries `outcome`, `retryable`, `changes` and per-stage `skipped`.
   - `tt-league-ingest-cli/tests/test_cli.py`: import `EXIT_NO_CHANGES` and `EXIT_SOURCE_UNAVAILABLE`; give
     `FakeIngestor` the same write/exit-code options; test `run --package` with nothing written -> 3; legacy
     download failure without content -> 4; parse that writes a JSON -> 0; extend the `--json` test to assert
     `outcome`, `retryable` and `changes`; text summary shows `skipped (no JSON changed)`.
   - `tt-league-ingest-rest/tests/test_rest.py`: a successful run exposes `outcome == "SUCCEEDED"`,
     `retryable == false` and `changes`; `outcome` is null while the run is held `RUNNING` (use the existing
     `release` event); an ingestor that raises -> `status` `FAILED`, `outcome` `FAILED`, `retryable` false, `error`
     set; a legacy download failure without content -> `outcome` `SOURCE_UNAVAILABLE`, `retryable` true.
8. **Docs (`tt-league-ingest/README.md`).** Replace the exit-code sentence in the CLI section with an outcome table
   (outcome, meaning, `retryable`, exit code: `SUCCEEDED` 0, `COMPLETED_WITH_ISSUES` 1, `FAILED` 1, `NO_CHANGES` 3,
   `SOURCE_UNAVAILABLE` 4; usage/configuration 2), state that `status` is unchanged for compatibility, document
   the skip rule (`PARSE` in the run, no `actas-json`/`equipos-json` byte change, no `--force`) and that
   package-only runs are never skipped, and how `SOURCE_UNAVAILABLE` is detected (legacy exit 1 and no content
   file written). In the REST section, document the run DTO fields `outcome` (null until finished), `retryable`,
   `changes` and per-stage `skipped`.
9. **Validation.** From `tt-league-ingest/`: `uv lock --check`, `uv sync --all-packages`, `uv run pytest`. No Maven
   module changes, so `mvn test` is not affected; confirm the diff touches only `tt-league-ingest/` and
   `docs/sdd/`.

## Acceptance Criteria

- [x] The run report has an `outcome` of `SUCCEEDED`, `NO_CHANGES`, `COMPLETED_WITH_ISSUES`, `SOURCE_UNAVAILABLE` or `FAILED`, plus a `retryable` flag; the existing `status` stays unchanged for compatibility
- [x] The pipeline fingerprints the season's `actas-json` (SHA-256 per file) and `equipos-json` before the first stage and after `PARSE`/`TEAMS`, and reports `actasChanged` and `contentChanged` counts
- [x] When the run includes `PARSE`, no JSON changed and `force` is not set, `PACKAGE` and `UPLOAD` are recorded as skipped and the outcome is `NO_CHANGES` instead of a delta packaging failure
- [x] A `DOWNLOAD` stage whose legacy script reported failures (exit code 1) while no content file was written gives `SOURCE_UNAVAILABLE` with `retryable=true`
- [x] Parse issues, invalid actas and stage failures give `COMPLETED_WITH_ISSUES` or `FAILED` with `retryable=false`
- [x] The CLI keeps exit codes 0/1/2 and adds 3 for `NO_CHANGES` and 4 for `SOURCE_UNAVAILABLE`; `--json` and the REST run DTO expose `outcome`, `retryable` and `changes`
- [x] Tests cover each outcome with fake ingestors and fixtures (no network); the README documents outcomes, skip rules and exit codes

# Implementation Guidelines

- Python only (`tt-league-ingest`); no Java change. Output files stay byte-compatible.
- Do not hide real failures as `NO_CHANGES`: a run that downloaded nothing because of errors is
  `SOURCE_UNAVAILABLE`, never "no changes".
- Do not change the legacy scripts' metrics files; outcome detection works on the folders they write.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal section "Changes to the Python repositories" item 2 (standardised exit codes) and item 3 (no ZIP when nothing changed).

## Planning notes (2026-10-04)

- The federation downloaders are ported legacy scripts (urllib, each with its own metrics file), not users of
  `PoliteHttpClient`. Reading three different metrics formats would couple the pipeline to each script, so
  outcomes are derived from the legacy exit code plus fingerprints of the folders the scripts write.
- Content fingerprints use size and mtime, so a script that rewrites unchanged pages counts as "content changed".
  That only makes `SOURCE_UNAVAILABLE` less likely, never hides a change. `actas-json` uses SHA-256 because it decides
  `NO_CHANGES`.
- RFETM teams (`equipos-json/<season>.json`) are part of the JSON fingerprint, so a teams-only change still packages.

## Plan rebuild (2026-10-04)

- Rebuilt against the code: steps 1-6 already landed with commit `3a5d1b5`, and the existing 160 ingest tests pass
  with the new behaviour. The remaining work is the outcome tests (step 7), the README (step 8) and validation.
- The code differs slightly from the first plan; the plan now follows the code. The legacy-failure marker is the exact
  `LEGACY_FAILURE_MESSAGE` constant in `run.py` (not a `where`/prefix match) and is read through
  `StageReport.legacy_failure`. `NO_CHANGES` is derived from "any skipped stage and no issues", which is the same thing
  because only `PACKAGE`/`UPLOAD` can be skipped.
- A `DOWNLOAD` + `PARSE` run without `PACKAGE` that changes nothing reports `SUCCEEDED`, not `NO_CHANGES`, because
  nothing is skipped. This matches the acceptance criteria. The orchestrator always requests `PACKAGE`, so this is kept
  as is. Revisit only if a caller needs "nothing changed" without packaging.
- Incremental downloaders skip complete and future jornadas, so a run where every selected page is already saved and
  one request fails (exit 1) also writes no content and is reported `SOURCE_UNAVAILABLE`. That is a retryable result,
  never a hidden change, so it fits the "do not hide real failures" guideline.
- Implementation finalized 2026-10-04: steps 7-9 added (fingerprint, pipeline, CLI and REST outcome tests; README outcome table, skip rule and exit codes). uv lock --check, uv sync --all-packages and uv run pytest pass (189 tests); no Maven module changed.
- Closed as done on explicit user request (2026-10-04).
