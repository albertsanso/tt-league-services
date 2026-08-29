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

## Done

### [FEAT-011] Player Search and Player Detail
- **Status:** done
- **Priority:** medium
- **Effort:** large (> 8h)
- **Depends on:** FEAT-001, FEAT-002, FEAT-009

#### Goal
Enable users to search for players and inspect their canonical identity, season registrations, clubs, competitions, and match-related information from the application.

##### Player search
- Users can search for players by name or other identifying information.
- Search results are displayed in a list with clear loading, empty, validation, and error states.
- Selecting a player from the search results navigates to a dedicated Player detail view.
- Any role can search for players, but only administrators can modify player details, and this is enforced by the backend.

##### Player detail view
- Displays the player's canonical identity, source and season context, season registrations, associated clubs, competition references, and relevant match summaries.
- The detail view is accessible and responsive across supported screen sizes.
- Display a group of actions that the user can take on the player (e.g., view matches, view clubs, etc.) based on their access level.
- Player detail modification action is available only to administrators and is enforced by the backend.

#### Acceptance Criteria
- [x] Users can search for players by name with clear loading, empty, validation, and error states, while preserving explicit source scoping where source identity affects matching.
- [x] Selecting a player opens a dedicated detail view identified by the canonical Player UUID rather than an ambiguous name.
- [x] The Player detail view presents the player's identity, source and season context, season registrations, associated clubs, competition references, and relevant match summaries.
- [x] Source, season, and competition filters are explicit, interdependent, URL-persisted, and do not mix records from different source or season contexts.
- [x] Player search and detail views provide accessible navigation, responsive layouts, and stable loading, not-found, empty, retry, and unauthorized states.
- [x] REST/API adapters, frontend consumers, domain queries, persistence adapters, tests, and relevant documentation expose the agreed player search/detail contracts consistently without adding external identifiers to Player or FederatedPlayer.

#### Feature Details
→ See [FEAT-011-DETAILS.md](./FEAT-011-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-010] club search returns canonical club entities with full details
- **Status:** done
- **Priority:** medium
- **Effort:** medium (2–8h)
- **Depends on:** FEAT-003, FEAT-008

#### Goal

Return canonical `Club` entities with full details in the club search results, including federated club references, competition summaries, and player counts.
Each result has a stable identity and the complete club details needed by the search and detail views and should include the canonical club's name, federated club names,
source identifiers, competition summaries, and player counts.

#### Acceptance Criteria
- [x] Club search results identify each club by its canonical `Club` UUID rather than a source-specific `FederatedClub` identity, with full details, including federated club references, competition summaries, and player counts.
- [x] Search results include the complete approved canonical club details and the source or season context needed to interpret associated data.
- [x] Each result has a stable identity and the complete club details needed by the search and detail views.
- [x] Selecting a search result opens the matching canonical club detail without losing the result's identity or returning details for another club.
- [x] Search behavior remains explicitly source-scoped where source identity affects matching, and duplicate federated records do not produce duplicate canonical results.
- [x] REST/API adapters, frontend consumers, tests, and relevant documentation expose the agreed canonical response contract consistently.

#### Feature Details
→ See [FEAT-010-DETAILS.md](./FEAT-010-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-009] Canonical player entity
- **Status:** done
- **Priority:** medium
- **Effort:** large (> 8h)
- **Depends on:** FEAT-006

#### Goal
Establish a canonical, season-independent player entity that can be referenced consistently across seasons, imports, persistence, and APIs.

`Player` canonical entity has the following properties only:
- `id` (UUID) (Primary key, a unique identifier for the canonical player entity)
- `name` (string) (Globally unique canonical display name)

`FederatedPlayer` is a source-dependent representation of a player.
`FederatedPlayer` references a `Player` entity and retains source-specific properties such as:
- `player_id` (UUID) (Foreign key referencing the canonical `Player` entity)
- `source` (string) (The source of the player data, e.g., a specific federation or league)
- `name` (string) (The name of the player as provided by the source)

#### Acceptance Criteria
- [x] A new `Player` entity is created in the backend with a unique identifier and name.
- [x] The `FederatedPlayer` entity is updated to reference the new `Player` entity via a nullable foreign key.
- [x] Canonical-player-facing references are updated to use `Player` where appropriate while source-scoped import lookups continue to use `FederatedPlayer`.
- [x] The database schema is updated to include the new `Player` table and the foreign key relationship between `FederatedPlayer` and `Player`.
- [x] All relevant JPA entities, helpers, mappers, and repository implementations are updated to reflect the new entity structure.
- [x] All JPQL queries and `@Query()` annotations are updated to use the new entity structure.
- [x] `PlayerSeason` registration identity, licences, season data, UUIDs, and all match, lineup, and doubles-pair references remain unchanged.
- [x] Source-scoped or explicitly disambiguated player resolution is preserved; no unscoped name lookup or `(source, name)` uniqueness rule is introduced for `FederatedPlayer`.
- [x] Canonical-player linking and consolidation remain exact-name, source-scoped, opt-in, deterministic, idempotent, non-destructive, and support report mode without persistence writes.
- [x] All tests, public API adapters, and documentation are updated to reflect the new entity structure without changing player routes, response field names, or registration semantics.

