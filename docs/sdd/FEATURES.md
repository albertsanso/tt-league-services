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

- [FEAT-00024: System settings](### [FEAT-00024] System settings)

## In Progress

No features currently in progress.

---

## In Review

No features currently in review.

---

## Backlog

### [FEAT-00024] System settings
- **Status:** in-review
- **Priority:** medium
- **Effort:** medium (2–8h)
- **Depends on:** —

#### Goal
Provide a central place where administrators can search, filter, create, update, and delete system settings using the same management experience as Users and roles.

#### Acceptance Criteria
- [x] The administrator route exposes a responsive System settings screen with a title, description, and a right-aligned Create Setting action on its own row, matching Users and roles.
- [x] Administrators can search by setting name/value context and filter by the supported `GENERAL`, `IMPORT`, and `NOTIFICATIONS` categories and change status.
- [x] Setting results are grouped by category and rendered as compact padded cards with the setting name/details, editable value control, and Save/Delete actions aligned on one row.
- [x] Administrators can create a Setting using only the entity fields `name`, `category`, and `value`; submission validates automatically and reports server errors without a separate validation action.
- [x] Administrators can edit setting values and delete settings only after confirming the operation; successful mutations refresh the results and show translated feedback.
- [x] Settings responses are normalized at the API boundary without introducing a `key` property; entity identity uses the backend `name` property.
- [x] The supported category list is aligned with the `SettingCategory` domain enum and does not expose the removed UI or DISPLAY categories.
- [x] The screen exposes explicit loading, empty, unauthorized/forbidden, server-error, conflict, success, and responsive states.
- [x] Catalan, Spanish, and English labels are provided for the screen, filters, creation flow, mutation feedback, and delete confirmation.
- [x] The route and settings API require administrator authorization.

#### Feature Details
→ See [FEAT-00024-DETAILS.md](./FEAT-00024-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

## Done
