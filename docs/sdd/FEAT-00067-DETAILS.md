# Build Plan
1. Add a close button to `MatchActaDialog` in
   [MatchActaDialog.jsx](../../tt-data-league-frontend/src/components/matches/MatchActaDialog.jsx),
   rendered unconditionally inside the `.acta-dialog` card (before the
   loading/error/no-data/acta branches) so it is present in every state:
   ```jsx
   <button
     type="button"
     className="acta-dialog-close"
     aria-label={t('common.close')}
     onClick={onClose}
   >
     &times;
   </button>
   ```
   Reuse the existing `common.close` i18n key (already defined in
   `en.js`/`es.js`/`ca.js`) — no new translation keys are required.
2. Style `.acta-dialog-close` in
   [app.css](../../tt-data-league-frontend/src/app.css) near the existing
   `.acta-dialog`/`.acta-card` rules (around line 4206-4230): absolutely
   position it in the top-right corner of the card, size it as a small
   circular/square icon button consistent with other icon buttons in the
   app, and add a hover/focus-visible state. Confirm it still reads
   correctly under the mobile override block (~line 4336-4345) where
   `.acta-dialog-overlay`/`.acta-dialog` lose their fixed positioning.
3. Verify the click does not conflict with the existing overlay-click-to-close
   behavior: the dialog card already calls `event.stopPropagation()` on
   `onMouseDown`, so a `onClick` on the new button firing `onClose` directly
   is safe and independent of the overlay's `onMouseDown={onClose}`.
4. Keep the existing Escape-key and overlay-click close paths unchanged —
   the button is an additional, discoverable way to close the modal, not a
   replacement.
5. Update
   [MatchActaDialog.test.jsx](../../tt-data-league-frontend/src/components/matches/MatchActaDialog.test.jsx):
   add a test that renders the dialog, finds the close button by its
   accessible name (`common.close` translation), clicks it, and asserts
   `onClose` was called — following the same pattern as the existing
   Escape-key/overlay-click tests in that file.
6. Run the frontend test suite for the changed files
   (`npm test -- MatchActaDialog` from `tt-data-league-frontend/`, or the
   project's equivalent test command) and confirm no regressions.

# Implementation Guidelines

- Do not introduce new i18n keys; `common.close` already exists in all three
  locale files and is the correct semantic key for a modal close action.
- The close button must be reachable in every dialog state (loading, error,
  no-data, and populated acta), not only when acta data has loaded.
- Do not remove or change the existing Escape-key handler or the
  overlay-click-to-close behavior in `MatchActaDialog.jsx`.
- Keep the button visually consistent with the app's existing button/icon
  styling rather than introducing a one-off visual style.

# Notes

- Implemented: close button (`&times;`, `icon-button acta-dialog-close`,
  `aria-label={t('common.close')}`) added to `MatchActaDialog.jsx`, rendered
  unconditionally in the dialog card; `.acta-dialog-close` styling added to
  `app.css` (absolute top-right within `.acta-card`, now `position:
  relative`).
- Added a test in `MatchActaDialog.test.jsx` clicking the close button and
  asserting `onClose` fires.
- Verified: full frontend suite (`npx vitest run`) — 42 files, 346 tests
  passed. Lint clean on the changed files.
