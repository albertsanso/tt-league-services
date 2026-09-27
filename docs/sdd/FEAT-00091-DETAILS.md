# Build Plan
> Fill this in when status moves to `planned`.

Source task: **T15** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Gap G21).

## Acceptance Criteria
- [ ] The Other-group round fallback supports acta_<home>-<away>_<jornada>.json or is replaced by payload jornada with a reported issue instead of a guess
- [ ] The need for multi-fixture splitting and BcnesaClubIndex is measured and the outcome recorded
- [ ] Tests cover legacy and new file names

# Implementation Guidelines

- Affected modules: import.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P2, size S, Slice 4: hardening. Depends on: FEAT-00075 (T0), FEAT-00081 (T6).
