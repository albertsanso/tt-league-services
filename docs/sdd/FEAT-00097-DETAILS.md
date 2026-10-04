# Build Plan
1. **Fingerprints (`tt-league-ingest/packages/tt-league-ingest-common/src/ingest_common/fingerprint.py`, new).**
   - `content_fingerprint(directory, patterns) -> dict[str, tuple[int, int]]`: relative POSIX path to `(size, mtime_ns)`
     for downloaded pages/PDFs (cheap; large PDFs are not hashed).
   - `json_fingerprint(directory) -> dict[str, str]`: relative path to SHA-256 of the bytes for `*.json`.
   - `count_changes(before, after) -> int`: added + modified + removed entries.
2. **Run model (`ingest_common/run.py`).**
   - Add `RunOutcome` enum (`SUCCEEDED`, `NO_CHANGES`, `COMPLETED_WITH_ISSUES`, `SOURCE_UNAVAILABLE`, `FAILED`) and
     `RETRYABLE_OUTCOMES = {SOURCE_UNAVAILABLE}`.
   - `StageReport` gains `skipped: str | None` (reason) and `source_unavailable: bool`.
   - `RunReport` gains `changes: dict[str, int]` (`contentChanged`, `actasChanged`) and `outcome`; `finish()` keeps the
     current `status` logic and derives `outcome` in this order: any stage `failed` -> `FAILED`, unless the failed
     stage is `DOWNLOAD` with `source_unavailable` -> `SOURCE_UNAVAILABLE`; a skipped `PACKAGE` with no issues ->
     `NO_CHANGES`; issues/invalid/failed counters -> `COMPLETED_WITH_ISSUES`; else `SUCCEEDED`.
   - `to_dict()` adds `outcome`, `retryable`, `changes` and per-stage `skipped`.
3. **Pipeline (`ingest_common/pipeline.py`).**
   - Before the first stage, snapshot `content_fingerprint(settings.content_dir(source) / season)` and
     `json_fingerprint` of `actas_json_dir(source) / season` plus the season teams file.
   - After `DOWNLOAD`: set `changes["contentChanged"]`; if the stage has a legacy-failure issue (from
     `record_exit_code` code 1) and `contentChanged == 0`, set `source_unavailable = True` and fail the stage so later
     stages do not run on stale content.
   - After `PARSE` (and `TEAMS`): set `changes["actasChanged"]`.
   - Before `PACKAGE`/`UPLOAD`: when `PARSE` ran, `actasChanged == 0` and not `request.force`, append a `StageReport`
     with `skipped="no JSON changed"` instead of executing. A run without `PARSE` (package-only) is never skipped.
4. **Legacy exit-code mapping (`ingest_common/scan.py`).** `record_exit_code` tags code-1 issues with a stable
   marker (`where == script`, message prefix `"the script finished with failures"`) and returns whether failures
   happened, so the pipeline can tell a failing download apart without parsing each source's metrics file.
5. **CLI (`tt-league-ingest/packages/tt-league-ingest-cli/src/ingest_cli/main.py`).** Add `EXIT_NO_CHANGES = 3` and
   `EXIT_SOURCE_UNAVAILABLE = 4`; `exit_code(report)` maps on `outcome` (`SUCCEEDED` 0, `NO_CHANGES` 3,
   `SOURCE_UNAVAILABLE` 4, others 1); usage errors stay 2. `format_summary` prints the outcome and change counts.
6. **REST (`tt-league-ingest/packages/tt-league-ingest-rest/src/ingest_rest/app.py`).** `RunRecord.to_dict()` adds `outcome`, `retryable`
   and `changes`; an exception inside `execute` sets `outcome = FAILED`, `retryable = false`.
7. **Tests.**
   - `tt-league-ingest-common/tests/test_fingerprint.py`: change counting (add/modify/remove).
   - `tt-league-ingest-common/tests/test_packaging_pipeline.py`: fake ingestor that writes nothing -> `NO_CHANGES`,
     package skipped, no ZIP; writes one JSON -> `SUCCEEDED` and ZIP; `force` packages anyway; download exit 1 with
     no files -> `SOURCE_UNAVAILABLE` and later stages not run; download exit 1 with files -> `COMPLETED_WITH_ISSUES`.
   - `tt-league-ingest-cli/tests/test_cli.py`: exit codes 0/1/2/3/4. `tt-league-ingest-rest/tests/test_rest.py`: DTO
     fields.
8. **Docs.** `tt-league-ingest/README.md`: outcome table, skip rule, new exit codes and REST fields.
9. **Validation.** From `tt-league-ingest/`: `uv lock --check`, `uv sync --all-packages`, `uv run pytest`.

## Acceptance Criteria

- [ ] The run report has an `outcome` of `SUCCEEDED`, `NO_CHANGES`, `COMPLETED_WITH_ISSUES`, `SOURCE_UNAVAILABLE` or `FAILED`, plus a `retryable` flag; the existing `status` stays unchanged for compatibility
- [ ] The pipeline fingerprints the season's `actas-json` (SHA-256 per file) and `equipos-json` before the first stage and after `PARSE`/`TEAMS`, and reports `actasChanged` and `contentChanged` counts
- [ ] When the run includes `PARSE`, no JSON changed and `force` is not set, `PACKAGE` and `UPLOAD` are recorded as skipped and the outcome is `NO_CHANGES` instead of a delta packaging failure
- [ ] A `DOWNLOAD` stage whose legacy script reported failures (exit code 1) while no content file was written gives `SOURCE_UNAVAILABLE` with `retryable=true`
- [ ] Parse issues, invalid actas and stage failures give `COMPLETED_WITH_ISSUES` or `FAILED` with `retryable=false`
- [ ] The CLI keeps exit codes 0/1/2 and adds 3 for `NO_CHANGES` and 4 for `SOURCE_UNAVAILABLE`; `--json` and the REST run DTO expose `outcome`, `retryable` and `changes`
- [ ] Tests cover each outcome with fake ingestors and fixtures (no network); the README documents outcomes, skip rules and exit codes

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
