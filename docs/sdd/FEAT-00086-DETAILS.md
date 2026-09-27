# Build Plan
> Fill this in when status moves to `planned`.

Source task: **T10** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.7; risk K8).

## Acceptance Criteria
- [ ] After a snapshot run, stored SCHEDULED matches of the season not seen in the run are reported, matched by id_partido or natural key
- [ ] Rounds beyond the snapshot's highest round are not flagged (FCTT sliding window)
- [ ] No match is deleted or modified by reconciliation
- [ ] Tests cover a vanished fixture and the FCTT window case

# Implementation Guidelines

- Affected modules: import, runtime.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P1, size S, Slice 3: visibility and safety. Depends on: FEAT-00081 (T6), FEAT-00083 (T18).
