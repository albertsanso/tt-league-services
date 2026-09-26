# Build Plan
> Fill this in when status moves to `planned`.

## Acceptance Criteria
- [ ] An `ActaCompleteness { PLAYED, PENDING, PARTIAL }` value type and a per-source classifier exist in `tt-data-league-import` (per fixture for BCNESA, after splitting).
- [ ] An acta with `acta_publicada: false` is always PENDING, and its `resultado_final`, `abc_es_local`, `partidos`, and `alineaciones` are not inspected.
- [ ] An acta with `acta_publicada: true` that breaks the published-acta schema rules (no games, or null `abc_es_local`) is reported as an issue and not classified as pending.
- [ ] Content rules (PENDING / PLAYED / PARTIAL, including the `no_disputado` walkover case) apply only when `acta_publicada` is missing, and never promote an acta to PLAYED on `resultado_final` alone.
- [ ] JUnit tests cover both FCTT placeholder shapes (home win 6-0 and all nulls) and assert they classify as PENDING.

# Implementation Guidelines
- Implement the ordered rules of plan section 4.1; the first matching rule wins.
- If RFETM or BCNESA later emit `acta_publicada`, step 1 must apply to them without code changes.
- Classification does not change processor behaviour yet; processors adopt it in the processor-lifecycle feature (T6).
- Follow the repository `AGENTS.md` and the nearest module `AGENTS.md`; keep module boundaries (R10) and source-scoped lookups (R11).

# Notes
- Source: task T1 of [docs/analysis/analysis-incremental-actas-for-current-jornada-import.md](../../docs/analysis/analysis-incremental-actas-for-current-jornada-import.md), priority tier P0 (registry priority `high`), size S, delivery slice 1 (lifecycle foundation).
- Plan dependencies: FEAT-00075 (T0).
- Plan references: section 4.1, G14, risks K2 and K13.
