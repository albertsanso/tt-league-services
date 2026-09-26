# Build Plan
> Fill this in when status moves to `planned`.

## Acceptance Criteria
- [ ] `scheduledCreated`, `upgradedToPlayed`, `rescheduled`, `partialActas`, and `unresolvedPendingFixtures` counters are added to the traversal summaries, `ImportExecutionMetrics`, and `ImportProcessResult`.
- [ ] A run that finds actas but changes nothing ends as SUCCESS (or a new NO_CHANGES mapped to PROCESSED); `EMPTY_RESULT` is kept for "no actas found at all".
- [ ] `ImportResource.lastProcessedDate` is set when a run finishes.
- [ ] The runtime README documents the new counters and the changed status of no-change runs.

# Implementation Guidelines
- Operators will see `ERROR` become `PROCESSED` for no-change runs; call it out in the README (K12).
- Follow the repository `AGENTS.md` and the nearest module `AGENTS.md`; keep module boundaries (R10) and source-scoped lookups (R11).

# Notes
- Source: task T7 of [docs/analysis/analysis-incremental-actas-for-current-jornada-import.md](../../docs/analysis/analysis-incremental-actas-for-current-jornada-import.md), priority tier P0 (registry priority `high`), size S, delivery slice 2 (incremental import).
- Plan dependencies: FEAT-00081 (T6).
- Plan references: section 4.8, G5, G10, risk K12.
