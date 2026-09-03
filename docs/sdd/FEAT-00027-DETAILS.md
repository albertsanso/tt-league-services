# Build Plan

1. Update `tt-data-league-frontend/src/api/client.js` and
   `src/api/importJobs.js` to submit authenticated `FormData` containing the
   selected file to `/api/v1/administration/import/upload`, with upload
   progress support.
2. Integrate the upload lifecycle into `ImportPanel.jsx` and
   `ImportFileControls.jsx`, including validation before submission and
   accessible progress, success, failure, and retry states.
3. Refresh the import status after the backend's asynchronous `202 Accepted`
   response without reloading the page, while preserving the selected source
   and season context.
4. Add focused frontend tests for multipart request construction, progress
   reporting, validation and error states, asynchronous status refresh, and
   retained source/season selections.

# Implementation Guidelines
- Use the backend multipart field name `file`; do not add source or season
  fields unless the API contract is explicitly extended.
- Reuse the existing API client, import-job abstractions, UI primitives,
  localization, and async state patterns rather than duplicating them.
- Keep upload and import state exposed through semantic controls and live
  regions, with explicit errors and no silent fallbacks.
- Use `/api/v1/administration/import/status` for post-upload refresh; resolve
  any existing history-endpoint mismatch separately from this feature.

# Acceptance Criteria
- [x] The file upload component sends the selected file to `/api/v1/administration/import/upload` using a multipart `file` field.
- [x] Upload progress, success, validation, failure, and retry states are presented accessibly in the frontend.
- [x] Successful uploads refresh import source status and history without requiring a page reload.
- [x] The component rejects empty or non-ZIP files before submission and preserves source/season selection context.

# Notes
- Implemented the authenticated multipart upload flow with XMLHttpRequest progress reporting, translated lifecycle messages, and focused frontend tests.
- Action messages are cleared immediately when a source reaches `available` or `error`, or automatically after 20 seconds.
