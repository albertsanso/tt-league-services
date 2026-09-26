# Build Plan
> Fill this in when status moves to `planned`.

## Acceptance Criteria
- [ ] An API (and UI) lists a season's matchdays with SCHEDULED and PLAYED matches and their status.
- [ ] Derived "overdue" and "postponed" labels are shown without being stored.
- [ ] Any manual states (for example CANCELLED) are decided and, if added, are never overridden by the import.

# Implementation Guidelines
- Separate future feature enabled by the lifecycle foundation (T2-T8); refine scope before planning (open question 4).
- Follow the repository `AGENTS.md` and the nearest module `AGENTS.md`; keep module boundaries (R10) and source-scoped lookups (R11).

# Notes
- Source: task T16 of [docs/analysis/analysis-incremental-actas-for-current-jornada-import.md](../../docs/analysis/analysis-incremental-actas-for-current-jornada-import.md), priority tier P3 (registry priority `low`), size L, delivery slice 5 (calendar and automation).
- Plan dependencies: FEAT-00079 (T4), FEAT-00083 (T8).
- Plan references: section 4.2, open question 4.
