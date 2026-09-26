# Build Plan
Adds a "Matches summary" strip to Player details, Matches tab
(`MatchHistoryPanel` in `tt-data-league-frontend/src/pages/PlayerDetailPage.jsx`),
styled and structured the same way as the Matches summary strip already
shipped for Club details, Matches tab in
[FEAT-00063](./FEAT-00063-DETAILS.md) (`MatchesSummaryStrip` in
`ClubDetailPage.jsx`).

Feasibility check against the current codebase:

- `PlayerDetailContent` already fetches `data.matches` scoped to the current
  Source/Season/Competition filters via
  `usePlayerDetails(playerId, sourceFilter, seasonFilter, competitionFilter)`
  (`PlayerDetailPage.jsx:47`) — unlike the club version, there is no separate
  match-fetch call to add; `MatchHistoryPanel` already receives exactly the
  right-scoped `matches` array as a prop.
- Each match record already carries `playerTeam` alongside `homeTeam`/
  `awayTeam` (`api/players.js`'s `normalizeMatch`, confirmed in
  `PlayerDetailPage.test.jsx`'s fixtures, e.g. `{ homeTeam: 'Club Beta',
  awayTeam: 'Club Terrassa', playerTeam: 'Club Terrassa', ... }`) — the
  home/away split only needs `match.playerTeam === match.homeTeam`, with no
  `resolveClubTeam`/team-list lookup needed (that was only necessary for the
  club version, which has no equivalent field on its match records).
  `result`, `homeGamesWon`/`awayGamesWon`, `dateTime`, `competition`, `round`,
  `id` all match the shape `clubSummary.js`'s helpers already operate on.
- Four of the five `clubSummary.js` helpers built for FEAT-00063 take only a
  `matches` array and never touch club-specific concepts (teams/competitions
  aggregates), so they can be imported and reused **unchanged**:
  `countPendingMatches`, `getFormGuide`, `getCurrentStreak`,
  `getNotableMatches`. Only `computeHomeAwaySplit` is club-specific (it takes
  a `teams` list to resolve which side is the club's team) and needs a
  player-specific counterpart.
- There is no per-competition `resultTotals`/`matchCount` aggregate on the
  player side (`computeOverallRecord(competitions)` doesn't apply) — the
  record tile must be computed directly from the raw `matches` array, same
  as `matchSummary.js`'s existing `formatRecord(matches)`, which already
  returns `{ wins, draws, losses }` from a raw match array; it just needs a
  win-rate and match-count wrapper.
- All strings needed (`common.playedMatches`, `common.winPercentage`,
  `detail.winsAbbrev`/`drawsAbbrev`/`lossesAbbrev`, `detail.win`/`loss`/
  `draw`, `detail.pendingResult`, `detail.matchesHomeAwayTile`,
  `detail.matchesPending`, `detail.matchesFormGuide`,
  `detail.matchesCurrentStreak`, `detail.matchesNotableTitle`,
  `detail.matchesClosest`, `detail.matchesBiggestWin`,
  `detail.matchesBiggestLoss`) and all CSS classes (`.stat-grid`,
  `.matches-summary-strip`, `.matches-form-guide`, `.form-guide-chip`,
  `.matches-current-streak`, `.card-block`/`.card-block-header`,
  `.match-row-list`, `.match-row-result`) already exist from FEAT-00063 —
  **no new i18n keys or CSS are needed**, only reuse.

1. **`computeRecord` and player-specific home/away split**
   (`tt-data-league-frontend/src/utils/matchSummary.js`)
   - Add `computeRecord(matches)`: reuses the existing `formatRecord(matches)`
     in this file for `{ wins, draws, losses }`, adds `matchCount:
     matches.length`, and a `winRate` computed the same way
     `clubSummary.js`'s private `winRateOf` does (`Math.round(wins /
     (wins+draws+losses) * 100)`, `0` when there are no decided matches) —
     duplicate the tiny formula rather than exporting `winRateOf` from
     `clubSummary.js`, since `matchSummary.js` has no club dependency today
     and this one-line formula isn't worth introducing one.
   - Add `computeHomeAwaySplit(matches)`: same shape as
     `clubSummary.js`'s `computeHomeAwaySplit` return value (`{ home: {
     wins, draws, losses, winRate }, away: { ...same } }`), but resolves the
     side directly from `match.playerTeam === match.homeTeam ? 'home' :
     match.playerTeam === match.awayTeam ? 'away' : null` instead of calling
     `resolveClubTeam` — skip a match where neither comparison matches
     (shouldn't happen given `normalizeMatch`'s validation, but stay
     defensive the same way the club version does for an unresolved team).
   - Unit tests in `matchSummary.test.js` (create if it doesn't already
     exist — check first) covering: `computeRecord` win rate/match count
     over a mixed win/draw/loss set and the zero-matches case; `
     computeHomeAwaySplit` classifying a match where `playerTeam` is the
     home side, one where it's the away side, and confirming an unresolved
     `playerTeam` (defensive case) is excluded from both totals.

2. **`MatchesSummaryStrip` component** (`PlayerDetailPage.jsx`, new function
   near `MatchHistoryPanel` at line ~225)
   - Import `StatTile` and `MatchRow` from `./ClubSummaryPanel.jsx` (already
     exported there for FEAT-00063) and `countPendingMatches`,
     `getFormGuide`, `getCurrentStreak`, `getNotableMatches` from
     `../utils/clubSummary.js`; import the two new functions from step 1
     from `../utils/matchSummary.js` (already imported in this file for
     `computeTrendNote`).
   - Mirror `ClubDetailPage.jsx`'s `MatchesSummaryStrip` structure exactly:
     a `.stat-grid` of 4 `StatTile`s (Matches/win rate with W-D-L sub-label,
     Home/Away split, Pending), a `.matches-form-guide` paragraph (last-5
     chips via `getFormGuide` + `getCurrentStreak`, reusing the module-level
     `resultLabel(result, t)` already defined in this file instead of
     re-declaring one), and a `.card-block` "Notable matches" `MatchRow`
     list (closest/biggest win/biggest loss from `getNotableMatches`,
     skipping any `null` slot, each row's `competition` field prefixed with
     its label the same way the club version does: `` `${t('detail.matchesClosest')} · ${match.competition}` ``).
   - `MatchRow` expects a `returnSearch` string for its `routePaths.matchSummary`
     link — pass the `returnSearch` prop `MatchHistoryPanel` already receives
     (`params.toString()`; confirm whether `returnSearch` needs `.toString()`
     called before passing down, since `MatchHistoryPanel` currently forwards
     the raw `URLSearchParams` object as `returnSearch` to `MatchCard`/
     `GameOpponents` — check how `routePaths.matchSummary`/`playerDetails`
     consume that second argument elsewhere in this file before deciding
     whether `MatchRow` needs a string or the same `URLSearchParams` works
     unchanged).
   - Memoize each computed value (`useMemo`, matching the club version's
     pattern) keyed off `matches`.

3. **Wire into `MatchHistoryPanel`**
   - Render `<MatchesSummaryStrip matches={matches} returnSearch={returnSearch} t={t} />`
     immediately above the `<ul className="match-card-list">`, only when
     `matches.length > 0` (the existing `matches.length === 0` branch already
     returns the `detail.competitionEmpty` empty state before reaching this
     point — no change needed there).
   - No changes to `MatchCard`, `GameOpponents`, pagination, or the empty
     state.

4. **Tests** (`PlayerDetailPage.test.jsx`)
   - Using the existing `details` fixture (4 matches, already spanning a
     home win, an away loss, a pending/no-score draw, and another win) and/or
     a small addition to it, assert in the Matches tab (`?view=matches`):
     - the 4 stat tiles render the expected match count, win rate with
       W-D-L breakdown, home/away split, and pending count (1, from
       `match-draw`);
     - the form-guide chip row renders in chronological order with the
       correct current-streak text;
     - the "Notable matches" card renders the expected closest/biggest-win/
       biggest-loss picks, and that a fixture with no losses at all (reuse
       or adapt `drawOnlyDetails`/a small custom fixture) renders no
       biggest-loss row rather than a placeholder.
   - Since the summary strip's "Notable matches" card can legitimately
     repeat a match that's also visible on the first page of
     `match-card-list` below (same lesson learned in FEAT-00063's club-side
     tests), scope any ambiguous `getByText` query with
     `within(screen.getByRole('list', { name: ... }))` against the specific
     list (`match-card-list` vs. the notable-matches `match-row-list`)
     rather than asserting on the whole document.
   - Re-run the existing Matches-tab tests unchanged to confirm pagination,
     `MatchCard` rendering, and the `detail.competitionEmpty` empty state
     aren't disturbed.

5. **Verification**
   - `npx vitest run` (or targeted `-- PlayerDetailPage matchSummary`) in
     `tt-data-league-frontend/`.
   - `npx eslint` on all changed/added files.
   - Manually sanity-check in the dev server against a player with matches
     across multiple sources/seasons/competitions and at least one pending
     match: confirm the strip renders above the match list only in the
     Matches tab, updates when the Source/Season/Competition filters change
     (since `data.matches` itself is refetched by `usePlayerDetails` on
     filter change), and that "Notable matches" links navigate to the
     correct match detail page.

# Implementation Guidelines

- Client-side only: no backend/API changes. `data.matches` is already scoped
  to the current Source/Season/Competition filters by `usePlayerDetails`; do
  not add a second fetch or client-side re-filtering of matches.
- Reuse, don't duplicate: `StatTile`, `MatchRow` (both exported from
  `ClubSummaryPanel.jsx`) and `countPendingMatches`, `getFormGuide`,
  `getCurrentStreak`, `getNotableMatches` (from `clubSummary.js`) must be
  imported as-is — they operate on a raw `matches` array already and need no
  club-specific data. Only the home/away split gets a new, player-specific
  function (`computeHomeAwaySplit` in `matchSummary.js`, keyed off
  `match.playerTeam` instead of a resolved club team).
- Reuse existing i18n keys and CSS classes shipped for FEAT-00063's Matches
  summary strip (see the feasibility check above for the exact list) — this
  feature should not need any new translation keys or CSS rules.
- Do not change `MatchCard`, `GameOpponents`, pagination, or the
  `detail.competitionEmpty` empty state in `MatchHistoryPanel`.
- Importing club-prefixed helpers (`clubSummary.js`) into a player-details
  file is a known naming quirk, not a blocker — renaming/moving those
  generic-over-`matches` helpers to a shared, non-club-prefixed module is a
  reasonable future cleanup but out of scope here.

# Notes

- Depends on [FEAT-00063](./FEAT-00063-DETAILS.md) only for its styling/
  content pattern and its exported `StatTile`/`MatchRow`/`clubSummary.js`
  helpers — no changes to FEAT-00063's own shipped code are anticipated.
- **Implementation notes (2026-09-13): shipped as planned**, confirming the
  feasibility check above held with no surprises:
  - Added `computeRecord(matches)` and `computeHomeAwaySplit(matches)` to
    `matchSummary.js` (the latter keyed off `match.playerTeam` directly, no
    team-list lookup needed), plus 5 new unit tests in the new
    `matchSummary.test.js` (this util previously had no test file).
  - `PlayerDetailPage.jsx` gained `MatchesSummaryStrip`, structurally
    identical to `ClubDetailPage.jsx`'s version, importing `StatTile`/
    `MatchRow` from `ClubSummaryPanel.jsx` and `countPendingMatches`/
    `getFormGuide`/`getCurrentStreak`/`getNotableMatches` from
    `clubSummary.js` unchanged, as planned — confirming those four helpers
    really are generic over any `matches` array. Wired into
    `MatchHistoryPanel` above the `match-card-list`, only rendered inside
    the existing `matches.length > 0` branch.
  - `returnSearch` needed no conversion: `routePaths`'s `withSearch` already
    accepts either a string or a `URLSearchParams` object
    (`typeof search === 'string' ? search : search.toString()`), so the same
    `params` object `MatchHistoryPanel` already forwards to `MatchCard`
    passes straight through to `MatchRow` unchanged.
  - No new i18n keys or CSS were needed, as predicted — every string and
    class reused from FEAT-00063 rendered correctly against player-level
    match records. Note for anyone testing this in Catalan: the form-guide
    chips take the first letter of the localized result word (`Victòria`/
    `Empat`/`Derrota`), so they read `V`/`E`/`D`, not the English `W`/`D`/`L`
    used in this doc's examples.
  - Tests: added 3 cases to `PlayerDetailPage.test.jsx` covering the 4 stat
    tile values, the form-guide chip string and streak text, the Notable
    matches card picking 3 distinct matches (closest/biggest-win/biggest-
    loss) for the base 4-match fixture, and two follow-on scenarios: a
    single-win fixture (biggest-loss row correctly absent from an otherwise
    1-row card) and a single-pending-match fixture (whole card hidden, no
    decided matches at all). All 57 existing + new tests in this file pass
    unchanged. Full suite: 338/338 passing; lint clean.
