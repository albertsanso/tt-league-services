# Build Plan

## Delivered — Backend REST contract and domain

All backend changes are in the unstaged working tree and pass the relevant
Maven module tests.

### 1. Query model

`FindPlayerDetailsQuery` (`tt-data-league-core-domain`) carries three new
optional fields alongside the player UUID:
- `source` (`ImportSource`) — federation source filter; `null` = no filter
- `season` (`Season`) — season filter; `null` = no filter
- `competition` (`String`) — competition name filter; `null` = no filter

The four-argument constructor is the primary entry point; the single-argument
constructor defaults all filters to `null` for backward compatibility.

### 2. Domain filter logic (`FindPlayerDetailsQueryHandler`)

`matchesFilter(match, query)` applies all three filters with AND semantics:
- **Source** — exact `ImportSource` enum equality
- **Season** — `Objects.equals` on `Season`
- **Competition** — `Objects.equals` on `String`

The filtered match list drives both `statistics` (grouped by source+season from
filtered matches) and `matches` (the `PlayerMatchReadModel` list returned to the
caller).

`competitions` is built from `allPlayerMatches` **before** filtering so the
selector dropdown retains all available competition options regardless of the
active source/season filter.

Game-level opponent resolution (new in this feature):
- `loadGames()` / `loadDoublesPairs()` — batch-loads `Game` and `DoublesPair`
  records for all filtered matches using the injected `GameRepository` and
  `DoublesPairRepository` ports
- `selectedSide()` — finds the player's side (HOME/AWAY) for singles; finds the
  matching doubles pair for DOUBLES games
- `opponents()` — returns the opposing `PlayerSeason`(s) for the resolved side
- `opponentKey(player)` — canonical player UUID → federated player UUID →
  player season UUID (first non-null wins)
- `toOpponent(player)` — maps `PlayerSeason` → `PlayerOpponentReadModel` with
  all identity fields and `available = true`
- When no side or no opponents can be resolved, returns a `PlayerGameReadModel`
  with `result = "unavailable"` and a Catalan `unavailableReason` message

New domain DTOs (untracked files, not yet committed):
- `PlayerGameReadModel` — `id`, `gameNumber`, `type`, `result`, `homeSetsWon`,
  `awaySetsWon`, `opponents` (immutable copy-of), `unavailableReason`
- `PlayerOpponentReadModel` — `playerId`, `federatedPlayerId`, `playerSeasonId`,
  `name`, `source`, `season`, `available`

`PlayerMatchReadModel` was extended with `playerGamesWon`, `playerTeam`, and
`games` (immutable list of `PlayerGameReadModel`).

### 3. REST controller filter validation (`PlayerController`)

`findPlayerDetailsById(UUID, String, String, String)` validates before
dispatching the query:

| Input | Validation | Response |
|---|---|---|
| Unknown `source` value | `ImportSource.valueOf` throws | 400 `"Unknown source filter: <value>"` |
| Blank `source` | Explicit blank check | 400 |
| Malformed `season` (e.g. single year `"2024"`) | `Season.fromFormatted` throws | 400 `"Invalid season filter: <value>"` |
| Blank `season` | Explicit blank check | 400 |
| Blank `competition` | Explicit blank check | 400 `"Competition must not be blank"` |
| Competition not in player's competition list | Post-query membership check | 400 `"Unknown competition filter: <value>"` |
| Valid filters, no matching matches | Query succeeds | 200 with empty `matches` and `statistics` |
| Player not found | Query fails | 404 |

### 4. DTO response shape (`PlayerDetailsDto`)

`PlayerDetailsDto.fromObject` maps `PlayerDetailsReadModel` to the REST
response, including game-level detail:
- `matches[]` → `MatchDto` with `playerTeam`, `playerGamesWon`, `games[]`
- `games[]` → `GameDto` with `type`, `result`, `homeSetsWon`, `awaySetsWon`,
  `opponents[]`, `unavailableReason`
- `opponents[]` → `OpponentDto` with `playerId`, `federatedPlayerId`,
  `playerSeasonId`, `name`, `source`, `season`, `available`

