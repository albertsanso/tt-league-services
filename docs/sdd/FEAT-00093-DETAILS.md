# Build Plan
> Fill this in when status moves to `planned`.

Source task: **T17** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 5 task T17).

## Acceptance Criteria
- [ ] A scheduled job fetches and uploads a snapshot per source and season
- [ ] Configuration is explicit and environment-driven with no committed secrets
- [ ] Failures are reported clearly without silent fallback

# Implementation Guidelines

- Affected modules: runtime / ops.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P3, size M, Slice 5: calendar feature and automation. Depends on: FEAT-00087 (T11).
