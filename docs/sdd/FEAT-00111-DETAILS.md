# Build Plan

> Draft outline.

1. `tt-league-pipeline-orchestrator-frontend`: calendar page (FullCalendar React, as in `tt-data-league-frontend`, or MUI-based) and match-day detail.
2. Tracker read endpoints in `tt-league-pipeline-orchestrator-runtime` if not already delivered by the tracker item.
3. Tests.

## Acceptance Criteria

- [ ] A month/week calendar shows one entry per match day and group, filterable by source, season, category and phase
- [ ] Entries are coloured by completion (all reported, in progress, has overdue, future) and show `reported / total`
- [ ] The match-day detail lists matches with status, result and reported-at, and a timeline of the runs that touched it
- [ ] Operators can refresh just that group, close the match day, mark a match ignored and add a note
- [ ] Tests cover colouring rules, filters and each action

# Implementation Guidelines

- The platform matches calendar (FEAT-00092/93) shows matches; this view shows pipeline completion. Do not duplicate overdue rules in the UI.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal UI views 1 and 2.