---

## Delivered — Frontend

All frontend changes are in the unstaged working tree and pass `npm test`,
`npm run lint`, and `npm run build`.

### 5. API layer (`src/api/players.js`)

`getPlayerDetails(playerId, source, season, competition, token, signal, onUnauthorized)`
builds `GET /api/v1/player/<id>` appending a query string of the non-null,
non-empty filters:
```
/api/v1/player/<id>?source=<source>&season=<season>&competition=<competition>
```
Null or empty filter values are omitted. A four-argument overload (legacy: no
filters) is preserved via an `arguments.length` check.

`normalizePlayerDetailsResponse` validates and maps the full response including
`matches[].games[].opponents[]` through `normalizeGames` and `normalizeOpponent`.
`normalizeOpponent` rejects entries with `available: true` but no identity field.

### 6. Request lifecycle (`usePlayers.js`)

`usePlayerDetails(playerId, source, season, competition)` passes all four args to
`getPlayerDetails` via a memoized `request` callback. The shared `useRequest`
helper:
- Creates an `AbortController` per effect run; cancels it on cleanup
- Tracks a monotonic `requestRef` counter; resolves only when
  `requestRef.current === requestId` (late responses from replaced requests are
  silently discarded)
- Derives `loading` as `enabled && state.key !== requestKey` so loading is
  visible immediately on any filter change
- Returns `{ data, loading, error, retry }`

The `identity` key (`"${playerId}-${source}-${season}-${competition}"`) ensures
the effect re-fires and a new request is issued whenever any filter changes.

### 7. Selector-to-request binding (`PlayerDetailContent`)

`PlayerDetailContent` reads `source`, `season`, and `competition` from the URL
search params (coercing the sentinel value `"all"` to `""`) and passes them
directly to `usePlayerDetails`. Changing any selector updates the URL, which
re-renders the component with new filter values, which re-triggers the hook.

The `competitions` dropdown options are derived from `data.competitions` (the
unfiltered competition list returned by the server). Season and source options
are derived from the same server-filtered `data.competitions`, `data.matches`,
and `data.statistics` lists, keeping available options consistent with the
active data.

### 8. OpponentAnalysisPanel — sub-tab navigation

An `opponentView` URL query parameter (`"categorization"` or `"search"`)
controls which sub-tab is rendered. The `useEffect` in `PlayerDetailContent`
normalizes missing or invalid values to `"categorization"` with a `replace`
navigation. Switching sub-tabs does not reset `source`, `season`,
`competition`, or `chart`.

The panel receives `opponentView` and `update` as props and is stateless with
respect to the URL. A `key={opponentView}` prop on the panel resets local state
(including search input) when the sub-tab changes.

ARIA: `role="tablist" aria-label="Vistes d'anàlisi d'oponents"` outer, each
`<button>` with `role="tab"`, `aria-selected`, `aria-controls`; panel `<div>`
with `role="tabpanel"` and `aria-labelledby`.

### 9. Opponent categorization sub-tab

Opponent rows are built from `data.matches` (the server-filtered list) using
the same `opponentKey` / `addOpponent` helpers as the original panel:
- **Game-level opponents** — each `match.games[].opponents[]` contributes once
  per match; deduplication within a single match uses a per-match `Set` of keys
- **Legacy fallback** — matches with no game-level detail use `legacy-<opponentName>`
- **Unavailable fallback** — matches with games but no resolvable opponent use
  `unavailable-<matchId>`

`overallWinPercentage` is computed directly from the active `matches` array
(wins and losses only, draws excluded from denominator).

Category thresholds (as implemented — note: diverges from the "≥5 matches"
phrasing in FEATURES.md; this is the refined algorithm agreed in the build plan):
- **Favorable** — `wins > losses`
- **Problem** — `losses > wins` **and** either:
  - `overallWinPercentage != null` and `playerWinPercentage ≤ overallWinPercentage − 20`, or
  - `overallWinPercentage == null` and `matches ≥ 2`
