# Build Plan

1. Extend the framework-light user domain in `tt-data-league-core-domain` with the management operations needed by this feature: validated username/email changes, role replacement using the existing `UserRole` enum, and explicit active-state transitions. Preserve role-derived permissions from `Permission`; do not introduce arbitrary persisted permissions or expose password hashes.
2. Expand `UserRepository` and `UserService` with administrator-facing list, search, active-status filtering, update, deactivation, and permanent-deletion operations. Permit permanent deletion only for already deactivated users, define explicit not-found, duplicate-identity, validation, active-user, and self-protection failures, and keep the existing registration/authentication behavior unchanged.
3. Update the JPA adapter in `tt-data-league-core-repository-jpa` (`UserRepositoryHelper`, `UserRepositoryJpa`, mappers, and user tests) to persist profile and role changes, support deterministic server-side filtering/pagination, and delete only deactivated users. Preserve the existing `AppUser`/`AppUserRole` tables, UUID identity, unique username/email constraints, and disabled-user authentication behavior. Update `docs/rfetm-datamodel.md` only if the persistence contract changes.
4. Add the administrator user-management REST contract in `tt-data-league-api-rest`: paginated/searchable `GET /api/v1/user`, detail `GET /api/v1/user/{id}`, create `POST /api/v1/user`, profile/role update `PUT /api/v1/user/{id}`, active-state transition `PATCH /api/v1/user/{id}/active`, permanent deletion `DELETE /api/v1/user/{id}`, and `GET /api/v1/user/roles` for the fixed role/derived-permission catalog. Add request records and response DTOs beside `UserDto`, omit password data from every response, and use `USERS_READ`/`USERS_WRITE` authorization with consistent `400`, `401`, `403`, `404`, and `409` responses.
5. Add focused domain, repository, and REST tests covering list/search/filter/pagination, role and permission projection, create/update/deactivate/delete flows, duplicate identities, malformed roles, unauthorized/non-admin access, missing users, rejection of active-user deletion, and protection against disabling or deleting the last administrator or the current administrator when the operation would remove their access.
6. Add `../../../../tt-data-league-frontend/src/api/users.js` and a focused users hook to call the REST contract through `apiRequest`, pass the current token, cancel stale requests, normalize response data, and expose loading, empty, validation, conflict, forbidden, unauthorized, and server-error states.
7. Replace the Users and Roles branch of `../../../../tt-data-league-frontend/src/pages/AdministrationPage.jsx` with a focused management view (or split out `UsersRolesPage.jsx`) containing search/filter controls, user rows, active status, roles, derived permissions, create/edit forms, enable/disable confirmation, and a delete action shown only for deactivated users with explicit confirmation. Keep permissions read-only and sourced from the server role catalog.
8. Preserve the existing centralized routes and administrator guards in `src/config/routes.js`, `src/App.jsx`, `src/config/navigation.js`, and `RequireAuth`. Ensure direct URL access remains protected by the backend and frontend `ADMIN`/`USERS_READ`/`USERS_WRITE` checks, while responsive, keyboard-accessible sidebar and page behavior remains intact.
9. Add Catalan, Spanish, and English translations for the panel, filters, forms, role/permission labels, confirmations, validation messages, loading/empty/error states, and accessibility text. Add focused API, hook, route, and page tests using the existing frontend test setup.
10. Validate from `tt-data-league-frontend` with `npm run lint`, `npm run build`, and the existing test command; then run `mvn -pl tt-data-league-core-domain -am test`, `mvn -pl tt-data-league-core-repository-jpa -am test`, `mvn -pl tt-data-league-api-rest -am test`, and the required root `mvn test`.

# Implementation Guidelines

