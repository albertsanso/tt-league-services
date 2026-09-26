# Build Plan
> Fill this in when status moves to `planned`.

## Acceptance Criteria
- [ ] With no existing match, a PENDING or PARTIAL acta creates a SCHEDULED match with header fields only (teams, competition, season, group, phase, round, date, time, venue) and never copies `resultado_final`, winner, or referee data.
- [ ] With no existing match, a PLAYED acta keeps today's behaviour.
- [ ] An existing SCHEDULED match is upgraded in place (same UUID) to PLAYED through `replaceMatchContent` when a PLAYED acta arrives.
- [ ] An existing SCHEDULED match has its date, time, venue, and city updated when a newer PENDING or PARTIAL acta changes them.
- [ ] A PENDING or PARTIAL acta for an existing PLAYED match changes nothing and is reported as an issue; an unchanged PLAYED acta is skipped.
- [ ] BCNESA dispatches pending fixtures named by `equipos` (bypassing or extending `BcnesaMatchdaySplitter`), and fixtures whose teams cannot be resolved are counted and reported, not guessed (R9).
- [ ] The FEAT-00074 early return for unpublished FCTT actas is replaced by the SCHEDULED branch in the same change, and the FCTT preview message reports a scheduled fixture.
- [ ] Re-importing the same snapshot changes nothing and succeeds (R6); tests cover create, upgrade, reschedule, and regression for each source.

# Implementation Guidelines
- Follow the shared algorithm of plan section 4.3; reuse `buildMatch` / `buildLineups` / `storeGames` for the PLAYED branch.
- Do not remove the FCTT unpublished-acta skip on its own: it keeps placeholder results out of the database until this feature ships (K13).
- Fixture identity comes from the natural key (later `id_partido`), never from the file name (G15).
- Consolidation must re-point SCHEDULED matches like PLAYED ones; add a consolidation test with a scheduled match (R12, K9).
- Follow the repository `AGENTS.md` and the nearest module `AGENTS.md`; keep module boundaries (R10) and source-scoped lookups (R11).

# Notes
- Source: task T6 of [docs/analysis/analysis-incremental-actas-for-current-jornada-import.md](../../docs/analysis/analysis-incremental-actas-for-current-jornada-import.md), priority tier P0 (registry priority `high`), size M, delivery slice 2 (incremental import).
- Plan dependencies: FEAT-00076 (T1), FEAT-00079 (T4), FEAT-00080 (T5).
- Plan references: section 4.3, G1, G4, G14, G15, requirements R1-R4, R6, R9, risks K13, K15.