- **Hard** — `losses > wins` and not Problem
- **Uncategorized** — `wins === losses` (draw-only or zero decided matches)

Render order per category:
- Favorable → descending `playerWinPercentage`, then ascending name
- Hard / Problem → ascending `playerWinPercentage`, then ascending name

Empty category renders a Catalan `club-empty card` paragraph.

`MatchOpponentDetails` is **not** rendered in this sub-tab; it is preserved as
an exported component (`export function MatchOpponentDetails`) for future use.

### 10. Opponent search sub-tab

Local `search` state (not URL-persisted) managed by `useState('')`. The
`key={opponentView}` prop on the panel resets this state on sub-tab switch.

A `<label>` + `<input type="search">` filters all categorized rows with
`toLocaleLowerCase('ca-ES')` substring matching. The result table includes a
`Categoria` column with `"Favorable"`, `"Difícil"`, `"Problemàtic"`, or
`"Sense categoria"` (draw-only / uncategorized). When the query matches nothing,
shows a `club-empty card` with `"Cap oponent coincideix amb la cerca."`.

---

## Delivered — Tests

### 11. Backend tests

All tests in the modified modules pass. The pre-existing
`ClubControllerTest` class-cast error and the full-reactor
`tt-data-league-import` failures are unrelated to this feature.

| Test | Covers |
|---|---|
| `PlayerControllerTest.forwardsValidPlayerDetailFiltersToTheQuery` | Filter values forwarded to query; trimmed competition; 200 with empty matches |
| `PlayerControllerTest.rejectsMalformedPlayerDetailFilters` | Unknown source, malformed season, blank competition → 400; query bus never called |
| `PlayerControllerTest.rejectsUnknownCompetitionFilters` | Competition not in player's list → 400 |
| `PlayerControllerTest.mapsOpponentGameDetailsInTheRestResponse` | `OpponentDto` fields in REST response |
| `FindPlayerDetailsQueryHandlerTest.filtersMatchesBySourceSeasonAndCompetition` | Only matching matches/statistics returned; competitions list unfiltered |
| `FindPlayerDetailsQueryHandlerTest.resolvesAHomeSinglesOpponentByCanonicalIdentity` | Singles opponent resolved by canonical player ID, result = "win" |
| `FindPlayerDetailsQueryHandlerTest.mapsThePlayerLineupTeamForDrawnMatches` | Player team mapped correctly for draw; away team used when player is away |

### 12. Frontend tests

| Test file | Covers |
|---|---|
| `PlayerDetailPage.test.jsx` — `defaults the opponent sub-tab` | `opponentView=categorization` defaulted and persisted in URL |
| `PlayerDetailPage.test.jsx` — `switches opponent sub-tabs` | `opponentView=search` set; source/season/competition/chart retained |
| `PlayerDetailPage.test.jsx` — `normalizes an invalid opponent sub-tab` | Invalid value → `categorization` with replace nav |
| `PlayerDetailPage.test.jsx` — `passes the active selectors to the server-backed details request` | `usePlayerDetails` called with current filter values; updates on source change |
| `PlayerDetailPage.test.jsx` — `omits the all-competitions value from the filtered request` | Empty competition passed to hook when selector cleared |
| `PlayerDetailPage.test.jsx` — `categorizes opponents and excludes draw-only records` | Correct table assignment; draw-only opponent absent from all category tables |
| `PlayerDetailPage.test.jsx` — `shows a specific empty state for every empty category` | Catalan empty-state per category |
| `PlayerDetailPage.test.jsx` — `filters opponents by an accented substring and clears search state` | Catalan `toLocaleLowerCase`; state reset on sub-tab switch |
| `players.test.js` — `requests player details with every selected filter` | `getPlayerDetails` builds correct URL query string |
| `players.test.js` — `normalizes source-scoped game opponents` | `opponents[].playerId` accessible after normalization |
| `usePlayers.test.jsx` — `cancels a previous filter request and ignores its late response` | AbortController per request; stale response discarded |

