# Build Plan
No backend changes are required. `GET /api/v1/player/{id}` (served by
`FindPlayerDetailsQueryHandler`) already returns, per filtered match/game:
- `PlayerSeasonStatisticsReadModel.averageScore` (average games won per match,
  per source/season) — computed but currently unused by the frontend.
- `PlayerMatchReadModel.games[]` with `type` (`SINGLES`/`DOUBLES`), `result`,
  `homeSetsWon`/`awaySetsWon` — enough to derive singles/doubles and set-margin
  breakdowns client-side, the same way `OpponentAnalysisPanel` already derives
  streaks/margins from this data.

1. **Frontend data helpers** — `tt-data-league-frontend/src/pages/PlayerDetailPage.jsx`
   1. Add `aggregateCareerStatistics(matches)`: iterate the (already
      filtered) `matches` array and, reusing the existing `gameSetsForPlayer`
      helper for each match's `games`, compute:
      - `matchesPlayed`, `wins`, `losses`, `draws`, `winPercentage` (reuse
        `winPercentage`/`compareMatches` already defined in this file).
      - `singlesWinPercentage` / `doublesWinPercentage` from games grouped by
        `game.type`.
      - `averageSetMargin`: mean of `(playerSets - opponentSets)` across all
        games with known scores.
      - `currentStreak` and `longestWinStreak`: sort matches with the existing
        `compareMatches` comparator and reuse/extend the `currentStreak`
        pattern already used in `buildOpponentRow` for a match-level (not
        opponent-level) streak; add a small `longestStreak(sortedMatchesDesc,
        'win')` helper alongside it.
   2. Keep all of this derived from the `matches` prop already passed into
      `HistorySection`, so it automatically respects the page's existing
      source/season/competition filters — no new query params or API calls.

2. **Frontend UI** — `HistorySection` in the same file
   1. Add a "Career summary" block rendered above the existing chart/table,
      only when `matches.length > 0`; render a definition-list/stat-grid of
      the values computed in step 1, each formatted with the existing
      `formatWinPercentage`/`toFixed` conventions and `—` for unavailable
      values (mirrors `streakLabel`/`formatWinPercentage` usage elsewhere in
      this file).
   2. Add an "Average score" column to the existing per-season history
      table (`<table className="history-table">` in `HistorySection`),
      sourced from `item.averageScore`, formatted like `winPercentage`
      (`toFixed(1)` or `—`).
   3. When `values.length === 0` (existing empty state), do not render the
      career summary block either — keep the current `detail.statisticsEmpty`
      message as the sole empty state for the tab.

3. **Styling** — `tt-data-league-frontend/src/app.css`
   - Add a small stat-grid style for the new career summary block (e.g.
     `.career-summary` / `.career-summary-stats`), consistent with existing
     `.club-detail-section`, `.card`, and `.history-legend` conventions
     already used on this page. No new component library or dependency.

4. **i18n** — `tt-data-league-frontend/src/i18n/ca.js`, `en.js`, `es.js`
   - Add new keys under `detail` in `ca.js` (source of truth) and add the
     matching overrides in `en.js`/`es.js` (both files spread `...ca.detail`
     and then override individual keys, so new keys must be added to all
     three or they silently fall back to Catalan text):
     `careerSummary`, `currentStreakLabel`, `longestWinStreak`,
     `singlesWinPercentage`, `doublesWinPercentage`, `averageSetMargin`,
     `averageScore`.
   - Reuse existing keys where possible: `common.playedMatches`,
     `common.winPercentage`, `detail.streakWin`/`streakLoss`/`streakDraw`/
     `noStreak`, `detail.singles`/`doubles`.

5. **Tests** — `tt-data-league-frontend/src/pages/PlayerDetailPage.test.jsx`
   - Add a fixture with a mix of singles and doubles matches (wins/losses)
     across two seasons and assert:
     - The career summary renders the expected win percentage, singles win
       percentage, doubles win percentage, and streak values.
     - The per-season table includes the new "Average score" column with the
       value from `statistics[].averageScore`.
     - Changing the season/source/competition filter updates the career
       summary values (reuse the existing filter-interaction test patterns
       already in this file).
     - The existing empty-state test (`detail.statisticsEmpty`) still passes
       and does not also render the career summary block.

6. **Match result spectrum plot** — `HistorySection` / new component in
   `PlayerDetailPage.jsx`
   1. Add `matchQualityTier(match)`, reusing the existing `qualityTier(result,
      margin)` helper (already used by `OpponentFormChips`) with
      `margin = playerGamesWon - opponentGamesWon`, derived the same way
      `matchScoreResult`/`scoreLabel` already derive the opponent's games won
      from `match.homeGamesWon`/`match.awayGamesWon` and `match.playerTeam`.
   2. Add a new chart component (e.g. `MatchQualitySpectrumChart`) plotting
      each of the currently filtered `matches`, sorted chronologically
      (ascending, mirroring `chartValues` in `HistorySection`), on an x-axis
      of match order/date, with a y-axis spectrum from strong-loss to
      strong-win (7 tiers: `strong-loss`, `loss`, `close-loss`, `draw`,
      `close-win`, `win`, `strong-win`), reusing the existing `quality-*` CSS
      classes/colors already defined for `OpponentFormChips` in `app.css` for
      point coloring.
   3. Render this chart immediately to the right of `ConnectedScatterPlot`
      inside `HistorySection` (side-by-side on wide viewports, stacked below
      it on narrow viewports per the existing responsive rules from
      FEAT-00051), so both charts respond to the same season/source/
      competition filters and the same empty-state handling as the rest of
      the tab.

