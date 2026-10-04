# Build Plan

> Draft outline.

1. `tt-league-pipeline-orchestrator-core`: `MatchDay`, `MatchTracking`, tracker rules and `PlatformMatchGateway` port.
2. `tt-league-pipeline-orchestrator-runtime`: Flyway migration (`match_day`, `match_tracking`), adapters calling `round-progress` and
   `calendar/range` with the service credential, recompute hook after run completion, REST endpoints for the
   manual actions.
3. Tests; datamodel document.

## Acceptance Criteria

- [ ] After every final run state and on a periodic recompute, the tracker reads round progress and calendar data from the platform and upserts `match_day` and `match_tracking` rows
- [ ] Match statuses `SCHEDULED`, `AWAITING_RESULT`, `REPORTED`, `POSTPONED` and `OVERDUE` follow the platform's derived states and grace period; `reported_at` records the first run that saw the result
- [ ] A match day closes when every match is reported, ignored or postponed out of its window; several match days can be open at once
- [ ] Operators with `matches:write` can close a match day manually, mark a match ignored and add a note, and each action records who and when
- [ ] Tests cover window calculation, postponed matches, closing rules and manual actions

# Implementation Guidelines

- Never store results; the platform owns them. Tracking rows hold operational state only.
- `CANCELLED`/`WALKOVER` are out of scope until the domain supports them (open question in the baseline item).

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal "Match-day detection and pending tracking". Domain events are deferred: the orchestrator recomputes after each run it drives.
