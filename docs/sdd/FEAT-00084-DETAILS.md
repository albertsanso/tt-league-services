# Build Plan
> Fill this in when status moves to `planned`.

Source task: **T8** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.6; requirement R7; gap G5).

## Acceptance Criteria
- [ ] MatchRepository.findRoundProgress(source, season) returns current round, last complete round and scheduled/played counts per competition/group/phase
- [ ] Progress is exposed in the import run result, the import-resource read model and the CLI summary
- [ ] Progress is informational and never used to skip files
- [ ] Test with the FCTT 2026-2027 tercera-nacional/G1 shape yields current jornada 1 and no last complete jornada
- [ ] README documents the progress output

# Implementation Guidelines

- Affected modules: domain, JPA, api-rest, runtime.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P1, size S, Slice 3: visibility and safety. Depends on: FEAT-00077 (T2), FEAT-00081 (T6).
