# Build Plan

1. Confirm the approved catalog contract from the current source and
   `docs/frontend/system-setting-create-search-mockup.md`. Keep the initial
   allowlist, categories, types, defaults, restrictions, and secret exclusions
   explicit. Define the key format, required definition fields, state filter
   semantics (`modified` versus `default`), catalog ordering, and version/backup
   behavior before changing any public contract.
2. Extend `tt-data-league-core-domain` settings models and ports so definitions
   are first-class catalog records rather than only hard-coded defaults.
   Provide explicit validation for key syntax and uniqueness, required
   metadata, type-compatible default/current values, allowed string values, and
   numeric bounds. Add commands/results for definition creation, catalog
   filtering by category/search/state, default restoration, preview, atomic bulk
   updates, and versioned backup/restore. Preserve immutable metadata,
   optimistic versions, and side-effect-free preview/rehydration.
3. Update
   `tt-data-league-core-repository-jpa/src/main/java/org/cttelsamicsterrassa/data/core/repository/jpa/settings`
   to persist definition metadata and values, enforce unique keys, preserve
   optimistic concurrency, and replace the complete catalog transactionally.
   Update `SettingRepositoryHelper`, `SettingsRepositoryJpa`, directional
   mappers, schema migration, focused H2 tests, and
   `docs/rfetm-datamodel.md` for every column, constraint, migration, and
   transaction change.
4. Extend `tt-data-league-api-rest/src/main/java/org/cttelsamicsterrassa/data/api/rest/settings`
   with immutable DTOs and administrator-protected operations for catalog
   reads, definition validation/creation, category/search/state filters,
   single/default/bulk changes, preview, and versioned backup/restore.
   Keep `/api/v1/administration/settings`, `ADMIN` authorization, explicit
   `401/403/404/409/413/422` mappings, payload limits, and secret exclusion
   consistent across all operations.
5. Update the frontend API boundary in
   `tt-data-league-frontend/src/api/settings.js` and asynchronous state in
   `src/hooks/useSettings.js`. Normalize definition metadata and typed values
   before rendering, serialize filters and mutations through `apiRequest`,
   handle cancellation/stale requests, retain entered creation data on
   validation failure, and expose conflict and authorization errors.
6. Expand `tt-data-league-frontend/src/components/settings/SettingsPanel.jsx`
   using the existing `AdministrationPage` route and role guard. Add the
   mockup's bordered filter fieldsets, search/clear actions, category and
   modified/default filters, categorized responsive cards, visible
   restrictions, edit/restore pending changes, pending summary, preview/cancel/
   atomic-save actions, definition creation and confirmation flow, and
   explicit loading/empty/unauthorized/forbidden/server-error states. Keep
   personal locale selection on `/settings` separate from this system catalog.
7. Add or extend Catalan, Spanish, and English strings in
   `tt-data-league-frontend/src/i18n/ca.js`, `es.js`, and `en.js` for catalog
   fields, categories, types, restrictions, validation, confirmations,
   backups, accessibility labels, and all mutation/error states. Adjust
   `src/app.css` only for the responsive settings layout and established
   Users and Roles styling patterns; do not add a second route or permission
   mechanism.
8. Add focused tests alongside each changed layer: domain validation,
   definition creation, filtering, preview non-persistence, default restore,
   version conflicts, atomic bulk behavior, complete backup/restore and secret
   exclusion; JPA metadata, round trips, uniqueness, stale writes, migration,
   and rollback; REST DTO/status/auth/payload contracts; and frontend API
   normalization, hook cancellation, filters, pending edits, creation
   validation/confirmation, backup/restore, translated states, accessibility,
   and direct administrator-route behavior.
9. Validate with the existing commands: `npm ci`, `npm test`, `npm run lint`,
   and `npm run build` from `tt-data-league-frontend`; focused Maven tests for
   the changed domain, JPA, and REST modules; then `mvn test` from the
   repository root. Only after the dependency and contract decisions are
   resolved should FEAT-00026 move from `planned` to `ready`.

# Implementation Guidelines

- Work from the current working tree and the referenced mockup only; do not
  depend on git history, prior implementations, or historical diffs.
- Keep domain code independent of Spring, Jakarta Persistence, React, and
  database encodings. Cross-module behavior must use domain ports, REST DTOs,
  and explicit mappers.
- Treat keys and definitions as an allowlisted, source-of-truth catalog.
  Reject unknown keys, duplicate keys, malformed definitions, incompatible
  values, invalid restrictions, deployment secrets, and partial restore data;
  never silently default or discard invalid input.
- Make creation, bulk save, and restore atomic. Use optimistic concurrency for
  edits and surface conflicts without overwriting newer administrator changes.
- Preview, validation, filtering, and rehydration must not persist or publish
  mutation events. Restore must be explicitly confirmed in the UI and
  transactionally validated on the server.
- Reuse `apiRequest`, `useAuth`, existing route metadata and role handling,
  current localization resources, shared UI primitives, and existing settings
  persistence conventions. Do not add a state library or dependency without an
  approved need.
- Do not expose locale preferences, datasource credentials, JWT secrets, mail
  credentials, or other deployment configuration as editable catalog records.
- Use four-space Java formatting, two-space frontend formatting, and the
  repository's existing test/build tools.

# Notes

- Before this plan, the registry marked FEAT-00026 as `idea`; it depends on
  FEAT-00023 and FEAT-00025. FEAT-00023 is listed as `done`, but no FEAT-00025
  block is present in `docs/sdd/FEATURES.md`; reconcile that dependency before
  marking this feature `ready`.
- The current tree contains an administrator settings route and panel,
  `/api/v1/administration/settings` REST operations, a ten-key domain catalog,
  JPA persistence, and frontend API/hook tests. The plan extends those current
  contracts to support persistent definitions and the create/search/filter
  workflow; it does not create a parallel settings destination.
- The current initial catalog is `ui.theme`, `ui.compactMode`,
  `notifications.emailEnabled`, `notifications.inAppEnabled`,
  `notifications.importCompleted`, `import.autoValidate`,
  `import.preserveHistory`, `import.maxBatchSize` (1-10000),
  `display.maxSearchResults` (10-100), and `display.maxPageSize` (10-100).
  Confirm whether administrators may add definitions beyond this initial
  allowlist before implementation.
- The backup contract must be versioned, complete, size-limited, validated
  before replacement, and free of secrets. The exact schema version and
  retention/audit requirements remain decisions for implementation approval.
