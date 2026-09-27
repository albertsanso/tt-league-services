# Build Plan
> Fill this in when status moves to `planned`.

Source task: **T5** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.4; gap G7; risk K3).

## Acceptance Criteria
- [ ] MatchRepository.replaceMatchContent replaces header and children (lineups, games, set scores, doubles pairs) of an existing match in one transaction, preserving its id
- [ ] MatchRepository.updateSchedule (or a verified createExisting/saveMatch merge) updates date, time, city, venue and referee
- [ ] JPA and in-memory implementations exist
- [ ] Rollback tests prove a failed replace leaves no half-written match

# Implementation Guidelines

- Affected modules: domain, JPA, import (tests).
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P0, size M, Slice 2: incremental import. Depends on: FEAT-00077 (T2).
