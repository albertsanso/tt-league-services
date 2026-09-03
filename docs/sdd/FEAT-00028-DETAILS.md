# Build Plan

1. **Confirm the resource contract and source boundary.**
   - Inspect the existing import-resource domain queries, REST controller, and
     frontend API client to identify the supported resource representation and
     whether a source/federation filter already exists.
   - Define or extend the API contract so a request names one explicit
     `ImportSource`; do not infer scope from display labels or return resources
     belonging to another source.
   - Preserve the existing authentication, response-envelope, and error
     handling conventions used by the Data Import APIs.

2. **Implement the source-scoped resource query.**
   - Add the smallest required application/domain port, query handler, and REST
     mapping in the appropriate modules if the current contract does not expose
     the list.
   - Return stable, presentation-ready resource fields (identifier or
     filename, upload timestamp, and processing status) and deterministic
     ordering.
   - Add focused unit and controller coverage for valid source filtering,
     empty results, invalid/unknown sources, authorization failures, and
     backend errors.

3. **Connect the Data Import Panel.**
   - Add an authenticated frontend API function and a cancellable hook or
     equivalent async state boundary for the selected source/federation.
   - Render the resource list in the existing import-panel layout, refreshing
     when the source changes and after a successful upload while retaining the
     current selection.
   - Provide accessible loading, empty, error, and retry states and prevent
     stale responses from replacing data for a newer source selection.

4. **Synchronize presentation and translations.**
   - Reuse existing UI primitives, status treatments, CSS tokens, and import
     panel patterns; do not introduce a second source selector or global state
     mechanism.
   - Add matching Catalan, Spanish, and English labels for resource fields,
     statuses, empty/error messages, and retry actions.
   - Preserve responsive behavior and keyboard/focus accessibility across
     supported administration viewports.

5. **Validate the complete slice.**
   - Test API response normalization, source isolation, cancellation, refresh,
     and all async states in the frontend.
   - Run the focused backend tests and the frontend lint, build, and Vitest
     suite; verify that only the selected source's resources are displayed.

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

- [ ] Selecting a source/federation displays only its import resources in the Data Import Panel.
- [ ] Each resource is presented with its filename or identifier, upload timestamp, and processing status using the existing import-panel visual language.
- [ ] The list supports loading, empty, error, and retry states without losing the selected source/federation.
- [ ] The resource list refreshes after a successful upload and when the selected source/federation changes, without a full page reload.
- [ ] Resource data is requested through an authenticated API contract scoped explicitly to the selected source/federation, with accessible labels and translated copy in Catalan, Spanish, and English.

# Notes

- 2026-09-03: New feature planned from the existing Data Import source
  selector and upload flow. The current resource-status endpoint lists sources
  with pending imports but does not yet provide the source-scoped resource list
  required by this feature; confirm the final contract before implementation.
- 2026-09-03: Initial acceptance criteria and build plan are synchronized with
  the `planned` registry entry; no application code is changed by this SDD
  maintenance task.
