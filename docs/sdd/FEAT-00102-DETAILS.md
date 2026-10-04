# Build Plan

> Draft outline.

1. Extend the `RoundProgress` read model in `tt-data-league-core-domain` with derived counts and date bounds
   (reuse the FEAT-00092 derivation and grace-period setting).
2. JPA query in `tt-data-league-core-repository-jpa` if the bounds need it; document the query behaviour.
3. Query handler and `MatchController` endpoint; DTO and OpenAPI docs.
4. Tests; README/API docs.

## Acceptance Criteria

- [ ] `GET /api/v1/match/round-progress?source=&season=` returns, per competition/group/phase and jornada, the counts of scheduled, played, derived postponed and overdue matches, and the first and last scheduled dates
- [ ] Counts reuse `MatchRepository.findRoundProgress` and the calendar's derived postponed/overdue rules; no state is stored
- [ ] Optional `competition` and `onlyOpen=true` filters narrow the result; `onlyOpen` keeps jornadas with any non-played match or a window overlapping today plus the grace period
- [ ] The endpoint needs `matches:read` and is available to the service credential
- [ ] Domain, JPA and controller tests cover the FCTT 2026-2027 shape and an overdue match

# Implementation Guidelines

- Read-only. The domain stays the single source of truth for "reported"; the orchestrator never re-derives statuses from raw tables.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Builds on FEAT-00084 (round progress) and FEAT-00092 (calendar, derived states, 7-day grace).
