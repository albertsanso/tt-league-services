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

- [FEAT-00041: Clubs consolidation on Club search results](### [FEAT-00041] Clubs consolidation on Club search results)

- [FEAT-00040: Import process for BCNESA with phases](### [FEAT-00040] Import process for BCNESA with phases)
- [FEAT-00039: Add Phase property into league matches](### [FEAT-00039] Add Phase property into league matches)
- [FEAT-00038: In Player details, Matches Tab, show the oponent as the Player name instead of theTeam name](### [FEAT-00038] In Player details, Matches Tab, show the oponent as the Player name instead of theTeam name)
- [FEAT-00024: System settings](### [FEAT-00024] System settings)
- [FEAT-00025: Administration > Data import design theme](### [FEAT-00025] Administration > Data import design theme)
- [FEAT-00026: Display Data Import left Source/Federation selector with dynamic status](### [FEAT-00026] Display Data Import left Source/Federation selector with dynamic status)
- [FEAT-00027: Integrate upload endpoint with frontend file upload component](### [FEAT-00027] Integrate upload endpoint with frontend file upload component)
- [FEAT-00028: Display list of imports resources for a given source/federation in Data Import Panel](### [FEAT-00028] Display list of imports resources for a given source/federation in Data Import Panel)
- [FEAT-00029: Import resource preview process](### [FEAT-00029] Import resource preview process)
- [FEAT-00030: Import resource process](### [FEAT-00030] Import resource process)
- [FEAT-00031: import process from endpoint performance improvement and implementation alignemt with CLI version](### [FEAT-00031] import process from endpoint performance improvement and implementation alignemt with CLI version)
- [FEAT-00032: asynchronous import process with progress feedback in the UI](### [FEAT-00032] asynchronous import process with progress feedback in the UI)
- [FEAT-00033: default system import folder setting](### [FEAT-00033] default system import folder setting)
- [FEAT-00034: Fixes pack 1](### [FEAT-00034] Fixes pack 1)
- [FEAT-00035: Some played matches dont appear for a season while percentage exists](### [FEAT-00035] Some played matches dont appear for a season while percentage exists)
- [FEAT-00036: Component analysis sorting players criteria](### [FEAT-00036] Component analysis sorting players criteria)
- [FEAT-00037: Enable Players to be searched by license id added to the name](### [FEAT-00037] Enable Players to be searched by license id added to the name)

## In Progress

No features currently in progress.
## In Review

### [FEAT-00041] Clubs consolidation action on Club search results
- **Status:** in-review
- **Priority:** medium
- **Effort:** medium
- **Depends on:** —

#### Goal
The Administration > Clubs search results must provide a **Consolidate** action for consolidating all the selected clubs into a single club, allowing administrators to merge duplicate or related club records.

#### Description
- The Club search results are selectable, and the **Consolidate** action is enabled only when at least two clubs are selected.
- When the **Consolidate** action is triggered, the user is prompted with a dialog allowing to enter a Canonical Name for the consolidated club and select which of the selected clubs will be the primary club to retain its identity.
- The consolidation process merges the selected clubs into a single club, preserving the underlying teams, player-season, and match history references.
- If the consolidation process provoques orphan Club records, the system must handle them gracefully, either by deleting them or reassigning them to the consolidated club.

#### Acceptance Criteria
- [ ] The Club search results provide a **Consolidate** action that is enabled only when at least two clubs are selected.
- [ ] When the **Consolidate** action is triggered, a dialog prompts the user to enter a Canonical Name for the consolidated club and select the primary club to retain its identity.
- [ ] The consolidation process merges the selected clubs into a single club, preserving the underlying teams, player-season, and match history references.
- [ ] The system handles orphan Club records gracefully, either by deleting them or reassigning them to the consolidated club.

#### Feature Details
→ See [FEAT-00041-DETAILS.md](./FEAT-00041-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

---

---

---

---
## Backlog

No features currently in the backlog.
## Done

### [FEAT-00040] Import process for BCNESA with phases
- **Status:** done
- **Priority:** medium
- **Effort:** medium
- **Depends on:** FEAT-00039

# Summary

The bcnesa import process must support phases for matches.
This means that when importing data, the system should be able to handle different stages or phases of the import process,
allowing for better organization and management of the data being imported.

#### Goal
Wire the BCNESA import process to populate the match phase property from source data, and make phase part of match identity so fixtures
from different phases with the same round number are not skipped or collided as duplicates.

# Description

The new unzipped folder structure is as follows:
```
/
├── actas-json/
│   └── <season>/
│       └── <Competition>/
│           └── <Group>/
│               └── <Phase>/
```

Where for Veterans, the `<Phase>` folder can be "1a Fase" or "Other".
In case of "Other", the parser should not assume that only that one phase exists. The value "Other" means that the parse value for Group is `null`.
In case of "Other" phases, the file name format is `acta_<number>_page_<*>.pdf`, where `<number>` is the "jornada" property number for that phase.
Possible values for phases when "Other" is used are "Play Off", "ASCENS", "DESCENS", "Finals", etc.
The "fase" field should be populated based on the folder structure, specifically from the `<Phase>` folder name.

**Correction (2026-09-08, confirmed by production bug report):** the folder that is literally named
`Other` is `<Group>`, not `<Phase>`. Under `<Competition>/Other/`, the `<Phase>` subfolders are named
"Play Off", "ASCENS", "DESCENS", "Finals", etc. (there can be several, sitting side by side). It is
`<Group>=Other` that means the parsed Group value is `null` for every fixture under it, regardless of
which of those phases it belongs to.

# Acceptance Criteria
- [x] this modification on import process focuses only in BCNESA Veterans competitions.
- [x] The import process should correctly identify and handle different phases of matches based on the folder structure. Only for Veterans competitions.
- [x] The "fase" field should be correctly populated in the imported data based on the `<Phase>` folder name.
- [x] The import process should be able to handle cases where the `<Group>` folder is named "Other" and correctly interpret the phase information from the `<Phase>` subfolders and, when needed, the file names.
- [x] The import process should correctly handle multiple phases within the "Other" group.
- [x] The import process should correctly handle cases where the `<Phase>` folder is named "1a Fase".
- [x] The BCNESA import pipeline sets Match.phase from the phase already parsed into BcnesaMatchReportContext for every imported fixture.
- [x] The BCNESA match natural-key lookup and unique constraint distinguish fixtures that share competition, season, group, and round but belong to different phases, so no fixture is skipped or rejected as a false duplicate.
- [x] Existing BCNESA matches already imported without a phase are left unaffected (no forced backfill) while newly imported/re-imported fixtures carry the correct phase.
- [x] Focused regression coverage in tt-data-league-import (and tt-data-league-core-repository-jpa if the natural key changes) verifies phase is persisted, that same-round fixtures across different phases are both imported, and that fixtures under a Veterans `<Group>=Other` folder (any of Play Off/ASCENS/DESCENS/Finals) are actually ingested rather than skipped.

#### Feature Details
→ See [FEAT-00040-DETAILS.md](./FEAT-00040-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

**Fixed (2026-09-08):** production reported that actas under `<Group>=Other` with `<Phase>` in
{Play Off, ASCENS, DESCENS} were not being ingested at all. Part B had keyed its "Other" detection off
the `<Phase>` folder name instead of `<Group>`, so the pre-existing `G\d+`-only group-folder filter
silently skipped the whole `Other` group before phase was ever considered. Part C re-keys the
detection off the literal `<Group>=Other` folder and accepts it for Veterans competitions; re-running
the import now ingests every previously-skipped fixture with `groupNumber = null` and its correct
`phase`. See `FEAT-00040-DETAILS.md` Part C and Notes for the full change and residual risk (the
Veterans-competition name heuristic is still not verified against a full real export).

**Fixed (2026-09-08):** the Veterans-competition name heuristic flagged as a residual risk above was
confirmed wrong - real BCNESA Veterans competition folders are named `"Vet 1a"`, `"Vet 2a"`, etc.
(starting with `"Vet "`), not containing "veteran" as Part B/C assumed, so Part C's `<Group>=Other`
fix never actually applied to any real competition. Part D widens the Veterans-competition match to
also accept a `"Vet "`-prefixed name. See `FEAT-00040-DETAILS.md` Part D and Notes.

---

### [FEAT-00039] Add Phase property into league matches
- **Status:** done
- **Priority:** medium
- **Effort:** medium
- **Depends on:** —

#### Goal
Record which competition phase (e.g. regular season, playoffs) a league match belongs to.

#### Description
1. The match domain model must expose an `phase` property that can be set during import and persisted for matches.
2. Existing matches without a known phase must have a sensible default or handling
3. The scope of this feature is limited to the domain model and services, jpa/persistence, Rest api and UI/Frontend. It does not include any changes to the import process or data sources, which will be handled in a separate feature.

#### Acceptance Criteria
- [x] The match domain model exposes a `phase` property that can be set during import and persisted for matches.
- [x] Existing matches without a known phase have a sensible default or handling.
- [x] The `phase` property is exposed in the Rest API and UI/Frontend for league matches, allowing users to view and filter matches by phase.
- [x] The implementation does not introduce any performance regressions or data inconsistencies in the match domain model, services, jpa/persistence, Rest api or UI/Frontend.
- [x] Focused regression coverage verifies the correct handling of the `phase` property in all relevant components.
- [x] Users can view and filter league matches by the `phase` property in the UI/Frontend.

#### Feature Details
→ See [FEAT-00039-DETAILS.md](./FEAT-00039-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00038] In Player details, Matches Tab, show the oponent as the Player name instead of the Team name
- **Status:** done
- **Priority:** medium
- **Effort:** small
- **Depends on:** —

#### Goal
Show the opposing player name in the Player details Matches tab instead of the opposing team name, so match records identify the actual opponent.

#### Description
In Player details, Matches Tab, show the oponent as the Player name played against (And optionally the Team name but less emphasys),
and the result of that player to player match instead of the global Team result match.

#### Acceptance Criteria
- [x] The Matches tab renders each game set score followed by its qualitative win/loss/draw result, with qualitative-only fallback when scores are unavailable.
- [x] Win result rows use green styling and loss result rows use red styling without changing opponent or match metadata.
- [x] Games without available opponent information do not render opponent or result sub-rows.
- [x] Opponent names and game-level scores are resolved consistently for all displayed match rows without changing team metadata or other match metadata.
- [x] Focused regression coverage verifies aligned multi-game rows, set-score and qualitative results, outcome colors, and removal of unavailable-opponent rows.
- [x] Match-table cell content is top-aligned so stacked opponent and result rows begin at the same vertical edge.
- [x] The final score column shows only the numeric global match result with green win/red loss styling, and a separate column on its right shows the opponent team name.
- [x] The implementation does not introduce any performance regressions or data inconsistencies in the Player details view.

#### Feature Details
→ See [FEAT-00038-DETAILS.md](./FEAT-00038-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

### [FEAT-00037] Enable Players to be searched by license id added to the name
- **Status:** done
- **Priority:** medium
- **Effort:** small (< 2h)
- **Depends on:** —

#### Goal
Allow users to find a player by searching for the license ID displayed alongside the player's name.

#### Acceptance Criteria
- [x] Player search matches the license ID included in the player's displayed name.
- [x] Searching by player name continues to work as before.
- [x] Search matching is applied consistently wherever players can be searched.

#### Feature Details
→ See [FEAT-00037-DETAILS.md](./FEAT-00037-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00036] Component analysis sorting players criteria
- **Status:** done
- **Priority:** medium
- **Effort:** medium (2–8h)
- **Depends on:** —

#### Goal
In Players opponents analysis in Player details, the sort criteria must be by Victories percentage, then by number of matches played, and finally by player name.

#### Acceptance Criteria
- [x] The categorized Players opponents analysis tables in Player details are sorted first by Victories percentage (descending), then by number of matches played (descending), and finally by player name (ascending).
- [x] The sorting criteria are applied consistently across all seasons and match data available for the selected player.

#### Feature Details
→ See [FEAT-00036-DETAILS.md](./FEAT-00036-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00035] Some played matches dont appear for a season while percentage exists
- **Status:** done
- **Priority:** medium
- **Effort:** medium (2–8h)
- **Depends on:** —

#### Goal
Ensure that a season's played-match count is available and displayed whenever
the season has a win percentage derived from played matches.

Use different symbols for scatter plot series:
- Played matches: cross
- Win percentage: triangle

#### Acceptance Criteria
- [x] For every season with an available win percentage derived from match
  results, the corresponding played-match count is returned and displayed.
- [x] The underlying season-statistics query or aggregation no longer omits
  played matches because of inconsistent joins, filters, grouping, or source
  data handling.
- [x] Seasons with no played matches remain distinguishable from seasons whose
  match data is unavailable, without inventing a played-match count.
- [x] Regression coverage includes the reported mismatch and verifies that
  unaffected season statistics continue to display correctly.
- [x] The connected scatter plot uses larger crosses for played matches and
  triangles for win percentage.

#### Feature Details
→ See [FEAT-00035-DETAILS.md](./FEAT-00035-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00034] Fixes pack 1
- **Status:** done
- **Priority:** low
- **Effort:** small (< 2h)
- **Depends on:** —

#### Goal
Implement and review the Player details statistics-chart fixes with explicit
played-matches and win-percentage vertical axes.

#### Acceptance Criteria
- [x] The Player details Statistics tab renders a connected scatter chart with
  the played-matches and win-percentage series for the selected seasons.
- [x] The chart calculates independent vertical positions for played matches
  and win percentage so both datasets remain visible even though their value
  ranges differ.
- [x] The chart exposes the win-percentage scale from 0% to 100%, translated
  left/right vertical-axis labelling, a legend for both datasets, and an
  accessible text table containing the underlying values.
- [x] The implemented chart behavior is covered by focused
  `PlayerDetailPage` tests, including default rendering, both vertical-axis
  labels and ticks, percentage grid lines, accessible chart description,
  removal of the chart-type selector, and the `connected-scatter` URL state.

#### Feature Details
→ See [FEAT-00034-DETAILS.md](./FEAT-00034-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00033] default system import folder setting
- **Status:** done
- **Priority:** medium
- **Effort:** medium (2–8h)
- **Depends on:** FEAT-00024

#### Goal
The application must always provide a default folder for import workflows, and administrators must be able to change it through the System settings experience.

#### Description
1. At application startup, the backend must check for the existence of a system setting named `import-folder` for category `IMPORT`. If it does not exist, the backend must create it with a default value of `c:\tt-repository`.

#### Acceptance Criteria
- [x] The backend checks for the existence of a system setting named `import-folder` for category `IMPORT` at application startup.
- [x] If the `import-folder` setting does not exist, the backend creates it with a default value of `c:\tt-repository`.
- [x] Administrators can view and change the `import-folder` setting through the System settings experience, and the change is persisted in the backend.
- [x] Import workflows use the configured default folder when no explicit folder is supplied.
- [x] Invalid, missing, or non-directory configured paths produce clear validation or import errors without silently selecting another folder.

#### Feature Details
→ See [FEAT-00033-DETAILS.md](./FEAT-00033-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00032] asynchronous import process with progress feedback in the UI
- **Status:** done
- **Priority:** medium
- **Effort:** large (> 8h)
- **Depends on:** FEAT-00031

#### Goal
Allow administrators to start an import asynchronously and follow its progress in the Data Import Panel without waiting for a long-running HTTP request.

#### Description
1. Analyze the `tt-data-league-import` and `tt-data-league-import-runtime` modules and how the backend implements import processing, including validation, error handling, and result generation.
2. Wire the existing `StartImportProcessCommandHandler` to submit the selected resource to an asynchronous backend process and return promptly without blocking the UI.
3. The Data Import Panel must display an accessible progress workspace with current state and available progress metrics until the import reaches a terminal state.
4. The progress workspace must present successful, empty-result, and failure outcomes, including processing findings and errors, with translated Catalan, Spanish, and English copy.
5. Administrators must be able to retry a failed import and continue using the panel without losing the selected resource context or starting an unscoped resource.
6. The asynchronous process must prevent duplicate submissions for the same active resource and preserve authenticated resource ownership.
7. The progress workspace must be designed to accommodate future enhancements, such as real-time progress updates, cancellation, and detailed error reporting.
8. The progress for import must be shown in percentual increments, with a progress bar and a textual representation of the current state, including the number of records processed, total records, and any errors encountered.

#### Acceptance Criteria
- [x] Starting an import submits the selected resource to an asynchronous backend process and returns promptly without blocking the UI.
- [x] The Data Import Panel displays an accessible progress workspace with current state and available progress metrics until the import reaches a terminal state.
- [x] The progress workspace presents successful, empty-result, and failure outcomes, including processing findings and errors, with translated Catalan, Spanish, and English copy.
- [x] Administrators can retry a failed import and continue using the panel without losing the selected resource context or starting an unscoped resource.
- [x] The asynchronous process prevents duplicate submissions for the same active resource and preserves authenticated resource ownership.
- [x] The progress workspace is designed to accommodate future enhancements, such as real-time progress updates, cancellation, and detailed error reporting.
- [x] The progress for import is shown in percentual increments, with a progress bar and a textual representation of the current state, including the number of records processed, total records, and any errors encountered.

#### Feature Details
→ See [FEAT-00032-DETAILS.md](./FEAT-00032-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

### [FEAT-00031] import process from endpoint performance improvement and implementation alignemt with CLI version
- **Status:** done
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
- [x] CLI and API imports invoke the same `ImportExecutionService` and therefore use identical source dispatch, processor order, season filtering, traversal failure rules, and post-processing order.
- [x] The shared execution result reports normalized traversal metrics, elapsed time, structured processor issues, persistence counters, and requested post-processing outcomes; the CLI logs it and the endpoint exposes a backward-compatible mapping.
- [x] Every execution owns source/season-scoped team, player-season, and match caches, and supported repository writes are flushed in bounded batches without weakening identity, idempotency, or processor-failure isolation.
- [x] Club and player consolidation remain independently opt-in, run only after successful traversal in club-then-player order, use the complete source-scoped inventory, and make the overall import fail when a requested phase fails.
- [x] The endpoint accepts only the stored import-resource identity/path and server-side consolidation configuration; defaults perform no consolidation and no cache state is shared between requests.
- [x] A documented like-for-like benchmark (same source, folder, season, restored database, JVM, and consolidation settings) shows the API shared-executor median within 10% of the CLI median across at least three measured runs, with no behavior or persisted-data regression.
- [x] Focused domain, import, navigator, JPA adapter, CLI, REST, and API-runtime wiring tests pass, followed by the full Maven reactor; frontend checks pass if the response contract changes.

#### Feature Details
→ See [FEAT-00031-DETAILS.md](./FEAT-00031-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

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

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---
