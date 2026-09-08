# Build Plan
1. **Domain: add a `ConsolidateClubsCommand` under `tt-data-league-core-domain`**
   - New package `application/club/consolidate/` (sibling to `create/`, `delete/`, `update/`, `find/`).
   - `ConsolidateClubsCommand` (record): `id` (command id), `timestamp`, `clubIds` (`List<UUID>`, size >= 2), `canonicalName` (String), `primaryClubId` (`UUID`, must be one of `clubIds`).
   - `ConsolidateClubsCommandHandler`: implements the `CommandBus` handler contract used by `ModifyFederatedClubNameCommandHandler`. Steps:
     - Load all `Club` entities for `clubIds` via `ClubRepository.findClubById`; fail with a domain error (`"Club not found: <id>"`) if any are missing, matching the existing error string convention consumed by `ClubController`.
     - Validate `primaryClubId` is a member of `clubIds`.
     - Rename the primary `Club` to `canonicalName` via `Club.modifyName(canonicalName)` and persist (`ClubRepository.saveClub`).
     - Reassign every `FederatedClub` currently pointing at a non-primary club (via `FederatedClubRepository`, add a `findFederatedClubsByClub(Club club)` lookup if missing) to the primary club using the existing `FederatedClub.withClub(primaryClub)` repoint method; persist each via `FederatedClubRepository.saveFederatedClub`.
     - Delete the now-orphaned non-primary `Club` records (`ClubRepository.deleteClubById`) once no `FederatedClub` references them; this satisfies the "handle orphan Club records gracefully" acceptance criterion by reassignment-then-delete rather than leaving dangling canonical Club rows.
     - Publish a `ClubsConsolidatedEvent` (new domain event, mirroring `ClubNameModifiedEvent`/`ClubDeletedEvent`) carrying the primary club id and the list of merged/removed club ids, for auditability.
   - Do not touch `Team`, `PlayerSeason`, `Match`, or `lineup` rows directly — they reference `Team`/`PlayerSeason`, not `Club`, so repointing `FederatedClub.club` is sufficient to preserve their history (confirmed via `rfetm-datamodel.md` FK chain).
   - Add `ClubRepository.findFederatedClubsByClub`/equivalent only if not already present after re-checking `FederatedClubRepository`.

2. **Persistence: `tt-data-league-core-repository-jpa`**
   - Implement any new repository methods added in step 1 (e.g. `findFederatedClubsByClub`) in the JPA adapter, following existing patterns in the `club` package.
   - Confirm/adjust cascade and FK behavior so deleting a non-primary `Club` after its `FederatedClub`s are repointed does not fail on lingering references (no direct FK from `federated_club` to old `club` remains once repointed, so plain delete should be safe — verify with an integration test).
   - Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` only if the FK/cascade behavior changes; otherwise note that no schema change is required (merge is application-level reassignment, not a schema change).

3. **REST API: `tt-data-league-api-rest`**
   - Add `ConsolidateClubsRequest` DTO (record): `clubIds` (`List<UUID>`, `@Size(min = 2)`), `canonicalName` (`@NotBlank`), `primaryClubId` (`@NotNull`).
   - Add `POST /consolidate` to `ClubController` (guard with `@PreAuthorize("hasAuthority('clubs:write')")`, matching the write-guard style used for `modifyClubName`):
     - Build `ConsolidateClubsCommand` with `UUID.randomUUID().toString()` id and `ZonedDateTime.now()`, push via `commandBus`.
     - Map `"Club not found:"` errors to 404 and `primaryClubId not in clubIds`/validation errors to 400, following the `modifyClubName` error-mapping convention.
     - Return the updated `ClubDto` for the consolidated (primary) club on success.
   - Add controller tests in `ClubControllerTest` covering: success path, <2 clubs, missing club id, primary not in list, and unauthorized access.

4. **Frontend: add an Administration > Clubs route with selectable search results**
   - `tt-data-league-frontend/src/config/routes.js`: add `routePaths.administrationClubs = '/administration/clubs'` and a `routesMeta` entry (permission `clubs:write`, breadcrumb/nav label), following the `administrationUsers`/`administrationImport` pattern. Add matching entry to `navigation.js` if that's where the admin nav menu is defined.
   - New page/panel `tt-data-league-frontend/src/pages/ClubsConsolidationPanel.jsx` (or extend `AdministrationPage.jsx`'s pathname dispatch to render it), reusing the existing club search hook (`useClubSearch` from `src/hooks/useClubs.js`) to list results.
   - Add row selection: a checkbox per club row (reuse `.checkbox-option` styling from `UserForm`) plus a "select all" control; track selected club ids in local state.
   - Add a **Consolidate** button above/below the results list, disabled unless `selectedIds.length >= 2`.
   - On click, open a confirm-style dialog (reuse `.confirm-dialog`/`.confirm-dialog-actions` markup/CSS from `UsersRolesPage.jsx`) with:
     - A text input for the Canonical Name (defaulted to the currently-longest/first selected club's name, editable).
     - A radio/select control to choose which selected club is primary.
     - Cancel / Confirm actions.
   - `src/api/clubs.js`: add `consolidateClubs({ clubIds, canonicalName, primaryClubId })` calling `POST /api/v1/club/consolidate`, normalizing the response the same way other club API calls do.
   - `src/hooks/useClubs.js`: add a `useConsolidateClubs()` mutation hook (mirrors the mutation hooks used in `useUsers.js`), exposing loading/error/success state.
   - Wire mutation success/error banners (`mutationSuccess`/`mutationError` state + `.form-success`/`.form-error` classes) and a `refreshKey`-driven reload of the search results after a successful consolidation, mirroring `UsersRolesPage.jsx`.
   - Ensure the checkbox, dialog, and button are keyboard accessible with accessible names/labels (per frontend `AGENTS.md`).
   - Add/extend `clubs.test.js` for the new API function, and a component test for the panel covering: consolidate disabled with <2 selected, dialog open/submit flow, success and error banner rendering.

5. **Cross-cutting**
   - Confirm `clubs:write` (or the closest existing) permission/authority is defined for the backend `@PreAuthorize` guard and the frontend route/permission gate; add it to the permission catalog if it doesn't exist yet, following how `clubs:read` is already used in `ClubController`.
   - Run `mvn -pl tt-data-league-core-domain,tt-data-league-core-repository-jpa,tt-data-league-api-rest -am test` for backend changes, and `npm run lint` / relevant frontend tests / `npm run build` for frontend changes, per root and module `AGENTS.md`.
   - Manually verify in the running app: select 3 clubs with distinct federated clubs/teams, consolidate, and confirm team/match/player history is unchanged and non-primary clubs disappear from search results.

# Implementation Guidelines

# Notes
- Implemented ConsolidateClubsCommand/Handler, POST /consolidate REST endpoint, and Administration > Clubs consolidation panel. Backend tests: 71 passed (core-domain), 57 passed (api-rest). Frontend: lint clean, 160/160 tests passed, build succeeds. Merged to main in commit 5851b03.
