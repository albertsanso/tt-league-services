# Build Plan
> Fill this in when status moves to `planned`.

Source task: **T14** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.7; gaps G6, G12; requirement R8; open question 1).

## Acceptance Criteria
- [ ] The manifest accepts an optional mode of snapshot or delta, defaulting to snapshot
- [ ] Delta mode merges files into the season folder without deleting existing ones
- [ ] A rollback copy of the season folder is kept for delta uploads
- [ ] README documents the manifest field

# Implementation Guidelines

- Affected modules: domain, api-rest, README.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P2, size M, Slice 4: hardening. Depends on: FEAT-00087 (T11).
