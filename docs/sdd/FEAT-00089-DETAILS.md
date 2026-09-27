# Build Plan
> Fill this in when status moves to `planned`.

Source task: **T13** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.2 diagram (correction); open question 3).

## Acceptance Criteria
- [ ] match_record.source_checksum stores a checksum of the applied acta and is documented in rfetm-datamodel.md
- [ ] When enabled and a PLAYED acta's checksum changes, the match is re-applied via replaceMatchContent and an audit line is logged
- [ ] The behaviour is opt-in and disabled by default

# Implementation Guidelines

- Affected modules: domain, JPA, import.
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P2, size M, Slice 4: hardening. Depends on: FEAT-00080 (T5).
