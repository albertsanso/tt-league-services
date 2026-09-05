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
- [FEAT-00027: Integrate upload endpoint with frontend file upload component](### [FEAT-00027] Integrate upload endpoint with frontend file upload component)
- [FEAT-00028: Display list of imports resources for a given source/federation in Data Import Panel](### [FEAT-00028] Display list of imports resources for a given source/federation in Data Import Panel)
- [FEAT-00029: Import resource preview process](### [FEAT-00029] Import resource preview process)
- [FEAT-00030: Import resource process](### [FEAT-00030] Import resource process)
- [FEAT-00031: import process from endpoint performance improvement and implementation alignemt with CLI version](### [FEAT-00031] import process from endpoint performance improvement and implementation alignemt with CLI version)

## In Progress

### [FEAT-00031] import process from endpoint performance improvement and implementation alignemt with CLI version
- **Status:** in-progress
- **Priority:** medium
- **Effort:** large (> 8h)
- **Depends on:** FEAT-00030

#### Goal
Improve the import process initiated from the endpoint and align its implementation and behavior with the CLI version.

#### Description
The import process performs much better from the CLI operation in tt-data-league-import-runtime than the import endpoint.

The endpoint is slower/different mainly because it is not executing the same import pipeline as the CLI:
- The CLI calls the navigator directly with its normal injected processor list.
- The endpoint uses NavigatorBackedImportResourceProcessService, which rebuilds processor lists and wraps every processor in recording decorators. This adds per-processor exception handling and error collection on every ACTAS.
- The endpoint always calls traverseSeason(...); the CLI can call traverse(...) and uses the optimized all-season traversal path.
- The endpoint is synchronous inside an HTTP request and returns a detailed result, while the CLI only logs the traversal summary.
- The CLI optionally performs club/team and player consolidation after traversal. The endpoint never does this, so its resulting data can appear materially worse even if traversal time is comparable.
- Both flows perform many per-record database lookups and writes; the endpoint currently has no run-level caching, batching, transaction strategy, or post-processing phase.

Recommended refactor
Extract a shared ImportExecutionService in tt-data-league-import.

It should own:
1. Source-specific navigator selection.
2. Processor selection and ordering.
3. Traversal and structured metrics.
4. A post-traversal phase.
5. Optional consolidation through explicit policies.
6. A single execution result model used by both CLI and API.

Then:
- Make the CLI App a thin adapter that converts CLI arguments into ImportExecutionOptions.
- Make the API process service call the same executor rather than duplicating navigator orchestration.
- Replace recording decorators with navigator-level structured error reporting, keeping the hot path free of
  wrapper try/catch logic.
- Add run-scoped caches for team/player resolution and batch persistence where repository contracts allow it.
- Add a post-processing policy. For the endpoint, consolidation should be server-configured and source-scoped,
  not controlled by arbitrary client paths.
- Preserve safe defaults: no consolidation unless explicitly enabled, and only mark the import successful after traversal and requested post-processing both succeed.

Before implementing, benchmark both paths against the same source, folder, season, database, and consolidation
settings. This will separate the likely throughput issue from the larger behavioral difference caused by CLI-only
consolidation.

#### Acceptance Criteria
- [ ] CLI and API imports invoke the same `ImportExecutionService` and therefore use identical source dispatch, processor order, season filtering, traversal failure rules, and post-processing order.
- [ ] The shared execution result reports normalized traversal metrics, elapsed time, structured processor issues, persistence counters, and requested post-processing outcomes; the CLI logs it and the endpoint exposes a backward-compatible mapping.
- [ ] Every execution owns source/season-scoped team, player-season, and match caches, and supported repository writes are flushed in bounded batches without weakening identity, idempotency, or processor-failure isolation.
- [ ] Club and player consolidation remain independently opt-in, run only after successful traversal in club-then-player order, use the complete source-scoped inventory, and make the overall import fail when a requested phase fails.
- [ ] The endpoint accepts only the stored import-resource identity/path and server-side consolidation configuration; defaults perform no consolidation and no cache state is shared between requests.
- [ ] A documented like-for-like benchmark (same source, folder, season, restored database, JVM, and consolidation settings) shows the API shared-executor median within 10% of the CLI median across at least three measured runs, with no behavior or persisted-data regression.
- [ ] Focused domain, import, navigator, JPA adapter, CLI, REST, and API-runtime wiring tests pass, followed by the full Maven reactor; frontend checks pass if the response contract changes.

#### Feature Details
→ See [FEAT-00031-DETAILS.md](./FEAT-00031-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

## In Review

No features currently in review.

---

## Backlog

---

## Done

### [FEAT-00030] Import resource process
- **Status:** done
- **Priority:** medium
- **Effort:** medium (2–8h)
- **Depends on:** FEAT-00029

#### Goal
Allow administrators to start the import of a selected resource and monitor its processing result.

#### Description
1. Analyze the `tt-data-league-import` and `tt-data-league-import-runtime` modules and how the backend implements import processing, including validation, error handling, and result generation.
2. Wire the existing `StartImportCommandHandler`, reusing current `tt-data-league-import` processing logic to start the import for the selected resource.
3. The endpoint `/api/v1/administration/import/start` must start the import for the selected resource and return the import status, including loading, success, empty-result, and failure states.
4. The frontend Data Import Panel currently provides an **Import** action for each resource card; it must trigger the import process and display the result in a dedicated import workspace.
5. The import workspace must present validation findings and processing errors accessibly, with translated copy in Catalan, Spanish, and English.
6. Administrators must be able to retry a failed import or proceed from a successful import to the next action without losing the selected resource context.
7. The import process must use the authenticated backend contract and not start a different or unscoped resource.

#### Acceptance Criteria
- [x] The **Import** action triggers the import process for the selected import resource and displays the result in a dedicated import workspace.
- [x] The import workspace presents the validation findings and processing errors accessibly, with translated copy in Catalan, Spanish, and English.
- [x] Administrators can retry a failed import or proceed from a successful import to the next action without losing the selected resource context.
- [x] The import process uses the authenticated backend contract and does not start a different or unscoped resource.
- [x] The endpoint `/api/v1/administration/import/start` provides the import status for the selected import resource.

#### Feature Details
→ See [FEAT-00030-DETAILS.md](./FEAT-00030-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00029] Import resource preview process
- **Status:** done
- **Priority:** medium
- **Effort:** medium (2–8h)
- **Depends on:** FEAT-00028

#### Goal
Allow administrators to preview an import resource and review its processing result before starting the import.

#### Description
1. Analyze the `tt-data-league-import` and `tt-data-league-import-runtime` modules and how the backend implements import processing, including validation, error handling, and result generation.
2. Wire the existing `StartImportPreviewCommandHandler`, reusing current `tt-data-league-import` processing logic to generate a preview result for the selected resource.
3. The endpoint `/api/v1/administration/import/preview_status` must provide the preview status for the selected import resource, including loading, success, empty-result, and failure states.
4. The frontend Data Import Panel currently provides a **Simulate** action for each resource card; it must trigger the preview process and display the result in a dedicated preview workspace.
5. The preview workspace must present validation findings and processing errors accessibly, with translated copy in Catalan, Spanish, and English.
6. Administrators must be able to retry a failed preview or proceed from a successful preview to the import action without losing the selected resource context.
7. The preview process must not import the resource; it is only a simulation to review the processing result.

#### Acceptance Criteria
- [x] The **Simulate** action triggers the preview process for the selected import resource and displays the result in a dedicated preview workspace.
- [x] The preview workspace presents the validation findings and processing errors accessibly, with translated copy in Catalan, Spanish, and English.
- [x] Administrators can retry a failed preview or proceed from a successful preview to the import action without losing the selected resource context.
- [x] The preview process does not import the resource; it is only a simulation to review the processing result.
- [x] The endpoint `/api/v1/administration/import/preview_status` provides the preview status for the selected import resource.

#### Feature Details
→ See [FEAT-00029-DETAILS.md](./FEAT-00029-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00028] Display list of imports resources for a given source/federation in Data Import Panel
- **Status:** done
- **Priority:** medium
- **Effort:** medium (2–8h)
- **Depends on:** FEAT-00026, FEAT-00027

#### Goal
Give administrators a clear, source-scoped list of uploaded import resources in the Data Import Panel so they can identify the resources available for the selected federation and act on the correct one.

#### Mockup designs
- See [theme-spec.md](docs/frontend/load-import/theme-spec.md) for the Data Import Panel design and visual language.

#### Description
1. The implementation is focused in Frontend, specifically in the Data Import Panel, which is a central area of the administration interface where users can manage import resources.
2. The Data Import Panel must display a source-scoped list of import resources, showing each resource's resource type, season, upload timestamp formatted as `YYYY-MM-DD HH:mm`, and processing status.
3. The existing endpoint to get the import resources information is `/api/v1/administration/import/list_by_source?source=<source>`.
4. The design of the resource cards must follow the existing import-panel visual language, including accessible labels and translated copy in Catalan, Spanish, and English.
5. The list must support loading, empty, error, and retry states without losing the selected source/federation.
6. The resource list must refresh after a successful upload and when the selected source/federation changes, without a full page reload.
7. Resource cards emphasize the resource type and season, while omitting the raw identifier and processed-date fields from the card presentation; **Simulate** and **Import** actions are aligned to the right in a horizontal layout.

#### Acceptance Criteria
- [x] Selecting a source/federation displays only its import resources in the Data Import Panel.
- [x] Each resource is presented with its filename or identifier, upload timestamp, and processing status using the existing import-panel visual language.
- [x] The list supports loading, empty, error, and retry states without losing the selected source/federation.
- [x] The resource list refreshes after a successful upload and when the selected source/federation changes, without a full page reload.
- [x] Resource data is requested through an authenticated API contract scoped explicitly to the selected source/federation, with accessible labels and translated copy in Catalan, Spanish, and English.

#### Feature Details
→ See [FEAT-00028-DETAILS.md](./FEAT-00028-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00027] Integrate upload endpoint with frontend file upload component
- **Status:** done
- **Priority:** medium
- **Effort:** medium (2–8h)
- **Depends on:** —

#### Goal
Connect the frontend file upload component to the backend upload endpoint so users can submit import files from the Data Import interface.

#### Description
- The file upload component **@file-browser** must send the selected file when clicking **@load-button** to the configured upload endpoint using the backend's multipart contract `/api/v1/administration/import/upload`.
- The component must present upload progress, success, validation, and failure states accessibly in the frontend.
- Successful uploads must refresh or expose the resulting import state without requiring a page reload.
- The component must prevent invalid submissions and preserve the existing source/season selection context.

#### Acceptance Criteria
- [x] The file upload component sends the selected file to `/api/v1/administration/import/upload` using a multipart `file` field.
- [x] Upload progress, success, validation, failure, and retry states are presented accessibly in the frontend.
- [x] Successful uploads refresh import source status and history without requiring a page reload.
- [x] The component rejects empty or non-ZIP files before submission and preserves source/season selection context.

#### Feature Details
→ See [FEAT-00027-DETAILS.md](./FEAT-00027-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

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
