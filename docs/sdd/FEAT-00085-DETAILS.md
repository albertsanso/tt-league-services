# Build Plan
> Fill this in when status moves to `planned`.

## Acceptance Criteria
- [ ] After a snapshot run, stored SCHEDULED matches of that source and season whose fixture was not seen are reported in the run result and logs.
- [ ] Fixtures are matched as "seen" by natural key (or by `id_partido` for FCTT once persisted), never by file name.
- [ ] Nothing is deleted automatically.
- [ ] Tests cover a removed fixture and a fixture whose file name changed on publication.

# Implementation Guidelines
- Deletion stays a manual, explicit decision (open question 5).
- Follow the repository `AGENTS.md` and the nearest module `AGENTS.md`; keep module boundaries (R10) and source-scoped lookups (R11).

# Notes
- Source: task T10 of [docs/analysis/analysis-incremental-actas-for-current-jornada-import.md](../../docs/analysis/analysis-incremental-actas-for-current-jornada-import.md), priority tier P1 (registry priority `medium`), size S, delivery slice 3 (visibility and safety).
- Plan dependencies: FEAT-00081 (T6).
- Plan references: section 4.7, G15, risk K8, open question 5.
