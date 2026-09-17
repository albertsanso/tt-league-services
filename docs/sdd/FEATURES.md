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

- [FEAT-00072: Fix W/L chronology chips in match details at player level](### [FEAT-00072] Fix W/L chronology chips in match details at player level)

- [FEAT-00071: Fix chronological W/L/D chips in matches](### [FEAT-00071] Fix chronological W/L/D chips in matches)
- [FEAT-00070: Match details label home/away team](### [FEAT-00070] Match details label home/away team)
- [FEAT-00069: BCNESA home/away orientation in extraction and import](### [FEAT-00069] BCNESA home/away orientation in extraction and import)
- [FEAT-00068: Mismatch in Match details: Team title and summary not matching with Players alignemt below](### [FEAT-00068] Mismatch in Match details: Team title and summary not matching with Players alignemt below)
- [FEAT-00067: Close button in acta modal](### [FEAT-00067] Close button in acta modal)
- [FEAT-00066: Limit tied results or draws display](### [FEAT-00066] Limit tied results or draws display)
- [FEAT-00065: Add MCP server layer](### [FEAT-00065] Add MCP server layer)
- [FEAT-00064: Add summary in Matches tab in player details](### [FEAT-00064] Add summary in Matches tab in player details)
- [FEAT-00063: Add player summary and search in Club details, Players tab](### [FEAT-00063] Add player summary and search in Club details, Players tab)
- [FEAT-00062: Add links to Match detail](### [FEAT-00062] Add links to Match detail)
- [FEAT-00061: Opponent insights metrics](### [FEAT-00061] Opponent insights metrics)
- [FEAT-00060: Oponent analysis redesign](### [FEAT-00060] Oponent analysis redesign)
- [FEAT-00059: Player details Matches tab redesign](### [FEAT-00059] Player details Matches tab redesign)
- [FEAT-00058: Global search](### [FEAT-00058] Global search)
- [FEAT-00057: Match Summary landing page](### [FEAT-00057] Match Summary landing page)
- [FEAT-00056: Matches search improvements](### [FEAT-00056] Matches search improvements)
- [FEAT-00055: Access actas from Matches search](### [FEAT-00055] Access actas from Matches search)
- [FEAT-00054: Club detail Summary and Stats tabs](### [FEAT-00054] Club detail Summary and Stats tabs)
- [FEAT-00053: Link list of club players to Player details](### [FEAT-00053] Link list of club players to Player details)

## In Progress

No features currently in progress.
## In Review

No features currently in review.
## Backlog

No features currently in the backlog.
## Done

### [FEAT-00072] Fix W/L chronology chips in match details at player level
- **Status:** done
- **Priority:** medium
- **Effort:** medium
- **Depends on:** —

#### Goal
The per-player W/L chips in the Match details lineup table are computed from the wrong set of matches: unlike the team strip above them (fixed in FEAT-00071), they draw on the player's five most recent matches of the whole source+season, so a historical match shows results that had not happened yet and mixes in matches from every other competition the player played.

#### Acceptance Criteria
- [x] Match details per-player form windows only use matches played before the viewed match, so a historical match never shows a player's later results
- [x] Match details per-player form is scoped to the viewed match's own competition, matching the team strip above it
- [x] Both filters are applied before the last-5 window is taken, so out-of-scope matches never consume a form slot
- [x] Player form remains cross-team within the competition, so a mid-season transfer's earlier matches still count
- [x] The per-player win rate follows the corrected window
- [x] Draws remain excluded from player form (FEAT-00066)
- [x] Regression tests cover competition scoping, before-the-match scoping, and the cross-team transfer case; they are verified to fail before the fix
- [x] The Player detail page form guide and streak are unchanged

#### Feature Details
→ See [FEAT-00072-DETAILS.md](./FEAT-00072-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

---

---

---

---

### [FEAT-00071] Fix chronological W/L/D chips in matches
- **Status:** done
- **Priority:** medium
- **Effort:** medium
- **Depends on:** —

#### Goal
The recent-form W/L/D chips shown for a team are computed from the wrong set of matches. On the Match details page the team's last-5 strip mixes in matches from every other competition that team played in the same source+season, and it draws on the season's latest results rather than the form going into the viewed match. On the Player/Club detail pages the strip includes pending matches with no recorded score.

#### Acceptance Criteria
- [x] Match details team form and alignment stability are scoped to the viewed match's own competition, not just its source and season
- [x] Match details recent-form windows only use matches played before the viewed match, so a historical match never reports results that had not happened yet
- [x] getFormGuide excludes pending matches (no recorded score) so the chip strip shows only decided matches, oldest to newest
- [x] getCurrentStreak excludes pending matches when computing the current win/loss/draw streak
- [x] Regression tests cover competition scoping and before-the-match scoping on the backend, and pending-match exclusion on the frontend
- [x] ClubSummaryPanel's plain recent-matches list (which intentionally includes pending matches) is unaffected

#### Feature Details
→ See [FEAT-00071-DETAILS.md](./FEAT-00071-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00070] Match details label home/away team
- **Status:** done
- **Priority:** medium
- **Effort:** medium
- **Depends on:** —

#### Goal
Show a home/away label on each team's summary panel on the Match details page so users know which side each team played on.

#### Acceptance Criteria
- [x] A small gray label reading Home or Away appears at the top of each team's summary panel on the Match details page, matching the team's side.
- [x] The label is translated (ca/es/en) consistently with the rest of the page.

#### Feature Details
→ See [FEAT-00070-DETAILS.md](./FEAT-00070-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

---

---

---

---

### [FEAT-00069] BCNESA home/away orientation in extraction and import
- **Status:** done
- **Priority:** medium
- **Effort:** medium
- **Depends on:** —

#### Goal
Store BCNESA matches with the real home and away teams, each side's own lineup, games, doubles and scores, and a games-won score instead of set totals, from PDF extraction through import.

#### Acceptance Criteria
- [x] The BCNESA extractor sets equipos.local/visitante to the real home/away teams from the acta header, not from the ABC/XYZ alignment line
- [x] When the A/B/C team is the away team, alineaciones, dobles and partidos are written by real side and abc_es_local is false
- [x] resultado_final.marcador_partidos holds games won (home/away) and marcador_juegos holds set totals (home/away); ganador is the team with more games
- [x] The BCNESA importer stores matches whose X/Y/Z side is home with lineups, games and doubles on the correct teams, covered by tests
- [x] Regenerated BCNESA JSON has no file whose lineup letters disagree with abc_es_local, and orientation is determined for every page with games

#### Feature Details
→ See [FEAT-00069-DETAILS.md](./FEAT-00069-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00068] Mismatch in Match details: Team title and summary not matching with Players alignemt below
- **Status:** done
- **Priority:** medium
- **Effort:** medium
- **Depends on:** —

#### Goal
Fix the Match details page (Match Summary) so the team summary panel match the players alignment below it, so the players belong to the correct team on that column, and the team order and identity shown in the match title/score header match the team order and identity of the panels rendered below it.

#### Description
For instance the screenshot `assets/img/match-details-mismatch-team-and-players.png` shows a match where the team title and summary on the left is "Club A" but the players listed below it are actually from "Club B", and vice versa for the right side. This is confusing for users and needs to be corrected.
The team A and Players belonging to team A are in red boxes and must be in the same column, and the team B and Players belonging to team B are in blue boxes and must be in the same column. The team order and identity shown in the match title/score header must match the team order and identity of the panels rendered below it.

#### Acceptance Criteria
- [x] The team title and summary on the left column of the Match details page matches the players alignment below it, so the players belong to the correct team on that column.
- [x] The team title and summary on the right column of the Match details page matches the players alignment below it, so the players belong to the correct team on that column.
- [x] The team order and identity shown in the match title/score header matches the team order and identity of the panels rendered below it.
- [x] Implementation matches the layout and content documented in [`/docs/frontend/match-details-team-player-alignment.md`](../frontend/match-details-team-player-alignment.md); any deviation is noted in FEAT-00068-DETAILS.md before merging

#### Feature Details
→ See [FEAT-00068-DETAILS.md](./FEAT-00068-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

---

---

---

---

---

---

### [FEAT-00067] Close button in acta modal
- **Status:** done
- **Priority:** medium
- **Effort:** medium
- **Depends on:** —

#### Goal
Add a visible close (X) button to the match acta modal so users can dismiss it without relying on Escape or clicking the overlay.

#### Acceptance Criteria
- [x] A close button is visible in the acta modal header
- [x] Clicking the close button dismisses the modal, same as pressing Escape or clicking the overlay

#### Feature Details
→ See [FEAT-00067-DETAILS.md](./FEAT-00067-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

---

---

---

### [FEAT-00066] Limit tied results or draws display
- **Status:** done
- **Priority:** medium
- **Effort:** medium
- **Depends on:** —

#### Goal
- Display tied results only at the team level, and not at the player level.
- Display tied results or draws only for the Superdivision Masc and Fem categories, and not for any other categories (e.g., season 25/26).
- Remove any tied results or draws from the Player stats.
- Remove any tied results or draws from the Team stats and match records for all other categories that are not Superdivision Masc and Fem

#### Acceptance Criteria
- [x] Tied results or draws are displayed only at the team level, and not at the player level.
- [x] Tied results or draws are displayed only for the Superdivision Masc and Fem categories, and not for any other categories (e.g., season 25/26).
- [x] Tied results or draws are removed from the Player stats.
- [x] Tied results or draws are removed from the Team stats and match records for all other categories that are not Superdivision Masc and Fem.

#### Feature Details
→ See [FEAT-00066-DETAILS.md](./FEAT-00066-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00065] Add MCP server layer
- **Status:** done
- **Priority:** medium
- **Effort:** medium
- **Depends on:** —

#### Goal
Expose league data (players, clubs, matches, stats) through an MCP server so MCP clients like Claude can query it as tools/resources.

Added as a new maven module `tt-data-league-api-mcp` (naming aligned with the
existing `tt-data-league-api-rest`/`tt-data-league-api-runtime` modules),
embedded in the existing `tt-data-league-api-runtime` Spring Boot app rather
than run standalone. It exposes read-only MCP tools for players, clubs,
matches, and stats data, backed by the existing `QueryBus` application
queries. The MCP endpoint itself is unauthenticated; access is instead
controlled by whether the server is enabled at all
(`spring.ai.mcp.server.enabled`), which defaults on under the `dev` profile
and off under the `prod` profile.

#### Acceptance Criteria
- [x] MCP server is implemented as a new maven module `tt-data-league-api-mcp`, embedded in the existing `tt-data-league-api-runtime` Spring Boot app
- [x] MCP server exposes read-only tools for players, clubs, matches, and stats data, following the MCP protocol (via Spring AI's MCP server starter)
- [x] MCP server endpoint (`/mcp/**`) does not require authentication
- [x] `tt-data-league-api-runtime`'s `application.yml` exposes a property to enable/disable the MCP server (`spring.ai.mcp.server.enabled`)
- [x] The MCP server is enabled by default under the `dev` Spring profile (`application-dev.yml`) and disabled under the `prod` Spring profile (`application-prod.yml`)
- [x] MCP server is documented with usage instructions for connecting an MCP client, in `tt-data-league-api-mcp/README.md`
- [x] MCP server tools are covered by unit tests, and the server's startup, tool registration, unauthenticated access, and profile-gated enable/disable behavior were verified against a running instance

#### Feature Details
→ See [FEAT-00065-DETAILS.md](./FEAT-00065-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00064] Add summary in Matches tab in player details
- **Status:** done
- **Priority:** medium
- **Effort:** medium
- **Depends on:** FEAT-00063 (Club details Matches summary strip — styling/content reference)

#### Goal
Let users see at-a-glance match stats (record, home/away split, form guide, notable matches) at the top of Player details, Matches tab, matching the summary strip already shipped for Club details, Matches tab (FEAT-00063).

#### Acceptance Criteria
- [x] Player details, Matches tab shows a "Matches summary" strip above the existing match list/pagination, following the same styling and content pattern as the Matches summary strip on Club details, Matches tab (FEAT-00063): stat tiles, a form guide with current streak, and a notable-matches card
- [x] The strip shows stat tiles for: total matches (in the current Source/Season/Competition filters), win rate (with W/D/L breakdown), home vs. away win-rate split, and count of pending (not-yet-scored) matches
- [x] The strip shows a last-5 form guide (chronological W/L/D chips) with the player's current streak
- [x] The strip shows a "Notable matches" mini-list (closest result, biggest win, biggest defeat), each linking to the match detail page, omitting any slot with no qualifying match and never repeating the same match under two labels
- [x] The strip is additive: it reads only from data the Matches tab already fetches (`data.matches`), scopes to the tab's existing Source/Season/Competition filters, and does not change the existing match list, pagination, or empty state
- [x] Implementation reuses the club-details Matches summary building blocks (`clubSummary.js` helpers, `StatTile`, and the record-bar/mini-list/match-row styling) where the data shapes allow, rather than duplicating logic, adapting for the player-level match record (which already carries `playerTeam` alongside `homeTeam`/`awayTeam`, so no team-resolution step is needed for the home/away split)

#### Feature Details
→ See [FEAT-00064-DETAILS.md](./FEAT-00064-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00063] Add player summary and search in Club details, Players tab
- **Status:** done
- **Priority:** medium
- **Effort:** medium
- **Depends on:** —

#### Goal
Let users see a quick players summary and filter the Players tab list by name in Club details, so they can find a specific player in clubs with many registered players without scrolling the whole list.

#### Acceptance Criteria
- [x] Players tab shows a summary count (e.g. shown vs total players) above the list
- [x] Players tab has a text search input that filters the visible player list by player/registration name as the user types
- [x] Search matches are case-insensitive and accent-insensitive, consistent with existing search behavior elsewhere in the app
- [x] Existing Source/Competition filters and player links (canonicalPlayerId) continue to work unchanged and combine with the name search
- [x] Empty-state message is shown when the search yields no matches, distinct from the existing no-players-at-all empty state
- [x] Players tab only lists players with a `canonicalPlayerId` (unconsolidated players are hidden); the summary count and no-players empty state reflect only canonical players
- [x] Players tab list is not scoped by the Season filter: each canonical player appears exactly once regardless of how many season records they have, and each row shows only the player's name (no per-season/license subtext)
- [x] Players tab shows a "Players summary" strip above the search box with roster-composition stats (player/competition/federation counts, players-by-competition breakdown, career "most active" players) — see [players.md](../frontend/club-details/players.md)
- [x] Matches tab shows a "Matches summary" strip above the source/season/competition hierarchy (match count, win rate, home vs. away split, pending-result count, last-5 form guide with current streak, notable closest/biggest-win/biggest-defeat matches) — see [matches.md](../frontend/club-details/matches.md)
- [x] Both new summary strips read from data already fetched by their tab, scope to the tab's existing filters, and introduce no new filter state or changes to existing empty states

#### Feature Details
→ See [FEAT-00063-DETAILS.md](./FEAT-00063-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps (includes the state-of-the-art research behind this expansion).

---

---

---

---

---

---

---

---

### [FEAT-00062] Add links to Match detail
- **Status:** done
- **Priority:** medium
- **Effort:** medium
- **Depends on:** —

#### Goal
- From the match detail page, let users navigate to related entities (teams, competition) that are not yet clickable, matching the existing player-name links.
- From Player details, Matches tab, let users navigate to the match detail page for a given match, matching the existing player-name links and back navigation.
- From the Player details, Matches tab, on displayed Players, let users navigate to the Player detail page for a given player, matching the existing player-name links and back navigation.
- From the Player details, Oponent analysis tab, on displayed Matches, let users navigate to the match detail page for a given match, matching the existing player-name links and back navigation.

#### Acceptance Criteria
- [x] Match detail page shows clickable links for both teams and the competition, navigating to the corresponding detail pages
- [x] Player details, Matches tab, shows clickable links for each match, navigating to the corresponding match detail page
- [x] Player details, Matches tab, shows clickable links for each player, navigating to the corresponding player detail page
- [x] Player details, Oponent analysis tab, shows clickable links for each match, navigating to the corresponding match detail page
- [x] Implementation matches the layout, linking patterns, and content documented in [`/docs/frontend/match-detail-linking.md`](../frontend/match-detail-linking.md); any deviation is noted in FEAT-00062-DETAILS.md before merging

#### Feature Details
→ See [FEAT-00062-DETAILS.md](./FEAT-00062-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

---

---

---

---

### [FEAT-00061] Opponent insights metrics
- **Status:** done
- **Priority:** medium
- **Effort:** medium
- **Depends on:** —

#### Goal
Expand the Player Details Opponent Analysis unified list with an Opponent insights panel exposing closeness, singles/doubles split, home/away split, longest streaks, rivalry frequency, competition breakdown, and result-quality trend, per the proposal in docs/frontend/oponent-analysis-metrics-expansion.md, computed client-side with no backend changes.

#### Acceptance Criteria
- [x] Implementation follows the metrics catalog and layout described in docs/frontend/oponent-analysis-metrics-expansion.md
- [x] Expanding an opponent's head-to-head reveals an "Opponent insights" panel of metric tiles (closeness, singles/doubles split, home/away split, longest streaks, rivalry frequency/recency, trend), each shown only when meaningful, above the unchanged per-match table
- [x] Competition breakdown appears only for opponents faced across more than one competition
- [x] The opponent sort dropdown gains a "Closeness" option, sorting by absolute average set margin ascending
- [x] All metrics are computed client-side from data already available to `PlayerDetailPage.jsx`; no new API fields or schema changes are introduced

#### Feature Details
→ See [FEAT-00061-DETAILS.md](./FEAT-00061-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00060] Oponent analysis redesign
- **Status:** done
- **Priority:** medium
- **Effort:** medium
- **Depends on:** —

#### Goal
Redesign the Player Details Opponent Analysis tab UI/UX by replacing the nested Categorization/Search sub-tabs with a unified, filterable opponent list per the proposal 
in docs/frontend/oponent-analysis-redesign.md, without changing underlying data or business logic.

#### Acceptance Criteria
- [x] Implementation follows the layout and interaction plan described in docs/frontend/oponent-analysis-redesign.md
- [x] The Opponent Analysis tab shows a single filterable list of opponents with category filter chips (All / Favourable / Difficult / Problematic, each showing a live count) and a search box for opponent name
- [x] Each opponent row shows the opponent name, a category badge, number of matches played, win/draw/loss record, and win rate percentage
- [x] Clicking an opponent row's head-to-head toggle expands an inline per-match breakdown with match date, competition, result, and score

#### Feature Details
→ See [FEAT-00060-DETAILS.md](./FEAT-00060-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

---

---

---

---

---

---

### [FEAT-00059] Player details Matches tab redesign
- **Status:** done
- **Priority:** medium
- **Effort:** medium
- **Depends on:** —

#### Goal
Improve the UX/readability of the Matches tab on the Player detail page by replacing the current 11-column table with a card-based layout that surfaces result, opponent, score, and date, and moves per-game breakdown into an expandable disclosure.

#### Acceptance Criteria
- [x] The Matches tab renders one card per match instead of a table row, showing a colored W/D/L result badge, opponent team name, score, and date as primary content
- [x] Season, source, group, and phase are shown as secondary context chips on the card instead of separate table columns
- [x] The per-game (singles/doubles) breakdown is moved into an expandable disclosure per card instead of nested lists inside table cells, preserving all currently shown per-game opponent/result data
- [x] Existing pagination (10 matches per page) and existing filters (season, source, competition) above the tabs are unchanged in behavior
- [x] Implementation matches the layout and content documented in /docs/frontend/player-details-matches-tab.md; any deviation is noted in the feature details file before merging

#### Feature Details
→ See [FEAT-00059-DETAILS.md](./FEAT-00059-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

---

---

---

---

### [FEAT-00058] Global search
- **Status:** done
- **Priority:** medium
- **Effort:** medium
- **Depends on:** —

#### Goal
Let a user search across players, clubs, and matches from one global search entry point instead of navigating to each dedicated search page separately.

In the Landing page, in Global Search component, when typing in the global search text box, the suggestion dropdown should show results grouped by entity type (Players, Clubs, Matches).
Selecting a result should navigate to the corresponding detail/search page for that entity, consistent with existing linking patterns.

#### Acceptance Criteria
- [x] A global search entry point (e.g. header search bar) is accessible from the main app navigation
- [x] Entering a query returns matching results grouped by entity type (Players, Clubs, Matches)
- [x] Selecting a result navigates to the corresponding detail/search page for that entity, consistent with existing linking patterns
- [x] Implementation matches the layout, grouping, and content documented in [`/docs/frontend/global-search-mockup-spec.md`](../frontend/global-search-mockup-spec.md); any deviation is noted in FEAT-00058-DETAILS.md before merging

#### Feature Details
→ See [FEAT-00058-DETAILS.md](./FEAT-00058-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00057] Match Summary landing page
- **Status:** done
- **Priority:** medium
- **Effort:** medium
- **Depends on:** FEAT-00055 (acta dialog), FEAT-00053 (player detail linking), FEAT-00054 (club summary/stats aggregation patterns)

#### Goal
Let a user viewing Matches search click a match result to open a Match summary landing page showing both teams, lineups, recent-form stats, alignment-stability analysis, and a link to the acta.

#### Acceptance Criteria
- [x] Clicking a match result row/card in Matches search navigates to a new Match summary landing page at /partits/:matchId
- [x] The page shows both clubs/teams and their lineup players (letter, name, ranking), each lineup player linking to the Player detail page when canonicalPlayerId exists
- [x] The page shows each team's recent-form stats (last N matches record and win rate, with a trend note vs the previous N matches)
- [x] The page shows each lineup player's recent-form stats (last N matches record and win rate)
- [x] The page shows an alignment-stability analysis per team: how many times this exact set of lineup players has been fielded together this season/source and its aggregate win rate, compared against the team's overall win rate, with a distinct state for a lineup fielded for the first time
- [x] The page includes a View acta action that opens the existing MatchActaDialog for this match, matching the acta link behavior from Matches search (FEAT-00055)
- [x] Implementation matches the layout and content documented in [`/docs/frontend/match-details-page.md`](../frontend/match-details-page.md); any deviation is noted in FEAT-00057-DETAILS.md before merging

#### Feature Details
→ See [FEAT-00057-DETAILS.md](./FEAT-00057-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

---

---

---

---

### [FEAT-00056] Matches search improvements
- **Status:** done
- **Priority:** medium
- **Effort:** medium
- **Depends on:** —

#### Goal
Improve the Matches search experience for users (scope to be refined).

#### Description
1. In the matches search filters, add a Club name filter as a free text search field, allowing users to filter matches by club name (home or away).
2. Remove Phase filter from the matches search filters, as it is not relevant to the majority of users and adds unnecessary complexity.
3. The text searches like Player name and Club name should support partial using fragments matches and be case-insensitive, improving usability.
4. The search button is only enabled once the minimum required filters are set: Source/Federation and Season. Competition and every other filter are optional.
5. Add pagination to Matches search results, allowing navigation between next page, previous page, and first/last page.

#### Acceptance Criteria
- [x] Matches search filters include a Club name filter as a free text search field, allowing users to filter matches by club name (home or away)
- [x] Phase filter is removed from the matches search filters
- [x] Text searches like Player name and Club name match ANY whitespace-separated fragment of the search term (not the whole phrase) and are case-insensitive
- [x] The search button is enabled once Source and Season are set; Competition and all other filters are optional
- [x] Matches search results show pagination controls to navigate to the first page, previous page, next page, and last page, duplicated both above and below the results list; controls are hidden when everything fits on one page and disabled at the start/end of the range
- [x] Implementation matches the layout, filter order, and content documented in [`/docs/frontend/match-search-filters-mockup-spec.md`](../frontend/match-search-filters-mockup-spec.md); any deviation is noted in FEAT-00056-DETAILS.md before merging
- [x] Existing matches search tests are updated/extended to cover the new filter and pagination behavior

#### Feature Details
→ See [FEAT-00056-DETAILS.md](./FEAT-00056-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00055] Access actas from Matches search
- **Status:** done
- **Priority:** medium
- **Effort:** medium
- **Depends on:** —

#### Goal
Let users open the original acta (match report) document for a match directly from the Matches search results.

#### Description
- Align styling and layout of the Matches search results with other search results (Players, Clubs) to include a link or action to view the source acta document for each match.
- Each match row/detail in the Matches search results should show a link or action to view its source acta document.
- Selecting the acta action should open or download the acta file associated with that match.
- Matches with no associated acta available should show the action as disabled or hidden, without erroring.
- Frontend rendering of the acta must follow the wiremock spec in [acta-simplified-rfetm.md](../frontend/actas/acta-simplified-rfetm.md).

#### Acceptance Criteria
- [x] Matches search results show a link or action to view the source acta document for each match
- [x] Align frontend styling and layout of the Matches search results with other search results (Players, Clubs) to include the acta link/action
- [x] Each match row/detail in Matches search shows a link or action to view its source acta document
- [x] Selecting the acta action opens or downloads the acta file associated with that match
- [x] Matches with no associated acta available show the action as disabled or hidden, without erroring
- [x] The frontend acta view matches the structure/fields defined in [acta-simplified-rfetm.md](../frontend/actas/acta-simplified-rfetm.md)

#### Feature Details
→ See [FEAT-00055-DETAILS.md](./FEAT-00055-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00054] Club detail Summary and Stats tabs
- **Status:** done
- **Priority:** medium
- **Effort:** medium
- **Depends on:** —

#### Goal
Give club detail page visitors a landing overview and a dedicated stats breakdown, instead of arriving directly on the raw Players list.

#### Acceptance Criteria
- [x] Club detail page defaults to a new Summary tab instead of Players. 
- [x] Summary tab shows player/match/win-rate/competition stat tiles, a recent-matches card, and a top-players card, each linking into the corresponding tab. 
- [x] A new Stats tab shows win/draw/loss breakdown per competition for a selected season plus a win-rate-by-season trend; existing Players and Matches tabs and their filters are unchanged.
- [x] Implementation matches the layout, tab order, and content documented in [`/docs/frontend/club_details_landing.md`](../frontend/club_details_landing.md); any deviation is noted in FEAT-00054-DETAILS.md before merging
- [x] Summary tab adds a "Top performer player" ranking by actual win rate (backed by a per-player win/draw/loss aggregation added to the club detail API), distinct from the existing "Most-capped players" card which ranks by number of competitions played.

#### Feature Details
→ See [FEAT-00054-DETAILS.md](./FEAT-00054-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

### [FEAT-00053] Link list of club players to Player details
- **Status:** done
- **Priority:** medium
- **Effort:** medium
- **Depends on:** —

#### Goal
Let a user viewing a club's players list click a player to open that player's details page, matching the linking pattern already used in the player search results.

#### Acceptance Criteria
- [x] Each player in the club detail Players panel that has a canonicalPlayerId is rendered as a link to the player details page (matching PlayersSearchPage's linking pattern), carrying source/season context as return search params
- [x] Players without a canonicalPlayerId (unmatched/unconsolidated players) render as before, without a link
- [x] Existing club players list tests are updated/extended to cover the new link behavior

#### Feature Details
→ See [FEAT-00053-DETAILS.md](./FEAT-00053-DETAILS.md) for a detailed breakdown of the feature, build plan, and implementation steps.

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---

---
