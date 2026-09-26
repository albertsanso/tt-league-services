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

- [FEAT-00073: Manual consolidation action stores historic actions](### [FEAT-00073] Manual consolidation action stores historic actions)

## In Progress

No features currently in progress.
## In Review

### [FEAT-00073] Manual consolidation action stores historic actions
- **Status:** in-review
- **Priority:** medium
- **Effort:** medium
- **Depends on:** —

#### Goal
When a user performs a manual consolidation action (e.g., merging two clubs), the system should store a record of this action in a historical log. 
This log will allow users to review past consolidation actions, understand the changes made, and provide an audit trail for accountability.

#### Domain entities: `ConsolidationAction`
New domain entities will be introduced to represent consolidation actions, including details such as:
- User who performed the action
- List of source club names and IDs
- List of target club names and IDs (will be unique after consolidation)
- Timestamp of the action
- Type of action (e.g., merge, split, rename)

#### Description
After a consolidation action is performed, the system will create a new record in `ConsolidationAction` with all relevant details. 
This record will be stored in a dedicated database table.
The trigger of this action is backend event-based, and the record will be created automatically without requiring additional user input. Then:
- Consolidation operation will publish an event to the backend, which will create a new `ConsolidationAction` record with all relevant details.

#### Acceptance Criteria
- [x] When a user performs a manual consolidation action, a new `ConsolidationAction` record is created in the database with all relevant details.
- [x] The `ConsolidationAction` record includes the user ID, source and target club names/IDs, timestamp, and action type.

#### Feature Details
→ See [FEAT-00073-DETAILS.md](./FEAT-00073-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

---

---

---

---
## Backlog

No features currently in the backlog.
## Done

No features currently in the backlog.