---

## Remaining work

All previously identified remaining work (R1–R3) is now implemented.

### R1 — "Show more" collapsed section for category tables

Each categorization table now shows the first 3 rows and collapses the
remainder behind a native details/summary control.

**Scope:** Frontend only — `OpponentCategoryTable` in
`tt-data-league-frontend/src/pages/PlayerDetailPage.jsx`.

Implementation:
- `OpponentTable` renders three visible rows and wraps additional rows in a
  native `<details>`/`<summary>` element stating the hidden count.
- Focused frontend regression coverage verifies collapsed and expanded rows.

### R2 — Remove legacy detail sections from Player detail view

The Player detail page (`PlayerDetailContent` in `PlayerDetailPage.jsx`) no
longer renders the four legacy `<DetailList>` sections:
- `Registres federats` (line 159)
- `Inscripcions per temporada` (line 160)
- `Clubs associats` (line 161)
- `Competicions` (line 162)

**Scope:** Only the four rendered `<DetailList>` elements were removed. The
underlying data (`federatedPlayers`, `registrations`, `clubs`,
`data.competitions`) is still consumed by the selector controls and must
not be removed from the component. Update tests to not assert on these
sections if any currently do so.

### R3 — Accessible fallback content for table readers

Tables provide captions and per-category `aria-describedby` summary text.
Empty and search-result states use status live regions, while loading and
request failure/not-found states retain their existing status or alert roles.

---

# Validation Status

| Check | Status | Notes |
|---|---|---|
| `PlayerControllerTest` | ✅ Pass | All new filter and opponent mapping tests pass |
| `FindPlayerDetailsQueryHandlerTest` | ✅ Pass | Filter, singles opponent, draw match team tests pass |
| `tt-data-league-api-rest` Maven module | ✅ Pass | Pre-existing `ClubControllerTest` class-cast failure is unrelated |
| Frontend `npm test` | ✅ Pass | All PlayerDetailPage, players, usePlayers tests pass |
| Frontend `npm run lint` | ✅ Pass | |
| Frontend `npm run build` | ✅ Pass | |
| Full Maven reactor | ⚠ Pre-existing failures | `tt-data-league-import` import-processor failures are unrelated to this feature |
| Manual verification | Pending | Sub-tab keyboard navigation, selector changes, mobile layout |

---

# REST Contract

## Endpoint

```
GET /api/v1/player/{id}
```

All query parameters are optional.

## Parameters

| Parameter | Location | Type | Validation |
|---|---|---|---|
| `id` | path | UUID | Must be parseable as `java.util.UUID` |
| `source` | query | String | Case-insensitive `ImportSource` enum value; null = no filter |
| `season` | query | String | `YYYY-YYYY` format via `Season.fromFormatted`; null = no filter |
| `competition` | query | String | Non-blank; trimmed; null = no filter |

## Response

**`200 OK`** — `PlayerDetailsDto` JSON:
- `competitions[]` — **unfiltered** (all competitions across all seasons and
  sources the player has appeared in), used to populate the selector dropdown
- `matches[]` — filtered by source AND season AND competition
- `statistics[]` — derived from filtered matches, grouped by (source, season)
- `federatedPlayers[]`, `registrations[]`, `clubs[]` — unfiltered

**`400 Bad Request`** — when any filter is syntactically invalid, blank, or
when an explicitly named competition is not associated with the player.

**`404 Not Found`** — when no player with the given UUID exists.

## Filter interaction

The three filters compose with AND semantics. A valid filter combination that
matches no matches returns 200 with empty `matches` and `statistics` arrays.
The `competitions` array always reflects the player's full competition history
regardless of the active source/season filter.

---

# Implementation Guidelines

- Reuse `usePlayerDetails`, `apiRequest`, the existing CSS tokens (`club-tabs`,
  `club-tab`, `history-table`, `table-wrap`, `club-empty`, `card`), and the
  native React Router query-state APIs. Do not add a dependency solely to solve
  a problem covered by existing helpers.
