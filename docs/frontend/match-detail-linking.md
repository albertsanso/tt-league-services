# Match detail linking (FEAT-00062)

Documents the link targets and conventions used to connect Match detail, Player
details → Matches, and Player details → Oponent analysis to their related
entities. Read this before implementing or reviewing FEAT-00062.

## Conventions (already established, reused here)

- **No dedicated link styling.** Use a bare `<Link>` from `react-router-dom`,
  matching the existing player-name link in `MatchSummaryPage.jsx`'s lineup
  table. Table/list links inherit the global anchor styling; no new CSS class
  is introduced for these links.
- **Route helpers only.** Never hand-build a path string; use `routePaths.*`
  from `src/config/routes.js`.
- **Query passthrough for "return context".** When a link is built from a page
  that itself received search params meant to restore filters on return (e.g.
  `season`, `source`, `competition`), pass the current `useSearchParams()`
  value as the route helper's `returnSearch` argument. Each route helper
  whitelists which keys survive (`PLAYER_DETAIL_QUERY_KEYS`,
  `MATCH_SEARCH_QUERY_KEYS`, `CLUB_DETAIL_QUERY_KEYS`) — do not add new keys
  without checking `withSearch()`.
- **Missing id → plain text, never a broken link.** If the id a link needs
  (canonical player id, club id, match id) is absent from the payload, render
  the existing plain-text fallback instead of a `Link`, exactly as
  `MatchSummaryPage.jsx`'s lineup table already does for players without a
  `canonicalPlayerId`.
- **Links inside a `<details>`/`<summary>` disclosure** (used by
  `MatchCard` in `PlayerDetailPage.jsx`) must call
  `event.stopPropagation()` in their `onClick`, otherwise clicking the link
  also toggles the disclosure open/closed because the click still bubbles to
  the native `<summary>` toggle handler.

## Link map

### 1. Match detail page (`MatchSummaryPage.jsx`)

| Element | Target | Notes |
|---|---|---|
| Home/away team name (page header `<h1>` and each `TeamPanel`'s `<h3>`) | `routePaths.clubDetails(team.clubId, returnSearch)` | Requires a backend addition: `clubId` on `MatchDetailDto.TeamDto` / `MatchDetailReadModel.TeamReadModel`, resolved as the team's `FederatedClub` id (`team.getFederatedClub().map(FederatedClub::getId)`). Falls back to plain text when `clubId` is `null` (team not yet linked to a federated club). |
| Competition text (`match-summary-competition`) | `routePaths.clubCompetitionDetails(match.homeTeam.clubId, match.season, match.competition, returnSearch)` | Scoped through the **home team's** club, since `ClubCompetitionDetailPage` requires a `clubId` and the match itself has no club of its own. Falls back to plain text when `homeTeam.clubId`, `match.season`, or `match.competition` is missing. |

`match.season` is already present on `MatchDetailDto` (top-level `season`
field) but not currently read by `MatchSummaryPage.jsx` — this feature starts
reading it.

### 2. Player details — Matches tab (`MatchHistoryPanel` / `MatchCard`)

| Element | Target | Notes |
|---|---|---|
| Each match card (`MatchCard`) | `routePaths.matchSummary(match.id, returnSearch)` | Rendered as a small link inside the `<summary>` (not wrapping the whole disclosure, so expand/collapse keeps working); needs `stopPropagation` per the convention above. `returnSearch` is the page's own `useSearchParams()`. |
| Each opponent player name inside a game row (`match-card-game-opponents`) | `routePaths.playerDetails(opponent.playerId, returnSearch)` | `opponent.playerId` is the canonical player id (see `normalizeOpponent` in `api/players.js`). `gameOpponentNames()` currently joins names into one string — it changes to return renderable entries so each name can be its own `Link`, comma-separated as today. Opponents without a `playerId` (or marked unavailable) stay plain text. |

Team names in this tab (`opponentTeamName`) are plain strings from the
player-matches API with no id and are **out of scope** — only the acceptance
criteria for match and opponent-player links apply here.

### 3. Player details — Oponent analysis tab (`OpponentAnalysisPanel` → `OpponentHeadToHead`)

| Element | Target | Notes |
|---|---|---|
| Each head-to-head history row | `routePaths.matchSummary(entry.matchId, returnSearch)` | The row's existing `id` field is composite (`${match.id}-${game.id}`) for per-game entries, used only as the React `key`; a separate `matchId: match.id` field is added alongside it in `addOpponent()`'s three call sites, and the link uses `matchId`. |

## Out of scope / deferred

- No `/teams/:teamId` route is introduced; team links reuse the existing club
  detail page.
- No standalone competition-only page; competition links reuse the existing
  per-club competition page.
- Opponent name links on the Oponent analysis table itself (as opposed to the
  match rows inside a head-to-head) are not part of this feature.
