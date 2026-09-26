# Build Plan
> Fill this in when status moves to `planned`.

## Acceptance Criteria
- [ ] `MatchRepository.findRoundProgress(source, season)` returns, per `(competition, group, phase)`, the last complete round, the current round (highest round with a PLAYED fixture), and scheduled and played counts.
- [ ] The progress is exposed in the import run result, the import-resource read model (REST), and the CLI summary.
- [ ] Progress is informational only and never used to skip files.
- [ ] The runtime README documents the progress output.

# Implementation Guidelines
- Derive from `match_record`; add an `import_round_progress` cache table only if the query proves slow.
- Follow the repository `AGENTS.md` and the nearest module `AGENTS.md`; keep module boundaries (R10) and source-scoped lookups (R11).

# Notes
- Source: task T8 of [docs/analysis/analysis-incremental-actas-for-current-jornada-import.md](../../docs/analysis/analysis-incremental-actas-for-current-jornada-import.md), priority tier P1 (registry priority `medium`), size S, delivery slice 3 (visibility and safety).
- Plan dependencies: FEAT-00077 (T2), FEAT-00081 (T6).
- Plan references: section 4.6, G5, G9, requirement R7.
