# Build Plan
> Fill this in when status moves to `planned`.

Source task: **T6** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.3; gaps G1, G2, G4, G15, G18; risks K13, K15, K16).

## Acceptance Criteria
- [ ] RFETM, FCTT and BCNESA match processors implement the shared algorithm of analysis section 4.3 (create, upgrade, reschedule, skip, regression issue)
- [ ] RFETM classifies actas before buildMatch and never stores unpublished actas as played
- [ ] FCTT replaces the FEAT-00074 unpublished skip with the SCHEDULED branch and skips no-team placeholders before the team processor
- [ ] BcnesaMatchdaySplitter yields one fixture named by equipos when partidos is empty
- [ ] The SCHEDULED branch never copies resultado_final or a winner; a PLAYED match is never downgraded
- [ ] The doubles path handles jugadores: [] without creating a DoublesPair
- [ ] FCTT preview wording reflects the new behaviour
- [ ] Tests cover create, upgrade, reschedule, idempotent re-import and regression for each source

# Implementation Guidelines

- Affected modules: import.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P0, size M, Slice 2: incremental import. Depends on: FEAT-00076 (T1), FEAT-00080 (T5).
- FEAT-00079 (T4) must ship with or before this feature (risk K1).
- 2026-09-27: Added FEAT-00079 (T4) as a formal dependency to enforce that ordering.
- Until this feature ships, do not import any 2026-2027 export (risk K15).
