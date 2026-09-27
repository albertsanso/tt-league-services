# Build Plan
> Fill this in when status moves to `planned`.

Source task: **T12** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.8; gap G11; risks K2, K14).

## Acceptance Criteria
- [ ] Preview reports counts of published, unpublished, invalid, partial and unresolved actas for RFETM, BCNESA and FCTT
- [ ] Preview reports new scheduled matches, upgrades, reschedules and regressions per competition/group
- [ ] Preview reports the resulting jornada progress
- [ ] Preview flags duplicate id_partido within a snapshot

# Implementation Guidelines

- Affected modules: import (preview processors).
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P1, size S, Slice 3: visibility and safety. Depends on: FEAT-00076 (T1), FEAT-00084 (T8).
