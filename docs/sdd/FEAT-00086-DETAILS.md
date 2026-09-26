# Build Plan
> Fill this in when status moves to `planned`.

## Acceptance Criteria
- [ ] Snapshot mode (each ZIP holds the full season as currently published) is documented in the relevant README.
- [ ] `ResourceRepositoryLoaderService` rejects a snapshot that has fewer acta files than the stored season folder unless an explicit override is given.
- [ ] The rejection fails clearly and leaves the stored season folder untouched.
- [ ] Tests cover the shrink rejection and the override.

# Implementation Guidelines
- The existing delete-and-replace of the season folder remains correct for snapshot mode.
- Follow the repository `AGENTS.md` and the nearest module `AGENTS.md`; keep module boundaries (R10) and source-scoped lookups (R11).

# Notes
- Source: task T11 of [docs/analysis/analysis-incremental-actas-for-current-jornada-import.md](../../docs/analysis/analysis-incremental-actas-for-current-jornada-import.md), priority tier P1 (registry priority `medium`), size S, delivery slice 3 (visibility and safety).
- Plan dependencies: none.
- Plan references: section 4.7, G6, requirement R8, risk K10, open question 1.
