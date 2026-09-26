# Build Plan
> Fill this in when status moves to `planned`.

## Acceptance Criteria
- [ ] An opt-in runtime command marks as SCHEDULED the matches with no `game` rows, no `winner_team_id`, and null or 0-0 games won.
- [ ] The command supports report and write modes; report mode runs the same analysis without persistence writes and prints the counts to review.
- [ ] The command is scoped by source and season and fails clearly on missing or invalid arguments.
- [ ] The runtime README documents the command, its modes, and the backfill rule; `rfetm-datamodel.md` documents the rule.

# Implementation Guidelines
- Follow the consolidation conventions: opt-in, source-scoped, report mode first.
- Never default-enable the backfill; review per source and season counts before writing (K5).
- Follow the repository `AGENTS.md` and the nearest module `AGENTS.md`; keep module boundaries (R10) and source-scoped lookups (R11).

# Notes
- Source: task T3 of [docs/analysis/analysis-incremental-actas-for-current-jornada-import.md](../../docs/analysis/analysis-incremental-actas-for-current-jornada-import.md), priority tier P0 (registry priority `high`), size S, delivery slice 1 (lifecycle foundation).
- Plan dependencies: FEAT-00077 (T2).
- Plan references: section 4.2, G2, risk K5.
