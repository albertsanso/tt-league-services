# Build Plan
> Fill this in when status moves to `planned`.

## Acceptance Criteria
- [ ] A nullable `match_record.source_fixture_id VARCHAR(100)` with a unique constraint on `(source, source_fixture_id)` is added, with a `Match` field, mappers, and in-memory support.
- [ ] FCTT fills it from `id_partido` on create and on upgrade; other sources leave it null until they send one.
- [ ] `external_id` is not reused.
- [ ] `rfetm-datamodel.md` documents the column and constraint; tests cover create, upgrade, and uniqueness.

# Implementation Guidelines
- `external_id` is `VARCHAR(20)` and has a different meaning; `id_partido` is about 45 characters.
- Match-level fixture id only; do not add external ids to `FederatedClub` or `FederatedPlayer` (R11).
- Follow the repository `AGENTS.md` and the nearest module `AGENTS.md`; keep module boundaries (R10) and source-scoped lookups (R11).

# Notes
- Source: task T18 of [docs/analysis/analysis-incremental-actas-for-current-jornada-import.md](../../docs/analysis/analysis-incremental-actas-for-current-jornada-import.md), priority tier P1 (registry priority `medium`), size S, delivery slice 3 (visibility and safety).
- Plan dependencies: FEAT-00077 (T2), FEAT-00081 (T6).
- Plan references: G16, section 2.5, requirement R11, open question 6.
