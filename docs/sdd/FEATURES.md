 dev# FEATURES.md — Feature Registry & Build Plans

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

- [FEAT-00053: Link list of club players to Player details](### [FEAT-00053] Link list of club players to Player details)


## In Progress

No features currently in progress.
## In Review

No features currently in review.
## Backlog

No features currently in the backlog.
## Done

### [FEAT-00053] Link list of club players to Player details
- **Status:** done
- **Priority:** medium
- **Effort:** medium
- **Depends on:** —

#### Goal
Let a user viewing a club's players list click a player to open that player's details page, matching the linking pattern already used in the player search results.

#### Acceptance Criteria
- [x] Each player in the club detail Players panel that has a canonicalPlayerId is rendered as a link to the player details page (matching PlayersSearchPage's linking pattern), carrying source/season context as return search params
- [x] Players without a canonicalPlayerId (unmatched/unconsolidated players) render as before, without a link
- [x] Existing club players list tests are updated/extended to cover the new link behavior

#### Feature Details
→ See [FEAT-00053-DETAILS.md](./FEAT-00053-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

---

---
