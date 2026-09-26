# Build Plan
> Fill this in when status moves to `planned`.

## Acceptance Criteria
- [ ] A `match_record.source_checksum` column stores the checksum of the acta that produced a PLAYED match.
- [ ] When a PLAYED acta's checksum changes, the match is re-applied through `replaceMatchContent` and an audit line is logged.
- [ ] The behaviour is opt-in at first.
- [ ] `rfetm-datamodel.md` documents the column; tests cover unchanged, amended, and opt-out cases.

# Implementation Guidelines
- Priority may rise depending on how often federations amend actas (open question 3).
- Follow the repository `AGENTS.md` and the nearest module `AGENTS.md`; keep module boundaries (R10) and source-scoped lookups (R11).

# Notes
- Source: task T13 of [docs/analysis/analysis-incremental-actas-for-current-jornada-import.md](../../docs/analysis/analysis-incremental-actas-for-current-jornada-import.md), priority tier P2 (registry priority `low`), size M, delivery slice 4 (hardening).
- Plan dependencies: FEAT-00080 (T5).
- Plan references: section 4.2 (correction path), open question 3.
