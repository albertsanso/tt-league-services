# Build Plan

> Draft outline.

1. `tt-league-pipeline-orchestrator-frontend`: runs page, run detail drawer, Run now dialog using the shared client and SSE hook.
2. Tests.

## Acceptance Criteria

- [ ] A runs table (newest first) shows trigger, scope, duration, step badges and outcome, with filters by source, status and date
- [ ] A run detail shows steps, issues, artifacts and the import report, and updates live while the run is active
- [ ] A Run now dialog (source or all, scope type, force) creates a manual run and shows the 409 message when one is active
- [ ] Tests cover table rendering, live updates and the dialog

# Implementation Guidelines

Follow the root and module `AGENTS.md` files.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal UI view 3.
