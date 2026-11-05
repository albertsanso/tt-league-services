# FEATURES.md — Feature Registry & Build Plans

This file is the single source of truth for planned, in-progress, and completed features.

**For humans:** Add new features under `## Backlog` using the template in [`task-management.md`](./task-management.md).
**For agents:** Only work on features marked `status: ready`. Update status as you progress. Never modify features marked `status: done` or `status: in-progress` unless explicitly asked.

---

## Status Legend

| Status | Meaning |
|-|-|
| `idea` | Captured but not planned yet — no build plan written |
| `planned` | Build plan written, not yet ready to implement |
| `ready` | Build plan approved, agent can start |
| `in-progress` | Currently being implemented |
| `in-review` | Implementation finalized and awaiting user review |
| `done` | Shipped after explicit user approval |
| `blocked` | Waiting on a dependency or decision |

---

## Main index

- [FEAT-00119: tt-league client app - allow all sources for current season as default filters in calendar view](#feat-00119-tt-league-client-app---allow-all-sources-for-current-season-as-default-filters-in-calendar-view)




## In Progress

No features currently in progress.
## In Review

No features currently in review.
## Backlog

No features currently in the backlog.
## Done

### [FEAT-00119] tt-league client app - allow all sources for current season as default filters in calendar view
- **Status:** done
- **Priority:** medium
- **Effort:** small
- **Depends on:** —

#### Goal
Calendar view opens with all sources selected for the current season by default, so users see the full current-season calendar without first choosing a source.

#### Acceptance Criteria
- [x] Opening the calendar view without source/season URL params pre-selects the current season with all sources included
- [x] The default all-sources selection aggregates matches from RFETM, FCTT and BCNESA for that season
- [x] Users can still narrow the calendar to a single source, and the season/competition/group/team filters keep working
- [x] Choosing an explicit source in the URL overrides the default
- [x] Filter labels and the all-sources option remain translated in ca, es and en

#### Feature Details
→ See [FEAT-00119-DETAILS.md](./FEAT-00119-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---
