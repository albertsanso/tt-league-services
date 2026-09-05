# Build Plan

1. **Define the canonical import-folder setting contract.**
   - Reuse `SettingCategory.IMPORT`, the existing `ResourceZipService.IMPORT_FOLDER`
     key (`import-folder`), and the exact default value `c:\tt-repository`
     already registered in `FEATURES.md`.
   - Add one shared domain-level definition or provisioning service so startup,
     import lookup, validation, and tests cannot drift on the category, key, or
     default value.
   - Keep the setting a persisted administrator setting; do not move it into
     Spring environment configuration or add a second deployment-only override.

2. **Provision the default setting during backend startup.**
   - Add an idempotent startup initializer in the API runtime that queries
     `SettingRepository` through the existing domain service/port and creates
     the setting only when `(IMPORT, import-folder)` is absent.
   - Wire it into the existing Spring Boot startup lifecycle without changing
     the command-line import execution path or creating duplicate settings on
     restart.
   - Surface persistence or initialization failures instead of silently
     continuing with an unconfigured folder.

3. **Validate and consume the configured folder consistently.**
   - Update the existing setting validation path used by create/update/bulk
     settings operations so `IMPORT/import-folder` is non-blank, syntactically
     usable as a filesystem path, and produces a clear client-visible error for
     invalid values.
   - Keep filesystem existence checks at import execution time: report a
     missing or non-directory configured path as an explicit import error rather
     than creating an unexpected directory or falling back to another path.
   - Ensure repository loading and upload-triggered import workflows continue to
     resolve the persisted setting when no explicit folder is supplied, while
     preserving any explicitly supplied resource path semantics.

4. **Expose the setting through the existing administrator settings experience.**
   - Reuse the current authenticated settings REST endpoints, DTOs, optimistic
     update behavior, and `SettingsPanel`; do not add a feature-specific
     endpoint or duplicate settings state.
   - Add the `import-folder` label and any validation/error copy to Catalan,
     English, and Spanish translations, and render it as a path-capable text
     control with accessible feedback.
   - Preserve category filtering, search, create/update behavior, authorization,
     and conflict handling for all other settings.

5. **Add focused regression coverage.**
   - Core domain: provisioning when absent, no-op when present, exact category/
     name/default value, and invalid import-folder values.
   - Runtime/startup: initializer wiring, restart/idempotency behavior, and
     propagation of repository failures.
   - Import: configured-folder lookup, missing/non-directory error messages,
     and successful use of a valid configured directory.
   - REST/frontend: administrator read/update flow, validation response mapping,
     translated label/error states, and preservation of existing settings
     filters and optimistic-concurrency behavior.

6. **Validate and document the delivered contract.**
   - Run focused core-domain, repository/JPA, API REST/runtime, and import tests,
     then `mvn test` from the repository root.
   - Run the frontend's existing `npm ci`, `npm run lint`, and `npm run build`
     commands from `tt-data-league-frontend`.
   - Update the affected runtime/module README if startup provisioning or the
     required filesystem-directory behavior changes operational assumptions.
   - Synchronize `FEATURES.md`, this details file, acceptance criteria, and
     notes before moving the feature from `planned` to `ready`.

# Implementation Guidelines

- Keep dependency direction unchanged: setting contracts and validation belong
  in `tt-data-league-core-domain`; JPA persistence remains in
  `tt-data-league-core-repository-jpa`; startup wiring belongs in
  `tt-data-league-api-runtime`; HTTP mapping belongs in
  `tt-data-league-api-rest`; and labels/rendering belong in
  `tt-data-league-frontend`.
- Reuse `SettingFinderService`, `SettingCreationService`, `SettingRepository`,
  `ResourceZipService`, and the existing settings command/query handlers before
  introducing new ports or adapters.
- Treat `(SettingCategory.IMPORT, "import-folder")` as a unique logical setting.
  Startup provisioning must be safe on repeated launches and must not overwrite
  an administrator's configured value.
- Validate path input explicitly and preserve the existing clear failure
  behavior in `ResourceRepositoryLoaderService`; do not add broad catches,
  silent defaults, path normalization that changes the configured value, or
  client-controlled filesystem access.
