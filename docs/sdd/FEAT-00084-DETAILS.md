# Build Plan
> Fill this in when status moves to `planned`.

## Acceptance Criteria
- [ ] A payload `jornada` that is missing or disagrees with the RFETM day folder is detected and reported.
- [ ] Before creating a match, the processor looks for the same teams, competition, and season in another round and raises an issue instead of creating a duplicate.
- [ ] Once the source fixture id is persisted (FCTT `id_partido`), a stored match with the same fixture id but a different natural key is treated as the duplicate signal.
- [ ] Tests cover the drift and the duplicate-prevention cases.

# Implementation Guidelines
- Refined by the source fixture id feature (T18) for FCTT; the generic guard does not depend on it.
- Follow the repository `AGENTS.md` and the nearest module `AGENTS.md`; keep module boundaries (R10) and source-scoped lookups (R11).

# Notes
- Source: task T9 of [docs/analysis/analysis-incremental-actas-for-current-jornada-import.md](../../docs/analysis/analysis-incremental-actas-for-current-jornada-import.md), priority tier P1 (registry priority `medium`), size S, delivery slice 3 (visibility and safety).
- Plan dependencies: FEAT-00081 (T6).
- Plan references: G8, G16, risk K4.
