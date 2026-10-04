# Build Plan

> Draft outline.

## Acceptance Criteria

- [ ] A daily job aggregates `daily_stats` (runs, failures, matches reported, average time to report, pending at end of day) per source
- [ ] Statistics endpoints return reporting progress per match day, time to report (median, p90) per source and category, pending by age, corrections after first report and runs by outcome
- [ ] A dashboard page charts these figures and a source-health panel (HTTP errors, timeouts, parse errors per source)
- [ ] Tests cover the aggregation and the endpoints

# Implementation Guidelines

Follow the root and module `AGENTS.md` files.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal "History, statistics and observability" and UI view 4. `source_fetch` health data comes from ingest run issues; a per-URL table is not planned.
