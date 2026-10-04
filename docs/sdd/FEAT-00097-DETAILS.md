# Build Plan

> Draft outline.

1. `ingest_common/run.py`: add `RunOutcome` and derive it in `RunReport.finish()` from stage reports
   (new counters or flags for "no change" and "source unavailable").
2. `ingest_common/http.py`: classify exhausted retries (connection error, timeout, 5xx) as a source-unavailable
   issue on the stage report.
3. `ingest_common/pipeline.py`: skip `PACKAGE`/`UPLOAD` when the parse stage wrote no file in a delta or
   snapshot run; record the skip.
4. `ingest_cli/main.py`: map outcomes to exit codes 0/1/2/3/4. `ingest_rest/app.py`: add `outcome` and `retryable`.
5. Tests per package; update `tt-league-ingest/README.md` (CLI exit codes, REST DTO).

## Acceptance Criteria

- [ ] The run report has an `outcome` of `SUCCEEDED`, `NO_CHANGES`, `COMPLETED_WITH_ISSUES`, `SOURCE_UNAVAILABLE` or `FAILED`, plus a `retryable` flag; the existing `status` stays for compatibility
- [ ] When download and parse change no JSON file, `package` and `upload` are skipped and the outcome is `NO_CHANGES` instead of a delta packaging failure
- [ ] HTTP connection errors, timeouts and 5xx answers from the federation site after the legacy retries give `SOURCE_UNAVAILABLE` with `retryable=true`
- [ ] Parse and schema-validation failures give `COMPLETED_WITH_ISSUES` or `FAILED` with `retryable=false`
- [ ] The CLI adds exit code 3 for `NO_CHANGES` and 4 for `SOURCE_UNAVAILABLE`, keeping 0/1/2, and the REST run DTO exposes `outcome` and `retryable`
- [ ] Tests cover each outcome with fake transports and fixtures; README documents outcomes and exit codes

# Implementation Guidelines

- Python only (`tt-league-ingest`); no Java change. Keep output files byte-compatible.
- Do not hide real failures as `NO_CHANGES`: a run that downloaded nothing because of errors is not "no changes".

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal section "Changes to the Python repositories" item 2 (standardised exit codes) and item 3 (no ZIP when nothing changed).
