 dev# FEATURES.md — Feature Registry & Build Plans

This file is the single source of truth for planned, in-progress, and completed features.

**For humans:** Add new features under `## Backlog` using the template in [`task-management.md`](./task-management.md).
**For agents:** Only work on features marked `status: ready`. Update status as you progress. Never modify features marked `status: done` or `status: in-progress` unless explicitly asked.

---

## Status Legend

| Status | Meaning |
|-|-|
| `idea` | Captured but not planned yet — no build plan written |
| `planned` | Build plan written, not yet ready to implement |
| `ready` | Build plan approved, agent can start |
| `in-progress` | Currently being implemented |
| `in-review` | Implementation finalized and awaiting user review |
| `done` | Shipped after explicit user approval |
| `blocked` | Waiting on a dependency or decision |

---

## Main index

- [FEAT-00016: Some fixes in Player details 1](### [FEAT-00016] Some fixes in Player details 1)
- [FEAT-00015: player opponent analisys - review 1](### [FEAT-00015] player opponent analisys - review 1)
- [FEAT-00014: Define layout for Player details](### [FEAT-00014] Define layout for Player details)
- [FEAT-00013: Player details fixes](### [FEAT-00013] Player details fixes)
- [FEAT-00012: Player search & detail - unique players](### [FEAT-00012] Player search & detail - unique players)
- [FEAT-00011: Player Search and Player Detail](### [FEAT-00011] Player Search and Player Detail)
- [FEAT-00010: Club search returns canonical club entities with full details](### [FEAT-00010] Club search returns canonical club entities with full details)
- [FEAT-00009: Canonical player entity](### [FEAT-00009] Canonical player entity)
- [FEAT-00008: Canonical club entity](### [FEAT-00008] Canonical club entity)
- [FEAT-00007: fixes in breadcrumb and navigation](### [FEAT-00007] fixes in breadcrumb and navigation)
- [FEAT-00006: Player entity rename to FederatedPlayer](### [FEAT-00006] Player entity rename to FederatedPlayer)
- [FEAT-00005: Club entity rename to FederatedClub](### [FEAT-00005] Club entity rename to FederatedClub)
- [FEAT-00004: Club detail redesign 1](### [FEAT-00004] Club detail redesign 1)
- [FEAT-00003: Club search and club detail](### [FEAT-00003] Club search and club detail)
- [FEAT-00002: Access control and secured navigation](### [FEAT-00002] Access control and secured navigation)
- [FEAT-00001: Frontend application skeleton and theme](### [FEAT-00001] Frontend application skeleton and theme)

## In Progress

No features currently in progress.

---

## In Review

### [FEAT-00016] Some fixes in Player details 1
- **Status:** in-review
- **Priority:** medium
- **Effort:** small (< 2h)
- **Depends on:** —

#### Goal
**Fix 1**: In Player details, in Statistics Tab, the seasons list below the plot needs to be sorted by season descending order, with the most recent season first.
**Fix 2**: In Player details, in Matches Tab, the matches list needs to be sorted by match date descending order, with the most recent match first. If the list is longer than 10 matches, the list should be paginated with 10 matches per page.
**Fix 3**: In Player details, in Opponent analysis Tab, the `Show more` component should a simple text, with a font size slightly smaller than the table font size, and a color that is slightly lighter than the table text color.
The `Show more` component should be aligned to the right of the table, and should be displayed only if there are more than 3 opponents in the category.
The newly displayed opponents should be displayed in the same table, below the first 3 opponents, and the `Show more` component should be hidden after clicking it.
**Fix 4**: In **player detail selector area**, the `season` selector should have the option **Totes les temporades** as the first option, and the `competition` selector should have the option **Totes les competicions** as the first option.
The slider must show the season marks or scales in grey color as a vertical line.
**Fix 5**: The season selector width is fixed to 100px. Make the width of the season selector dynamic, so that it can accommodate the longest season name without truncation.
**Fix 6**: The season selector should occupy the full first row of the
**player detail selector area**.
**Fix 7**: The source selector should be on the left and the competition
selector on the right of the second row of the **player detail selector area**.
**Fix 8**: The **player detail selector area** should remain responsive while
preserving the intended selector hierarchy.
**Fix 9**: The plot x-axis must be ordered by season ascending order, with the oldest season first, and the most recent season last. The plot must be responsive, so that it adjusts to the available width of the **player detail content area**.
**Fix 10**: In Matches Tab, the navigation buttons for the pagination of the matches list should be smaller and aligned with the overall styling of the application.
**Fix 11**: In Matches Tab, the pagination buttons and page counter should use the common font size of the page.
**Fix 12**: Apply the reference layout from
`docs/frontend/player-detail-selector-area-mockup-spec.md`: the season selector
occupies the full first row; the source selector is on the left of the second
row; and the competition selector is on the right of the second row.
**Fix 13**: When a selector changes, retain the current player detail view
while the filtered request is loading instead of replacing the page with a
full loading state. Review React context dependencies and the request reload
strategy so the update does not look like a browser refresh.

#### Acceptance Criteria
- [x] In Player details, in Statistics Tab, the seasons list below the plot is sorted by season descending order, with the most recent season first.
- [x] In Player details, in Matches Tab, the matches list is sorted by match date descending order, with the most recent match first. If the list is longer than 10 matches, the list is paginated with 10 matches per page.
- [x] In Player details, in Opponent analysis Tab, the `Show more` component is a simple text, with a font size slightly smaller than the table font size, and a color that is slightly lighter than the table text color. The `Show more` component is aligned to the right of the table, and is displayed only if there are more than 3 opponents in the category.
- [x] In **player detail selector area**, the `season` selector has the option **Totes les temporades** as the first option, and the `competition` selector has the option **Totes les competicions** as the first option. The slider shows the season marks or scales in grey color as a vertical line.
- [x] The season selector width is dynamic, so that it can accommodate the longest season name without truncation.
- [x] The season selector occupies the full width of the first row, as defined
  by `docs/frontend/player-detail-selector-area-mockup-spec.md`.
- [x] The source selector occupies the left side of the second row, as defined
  by the selector mockup specification.
- [x] The competition selector occupies the right side of the second row, as
  defined by the selector mockup specification.
- [x] The **player detail selector area** remains responsive, with the season
  spanning both columns and the source and competition controls sharing the
  second row.
- [x] Changing a selector retains the current player detail view during the
  filtered request and does not replace it with a full-page loading state.
- [x] React context dependencies and the request reload strategy do not cause
  unnecessary player-detail reloads.
- [x] The plot x-axis is ordered by season ascending order, with the oldest season first, and the most recent season last. The plot is responsive, so that it adjusts to the available width of the **player detail content area**.
- [x] In Matches Tab, the navigation buttons for the pagination of the matches list are smaller and aligned with the overall styling of the application.
- [x] In Matches Tab, the pagination buttons and page counter use the common font size of the page.

#### Feature Details
→ See [FEAT-00016-DETAILS.md](./FEAT-00016-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

## Backlog

No features currently in backlog.

---

## Done

### [FEAT-00015] player opponent analisys - review 1
- **Status:** done
- **Priority:** medium
- **Effort:** medium (2–8h)
- **Depends on:** FEAT-00014

#### Goal
Provide a focused opponent analysis view so users can review a player's performance against each opponent.
The player opponent analysis consists of different reports and visualizations that summarize the player's match history grouped by opponent.

#### Report: Opponent categorization
- "Hard opponents"
  - Opponents who beat the current selected player more than 50% of decided matches.
  - Display only first 3; the remaining opponents are collapsed in a "Show more" section.
- "Favorable opponents"
  - Opponents the current selected player beat more than 50% of decided matches.
  - Display only first 3; the remaining opponents are collapsed in a "Show more" section.
- "Problem opponents"
  - Hard opponents against whom the player's win percentage is at least 20 percentage points below the player's overall win percentage across the active filters. When no overall win percentage is available, any hard opponent with 2 or more matches qualifies.
  - The "≥5 matches" threshold from the original requirement was refined to the 20 pp algorithm above in the build plan; see FEAT-00015-DETAILS.md Notes.
  - Display only first 3; the remaining opponents are collapsed in a "Show more" section.

Each category is displayed in a separate table. Selectors in the **player detail selector area** (source, season, competition) issue REST requests and filter the content of both tabs.
Columns: opponent name, matches played, wins, draws, losses, win percentage.

#### Report: Opponent search
Search by name fragment (case-insensitive Catalan substring match). Displays opponent name, matches played, wins, draws, losses, win percentage, and category.

#### Removal of some sections
Remove the displayed sections from all tabs and views in the Player details view:
- `Registres federats`
- `Inscripcions per temporada`
- `Clubs associats`
- `Competicions`

#### Acceptance Criteria
- [x] The player opponent analysis view is accessible from the Player details view and displays a tabbed interface with tabs for Opponent categorization and Opponent search.
- [x] The Opponent categorization tab displays three tables for hard opponents, favorable opponents, and problem opponents, with columns for opponent name, matches played, wins, draws, losses, and win percentage.
- [x] The Opponent search tab allows users to search for a specific opponent and displays their categorization based on the player's match history, with columns for opponent name, matches played, wins, draws, losses, win percentage, and category.
- [x] The source, season, and competition selectors in the **player detail selector area** issue REST requests for filtered opponent data and affect the content displayed in both tabs of the player opponent analysis view.
- [x] The player opponent analysis view is accessible and responsive across supported screen sizes.
- [x] The player opponent analysis view handles cases where there are no opponents in a category or no search results gracefully.
- [x] The player opponent analysis view updates dynamically by requesting the selected source, season, and competition data.
- [x] Filter changes cancel stale requests, expose loading/error states, and never reuse an unfiltered response as filtered data.
- [x] The player opponent analysis view displays a "Show more" section for remaining opponents beyond the first 3.
- [x] The player opponent analysis view provides accessible fallback content for users who cannot interpret the tables, and preserves loading, empty, error, unauthorized, and not-found states.
- [x] Remove the displayed sections from all tabs and views in the Player details view: `Registres federats`, `Inscripcions per temporada`, `Clubs associats`, and `Competicions`.

#### Feature Details
→ See [FEAT-00015-DETAILS.md](./FEAT-00015-DETAILS.md) for a detailed breakdown of the delivered REST-backed architecture, remaining work (R1–R3), validation status, and implementation notes.

---

### [FEAT-00014] Define layout for Player details
- **Status:** done
- **Priority:** medium
- **Effort:** medium (2–8h)
- **Depends on:** FEAT-00011, FEAT-00012, FEAT-00013

#### Summary
Define a clear, accessible, and responsive layout for the Player details view so users can quickly understand player identity, context, history, and available actions.

#### Context

In Player details, the current selectors source, season and competition define the **player detail selector area**.
The player detail selector area is followed by the **player detail content area**, which gives access to different views of the player's history and statistics.

#### Goal

Create the **player detail content area** as a tabbed interface providing access to different views while preserving the source and season context defined in the **player detail selector area**.

The modifications on **player detail selector area** affect the **player detail content area** and all the different views contained in the tabbed interface.

##### Player stats Tab
Contains the current content of the Player details view, including the plot and table of player statistics.

##### Player matches Tab
Contains a table of the player's match history, with the ability to filter by source and season using the selectors in the **player detail selector area**. The table should include columns for match date, competition, opponent, result, and score.

#### Player opponent analysis Tab
Contains a table of the player's match history, grouped by opponent, with the ability to filter by source and season using the selectors in the **player detail selector area**. The table should include columns for opponent name, matches played, wins, draws, losses, and win percentage.

#### Acceptance Criteria
- [x] The Player details view is organized into a tabbed interface with tabs for Player stats, Player matches, and Player opponent analysis.
- [x] The Player stats tab displays the current content of the Player details view, including the plot and table of player statistics.
- [x] The Player matches tab displays a table of the player's match history, with columns for match date, competition, opponent, result, and score.
- [x] The Player opponent analysis tab displays a table of the player's match history grouped by opponent, with columns for opponent name, matches played, wins, draws, losses, and win percentage.
- [x] The source and season selectors in the **player detail selector area** affect the content displayed in all tabs of the **player detail content area**.
- [x] The Player details view is accessible and responsive across supported screen sizes.

#### Feature Details
→ See [FEAT-00014-DETAILS.md](./FEAT-00014-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

### [FEAT-00013] Player details fixes
- **Status:** done
- **Priority:** medium
- **Effort:** medium (2–8h)
- **Depends on:** FEAT-00011, FEAT-00012

#### Goal
Correct the remaining issues in the Player detail experience so users can reliably view player information and navigate through its related data.

*Fix 1*: Remove average score from the plot and table, as it is not a meaningful statistic for players. The plot should only display matches played and win percentage.

*Fix 2*: Ensure the supported plots are in the next list:
- Line chart
- Bar chart
- Connected scatter plot, rendered as one combined plot containing line
  series across all selected seasons, rather than one plot per season, with no
  filled areas between points. The plot must remain compact, use typography
  consistent with the surrounding interface, use thin series lines, and
  preserve its aspect ratio without stretching or cropping labels.

#### Acceptance Criteria
- [x] The Player detail plot and table no longer display average score, and only show matches played and win percentage.
- [x] The Player detail plot supports only line chart, bar chart, and connected scatter plot; connected scatter renders all selected seasons as unfilled, thin line series in the same compact plot, with surrounding-interface typography, proportional aspect ratio without stretched or cropped labels, seasons on the shared x-axis, and no filled areas between points.
- [x] The Player detail plot and table update correctly when source, season, or competition filters change, and the filters remain interdependent and URL-persisted.
- [x] The Player detail plot and table provide accessible fallback content for users who cannot interpret the plot, and preserve loading, empty, error, unauthorized, and not-found states.
- [x] The Player detail plot and table are responsive and adjust to different screen sizes, while maintaining accessibility and usability.

#### Feature Details
→ See [FEAT-00013-DETAILS.md](./FEAT-00013-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00012] Player search & detail - unique players
- **Status:** done
- **Priority:** medium
- **Effort:** medium (2–8h)
- **Depends on:** FEAT-00009, FEAT-00011

#### Goal
Ensure player search and detail navigation represent each canonical player only once, while providing complete, filterable source- and season-scoped history.

##### Player search strategy
- Search on FederatedPlayer because it is the source-scoped representation of a player, but return only one result.
- Group multiple FederatedPlayer records under one canonical Player UUID, preserving the source context for interpretation.
- Player names can be slightly different across sources, so the search should not rely on exact name matching alone, must use search by fragments, and must not return duplicate results for the same canonical player.

##### Player detail strategy
- Selecting a search result opens the canonical Player detail view, preserving the source and season context of the selected FederatedPlayer.
- The canonical Player detail view should display the player's identity, source and season context, season registrations, associated clubs, competition references, and relevant match summaries.
- The detail view should be accessible and responsive across supported screen sizes.
- Display a group of actions that the user can take on the player (e.g., view matches, view clubs, etc.) based on their access level.
- Player detail modification action is available only to administrators and is enforced by the backend.
- The player detail API must expose the season-level statistics required by the history visualization: matches played, win percentage, and average score, with explicit source and season context.

##### Player detail mockup
- Display player name and license number at the top of the detail view.
- Display a source selector based on radio buttons for each available source, with the current source highlighted. Add a radio button to select "All sources" and show all available sources.
- Display a season selector based on a horizontal slider of available seasons, with the current season highlighted. Add a radio button to select "All seasons" and show all available seasons.
- Display a plot graphical view of the player's match history, with the ability to filter by source and season using the selectors above.
- Display a table of the player's match history, with the ability to filter by source and season using the selectors above. The table should include columns for match date, competition, opponent, result, and score.

##### History visualization for Player detail
- Display an accessible, responsive plot of the player's history statistics.
- Use the available seasons on the x-axis.
- Display series for matches played, win percentage, and average score.
- Ensure the plot updates when source, season, or competition filters change.
- Provide a non-graph fallback or accessible data representation for users who cannot interpret the plot.

##### Responsive plot for Player history statistics
- The plot type is selectable from a selector dropdown, with options for:
  - Series comparison
    - Line chart
    - Bar chart
  - Stacked comparison
    - Stacked bar chart
  - Correlation comparison
    - Scatter plot
    - Bubble chart
    - Heatmap
- The plot should be responsive and adjust to different screen sizes.
- The plot should be accessible and provide alternative text descriptions for users who cannot interpret the plot.

##### Match history table for Player detail

#### Acceptance Criteria
- [x] Player search returns one result per canonical Player UUID, with source context retained for interpretation.
- [x] Duplicate federated or season-specific records do not create duplicate search results or ambiguous detail navigation.
- [x] Selecting a result opens the matching canonical player detail and preserves its available source and season context in the URL and displayed filters.
- [x] The player detail API and read model expose complete, source- and season-scoped history statistics for matches played, win percentage, and average score without changing persisted identity or historical references.
- [x] The detail view provides an accessible source selector with an “All sources” option and a responsive season slider with an “All seasons” option; filter changes remain URL-persisted and interdependent.
- [x] The detail view renders a responsive history plot with seasons on the x-axis and series for matches played, win percentage, and average score, updating for the selected filters.
- [x] The detail view provides an accessible tabular or textual fallback for the plotted statistics and preserves loading, empty, error, unauthorized, and not-found states.
- [x] Existing player registration, club, competition, match, and lineup history remains intact while results are deduplicated and statistics are filtered only in the read projection.
- [x] REST, frontend, and focused regression tests cover statistics serialization, source/season filtering, slider behavior, plot updates, accessible fallback content, canonical navigation, and failure states.
- [x] The history plot provides a keyboard-accessible type selector with line, bar, stacked-bar, scatter, bubble, and heatmap options, and each option preserves the same season and metric data.
- [x] The plot includes an explicit legend, axis labels, units, and an accessible textual alternative that describes the selected plot type and its displayed values.
- [x] The match-history table includes date, competition, opponent, result, and score columns, preserves source and season context, and represents unavailable scores as an em dash rather than an invented value.
- [x] All-source and all-season views aggregate every eligible source-scoped match without arbitrary history truncation; source, season, and competition changes update both the plot and table consistently.
- [x] Unlinked federated search records remain distinct, retain their source context, and do not navigate to a fabricated canonical player detail URL.
- [x] Malformed or unavailable statistics produce an explicit error or unavailable state at the API boundary and never render invalid percentages, counts, or averages.

#### Feature Details
→ See [FEAT-00012-DETAILS.md](./FEAT-00012-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00011] Player Search and Player Detail
- **Status:** done
- **Priority:** medium
- **Effort:** large (> 8h)
- **Depends on:** FEAT-00001, FEAT-00002, FEAT-00009

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
→ See [FEAT-00011-DETAILS.md](./FEAT-00011-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00010] club search returns canonical club entities with full details
- **Status:** done
- **Priority:** medium
- **Effort:** medium (2–8h)
- **Depends on:** FEAT-00003, FEAT-00008

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
→ See [FEAT-00010-DETAILS.md](./FEAT-00010-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00009] Canonical player entity
- **Status:** done
- **Priority:** medium
- **Effort:** large (> 8h)
- **Depends on:** FEAT-00006

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
→ See [FEAT-00009-DETAILS.md](./FEAT-00009-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00008] Canonical club entity
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
→ See [FEAT-00008-DETAILS.md](./FEAT-00008-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

### [FEAT-00007] fixes in breadcrumb and navigation
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
→ See [FEAT-00007-DETAILS.md](./FEAT-00007-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.


### [FEAT-00006] Player entity rename to FederatedPlayer
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
→ See [FEAT-00006-DETAILS.md](./FEAT-00006-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00005] Club entity rename to FederatedClub
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
→ See [FEAT-00005-DETAILS.md](./FEAT-00005-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00004] Club detail redesign 1
- **Status:** done
- **Priority:** medium
- **Effort:** medium (2–8h)
- **Depends on:** FEAT-00003

#### Goal
Redesign the Club detail view to present club identity, navigation, filters, and competition summaries in a clear and consistent layout.

Expand `docs/frontend/club-detail-view-mockup-spec.md` into an implementation sub-plan inside `FEAT-00004-DETAILS.md`, then use that sub-plan to implement the application
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
See [FEAT-00004-DETAILS.md](./FEAT-00004-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00003] Club search and club detail
- **Status:** done
- **Priority:** medium
- **Effort:** medium (2–8h)
- **Depends on:** FEAT-00001, FEAT-00002

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
→ See [FEAT-00003-DETAILS.md](./FEAT-00003-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00002] Access control and secured navigation
- **Status:** done
- **Priority:** high
- **Effort:** large (> 8h)
- **Depends on:** FEAT-00001

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
→ See [FEAT-00002-DETAILS.md](./FEAT-00002-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00001] Frontend application skeleton and theme
- **Status:** done
- **Priority:** medium
- **Effort:** medium (2–8h)
- **Depends on:** —

#### Goal
Create a basic frontend application skeleton with a consistent theme, including routing, state management, and UI components.
Expand `docs/frontend/theme-spec.md` into an implementation sub-plan inside
`FEAT-00001-DETAILS.md`, then use that sub-plan to implement the application
shell, overview page, responsive behavior, accessibility, and visual system.

#### Acceptance Criteria
- [x] A frontend application skeleton is created with routing, state management, and UI components.
- [x] `FEAT-00001-DETAILS.md` contains an explicit sub-plan covering the sections and implementation requirements of `docs/frontend/theme-spec.md`.
- [x] The application follows the theme specifications defined in `docs/frontend/theme-spec.md` and the expanded sub-plan.
- [x] The application is responsive and works on different screen sizes, desktop and mobile.

#### Feature Details
→ See [FEAT-00001-DETAILS.md](./FEAT-00001-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---