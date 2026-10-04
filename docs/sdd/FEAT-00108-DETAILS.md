# Build Plan

> Draft outline.

1. `tt-league-pipeline-orchestrator-core`: `PollingPolicy`, `ScopeBuilder`, `PollSchedule`.
2. `tt-league-pipeline-orchestrator-runtime`: Flyway migration, a scheduler tick that asks the policy which scopes are due (replacing per-source cron
   when adaptive mode is enabled), settings API restricted to `ADMIN`.
3. Tests; README.

## Acceptance Criteria

- [ ] A scope builder turns a source's open match days into ingest `scopes` (territory/category/group/phase/match days)
- [ ] `poll_schedule` stores the next run, interval, consecutive no-change count and policy level per source and scope hash
- [ ] Poll intervals follow the proposal's policy table (configurable per source), double after 3 consecutive `NO_CHANGES` up to the next level, and stop with an alert for matches overdue beyond 21 days
- [ ] A weekly full-scope run (and one at season start) refreshes fixtures, phases and re-draws
- [ ] Admins can change the policy settings through an API; tests cover each policy level and the back-off

# Implementation Guidelines

- Respect federation sites: never shorten the ingest delays; scoped runs are the way to poll more often.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal "Adaptive polling" and "Scope building". Rollout phase 3 exit: runs stop by themselves when a match day is complete.
