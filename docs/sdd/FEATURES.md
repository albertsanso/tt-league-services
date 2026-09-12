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
