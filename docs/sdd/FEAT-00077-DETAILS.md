# Build Plan
> Fill this in when status moves to `planned`.

Source task: **T2** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.2; gaps G2, G13).

## Acceptance Criteria
- [ ] MatchStatus { SCHEDULED, PLAYED } exists in the domain as a Match field and builder property
- [ ] match_record.status is VARCHAR(20) NOT NULL DEFAULT 'PLAYED' mapped with @Enumerated(STRING) and works under ddl-auto: update on a populated table
- [ ] Mappers and in-memory repositories carry the status
- [ ] rfetm-datamodel.md documents the column, default and the invariant SCHEDULED => no lineups, games, set scores, doubles pairs or winner
- [ ] JPA tests cover the default value and the invariant

# Implementation Guidelines

- Affected modules: domain, JPA, import (tests).
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P0, size S, Slice 1: lifecycle foundation. Depends on: —.
