# Build Plan
> Fill this in when status moves to `planned`.

## Acceptance Criteria
- [ ] `MatchOutcome.teamOutcome` / `playerOutcome` return empty for SCHEDULED matches, and the javadoc invariant reads "a winner-less PLAYED match is a tie".
- [ ] Match, player, club, federated-club, federated-club-competition, and club-name-search query handlers compute stats, win rates, form, and streaks over PLAYED matches only.
- [ ] `MatchRepositoryHelper` counts and season listings (`countBySeason`, `countAllMatches`, `findAllSeasons`) consider PLAYED only.
- [ ] Match search and fragment search accept a status filter that defaults to PLAYED, so current API behaviour is unchanged.
- [ ] REST and MCP `MatchDto` / `MatchDetailDto` expose an additive `status` field.
- [ ] Consolidation lookups (`findAllMatchesByTeamIds*`) are not filtered by status.
- [ ] Each affected handler has a test with a mixed SCHEDULED/PLAYED fixture.

# Implementation Guidelines
- Must ship with or before the processor-lifecycle feature (T6); the 2026-2027 FCTT calendar (363 fixtures) must not be imported as SCHEDULED before this lands (K15).
- Consumer inventory: plan sections 2.4 (G3) and 4.5.
- Consolidation must keep seeing scheduled matches (R12, K9).
- Follow the repository `AGENTS.md` and the nearest module `AGENTS.md`; keep module boundaries (R10) and source-scoped lookups (R11).

# Notes
- Source: task T4 of [docs/analysis/analysis-incremental-actas-for-current-jornada-import.md](../../docs/analysis/analysis-incremental-actas-for-current-jornada-import.md), priority tier P0 (registry priority `high`), size M, delivery slice 1 (lifecycle foundation).
- Plan dependencies: FEAT-00077 (T2).
- Plan references: sections 4.5 and 2.4 (G3), requirements R5 and R12, risks K1, K9, K15.
