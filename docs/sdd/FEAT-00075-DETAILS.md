# Build Plan
> Fill this in when status moves to `planned`.

## Acceptance Criteria
- [ ] Current-season (2026-2027) RFETM, FCTT, and BCNESA actas are collected for the pending, partial, played, walkover, and rescheduled cases where the source provides them, and the gaps are recorded.
- [ ] The payload shape of each case (`partidos`, `resultado_final`, `jornada`, `fecha`, `lugar`, `equipos`) is recorded in this feature's notes.
- [ ] Anonymised JUnit fixtures are added under the import module test resources, including an FCTT all-null 2026-2027 placeholder acta and an FCTT published/unpublished pair sharing the same `id_partido`.
- [ ] The content of an empty (pending) BCNESA matchday acta is confirmed, or its absence is documented as an open question (G4, K7).

# Implementation Guidelines
- Test resources only; no production code changes.
- Anonymise player names and licences in every committed fixture; never commit raw federation exports.
- FCTT is partly done (plan section 2.5): the export is measured and `acta_fctt_unpublished.json` already exists.
- Follow the repository `AGENTS.md` and the nearest module `AGENTS.md`; keep module boundaries (R10) and source-scoped lookups (R11).

# Notes
- Source: task T0 of [docs/analysis/analysis-incremental-actas-for-current-jornada-import.md](../../docs/analysis/analysis-incremental-actas-for-current-jornada-import.md), priority tier P0 (registry priority `high`), size S, delivery slice 1 (lifecycle foundation).
- Plan dependencies: none.
- Plan references: G4, G14, section 2.5, open questions 2 and 6, risks K2 and K7.
