# Build Plan

1. **Ground the current state before writing code.**
   - `src/components/import/ImportPanel.jsx` is currently an empty stub
     (`<div></div>`). There is no themed markup to re-skin yet — this feature
     must build the import experience, not just restyle existing markup.
   - `src/api/importJobs.js`, `src/hooks/useImportSources.js`, and the
     `importPanel.*` keys in `src/i18n/{ca,es,en}.js` already exist from
     earlier scaffolding. They model a single-source
     preview → validate → start → cancel → rollback job lifecycle. Reuse
     this surface; do not duplicate it. Only add new API calls, hook state,
     or i18n keys for pieces the wireframe needs that this surface does not
     already cover (season list rows, per-row Load/Simulate actions, the
     report/status panel, file chooser).
   - Known pre-existing defect (found while grounding this plan, not caused
     by this feature): `mvn -pl tt-data-league-api-rest -am test-compile`
     fails because `ImportJobControllerTest` and
     `InMemoryImportJobsServiceTest`
     (`tt-data-league-api-rest/src/test/.../importjob/`) reference
     `ImportJobController`, `InMemoryImportJobsService`, and `ImportResourceDto`
     classes that do not exist under `tt-data-league-api-rest/src/main`.
     This blocks true end-to-end validation against a live backend and is
     outside this feature's frontend-theme scope; flag it to the user/backend
     owner rather than fixing it here. Frontend work below can be planned and
     validated with mocked API modules, matching the existing
     `ClubsSearchPage.test.jsx` pattern.

2. **Layout composition** (`src/components/import/`), matching
   `docs/frontend/load-import/theme-spec.md` sections 1–2:
   - `ImportPanel.jsx` — composition root replacing the stub. Renders
     `SectionLabel` + `importPanel.title` / `importPanel.description`, then
     the top control bar and the three-panel layout below.
   - `ImportFileControls.jsx` — file chooser (accessible `<label>` + `<input
     type="file">`) and the global Load button (`Button` primitive,
     `variant="primary"`).
   - `ImportSourceSelector.jsx` — left panel; one card per source from
     `useImportSources` (RFETM/BCNESA/FCTT), each with its label/description
     (`importPanel.sourceDescription`) and a favourite/star toggle; use
     `Card` and the existing `--accent-*` tokens for the selected state.
   - `SeasonImportList.jsx` + `SeasonImportListItem.jsx` — center panel;
     one row per season with identifier, last-run timestamp, a Load button +
     status indicator, and a Simulate button + status indicator, per
     `theme-spec.md` section 2.A. Map Load/Simulate to the existing
     `startImport`/`createImportPreview` calls and status indicators to
     `ImportJobStatus` values via `Badge` tones (`success`/`warning`/
     `error`/`subtle`) — do not hard-code the wireframe's literal "light
     green"/"light pink" colors.
   - `ImportReportPanel.jsx` — right panel report/status workspace for the
     selected season/job; reuse `LoadingState`, `EmptyState`, `ErrorState`
     for its async states.

3. Confirm `AdministrationPage.jsx` routing needs no change (the
   `administrationImport` destination already renders `ImportPanel`) and
   that `src/config/routes.js` / `src/config/navigation.js` require no new
   entries.

4. **i18n**: add new `importPanel.*` keys to `src/i18n/ca.js` first (file
   chooser label, global load action, favourite toggle, season row labels,
   simulate action, status-indicator states, report-panel states), then
   mirror the same keys into `es.js` and `en.js` in the same change. Keep
   existing keys (`sourcesTitle`, `historyTitle`, `actionsTitle`, `validate`,
   `start`, `cancel`, `rollback`, `sourceDescription`, etc.) — extend, don't
   remove, unless a step below makes one genuinely unused.

5. **Styling**: reuse existing utility classes (`card`, `ui-button`,
   `ui-state`, `section-label`, `badge`) and `--surface-*`/`--accent-*`/
   `--secondary`/`--color-*` tokens from `src/index.css`. Add a small
   `import-panel` rule block in `src/app.css` only for the 3-column
   responsive grid: stacked panels below 768px, two columns 768–1279px,
   three columns at ≥1280px, per the design contract's breakpoints.

6. **Tests**: add `ImportPanel.test.jsx` (plus focused component tests as
   needed) mocking `../../api/importJobs.js` and `useImportSources`, covering
   loading, empty (no sources / no seasons), error (401/403/5xx), and
   success rendering of the source selector, season list, and report panel.
   Verify accessible names/roles for the file input, Load/Simulate controls,
   favourite toggle, and status live regions.