7. **Tests** — extend the `PlayerDetailPage.test.jsx` fixture from step 5 to
   also assert the match result spectrum plot renders one point per filtered
   match with the expected quality tier for a strong win, a close win, and a
   loss.

8. **Manual verification**
   - Run the frontend dev server, open a Player Detail page for a player with
     both singles and doubles match history, and confirm the career summary,
     average score column, and match result spectrum plot render correctly
     and update together when the season, source, and competition filters
     change.

# Implementation Guidelines

- No backend or API changes; this is a frontend-only feature built entirely
  from data already returned by `GET /api/v1/player/{id}`.
- Reuse existing helpers/patterns in `PlayerDetailPage.jsx`
  (`gameSetsForPlayer`, `compareMatches`, `winPercentage`,
  `formatWinPercentage`, the `currentStreak`/`streakLabel` pattern, and
  `qualityTier`/`qualityLabel`/the `quality-*` CSS classes already used by
  `OpponentFormChips`) instead of duplicating logic already used for opponent
  analysis.
- Career summary and match-quality-spectrum values must derive from the same
  filtered `matches` array already used elsewhere on the page — do not
  introduce a second, differently filtered data source.
- Follow the existing i18n convention: Catalan (`ca.js`) is the source of
  truth; `en.js`/`es.js` must add explicit overrides for every new key.
- Keep the new spectrum chart responsive/usable on narrow viewports,
  consistent with the mobile-usability work already done in FEAT-00051.
- Out of scope: no new backend aggregate fields, no historical statistics
  beyond what `matches`/`statistics` already provide, no charting library
  changes (build the new chart the same way `ConnectedScatterPlot` is built,
  as inline SVG).

# Notes

- `PlayerSeasonStatisticsReadModel.averageScore` already exists server-side
  (`tt-data-league-core-domain/.../dto/PlayerSeasonStatisticsReadModel.java`)
  and is returned by `FindPlayerDetailsQueryHandler`, but the frontend never
  reads it today — surfacing it is a low-risk first step.
- Singles/doubles and set-margin breakdowns are derivable from
  `PlayerMatchReadModel.games[]` (`PlayerGameReadModel.type`,
  `homeSetsWon`/`awaySetsWon`), the same data `OpponentAnalysisPanel` already
  consumes.
- The match result spectrum plot ("tug-of-war") request was captured directly
  in the `FEAT-00052` registry entry using examples like 3-0/3-2/0-3, which
  describe an individual game's set score, not the match-level games-won
  score. The plot was initially built against `homeGamesWon`/`awayGamesWon`
  (match-level) and was corrected afterward — see the follow-up note below —
  to use the player's individual game set score, since the Player Detail
  page is about the player, not their team's aggregate match result.
- Implemented as planned: `aggregateCareerStatistics`, `matchQualityTier`, and
  `MatchQualitySpectrumChart` were added to
  `tt-data-league-frontend/src/pages/PlayerDetailPage.jsx`; note that
  `game.type` values in this codebase are `INDIVIDUAL`/`DOUBLES` (not
  `SINGLES`/`DOUBLES` as originally written in this plan) — singles is
  computed as `game.type !== 'DOUBLES'`.
- The career summary and spectrum chart render from the `matches` array
  passed into `HistorySection`, which is already filtered server-side by
  source/season/competition, so no additional client-side filtering was
  needed beyond what `OpponentAnalysisPanel` already relies on.
- Verified acceptance criteria: extended
  `PlayerDetailPage.test.jsx` with career-summary, average-score-column,
  filter-update, and spectrum-tier tests (45/45 tests passing); manually
  verified rendering (career summary, side-by-side/stacked chart layout,
  average score column, empty state) via a throwaway local preview harness
  against the mock fixture data, removed after verification. Full frontend
  suite (`npx vitest run`, 211 tests) and `eslint` both pass.
- Follow-up: the spectrum chart's Y-axis tick labels now show the real set
  score of the player's own individual game (`matchIndividualScore`, prefers
  a non-`DOUBLES` game, falling back to the first available game and then to
  the match-level score if a match has no `games` detail), formatted as
  `playerSets-opponentSets` (e.g. `3-0`, `3-2`, `1-3`) — not the team's
  match-level games-won score, since the Player Detail page is focused on
  the player. This also fixed a latent inconsistency: `qualityTier`'s
  magnitude thresholds (>=3 strong, ==2 plain, ==1 close) were calibrated
  for a single game's set margin (max 3, as already used by
  `OpponentFormChips`), not a team match's games-won margin, which can
  exceed that range. A tier row with no currently plotted match falls back
  to the tier's translated label (`detail.qualityWin` etc.), kept as an
  `<title>` tooltip on every tick for accessibility. The X-axis now also
  renders a real date timeline (`timelineTicks`, up to 5 evenly spaced tick
  marks with localized dates) instead of an unlabeled match-order axis, and
  the left padding between the Y-axis labels and the plot area was
  increased for readability.
