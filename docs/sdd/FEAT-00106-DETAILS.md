# Build Plan

> Draft outline.

1. `tt-league-pipeline-orchestrator-runtime`: `@Scheduled` per-source tick using the configured cron; ShedLock with a Flyway migration for its table.
2. Reuse the `TriggerRun` use case; tests; README.

## Acceptance Criteria

- [ ] Each source has an explicitly configured cron expression; a source without one is never scheduled, and an invalid expression fails startup
- [ ] Scheduled ticks create `SCHEDULED` runs through the same trigger path as manual runs and skip a source with an active run
- [ ] ShedLock (JDBC, `pipeline` schema) ensures only one orchestrator instance fires a tick
- [ ] Tests cover tick handling, skip-when-active and lock behaviour; README documents the configuration

# Implementation Guidelines

- Scheduling is opt-in per source; no default schedule (root `AGENTS.md`: no silent defaults).

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal rollout phase 2 exit: no manual uploads needed. Replaced in practice by adaptive polling once that item lands; keep it as the fallback mode.
