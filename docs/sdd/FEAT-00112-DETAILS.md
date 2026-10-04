# Build Plan

> Draft outline. Pick the channel (open question) before planning.

## Acceptance Criteria

- [ ] A `Notifier` port has one adapter for the chosen channel, configured from the environment and disabled when unconfigured
- [ ] Alerts fire for: match day closed, two consecutive failed runs for a source, a match unreported past a configured threshold, and no successful run in 24 h during an open match day
- [ ] Each alert is sent once per condition until it clears; tests use a fake notifier

# Implementation Guidelines

- Never include credentials or tokens in messages.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal "Notifications" and "Operational observability" alerts. Blocked on the channel decision.
