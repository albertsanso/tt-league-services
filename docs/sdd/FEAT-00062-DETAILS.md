# Build Plan
Full link-target design, conventions, and rationale are documented in
[`/docs/frontend/match-detail-linking.md`](../frontend/match-detail-linking.md).
Follow it for exact route helpers, fallback behavior, and the
`stopPropagation` requirement for links nested in `<summary>`. Any deviation
from that doc must be noted under `# Notes` below before merging.

## 1. Backend: expose a club id on match team payloads

- `tt-data-league-core-domain/.../match/find/dto/MatchDetailReadModel.java`:
  add `UUID clubId` to `TeamReadModel`.
- `tt-data-league-core-domain/.../match/find/FindMatchDetailsQueryHandler.java`:
  in `team(Team team)`, resolve
  `team.getFederatedClub().map(FederatedClub::getId).orElse(null)` and pass it
  as `clubId`. Import `org.cttelsamicsterrassa.data.core.domain.club.model.FederatedClub`.
- `tt-data-league-api-rest/.../match/MatchDetailDto.java`: add `UUID clubId`
  to `TeamDto`, and pass `value.clubId()` through in the `team(...)` mapping
  helper.
- Update `FindMatchDetailsQueryHandlerTest.java` (and any `MatchDetailDto`
  mapping test) to assert `clubId` is populated for a team with a federated
  club, and `null` for one without.

## 2. Match detail page — link teams and competition

File: `tt-data-league-frontend/src/pages/MatchSummaryPage.jsx`

- Add a small helper (or inline conditional) rendering a team name as
  `<Link to={routePaths.clubDetails(team.clubId, returnSearch)}>{team.name}</Link>`
  when `team?.clubId` is present, else the current plain text. Apply it to:
  - the page header `<h1>` (lines ~88, 90) for `match.homeTeam` / `match.awayTeam`
  - `TeamPanel`'s `<h3>` (line ~146)
- Wrap the competition text (line ~82, `match.competition`) in a `Link` to
  `routePaths.clubCompetitionDetails(match.homeTeam?.clubId, match.season, match.competition, returnSearch)`
  when `match.homeTeam?.clubId`, `match.season`, and `match.competition` are
  all present; otherwise render the current plain text. `returnSearch` is the
  existing `params` from `useSearchParams()`, already available in this
  component and already passed to `TeamPanel`.
- No changes needed to `api/matches.js` (`getMatchDetails` already passes the
  raw payload through) or to `routePaths` (`clubDetails` and
  `clubCompetitionDetails` already exist).

## 3. Player details — Matches tab: link matches and opponent players

File: `tt-data-league-frontend/src/pages/PlayerDetailPage.jsx`

- Thread `returnSearch` down: `MatchHistoryPanel` currently takes
  `{ matches, t }` (line 225) and is called with
  `<MatchHistoryPanel key={...} matches={matches} t={t} />` (line 174). Add a
  `returnSearch` prop there, sourced from the page's existing `params` (line
  47), and pass it through to `MatchCard` (called at line 236).
- In `MatchCard` (line 248), add a link to
  `routePaths.matchSummary(match.id, returnSearch)` placed inside the
  `<summary>` (e.g. near `match-card-competition` or as its own small
  control), with `onClick={(event) => event.stopPropagation()}` so clicking it
  navigates instead of toggling the `<details>`.
- Change `gameOpponentNames(game, t)` (line 935) to return an array of
  `{ key, name, playerId }` entries instead of a joined string (dedupe by
  `opponentKey(opponent)` as today). In `MatchCard`'s game row (line 280,
  `match-card-game-opponents`), render the array as a comma-separated list of
  `Link`s (`routePaths.playerDetails(entry.playerId, returnSearch)`) for
  entries with a `playerId`, and plain text otherwise — same fallback pattern
  as `MatchSummaryPage.jsx`'s lineup table.

## 4. Player details — Oponent analysis tab: link matches

Same file, `OpponentAnalysisPanel` (line 291) and `OpponentHeadToHead` (line 470).

- In `OpponentAnalysisPanel`, add `matchId: match.id` alongside the existing
  `id` field in all three `addOpponent(...)` call sites (lines ~309, ~325,
  ~337) — `id` stays the composite/legacy value used as the React key;
  `matchId` is always the plain `match.id`.
- Thread `returnSearch` (the panel's existing `params`) down through
  `OpponentTable` (line 408) to `OpponentHeadToHead` (line 470).
- In `OpponentHeadToHead`'s row rendering (line 478), wrap the date cell
  (`match-card-game-type`, line 479) in a
  `Link` to `routePaths.matchSummary(entry.matchId, returnSearch)`.

## 5. Tests

- `MatchSummaryPage.test.jsx`: extend the `homeTeam`/`awayTeam` fixtures with
  `clubId`, and add cases for (a) team/competition links rendering when ids
  are present, and (b) plain text fallback when `clubId` is missing.
- `PlayerDetailPage.test.jsx`: add cases for the Matches-tab match link
  (present + `stopPropagation` keeps the card collapsed on click), opponent
  player links, and the Oponent-analysis match link.
- Backend: extend `FindMatchDetailsQueryHandlerTest.java` per step 1.

## Implementation order

1. Backend `clubId` change + test (step 1).
2. Match detail page links (step 2) + tests.
3. Matches tab links (step 3) + tests.
4. Oponent analysis tab links (step 4) + tests.

# Implementation Guidelines

- Follow [`/docs/frontend/match-detail-linking.md`](../frontend/match-detail-linking.md)
  for link targets, fallback behavior, and styling (no new CSS classes).
- Do not introduce a `/teams/:teamId` route or a standalone competition page;
  team links reuse Club detail, competition links reuse Club Competition
  detail, scoped through the home team's club.
- Missing ids (no `clubId`, no canonical `playerId`) must fall back to plain
  text, never a broken or empty-href link.
- Links rendered inside a `<summary>` (Matches tab match cards) must stop
  click propagation so they don't also toggle the disclosure.

# Notes

- Acceptance criteria assume `docs/frontend/match-detail-linking.md`; that
  file was authored as part of this planning pass (previously missing).
- Team link target (Club detail) and competition link target (Club
  Competition detail, scoped via the home team's club) were confirmed with
  the feature owner during planning, given no dedicated team/competition
  pages exist yet.
- Implementation completed per the build plan above, with no deviation from
  `docs/frontend/match-detail-linking.md`. Validation:
  - Backend: `clubId` added to `MatchDetailReadModel.TeamReadModel` /
    `MatchDetailDto.TeamDto`; `FindMatchDetailsQueryHandlerTest` covers both a
    team with a federated club and one without. Full `tt-data-league-core-domain`
    and `tt-data-league-api-rest` test suites pass.
  - Frontend: all five acceptance criteria covered by new/updated tests in
    `MatchSummaryPage.test.jsx` and `PlayerDetailPage.test.jsx` (team/competition
    links + plain-text fallback, Matches-tab match link with disclosure
    click-through prevention, opponent-player links + fallback, Oponent
    analysis head-to-head match link). Full frontend suite (288 tests) and
    lint pass.
