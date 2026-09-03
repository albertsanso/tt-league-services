# Build Plan

1. **Confirm the existing route and UI boundaries.**
   - Keep the administrator-only `/administration/import` route and
     `AdministrationPage.jsx` composition unchanged.
   - Reuse `ImportPanel.jsx`, `ImportSourceSelector.jsx`, the existing UI
     primitives, i18n catalogues, and frontend API client rather than adding a
     second import screen or state mechanism.
   - Use the existing backend contract
     `GET /api/v1/administration/import/status`, whose successful
     `DomainQueryResponse` contains
     `{ response: { sources: [{ sourceName, uploadDateTime }] } }`.

2. **Add a status API boundary and polling hook.**
   - Add `getImportStatus` to
     `tt-data-league-frontend/src/api/importJobs.js`, preserving token,
     abort-signal, and unauthorized-session handling.
   - Extend or add a focused hook under `src/hooks/` that requests status on
     mount and polls every 5 seconds while mounted. Abort in-flight requests
     and clear the timer on unmount; avoid overlapping requests and expose
     `loading`, `error`, `data`, and `retry` state.
   - Normalize the response at the hook boundary. Always expose exactly the
     ordered source identifiers `RFETM`, `BCNESA`, and `FCTT`; mark a source
     `available` when its matching `sourceName` is present, otherwise
     `unavailable`. A source returned by a successful API response must remain
     `available` while a later poll error is reported separately; use `error`
     statuses only when the initial request has no source data.

3. **Integrate status into the left selector.**
   - Update `ImportPanel.jsx` and `ImportSourceSelector.jsx` to consume the
     normalized status state while retaining source selection and the existing
     seasons/report panels.
   - Render one large row/card per source on the left, with a clear label and
     a star-only accessible status indicator. Make the entire source card
     clickable, including the star area, keep the selected source connected
     to `@seasons-import-list`, and do not make status availability silently
     change the selected value.
   - Represent status only through the star's filled/unfilled state and
     existing design tokens, not visible status text or hard-coded colours.
     Provide a keyboard-accessible selection control and an `aria-live`
     status announcement.
   - Use the light yellow-green `--color-success-warm` theme token for the
     filled available star; keep loading, unavailable, and error stars
     unfilled. Style the enabled Start/Load action with the light green
     success background and success border.

4. **Synchronize translations and styling.**
   - Add accessible source-status labels and any retry/error copy to
     `src/i18n/ca.js`, then mirror the same keys in `es.js` and `en.js`;
     status labels must not be rendered as visible badge text.
   - Extend the import-panel styles in `src/app.css` only as needed for the
     left-side rows, selected state, star status indicator, green Start/Load
     action, and responsive layout.
     Preserve the existing desktop/tablet/mobile breakpoints, focus states,
     contrast, and reduced-motion behavior.

5. **Add focused frontend coverage.**
   - Test API request construction and response normalization for the wrapped
     status payload, including missing sources and malformed/failed responses.
   - Test the polling hook's initial load, 5-second refresh, cleanup/abort,
     retry, initial error, and later-poll error states with fake timers,
     including retention of available statuses after a failed poll.
   - Extend `ImportPanel.test.jsx` or add focused selector tests for exactly
     three sources, all four status presentations, accessible labels/live
     regions, source selection, and the existing seasons-panel handoff.

6. **Validate the frontend slice.**
   - Run `npm run lint`, `npm run build`, and the Vitest suite from
     `tt-data-league-frontend`.
   - Run `mvn -pl tt-data-league-frontend -am test` only when Maven-integrated
     validation is required; do not change unrelated backend modules or
     duplicate the existing `/status` query implementation.

# Implementation Guidelines

- Follow `tt-data-league-frontend/AGENTS.md` and
  `docs/frontend/design-contract.md`: use existing UI primitives and CSS
  variables, two-space JavaScript formatting, and translated user-facing copy.
- Keep source identity explicit and fixed to `RFETM`, `BCNESA`, and `FCTT`.
  Do not infer availability from labels, names, or the seasons history
 endpoint; only a matching `sourceName` in a successful `/status` response
 means `available`. Do not overwrite those available statuses with a later
 polling error.
- Keep the filled available star visually distinct from the blue primary
 accent by using the warm success token `--color-success-warm` (`#65a30d`);
 do not render textual source statuses in the selector.
- Keep asynchronous effects cancellable and visible to the user. Do not add
  broad catches, silent fallback success, unbounded polling, or a new global
  state library.
- Preserve the current import actions, route metadata, authorization behavior,
  and report/seasons workflows. Backend changes are out of scope unless the
  verified `/status` response differs from the documented contract.

# Acceptance Criteria

- [x] The left-side Source/Federation selector is displayed in the Data Import interface with exactly 3 options: `RFETM`, `BCNESA`, and `FCTT`.
- [x] Each selector has a clear label and a visual indicator of its current status.
- [x] The status of each selector is obtained from the `/api/v1/administration/import/status` endpoint and is updated dynamically every 5 seconds without requiring a page reload.
- [x] Every source included in a successful status response is displayed as `available` with the green status indicator, including after a later polling error.
- [x] Status is expressed only by the star's filled/unfilled state; accessible labels remain available without rendering `DISPONIBLE`, `ERROR`, or other status text.
- [x] The available star uses the light yellow-green `--color-success-warm` tone, and the enabled Start/Load action uses the light green success styling.
- [x] Each complete source/federation card, including its star area, is selectable by clicking and gives access to a more detailed panel in `@seasons-import-list`.

# Notes

- 2026-09-03: Planned against the current frontend import components and the
  existing backend `ImportResourceController` `/status` endpoint. The endpoint
  returns `PendingImportsInfoDto.sources` with `SourcePendingImportInfo.sourceName`
  and `uploadDateTime`; the frontend currently does not call it or poll it.
- 2026-09-03: Implemented the status API boundary, cancellable five-second
  polling hook, fixed three-source selector, translated status states, and
  focused frontend coverage. Validation passed with `npm run lint`, `npm run
  build`, and the full Vitest suite.
- 2026-09-03: `mvn -pl tt-data-league-frontend -am test` was attempted but
  could not run because `npm ci` hit an EPERM lock on an existing Vite/Node
  process's native dependency.
- 2026-09-03: Corrected status normalization to unwrap the REST
  `DomainQueryResponse.response` envelope. Without this, a valid response was
  treated as malformed and all three cards were shown as `error`.
- 2026-09-03: Clarified that successful source entries are always shown as
  available/green. Polling failures retain the last successful statuses and
  display the error separately; only an initial failure without source data
  uses per-source error statuses.
- 2026-09-03: Finalized the selector as star-only status presentation and
  applied the light yellow-green `--color-success-warm` tone to available
  stars. The enabled Start/Load action uses the light green success styling.
- 2026-09-03: User-approved closure recorded; feature moved to `done`.
