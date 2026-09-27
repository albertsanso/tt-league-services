# Build Plan
> Fill this in when status moves to `planned`.

Source task: **T18** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.4, 6; gaps G15, G16).

## Acceptance Criteria
- [ ] match_record.source_fixture_id is a nullable VARCHAR(100) with a unique (source, source_fixture_id) constraint; external_id is not reused
- [ ] Match has a sourceFixtureId field supported by mappers and in-memory repositories
- [ ] MatchRepository.findBySourceFixtureId(ImportSource, String) exists
- [ ] All three sources fill it from id_partido on create and on upgrade; legacy rows stay null
- [ ] rfetm-datamodel.md documents the column and constraint

# Implementation Guidelines

- Affected modules: domain, JPA, import.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P1, size S, Slice 2: incremental import. Depends on: FEAT-00077 (T2), FEAT-00081 (T6).
