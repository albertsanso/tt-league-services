# Build Plan
> Fill this in when status moves to `planned`.

## Acceptance Criteria
- [ ] `MatchRepository.replaceMatchContent(Match, List<Lineup>, List<Game>, List<SetScore>, List<DoublesPair>)` deletes the match's existing children, updates the header, and inserts the new children in one transaction, preserving the match UUID.
- [ ] `MatchRepository.updateSchedule(UUID, ZonedDateTime, String city, String venue)` exists, or reuse of `saveMatch` with `createExisting` is verified to merge by id and documented.
- [ ] JPA and import-module in-memory implementations exist for both.
- [ ] JPA rollback tests prove a failed upgrade leaves no half-written match.

# Implementation Guidelines
- Keep the port in the domain; the JPA adapter owns the transaction.
- No broad catches; a failed upgrade is a processor failure.
- Follow the repository `AGENTS.md` and the nearest module `AGENTS.md`; keep module boundaries (R10) and source-scoped lookups (R11).

# Notes
- Source: task T5 of [docs/analysis/analysis-incremental-actas-for-current-jornada-import.md](../../docs/analysis/analysis-incremental-actas-for-current-jornada-import.md), priority tier P0 (registry priority `high`), size M, delivery slice 2 (incremental import).
- Plan dependencies: FEAT-00077 (T2).
- Plan references: section 4.4, G7, risk K3.
