# Build Plan
> Fill this in when status moves to `planned`.

## Acceptance Criteria
- [ ] The manifest accepts an optional `"mode": "snapshot" | "delta"`; unknown keys are still rejected.
- [ ] In delta mode, files are merged into the season folder without deleting, and a rollback copy is kept.
- [ ] Snapshot remains the default when `mode` is absent.
- [ ] README documents the mode; tests cover both modes and the rollback copy.

# Implementation Guidelines
- Needed only if uploads are not full-season snapshots (open question 1).
- Follow the repository `AGENTS.md` and the nearest module `AGENTS.md`; keep module boundaries (R10) and source-scoped lookups (R11).

# Notes
- Source: task T14 of [docs/analysis/analysis-incremental-actas-for-current-jornada-import.md](../../docs/analysis/analysis-incremental-actas-for-current-jornada-import.md), priority tier P2 (registry priority `low`), size M, delivery slice 4 (hardening).
- Plan dependencies: FEAT-00086 (T11).
- Plan references: section 4.7, G6, G12, risk K10, open question 1.
