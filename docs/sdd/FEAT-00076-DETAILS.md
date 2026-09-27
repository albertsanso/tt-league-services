# Build Plan
> Fill this in when status moves to `planned`.

Source task: **T1** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.1; gaps G14, G17, G20).

## Acceptance Criteria
- [ ] ActaCompleteness { PLAYED, PENDING, PARTIAL, INVALID } and a shared classifier over Acta exist in tt-data-league-import
- [ ] Rules are evaluated in the order of analysis section 4.1 and resultado_final is never read to decide the class
- [ ] Games with no_disputado: true count as complete; legacy acta without acta_publicada needs at least one game with a result to be PLAYED
- [ ] A PENDING acta without both teams is identified as an unresolved pending fixture
- [ ] A JUnit test covers each rule and each T0 fixture; the FCTT 6-0, BCNESA 4-4 and RFETM decided 0-0 fixtures classify as PENDING

# Implementation Guidelines

- Affected modules: import.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P0, size S, Slice 1: lifecycle foundation. Depends on: FEAT-00075 (T0).
