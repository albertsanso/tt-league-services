# Build Plan

> Draft outline.

1. `ingest_common/run.py`: add `IngestScope` and `IngestRequest.scopes`; keep `filters` as a single-scope shortcut.
2. Each federation `download.py`/`parse.py`: iterate scopes (BCNESA and FCTT by territory/category/group/phase;
   RFETM by category/match day), keeping the legacy delays and the one-at-a-time rule.
3. `packaging.package_season`: select actas by the scopes' match days in delta mode.
4. `ingest_rest/app.py`: request validation and the match-days-status endpoint (read the file atomically written today).
5. `ingest_cli/main.py`: `--scope-file`. Tests and README.

## Acceptance Criteria

- [ ] `POST /api/v1/ingest/runs` accepts `scopes: [{category, group, phase, territory, gender, matchDays}]`, and a run downloads, parses and packages only the union of the scopes
- [ ] The existing single `filters` body stays valid; sending both `filters` and `scopes` is a 400
- [ ] Unsupported scope fields for a source fail the run before any network call, as filters do today
- [ ] `GET /api/v1/ingest/sources/{source}/match-days-status?season=` returns the current `match-days-status.json` (404 when none exists)
- [ ] The CLI `run` accepts a `--scope-file` JSON with the same shape
- [ ] Tests cover scope union, validation and the status endpoint; README documents both

# Implementation Guidelines

- Python only. Scraping etiquette and the incremental skip rules (complete/future jornadas) stay unchanged.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal item 3 (scoped, incremental runs). BCNESA has four territory pipelines (Barcelona, Girona, Lleida, Tarragona); scopes must carry the territory.
