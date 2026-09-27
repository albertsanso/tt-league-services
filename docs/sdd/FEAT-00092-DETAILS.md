# Build Plan
> Fill this in when status moves to `planned`.

Source task: **T16** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Revision note 1; open question 4).

## Acceptance Criteria
- [ ] API lists a season calendar per competition/group/jornada with match status
- [ ] Derived postponed/overdue states are shown without being stored
- [ ] UI presents the calendar and jornada progress

# Implementation Guidelines

- Affected modules: api, UI.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P3, size L, Slice 5: calendar feature and automation. Depends on: FEAT-00079 (T4), FEAT-00084 (T8).