- Keep domain code independent of Spring, JPA, and React. Cross-module behavior must use the existing domain repository/service boundary and REST DTOs.
- Reuse `UserRole`, `Permission`, `UserDto.fromObject`, `RbacCatalog`, `apiRequest`, `useAuth`, `RequireRole`, existing page/layout primitives, and the current localization conventions before adding abstractions.
- Enforce authorization on the backend; hiding navigation and guarding routes are supplementary UX protections only.
- Use server-side pagination and filtering rather than loading the complete user inventory into the browser. Search must be scoped to user identity fields (username and email) and have a defined default sort.
- Treat role permissions as derived from the fixed role matrix. Do not add custom role/permission persistence as part of this feature.
- Prefer soft deactivation because the current domain, authentication service, and events already model `enable()`/`disable()`; permanent deletion is an explicit follow-up action restricted to deactivated users.
- Keep all user-facing copy translated, all mutation states explicit, and all API failures visible. Do not add broad catches or silent success fallbacks.
- Preserve existing public API shapes unless all callers are updated together; extend `UserDto` compatibly and keep role/permission values sorted and stable.

# Acceptance Criteria

- [x] Administrators can view a paginated list and individual details of users with their assigned roles, derived permissions, and active status.
- [x] Administrators can create users, edit usernames/emails and roles, and activate or deactivate users.
- [x] Administrators can permanently delete deactivated users, with an explicit confirmation and no delete action available for active users.
- [x] Administrators can assign only fixed roles, with permissions derived from the server role catalog and exposed read-only.
- [x] Administrators can search by username/email, filter by active status, and navigate deterministic server-side pagination.
- [x] The REST and frontend management surfaces enforce `USERS_READ`/`USERS_WRITE` access, omit password data, and expose translated loading, empty, validation, conflict, unauthorized, forbidden, and server-error states.
- [x] The users list refreshes after any user-management action, including adding, updating, activating, deactivating, or deleting a user.
- [x] The search controls use matching legends over bordered boxes, give the text search field the available width, keep the Status selector compact, and place Search/Clear actions below the filters.

# Notes

- FEAT-00020 supplies the stable `/administration/users` destination and administrator-only navigation; this feature owns the panel behavior behind that route.
- Current backend support is limited to `GET /api/v1/user/me`, `UserService` enable/disable/password operations, and a repository with `findAll`/identity lookups. The management contract and associated domain operations therefore need to be introduced rather than assumed.
- Recommended scope decisions: fixed roles with derived permissions; server-side pagination; username/email search; active/inactive/all filtering; deactivation as the default access-removal operation; permanent deletion only for deactivated users; no self-deactivation and no removal of the last `ADMIN`.
- Before implementation is marked `ready`, confirm the exact page size/default sort and whether administrators need password reset from this panel or only account/profile/role management.
- Implementation finalized in commit `6d7588b` (2026-08-30): delivered the domain/JPA pagination and management services, REST endpoints and RBAC constants, frontend API/hooks/page with translated states, and focused domain, REST, and API tests. The delivered contract uses a default page size of 20 (REST clamps invalid sizes to 20), username/email filtering, active-status filtering, fixed roles with derived permissions, soft deactivation, self-deactivation and last-admin protection, and no password fields in responses.
- Scope extension (2026-08-30): permanent-delete action for deactivated users only delivered. Domain ActiveUserDeletionException and UserAdminService.deleteUser(), JPA UserRepositoryJpa.delete(), REST DELETE /api/v1/user/{id} (requires users:write, rejects active users with 409), frontend delete button visible only for inactive users with explicit confirmation dialog, danger-button CSS, and Catalan/Spanish/English translations added. Domain, JPA, and REST tests extended.
- Follow-up validation (2026-08-30): permanent deletion now also rejects removal of the last administrator with a conflict, preserving an administrator role even when the account is already inactive.

- Registry reconciliation (2026-08-30): confirmed that the users-list-refresh acceptance criterion was already implemented via refreshKey/refresh() in UsersRolesPage.jsx (called after save, delete, and active-toggle); criterion marked [x] in FEATURES.md.
- User-approved closure (2026-08-30): FEAT-00021 is complete and moved to the Done section of the feature registry.
