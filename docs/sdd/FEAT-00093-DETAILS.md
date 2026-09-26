# Build Plan
> Fill this in when status moves to `planned`.

## Acceptance Criteria
- [ ] A scheduled job fetches the current-season actas per source and uploads them as a snapshot ZIP.
- [ ] Failures are reported clearly and never fall back to another source, season, or mode.
- [ ] Operational configuration is environment-driven and documented in the runtime README; no credentials are committed.

# Implementation Guidelines
- Runtime/ops only; reuse the existing upload endpoint and snapshot contract.
- Follow the repository `AGENTS.md` and the nearest module `AGENTS.md`; keep module boundaries (R10) and source-scoped lookups (R11).

# Notes
- Source: task T17 of [docs/analysis/analysis-incremental-actas-for-current-jornada-import.md](../../docs/analysis/analysis-incremental-actas-for-current-jornada-import.md), priority tier P3 (registry priority `low`), size M, delivery slice 5 (calendar and automation).
- Plan dependencies: FEAT-00086 (T11).
- Plan references: section 5, open question 1.