7. **Validation**: `npm run lint`, `npm run build`, and the Vitest suite from
   `tt-data-league-frontend`; run `mvn -pl tt-data-league-frontend -am test`
   only if Maven-integrated validation is requested. Do not attempt to fix
   the pre-existing `tt-data-league-api-rest` compile failure as part of
   this feature unless explicitly asked.

# Implementation Guidelines
- Follow `tt-data-league-frontend/AGENTS.md` and
  `docs/frontend/design-contract.md`: reuse `src/components/ui` primitives
  and existing CSS tokens; `Inter` for interface text, `DM Mono` only for
  numeric stats; border-radius ≤ 0.5rem; no gradients, illustrations, emoji,
  multicolour backgrounds, or large shadows.
- Translate the wireframe's "light green Load" / "light pink Simulate" cues
  into existing success/secondary tokens and `Badge` tones rather than
  introducing new hard-coded feature colors.
- Preserve desktop (≥1280px) / tablet (768–1279px) / mobile (<768px)
  behavior; the three-column layout must degrade to a stacked, scrollable
  layout on mobile.
- Every interactive control (file input, Load/Simulate buttons, favourite
  toggle, status indicators) needs a semantic element, accessible name,
  visible focus state, and keyboard behavior; async status must expose an
  appropriate live region, consistent with `LoadingState`/`ErrorState`/
  `EmptyState`.
- Keep Catalan (`ca.js`) as the source of truth for new copy and mirror
  `es.js`/`en.js` in the same change; never leave a locale out of sync.
- Do not duplicate backend business rules in the frontend; only render
  `ImportSource`/`ImportJobStatus` values already exposed by the API/DTO
  contracts.
- Keep new components small and focused (≤ ~150 lines); split further if a
  component grows past that.
- Out of scope for this feature: implementing or fixing the backend
  `ImportJobController` / `InMemoryImportJobsService` main classes, changing
  the import job lifecycle/API contract, and any non-import administration
  screens.

# Acceptance Criteria
- [x] The data import experience uses the shared administration design theme for layout, typography, color, spacing, and controls.
- [x] Import states, actions, validation feedback, and progress indicators are visually consistent with the administration theme.
- [x] The themed data import experience is responsive and accessible across supported administration viewports.
- [x] Catalan, Spanish, and English data import labels and messages are visually consistent with the administration theme.
- [x] The implementation takes into account the existing design system in `docs/frontend/load-import/theme-spec.md`.

# Notes
- 2026-09-02: Resolved the reported Spring
  `MaxUploadSizeExceededException` by aligning the multipart upload-size
  configuration with supported ZIP import payloads; no application-code
  changes are part of this documentation update.
- 2026-09-02: Implementation finalized and feature closed following explicit
  user approval; the multipart upload-size issue is resolved.
- 2026-09-02: Implemented the responsive import panel, source selector, file controls,
  season actions, report workspace, shared-token styling, and synchronized
  Catalan, Spanish, and English copy. Frontend lint and production build pass.
- 2026-09-02: Build plan drafted. Grounded against
  `docs/frontend/load-import/theme-spec.md` (wireframe/component spec),
  `docs/frontend/design-contract.md` and `docs/frontend/theme-spec.md`
  (visual tokens/rules), `tt-data-league-frontend/AGENTS.md` (module
  boundaries and validation commands), and the current source tree:
  `ImportPanel.jsx` is an empty stub; `api/importJobs.js`,
  `useImportSources.js`, and `i18n` `importPanel.*` keys already exist from
  earlier scaffolding for a single-source job lifecycle that does not yet
  match the multi-panel, per-season wireframe and will need targeted
  extension rather than a rewrite.
- 2026-09-02: Found a pre-existing, unrelated defect while grounding this
  plan: `tt-data-league-api-rest`'s `importjob` test package does not
  compile (`ImportJobController` / `InMemoryImportJobsService` /
  `ImportResourceDto` are referenced by tests but have no `src/main`
  implementation). This is outside this feature's frontend-theme scope and
  is not being fixed here; it must be resolved (or explicitly accepted as a
  known gap) before the themed import experience can be validated against a
  live backend end-to-end.
- 2026-09-02: Re-estimated effort from `medium (2–8h)` to `large (>8h)`
  after finding `ImportPanel.jsx` is an empty stub: the feature requires
  building the full multi-panel import UI (file controls, source selector,
  season list, report panel) from scratch, not re-skinning existing markup.
