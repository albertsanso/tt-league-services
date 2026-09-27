# Build Plan
> Fill this in when status moves to `planned`.

Source task: **T0** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Sections 2.5, 2.6; gaps G14, G17, G18).

## Acceptance Criteria
- [ ] Anonymised fixtures exist for RFETM 2026-2027 published and unpublished actas, RFETM 2025-2026 "decided 0-0" (G17) and a legacy empty acta
- [ ] Anonymised fixtures exist for BCNESA 2026-2027 unpublished actas, including the 4-4 placeholder score (G14)
- [ ] Anonymised fixtures exist for an FCTT 2026-2027 published/unpublished pair, the no-team placeholder (G18), and the 2025-2026 6-0 placeholder
- [ ] docs/acta-model-definition.json restores the id_partido stability and unpublished resultado_final placeholder descriptions and keeps a source-neutral title
- [ ] All fixtures parse with the existing Acta parser in a JUnit test

# Implementation Guidelines

- Affected modules: import (test resources), docs.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P0, size S, Slice 1: lifecycle foundation. Depends on: —.
