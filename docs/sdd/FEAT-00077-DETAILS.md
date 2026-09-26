# Build Plan
> Fill this in when status moves to `planned`.

## Acceptance Criteria
- [ ] `MatchStatus { SCHEDULED, PLAYED }` exists in `tt-data-league-core-domain` as a `Match` field and builder property.
- [ ] `MatchJPA` maps `match_record.status VARCHAR(20) NOT NULL DEFAULT 'PLAYED'` with `@Enumerated(STRING)`, so `ddl-auto: update` works on a populated database.
- [ ] Domain/JPA mappers and the import-module in-memory repositories carry the status.
- [ ] JPA tests cover the column default and the invariant "SCHEDULED implies no lineups, games, set scores, doubles pairs, or winner".
- [ ] `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` documents the column, its default, and the invariant.

# Implementation Guidelines
- Use a database default in `columnDefinition`; Hibernate never backfills values (G13, K6).
- "Postponed" and "overdue" are derived read-model labels, not stored states.
- Also add the `(source, season, competition, status)` index described in plan section 6.
- Follow the repository `AGENTS.md` and the nearest module `AGENTS.md`; keep module boundaries (R10) and source-scoped lookups (R11).

# Notes
- Source: task T2 of [docs/analysis/analysis-incremental-actas-for-current-jornada-import.md](../../docs/analysis/analysis-incremental-actas-for-current-jornada-import.md), priority tier P0 (registry priority `high`), size S, delivery slice 1 (lifecycle foundation).
- Plan dependencies: none.
- Plan references: section 4.2, G2, G13, risk K6.
