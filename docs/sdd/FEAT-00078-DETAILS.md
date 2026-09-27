# Build Plan
> Fill this in when status moves to `planned`.

Source task: **T3** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.2 backfill; gaps G3, G17; risks K5, K15).

## Acceptance Criteria
- [ ] An opt-in runtime command marks as SCHEDULED matches with no game result, no winner_team_id and null or 0-0 games won
- [ ] The command is scoped by source and season and supports report and write modes; report mode performs no writes
- [ ] Tests cover legacy empty actas, decided 0-0 matches, and a real played match that must stay PLAYED
- [ ] The runtime README documents the command, its arguments and modes

# Implementation Guidelines

- Affected modules: JPA or import, runtime, README.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P0, size S, Slice 1: lifecycle foundation. Depends on: FEAT-00077 (T2).