- No JPA schema change is expected: the existing settings table and persistence
  mapping already support this setting. Update the persistence schema document
  only if implementation evidence requires a schema change.

# Acceptance Criteria

- [x] The backend checks for the existence of a system setting named `import-folder` for category `IMPORT` at application startup.
- [x] If the `import-folder` setting does not exist, the backend creates it with a default value of `c:\tt-repository`.
- [x] Administrators can view and change the `import-folder` setting through the System settings experience, and the change is persisted in the backend.
- [x] Import workflows use the configured default folder when no explicit folder is supplied.
- [x] Invalid, missing, or non-directory configured paths produce clear validation or import errors without silently selecting another folder.

# Notes

- 2026-09-06: Build plan drafted from the existing settings domain/API/frontend
  and `ResourceZipService`/`ResourceRepositoryLoaderService` import-folder
  integration. Status should move to `planned`; implementation remains blocked
  from starting until the plan is approved and status is changed to `ready`.
- 2026-09-06: Implemented end-to-end. Added the canonical
  `ImportFolderSetting` contract (category, name, default value, path
  validation) and `ImportFolderSettingProvisioningService` in
  `tt-data-league-core-domain`; wired an idempotent
  `ImportFolderSettingStartupInitializer` (`CommandLineRunner`) in
  `tt-data-league-api-runtime` that lets persistence/initialization failures
  fail application startup. `SettingCreationService` and
  `SettingModificationService` now validate `IMPORT/import-folder` values
  on create/update; `SettingsController.updateSetting` now maps
  `IllegalArgumentException` to a 400 response with a clear message,
  matching the existing `createSetting` behavior.
  `ResourceRepositoryLoaderService` already resolved the persisted setting
  and reported missing/non-directory configured folders as import errors; this
  behavior is preserved and now covered by focused tests.
  Frontend: added the `import-folder` label and an accessible
  `importFolderHint` translation (Catalan, English, Spanish) and rendered a
  `setting-hint` paragraph linked via `aria-describedby` for that setting in
  `SettingsPanel`; no new endpoint or duplicated settings state was added.
  Tests added: `ImportFolderSettingTest`,
  `ImportFolderSettingProvisioningServiceTest`, `SettingCreationServiceTest`,
  `SettingModificationServiceTest`, `ResourceRepositoryLoaderServiceTest`
  (core-domain); `ImportFolderSettingStartupInitializerTest` (api-runtime);
  an added case in `SettingsControllerTest` (api-rest). All pass.
  Validation run: `mvn -pl tt-data-league-core-domain -am test` (63 tests,
  0 failures) and `mvn -pl tt-data-league-api-runtime test` (8 tests, 0
  failures) both pass cleanly. `tt-data-league-api-rest` tests pass (53
  tests, 0 failures) once the module is built standalone against installed
  dependency jars, because an unrelated pre-existing compile failure in
  `importjob/ImportJobControllerTest` and `InMemoryImportJobsServiceTest`
  (referencing classes that do not exist anywhere in the repository) blocks a
  reactor `mvn test` run from the root; this is a pre-existing defect,
  unrelated to this feature, and was left untouched. A full root `mvn test`
  is additionally blocked by two other pre-existing, unrelated failures
  verified on a clean `git stash -u` baseline: (1)
  `tt-data-league-core-repository-jpa` Spring context tests fail with a
  missing `ImportRunRegistry` bean for `JpaTestApplication`; (2)
  `tt-data-league-import` club/team consolidation processor tests
  (`BcnesaImportProcessorsTest`, `FcttImportProcessorsTest`,
  `ImportProcessorsTest`, `TeamToClubConsolidationProcessorTest`) fail on
  assertions unrelated to settings or import folders. None of these are
  touched by this feature; they should be filed and fixed separately.
  Frontend: `npm run lint`, `npm test` (144 tests, 0 failures), and
  `npm run build` all pass from `tt-data-league-frontend`.
  Status moved from `planned` to `in-review`; implementation is finalized
  and awaiting user review before any `done` transition.
