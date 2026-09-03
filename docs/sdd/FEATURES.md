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
- [FEAT-00025: Administration > Data import design theme](### [FEAT-00025] Administration > Data import design theme)
- [FEAT-00026: Display Data Import left Source/Federation selector with dynamic status](### [FEAT-00026] Display Data Import left Source/Federation selector with dynamic status)

## In Progress

No features currently in progress.

---

## In Review

---

## Backlog

---

## Done

### [FEAT-00026] Display Data Import left Source/Federation selector with dynamic status
- **Status:** done
- **Priority:** medium
- **Effort:** medium (2–8h)
- **Depends on:** —

#### Goal
Give administrators a left-side Source/Federation selector in Data Import that clearly reflects each option's current status.

#### Description
- The selectors are labeled as `@source-selector` must be positioned on the left side of the Data Import interface. There are exactly 3 selectors sources/federations to display: `RFETM`, `BCNESA`, and `FCTT`.
- Each selector should have a clear label and a visual indicator of its current status.
- The status is obtained using a GET request to `/api/v1/administration/import/status`; every source/federation whose related **sourceName** property is present in the successful response envelope is `available` (green).
- There is polling every 5 seconds to update the status of each selector dynamically, without requiring a page reload.
- The selectors design is a box/area where each selector is displayed as a row (big box too) with a label and a star-only status indicator. A filled light yellow-green star (`--color-success-warm`) means available; an unfilled star means loading, unavailable, or error. A later polling error must not replace the last known available statuses.
- The enabled Start/Load action uses the light green success background and border, distinct from the blue primary accent.
- Each complete source/federation card, including its star area, is selectable by clicking and must give access to a more detailed panel in `@seasons-import-list`.
#### Mockup designs
- See [theme-spec.md](docs/frontend/load-import/theme-spec.md)
#### Acceptance Criteria
- [x] The left-side Source/Federation selector is displayed in the Data Import interface with exactly 3 options: `RFETM`, `BCNESA`, and `FCTT`.
- [x] Each selector has a clear label and a visual indicator of its current status.
- [x] The status of each selector is obtained from the `/api/v1/administration/import/status` endpoint and is updated dynamically every 5 seconds without requiring a page reload.
- [x] Every source included in a successful status response is displayed as `available` with the green status indicator, including after a later polling error.
- [x] Status is expressed only through the star's filled/unfilled state; accessible labels remain available without visible `DISPONIBLE`, `ERROR`, or other status text.
- [x] The available star uses the light yellow-green success tone, and the enabled Start/Load action is styled with the light green success treatment.
- [x] Each complete source/federation card, including its star area, is selectable by clicking and gives access to a more detailed panel in `@seasons-import-list`.
#### Feature Details
→ See [FEAT-00026-DETAILS.md](./FEAT-00026-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00025] Administration > Data import design theme
- **Status:** done
- **Priority:** medium
- **Effort:** medium (2–8h)
- **Depends on:** —

#### Goal
Establish a consistent administration design theme for the data import experience.

Analyze the design document in `docs/frontend/load-import/theme-spec.md` and implement the proposed layout, typography, color, spacing, 
and controls for the data import experience. 
Ensure that import states, actions, validation feedback, and progress indicators use the shared theme consistently. 
The themed data import experience should be responsive and accessible across supported administration viewports.
Additionally, ensure that Catalan, Spanish, and English data import labels and messages remain visually consistent with the administration theme.

#### Acceptance Criteria
- [x] The data import experience uses the shared administration design theme for layout, typography, color, spacing, and controls.
- [x] Import states, actions, validation feedback, and progress indicators are visually consistent with the administration theme.
- [x] The themed data import experience is responsive and accessible across supported administration viewports.
- [x] Catalan, Spanish, and English data import labels and messages are visually consistent with the administration theme.
- [x] The plan takes into account the existing design system in `docs/frontend/load-import/theme-spec.md` and any necessary adjustments to ensure a cohesive user experience.

#### Feature Details
→ See [FEAT-00025-DETAILS.md](./FEAT-00025-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00024] System settings
- **Status:** done
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
