# Build Plan
> Fill this in when status moves to `planned`.

Source task: **T11** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.7; gaps G6, G12; risk K10).

## Acceptance Criteria
- [ ] Snapshot mode (season folder replaced on upload) is documented as the default contract
- [ ] An upload with fewer published actas than the stored season folder is rejected unless an explicit override is given
- [ ] A moving FCTT window with at least as many published actas is still accepted
- [ ] Tests cover accepted, rejected and overridden uploads

# Implementation Guidelines

- Affected modules: domain (ResourceRepositoryLoaderService, ResourceZipService), README.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P1, size S, Slice 3: visibility and safety. Depends on: —.
