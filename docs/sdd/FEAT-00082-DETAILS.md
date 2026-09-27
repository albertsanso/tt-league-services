# Build Plan
> Fill this in when status moves to `planned`.

Source task: **T7** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.8; gaps G5, G10; risk K12).

## Acceptance Criteria
- [ ] Traversal summaries, ImportExecutionMetrics and ImportProcessResult carry scheduledCreated, upgradedToPlayed, rescheduled, partialActas, invalidActas and unresolvedPendingFixtures
- [ ] A run with no changes (for example all actas pending and already stored) ends SUCCESS/PROCESSED; EMPTY_RESULT is kept for no actas found
- [ ] ImportResource.lastProcessedDate is set when a run finishes
- [ ] README documents the status change and counters

# Implementation Guidelines

- Affected modules: import, domain, runtime.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P0, size S, Slice 2: incremental import. Depends on: FEAT-00081 (T6).
