# Build Plan

1. **Lock down the existing source-scoped API contract.**
   - Use the existing `FindImportResourcesBySourceQuery`,
     `FindImportResourcesBySourceQueryHandler`, `ImportResourceDto`, and
     `ImportResourceController` rather than creating a parallel endpoint.
   - Confirm that `/api/v1/administration/import/list_by_source?source=<source>`
     accepts only an explicit supported source, preserves the API's
     authentication/response-envelope conventions, and never falls back to an
     unscoped or differently sourced result.
   - Map the current DTO fields (`importResourceId`, `season`, `status`,
     `createdDate`, and `lastProcessedDate`) to the resource card; add a
     filename field only if the backend contract cannot provide a useful
     identifier.

2. **Harden and verify the backend slice where required.**
   - Keep source filtering in `ImportResourceRepository.findBySourceAndType`
     and the JPA adapter, with the resource type restriction explicit.
   - Add deterministic ordering and explicit handling for empty and
     invalid/unknown source results if the current adapter/query behavior does
     not already provide them.
   - Add focused domain/controller coverage for valid source isolation, empty
     results, invalid sources, authorization failures, and backend errors.

3. **Add the frontend resource-list API boundary.**
   - Extend `src/api/importJobs.js` with an authenticated, abortable request for
     `list_by_source`, normalizing the response envelope at the API or hook
     boundary.
   - Add a cancellable hook or equivalent state boundary driven by
     `selectedSource`; clean up requests on unmount/source changes and ignore
     stale responses.
   - Keep `useImportSourceStatus` as the single source-selection mechanism and
     retain the selection through loading, retry, and error transitions.

4. **Render and refresh resources in the Data Import Panel.**
   - Replace the current season/history placeholder path in
     `src/components/import/ImportPanel.jsx` with source-scoped resource data,
     reusing `SeasonImportList`/its card primitives or a focused resource-list
     variant as appropriate.
   - Show identifier/filename, upload timestamp, and processing status, with
     accessible loading, empty, error, and retry states.
   - Refresh after a successful `uploadImportFile` call and whenever the source
     changes, without a full page reload or resetting the selection.

5. **Synchronize copy, accessibility, and validation.**
   - Add resource-list labels, statuses, empty/error text, and retry actions to
     `src/i18n/ca.js`, `src/i18n/es.js`, and `src/i18n/en.js`.
   - Reuse existing import-panel CSS/UI primitives and preserve responsive
     keyboard/focus behavior; do not add a second selector or global state
     mechanism.
   - Add focused frontend tests for normalization, source changes, refresh
     after upload, cancellation/stale responses, and every async state. Run
     backend tests plus `npm run lint`, `npm run build`, and the frontend test
     suite.

# Implementation Guidelines

- Keep federation/source identity explicit at every repository, application,
  API, and UI boundary. Never perform an unscoped resource lookup.
- Reuse existing import-resource services, DTO conventions, API client
  authentication, `ImportSourceSelector`, and import-panel components before
  adding new abstractions.
- Treat API failures and malformed responses as visible errors; do not silently
  show another source's data or fabricate successful resource state.
- Keep asynchronous requests cancellable and ignore stale responses after a
  source/federation selection changes.
- Use translated text and semantic list/status markup with accessible names,
  live updates where appropriate, and visible focus states.
- Keep upload initiation and resource listing concerns separate: a successful
  upload should trigger a refresh, not duplicate resource-list business logic.
- Out of scope: changing import parsing/reconciliation rules, changing the
  existing upload file contract, or redesigning unrelated administration
  screens.

# Acceptance Criteria
- [x] Selecting a source/federation displays only its import resources in the Data Import Panel.
- [x] Each resource is presented with its filename or identifier, upload timestamp, and processing status using the existing import-panel visual language.
- [x] The list supports loading, empty, error, and retry states without losing the selected source/federation.
- [x] The resource list refreshes after a successful upload and when the selected source/federation changes, without a full page reload.
- [x] Resource data is requested through an authenticated API contract scoped explicitly to the selected source/federation, with accessible labels and translated copy in Catalan, Spanish, and English.

# Notes

- 2026-09-03: New feature planned from the existing Data Import source
  selector and upload flow. The current resource-status endpoint lists sources
  with pending imports but does not yet provide the source-scoped resource list
  required by this feature; confirm the final contract before implementation.
- 2026-09-03: Initial acceptance criteria and build plan are synchronized with
  the `planned` registry entry; no application code is changed by this SDD
  maintenance task.
- 2026-09-04: Repository inspection confirmed that the source-scoped REST
  endpoint, domain query, DTO, and source/type repository lookup already exist.
  The remaining plan emphasizes contract hardening/coverage and connecting the
  frontend panel to that endpoint; this refinement changes SDD documentation
  only.
- 2026-09-04: Implemented the source-scoped frontend resource API, cancellable
  hook, accessible resource cards, upload refresh wiring, translations, and
  deterministic backend ordering. Feature moved to `in-review`.
- 2026-09-04: Added resource type and season emphasis, formatted upload dates,
  and right-aligned horizontal **Simulate** and **Import** actions. Feature
  moved to `done` after explicit approval.