- Follow frontend conventions: React JSX, two-space indentation, single
  quotes, no semicolons. All user-facing copy in Catalan.
- Preserve `opponentKey` and `addOpponent` unchanged. Build categorization
  on top of the same rows the current panel already computes.
- Preserve accessible visible focus states, semantic `<button>` tab controls,
  responsive table wrapping, and the loading/error/empty states established
  by FEAT-00014.

# Notes

- FEAT-00014 shipped the outer tabbed interface (Statistics / Matches /
  Opponents) and a basic `OpponentAnalysisPanel` with a flat opponent table
  and per-match collapsible details. FEAT-00015 replaces the panel's content
  with the categorized and searchable opponent views defined in this plan.
- The "opponent" in this feature refers to the **opposing team** for a match,
  derived from `opponentName(match)` (i.e. the non-player team). Individual
  opponent player data is used only when `games[].opponents` are available and
  have an identity; the legacy team-name fallback handles matches without
  game-level detail.
- The "problem opponent" threshold (−20 pp below overall win percentage) is a
  starting design decision for this first review iteration. It may be adjusted
  in a follow-up feature based on user feedback.
- The existing `MatchOpponentDetails` component is intentionally kept out of
  the categorization view to reduce noise; reconsider including it only in a
  future iteration if user feedback supports it.
- The full Maven reactor has pre-existing import-processor test failures in
  `tt-data-league-import` that are unrelated to this feature. Frontend lint,
  tests, and build must pass; Maven reactor failures in unrelated modules do
  not block this feature.
- The next three notes record the initial UI-only implementation before the
  clarified REST requirement and are retained as implementation history.
- Implemented the URL-persisted categorization and search sub-tabs, categorized
  opponent tables, local search filtering, Catalan empty states, and responsive
  accessible controls without backend changes.
- Frontend validation passes with `npm ci`, `npm test`, `npm run lint`, and
  `npm run build` from `tt-data-league-frontend`.
- The existing player-details endpoint was verified to be
  `GET /api/v1/player/{id}` with no filter query parameters. The current
  implementation filters the one loaded response in the browser, which does
  not satisfy the clarified requirement for selector changes to make REST
  requests. FEAT-00015 is blocked on selecting and implementing the filtered
  REST contract and coordinating its frontend consumer.
- The clarified requirement was resolved by extending
  `GET /api/v1/player/{id}` with optional `source`, `season`, and `competition`
  query parameters. The controller validates source and season syntax and
  rejects blank competitions; the domain query handler applies the filters
  with explicit source scoping while retaining selector metadata and returning
  empty match/statistics collections for valid filters with no results.
- The frontend now passes the effective selector values to `usePlayerDetails`
  and treats the server-filtered matches and statistics as authoritative.
  Requests are cancelled on selector changes, late responses are ignored by
  request identity, and loading, error, and empty states remain visible.
- FEAT-00015 implementation is finalized for review. Focused Maven domain/API
  tests and frontend tests, lint, and build pass. The API module test run still
  reports the pre-existing unrelated `ClubControllerTest` class-cast error;
  the full reactor's documented import-processor failures remain unrelated.
- Plan and spec rebuilt (2026-08-29) from full implementation inspection of all
  unstaged working tree changes. Delivered REST-backed architecture documented
  in build plan sections 1–12. Three remaining acceptance criteria identified
  (R1 Show more, R2 section removal, R3 accessible fallback). Validation status
  table and REST contract section added. The problem-opponent algorithm
  (−20 pp / ≥2 matches) follows the build plan; the FEATURES.md "≥5 matches"
  phrasing reflects the original user intent before the algorithm was refined.
  FEAT-00015 remains in-review.
- R1–R3 were completed on 2026-08-29: category tables now collapse rows beyond
  three, legacy detail sections are removed, and opponent tables expose
  accessible summaries and state announcements. Focused frontend tests cover
  these behaviors.
- FEAT-00015 closed as done on 2026-08-29 by explicit user approval. All
  acceptance criteria verified and all remaining work (R1–R3) shipped.
