 dev# FEATURES.md — Feature Registry & Build Plans

This file is the single source of truth for planned, in-progress, and completed features.

**For humans:** Add new features under `## Backlog` using the template below.
**For agents:** Only work on features marked `status: ready`. Update status as you progress. Never modify features marked `status: done` or `status: in-progress` unless explicitly asked.

---

## Status Legend

| Status | Meaning |
|-|-|
| `idea` | Captured but not planned yet — no build plan written |
| `planned` | Build plan written, not yet ready to implement |
| `ready` | Build plan approved, agent can start |
| `in-progress` | Currently being implemented |
| `done` | Shipped |
| `blocked` | Waiting on a dependency or decision |

---

## Template

Copy this block to add a new feature:

```
### [FEAT-000] Feature Name
- **Status:** idea
- **Priority:** low | medium | high
- **Effort:** small (< 2h) | medium (2–8h) | large (> 8h)
- **Depends on:** —

#### Goal
One sentence: what problem does this solve for the user?

#### Acceptance Criteria
- [ ] Criterion 1
- [ ] Criterion 2

#### Feature Details
→ See [FEAT-000-DETAILS.md](./FEAT-000-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.
```

### Feature Details file format
```
# Build Plan
> Fill this in when status moves to `planned`.

1. Step 1
2. Step 2
...

# Implementation Guidelines

# Notes
Any open questions, design decisions, or links.
```
## In Progress

## Backlog

### [FEAT-005] Club entity rename to FederatedClub
- **Status:** blocked
- **Priority:** medium
- **Effort:** large (> 8h)
- **Depends on:** —

#### Goal
Rename the `Club` entity to `FederatedClub` in the backend codebase.
- Consider domain components `Club` entity to `FederatedClub`.
- DONT rename current package names.
- Consider jpa entities, helpers, mappers and repository implementations.
- Consider jpa @Query() annotation and JPQL queries.
- Consider annotation @Param("clubId").
- Consider unscoped club lookups, use uniqueness constraint to use (source, name).
- Table `club`to `federated_club`.
- Columns, used as foreign keys, named `club_id`, to be renamed to `federated_club_id`.

#### Acceptance Criteria
- [x] All references to `Club` in the domain codebase are renamed to `FederatedClub`.
- [x] Package names are NOT updated.
- [x] All JPA entities, helpers, mappers, and repository implementations are updated to reflect the new entity name.
- [x] All JPQL queries and `@Query()` annotations are updated to use the new entity name.
- [x] All `@Param("clubId")` annotations are updated to use `@Param("federatedClubId")`.
- [x] All unscoped club lookups are updated to use the uniqueness constraint `(source, name)`.
- [ ] The database table `club` is renamed to `federated_club`, and all foreign key columns named `club_id` are renamed to `federated_club_id`.
- [x] All tests and documentation are updated to reflect the new entity name and any changes made during the renaming process.
- [ ] All migration scripts are updated to reflect the new table and column names.

