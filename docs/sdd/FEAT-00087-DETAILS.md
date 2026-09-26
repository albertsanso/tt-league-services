# Build Plan
> Fill this in when status moves to `planned`.

## Acceptance Criteria
- [ ] Preview reports, per competition and group, new scheduled fixtures, upgrades, reschedules, regressions, partial actas, and unresolved fixtures.
- [ ] Preview reports the resulting jornada progress.
- [ ] Preview flags two files that carry the same `id_partido` in one snapshot.
- [ ] Tests cover the preview counters for each source.

# Implementation Guidelines
- Preview the 2026-2027 FCTT upload before its first production run (K15).
- Follow the repository `AGENTS.md` and the nearest module `AGENTS.md`; keep module boundaries (R10) and source-scoped lookups (R11).

# Notes
- Source: task T12 of [docs/analysis/analysis-incremental-actas-for-current-jornada-import.md](../../docs/analysis/analysis-incremental-actas-for-current-jornada-import.md), priority tier P1 (registry priority `medium`), size S, delivery slice 3 (visibility and safety).
- Plan dependencies: FEAT-00076 (T1), FEAT-00083 (T8).
- Plan references: G11, risks K2, K14, K15.
