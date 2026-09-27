# Build Plan
> Fill this in when status moves to `planned`.

Source task: **T4** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.5; gap G3; risks K1, K9).

## Acceptance Criteria
- [ ] MatchOutcome returns empty for SCHEDULED matches and its javadoc states that a winner-less PLAYED match is a tie
- [ ] Match, player, club, federated-club, competition and club-search query handlers compute stats, win rates, form and streaks over PLAYED only
- [ ] MatchRepositoryHelper countBySeason, countAllMatches and findAllSeasons count PLAYED only; searchMatches/countMatches and fragment search default to PLAYED
- [ ] REST and MCP MatchDto/MatchDetailDto expose an additive status field
- [ ] findAllMatchesByTeamIds used by consolidation is not filtered by status
- [ ] Each affected handler has a test with a mixed SCHEDULED/PLAYED fixture
- [ ] Shipped with or before the processor lifecycle feature (T6)

# Implementation Guidelines

- Affected modules: domain (application), JPA, api-rest, api-mcp.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P0, size M, Slice 1: lifecycle foundation. Depends on: FEAT-00077 (T2).
- Must ship with or before FEAT-00081 (T6): otherwise scheduled rows leak into statistics (risk K1).
