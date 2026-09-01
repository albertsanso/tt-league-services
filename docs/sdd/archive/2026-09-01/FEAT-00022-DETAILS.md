# Build Plan

1. Confirm the settings contract before implementation: define the supported
   setting catalog, stable keys, value types, categories, defaults, validation
   rules, safe-to-preview behavior, and which settings are explicitly out of
   scope. Persist application settings managed by this panel; do not expose
   datasource credentials, JWT secrets, mail credentials, or other deployment
   secrets as editable browser settings. Record the chosen default ordering and
   backup format as part of the implementation notes.
2. Add framework-light settings read models and commands in
   `tt-data-league-core-domain`, including category/search/filter queries,
   validated single-setting and bulk updates, preview/validation without
   persistence, and backup/restore models. Return typed, display-safe values
   and validation errors rather than leaking infrastructure configuration.
   Define explicit not-found, invalid-value, conflict/version, malformed
   backup, and restore-validation failures.
3. Extend the domain repository port and implement the persistence adapter in
   `tt-data-league-core-repository-jpa`. Use a dedicated settings/key-value
   table (or the existing repository convention if one is approved), enforce
   unique keys and optimistic versioning, preserve category/type metadata, and
   make restore atomic. Update
   `../../../../tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for every table,
   column, constraint, or transaction-behavior change.
4. Add the versioned REST contract in `tt-data-league-api-rest`: an
   administrator-protected list/read endpoint with category/search filters,
   update endpoints for single and bulk changes, a validation/preview endpoint,
   and authenticated backup-download and restore-upload/import endpoints.
   Add immutable request/response DTOs, OpenAPI descriptions, content/type and
   size limits for restore payloads, and consistent `401`, `403`, `404`, `409`,
   `413`, and `422` responses. Enforce `ADMIN` (or a dedicated settings read /
   write authority if the RBAC catalog is intentionally extended) on the
   backend; frontend route hiding is not authorization.
5. Add focused domain, repository, and REST tests for catalog/default
   projection, category/search filtering, every supported value type,
   validation and unsafe-value rejection, preview non-persistence, optimistic
   conflicts, atomic bulk update, backup round-trip, malformed/oversized/
   incompatible restore files, rollback on restore failure, unauthorized and
   non-administrator access, and secret/configuration exclusion.
6. Replace the System Settings destination in
   `../../../../tt-data-league-frontend/src/pages/AdministrationPage.jsx` with a focused
   settings panel (split into a feature component if useful). Add an API
   boundary under `src/api/` and cancellable hooks under `src/hooks/` that use
   `apiRequest`, pass the current token, normalize server data, and expose
   loading, empty, validation, conflict, forbidden, unauthorized, and server
   error states. Keep locale selection in the existing personal `/settings`
   page unless the approved system catalog explicitly includes a separate
   default locale setting.
7. Render categorized settings with search and category filters, typed
   controls, inline validation, dirty-state tracking, preview/test results,
   explicit apply/cancel behavior, and a clearly destructive restore flow.
   Provide download backup and file-selection/import actions, confirmation
   dialogs, progress/status announcements, keyboard-accessible labels, and
   responsive behavior using existing page, card, form, dialog, and button
   primitives. Refresh displayed values after successful updates or restore.
8. Keep route metadata, the `ADMIN` guard, and the existing Administration
   navigation contract in `src/config/routes.js`, `src/config/navigation.js`,
   and `src/App.jsx`; do not create a second route or permission mechanism.
   Add Catalan, Spanish, and English translations for categories, setting
   labels/help, validation, preview, backup/restore, confirmations, and all
   loading/empty/error/accessibility copy.
9. Add focused frontend API, hook, route, and page tests covering normalized
   responses, abort/stale-request handling, filters, edits, preview/apply/
   cancel, backup download, restore confirmation and failures, translated
   states, accessibility, and administrator-only direct-route behavior.
10. Validate from `tt-data-league-frontend` with `npm ci`, the existing test
    command, `npm run lint`, and `npm run build`; validate the changed Maven
    modules with `mvn -pl <module> -am test` and finish with the required root
    `mvn test`. Only after all acceptance criteria pass, move the feature to
    `ready` for implementation; completion later moves it to `in-review`, not
    `done`, until the user explicitly closes it.

# Implementation Guidelines

- Keep domain code independent of Spring, JPA, and React. Cross-module
  behavior must use domain repository/service boundaries, immutable read
  models, REST DTOs, and explicit mappers.
- Treat the supported setting catalog as an allowlist. Unknown keys, wrong
  types, invalid ranges, unsafe values, and deployment secrets must be
  rejected rather than silently stored or defaulted.
- Separate personal browser preferences from administrator-managed system
  settings. The existing `/settings` language selector is local
  preference behavior; this feature owns only the protected
  `/administration/settings` destination.
- Reuse `apiRequest`, `useAuth`, `RequireAuth`, `RequireRole`, centralized route
  metadata, existing localization resources, and established loading/error and
  responsive UI patterns. Do not add a state library or dependency without an
  approved need.
- Enforce authorization and input limits server-side, make bulk updates and
  restore operations transactional, and use optimistic concurrency so a stale
  administrator cannot overwrite newer settings.
- Preview/test must be side-effect free. Backup/restore must use a documented,
  versioned, validated format and must never include secrets. Restore must be
  explicitly confirmed and safely reject partial application.
- Keep all user-facing copy translated and all mutation, loading, empty, and
  failure states visible. Do not add broad catches or success-shaped fallbacks.

# Acceptance Criteria

- [x] Administrators can view and update the approved system settings catalog,
  organized by category with typed controls and persisted values.
- [x] The panel validates values (including bulk changes) before persistence,
  rejects unsafe or unknown settings, and presents clear actionable errors.
- [x] Administrators can search and filter settings by category and setting
  identity without loading unrelated or hidden settings into the UI.
- [x] Administrators can preview or test pending changes without side effects,
  then explicitly apply or cancel them with stale-write/conflict protection.
- [x] Administrators can export a versioned backup and restore it through an
  explicitly confirmed, validated, atomic operation with visible failure and
  rollback behavior.
- [x] The REST API, frontend route, and every mutation enforce administrator
  authorization; non-administrators and direct unauthenticated access are
  denied, and deployment secrets are never returned or persisted by this panel.
- [x] The frontend exposes translated Catalan, Spanish, and English labels,
  accessible controls, responsive layout, and explicit loading, empty,
  validation, unauthorized, forbidden, conflict, and server-error states.

# Notes

- Status was intentionally `planned` before implementation: the existing `/administration/settings`
  route is a localized navigation-only shell, while `/settings` currently
  stores only the browser locale preference. No system-settings domain,
  persistence table, REST contract, or settings API currently exists.
- FEAT-00020 is the dependency that supplies the stable protected destination
  and Administration navigation. FEAT-00021 and FEAT-00023 are parallel
  administration panels and are not runtime dependencies.
- Completion ordering: approve the setting catalog and security boundary
  first; then implement domain contract, persistence/schema, REST contract,
  frontend API/hooks, UI/localization, and tests in that order. Run focused
  validations after each layer and full Maven/frontend validation before
  requesting `ready`.
- Open decisions before implementation: exact catalog/categories and value
  types, whether settings take effect immediately or require a runtime reload,
  preview/test semantics for each setting, maximum backup size, retention/
  audit requirements, and whether dedicated `settings:read`/`settings:write`
  authorities are needed instead of the existing `ADMIN` role.
- Implementation decisions (2026-08-30): the conservative allowlist is
  `ui.theme` (`light`, `dark`, `system`), `ui.compactMode`,
  `notifications.emailEnabled`, `notifications.inAppEnabled`,
  `notifications.importCompleted`, `import.autoValidate`,
  `import.preserveHistory`, `import.maxBatchSize` (1–10000),
  `display.maxSearchResults` (10–100), and `display.maxPageSize` (10–100).
  Values are typed booleans, bounded integers, or allowlisted strings. Locale,
  datasource credentials, JWT secrets, mail credentials, and deployment
  configuration remain out of scope and are rejected by the allowlist.
- Settings take effect on the next read/request; no unsafe runtime reload
  wiring was introduced. Preview is validation-only and side-effect-free.
  Backups use JSON schema version 1 (`schemaVersion` plus a complete `settings`
  object), restore payloads are limited to 1 MiB, and restore replacement is
  transactional. The existing `ADMIN` role protects reads and writes; no new
  audit or retention mechanism was added.