#### Feature Details
→ See [FEAT-009-DETAILS.md](./FEAT-009-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-008] Canonical club entity
- **Status:** done
- **Priority:** medium
- **Effort:** large (> 8h)
- **Depends on:** —

#### Goal
Establish a canonical, season-independent club entity that can be referenced consistently across seasons, imports, persistence, and APIs.

`Club` canonical entity the following properties only:
- `id` (UUID) (Primary key a unique identifier for the canonical club entity)
- `name` (string) (Unique name of the club)

`FederatedClub` entity is a source-dependent representation of a club.
`FederatedClub` references a `Club` entity and includes additional properties such as:
- `club_id` (UUID) (Foreign key referencing the canonical `Club` entity)
- `source` (string) (The source of the club data, e.g., a specific federation or league)
- `name` (string) (The name of the club as provided by the source)

#### Acceptance Criteria
- [x] A new `Club` entity is created in the backend with a unique identifier and name.
- [x] The `FederatedClub` entity is updated to reference the new `Club` entity via a foreign key.
- [x] All existing references to `FederatedClub` in the backend codebase are updated to use the canonical `Club` entity where appropriate.
- [x] The database schema is updated to include the new `Club` table and the foreign key relationship between `FederatedClub` and `Club`.
- [x] All relevant JPA entities, helpers, mappers, and repository implementations are updated to reflect the new entity structure.
- [x] All JPQL queries and `@Query()` annotations are updated to use the new entity structure.
- [x] All tests, public API adapters, and documentation are updated to reflect the new entity structure without changing existing functionality.

#### Feature Details
→ See [FEAT-008-DETAILS.md](./FEAT-008-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

### [FEAT-007] fixes in breadcrumb and navigation
- **Status:** done
- **Priority:** medium
- **Effort:** medium (2–8h)
- **Depends on:** —

#### Goal
In the frontend, fix breadcrumb labels, links, and navigation behavior so users can move through the application consistently.

The Club detail breadcrumb is missing or incorrect in the frontend. After selecting a club from the search results, the breadcrumb should show the correct hierarchy and label for the Club detail view and provide a link back to the Club search results.

#### Acceptance Criteria
- [x] The Club detail view shows `General > Cerca de clubs > Detall del club`, with `Cerca de clubs` linking to `/clubs`.
- [x] Breadcrumb and navigation links lead to the intended destinations and preserve relevant query/navigation state.
- [x] Supported Club detail, competition detail, and edit routes use consistent breadcrumb hierarchy and valid dynamic links.
- [x] Breadcrumbs and navigation remain accessible and responsive across supported screen sizes.

#### Feature Details
→ See [FEAT-007-DETAILS.md](./FEAT-007-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.


### [FEAT-006] Player entity rename to FederatedPlayer
- **Status:** done
- **Priority:** medium
- **Effort:** large (> 8h)
- **Depends on:** —

#### Goal
Rename the season-independent `Player` entity to `FederatedPlayer` throughout the backend codebase while preserving `PlayerSeason` registration identity and the public API contract.

- Consider domain components `Player` entity to `FederatedPlayer`.
- DONT rename current package names.
- Consider JPA entities, helpers, mappers and repository implementations.
- Consider JPA `@Query()` annotations, JPQL queries, and `@Param("playerId")`.
- Replace unscoped exact player resolution with source-scoped or otherwise explicitly disambiguated lookup; do not introduce a `(source, name)` uniqueness rule because player registrations are identified through `PlayerSeason`.
- Table `player` to `federated_player`.
- Rename only foreign-key columns that target the season-independent Player entity. The `PlayerSeason` association currently uses `player_id` and should become `federated_player_id`; lineup, game, and doubles-pair `player_id` columns target `PlayerSeason` and must remain unchanged.

#### Acceptance Criteria
- [x] All references to the season-independent `Player` entity in the domain codebase are renamed to `FederatedPlayer`, while `PlayerSeason` and its registration terminology remain intact.
- [x] Package names are NOT updated.
- [x] All JPA entities, helpers, mappers, and repository implementations are updated to reflect the new entity name, including the `PlayerSeason` association to the canonical player.
- [x] All JPQL queries and `@Query()` annotations are updated to use the new entity name and all direct `@Param("playerId")` references are updated to `@Param("federatedPlayerId")`.
- [x] Unscoped exact player lookups are removed or reworked to require source and, when source plus name is still ambiguous, an explicit disambiguating identity; no unsafe first-match resolution remains.
- [ ] The database table `player` is renamed to `federated_player`, and the canonical-player foreign key `player_season.player_id` is renamed to `player_season.federated_player_id`, including related indexes, foreign keys, and constraints.
- [x] Foreign keys named `player_id`, `home_player_id`, or `away_player_id` that target `PlayerSeason` remain unchanged, along with season-specific registration, match history, lineup, and doubles-pair references.
- [x] All tests, public API adapters, and documentation are updated to reflect the renamed entity without changing player routes, response field names, or registration semantics.
- [ ] All migration scripts are updated to reflect the new table and canonical-player column names, with existing UUIDs, rows, registrations, and references preserved.

#### Feature Details
→ See [FEAT-006-DETAILS.md](./FEAT-006-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-005] Club entity rename to FederatedClub
- **Status:** done
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