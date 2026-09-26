# Build Plan
> Fill this in when status moves to `planned`.

## Acceptance Criteria
- [ ] The BCNESA export is measured for files that still contain several fixtures, and the result is recorded in the notes.
- [ ] If no file needs them, the multi-fixture split and licence-based club index are simplified; otherwise they are kept and the reason is documented.
- [ ] Existing BCNESA import tests pass unchanged in behaviour.

# Implementation Guidelines
- The local export (16,387 files, 2020-2021 to 2025-2026) has exactly one fixture per file; javadocs were re-measured on 2026-09-26.
- Follow the repository `AGENTS.md` and the nearest module `AGENTS.md`; keep module boundaries (R10) and source-scoped lookups (R11).

# Notes
- Source: task T15 of [docs/analysis/analysis-incremental-actas-for-current-jornada-import.md](../../docs/analysis/analysis-incremental-actas-for-current-jornada-import.md), priority tier P2 (registry priority `low`), size S, delivery slice 4 (hardening).
- Plan dependencies: FEAT-00075 (T0), FEAT-00081 (T6).
- Plan references: G4, risk K7.