#### Feature Details
→ See [FEAT-005-DETAILS.md](./FEAT-005-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

## Done

---

### [FEAT-004] Club detail redesign 1
- **Status:** done
- **Priority:** medium
- **Effort:** medium (2–8h)
- **Depends on:** FEAT-003

#### Goal
Redesign the Club detail view to present club identity, navigation, filters, and competition summaries in a clear and consistent layout.

Expand `docs/frontend/club-detail-view-mockup-spec.md` into an implementation sub-plan inside `FEAT-004-DETAILS.md`, then use that sub-plan to implement the application
shell, overview page, responsive behavior, accessibility, and visual system.

#### Behaviour
- Selecting `season` or `competition` filters updates the displayed competition summaries accordingly.
- The `season` filter includes `Totes les temporades`, which displays competition summaries across all seasons.
- The `Font` filter includes `Totes les fonts` and the available import sources.
- Selecting `source`, `season`, or `competition` filters updates the available values in the other filters and the displayed data accordingly.
- The Players tab filters players by the competitions recorded for each player-season registration.
- The Players tab displays the season associated with each player-season registration.
- Selecting a club from Club search opens its detail view with `Totes les fonts`, `Totes les temporades`, and `Totes les competicions` selected by default.
- Selecting `season` or `competition` filters update each other: selecting a season filters the available competitions, while selecting a competition restricts the available seasons to those where it is present.
- Selecting a competition summary navigates to a dedicated Competition detail view for that competition and season.
- From the dedicated Competition detail view, users can navigate back to the Club detail view with the previously selected filters preserved.
- `Equips i inscripcions` section is removed from the `Partits` tab in the Club detail view.

#### Acceptance Criteria
- [x] The Club detail view presents the club identity, data source, and administrator edit action in the redesigned header.
- [x] Users can switch between player and match views and filter the displayed data by source, season, all seasons, and competition.
- [x] Club search navigation opens Club detail with all sources, all seasons, and all competitions selected by default.
- [x] Competition summaries display the competition name, season, available match count, and win/draw/loss totals.
- [x] The redesigned view is accessible and responsive across supported screen sizes.
- [x] Users can navigate back to the Club detail view from the Competition detail view with the previously selected filters preserved.
- [x] The filters `season` and `competition` are interdependent and update each other when one is selected, including competition-specific season choices.
- [x] The filters `source`, `season`, and `competition` are interdependent and update each other while preserving explicit source scoping.
- [x] The Players tab applies the selected competition to each player's related competition references.
- [x] Each player in the Players tab displays the season associated with their registration.

#### Feature Details
See [FEAT-004-DETAILS.md](./FEAT-004-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-003] Club search and club detail
- **Status:** done
- **Priority:** medium
- **Effort:** medium (2–8h)
- **Depends on:** FEAT-001, FEAT-002

#### Goal
Enable users to find clubs and inspect the relevant club information from the application.

#### Club search
- Users can search for clubs by name or other identifying information.
- Search results are displayed in a list with clear loading, empty, and error states.
- Selecting a club from the search results navigates to a dedicated Club detail view.

#### Club detail view
- Displays the club's identifying information (name, location, league, etc.).
- Displays relevant league data associated with the club.
- The detail view is accessible and responsive across supported screen sizes.
- Display a group of actions that the user can take on the club (e.g., view players, view matches, etc.) based on their access level.

##### Action: Players search (Access: Any role)
- Navigates to a dedicated Players search view filtered by the selected club.

##### Action: Matches search (Access: Any role)
- Navigates to a dedicated Matches search view filtered by the selected club.

##### Action: Edit club (Access: Admin role)
- Navigates to a dedicated Club edit view where the user can modify the club's information.

#### Acceptance Criteria
- [x] Users can search for clubs and see matching results with clear loading, empty, and error states.
- [x] Selecting a club opens a dedicated detail view with its identifying information and relevant league data.
- [x] Club detail actions allow any authorized role to open filtered player/match views, while club editing is available only to administrators and is enforced by the backend.
- [x] Search and detail views are accessible and responsive across supported screen sizes.

#### Feature Details
→ See [FEAT-003-DETAILS.md](./FEAT-003-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-002] Access control and secured navigation
- **Status:** done
- **Priority:** high
- **Effort:** large (> 8h)
- **Depends on:** FEAT-001

#### Goal
Protect application routes and data while presenting users only with navigation options permitted by their access level.

#### REST API integration
The frontend integrates with backend authentication and authorization endpoints to manage user sessions and permissions. The backend remains the authority for every protected operation.

**Strategy:** Route guards, context providers, and state management control navigation and UI presentation while Spring Security enforces protected operations.
**Architecture:** same-origin relative URLs + Vite dev proxy

#### REST API endpoints
- `POST /api/v1/auth/login` — Authenticate user and return a JWT token.
- `POST /api/v1/auth/register` — Register a new user account.
- `POST /api/v1/auth/logout` — Invalidate the current user session.
- `GET /api/v1/auth/me` — Return the authenticated user's profile and permissions; `/api/v1/user/me` remains a compatibility alias.
- `POST /api/v1/auth/password/forgot` — Start an email-based password recovery flow without revealing whether an account exists.
- `POST /api/v1/auth/password/reset` — Consume a one-time, expiring recovery token and set a new password.

#### Acceptance Criteria
- [x] Unauthenticated users are redirected to a login flow when accessing protected routes.
- [x] Authenticated users can access only the routes and actions allowed by their permissions.
- [x] Navigation reflects the current user's permissions and does not expose unauthorized destinations.
- [x] Unauthorized access produces a clear forbidden state without disclosing protected data.
- [x] Login page can allow a user registration flow, password reset, and account recovery.

#### Feature Details
→ See [FEAT-002-DETAILS.md](./FEAT-002-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-001] Frontend application skeleton and theme
- **Status:** done
- **Priority:** medium
- **Effort:** medium (2–8h)
- **Depends on:** —

#### Goal
Create a basic frontend application skeleton with a consistent theme, including routing, state management, and UI components.
Expand `docs/frontend/theme-spec.md` into an implementation sub-plan inside
`FEAT-001-DETAILS.md`, then use that sub-plan to implement the application
shell, overview page, responsive behavior, accessibility, and visual system.

#### Acceptance Criteria
- [x] A frontend application skeleton is created with routing, state management, and UI components.
- [x] `FEAT-001-DETAILS.md` contains an explicit sub-plan covering the sections and implementation requirements of `docs/frontend/theme-spec.md`.
- [x] The application follows the theme specifications defined in `docs/frontend/theme-spec.md` and the expanded sub-plan.
- [x] The application is responsive and works on different screen sizes, desktop and mobile.

#### Feature Details
→ See [FEAT-001-DETAILS.md](./FEAT-001-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---