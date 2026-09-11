# Build Plan
All work is frontend-only (`tt-data-league-frontend`); the required data
(per-game `homeSetsWon`/`awaySetsWon`, `result`, `dateTime`, `opponents`) is
already delivered on `match.games` via `usePlayerDetails` — no API/backend
changes are needed.

1. **Extend per-opponent aggregation** in
   `src/pages/PlayerDetailPage.jsx` (`addOpponent`, `OpponentAnalysisPanel`):
   - Track each opponent's individual match/game entries (date, result,
     `homeSetsWon`/`awaySetsWon` from the player's perspective) instead of
     only running win/draw/loss counters, so later steps can derive
     quality, streaks, and history from the same collected list.
   - Add a `setMargin` helper that converts a game's set score into a
     signed quality value (e.g. `playerSets - opponentSets`, so 3-0 → +3,
     3-2 → +1, 2-3 → -1, 0-3 → -3) and aggregate an average/most-recent
     margin per opponent for use in categorization and indicators.

2. **Quality indicators for wins/losses** (`OpponentTable`):
   - Add a "quality" column/badge per opponent (and optionally per game in
     the head-to-head detail) driven by `setMargin`, e.g. a color scale
     (strong win → close win → close loss → strong loss) using existing
     `club-tab`/badge CSS conventions in `src/index.css` (check existing
     `.match-result-*` classes and reuse the same color tokens).
   - Keep the existing win/draw/loss counts and win percentage columns;
     the indicator is additive, not a replacement.

3. **Recent form / streak** (`OpponentTable` + a new small helper):
   - Sort each opponent's collected matches by `dateTime` (reuse
     `compareMatches` ordering, falling back to `id` when `dateTime` is
     null, consistent with `MatchHistoryPanel`).
   - Compute: last-N (e.g. 5) result sequence for a compact trend
     indicator (W/L/D chips), and the current streak (count + type) from
     the most recent matches.
   - Render as an additional column or inline chip row in `OpponentTable`.

4. **Sorting controls** (`OpponentAnalysisPanel`, `OpponentTable`):
   - Add sortable column headers (win percentage, matches played,
     last-played date) to `OpponentTable`, following the existing URL
     `params`/`update` pattern used elsewhere on the page so sort state is
     shareable (e.g. `opponentSort` query param, mirroring how
     `opponentView` is normalized in the `useEffect`/`update` logic).
   - Keep current default ordering (`compareCategorizedOpponents` /
     `compareOpponentNames`) as the default when no explicit sort is
     selected.

5. **Head-to-head detail view** (new component, e.g.
   `OpponentHeadToHeadDetail`):
   - Make each opponent row in `OpponentTable` clickable/expandable
     (button or `<details>`, matching the accessible pattern already used
     by `MatchOpponentDetails`) to reveal every match/game played against
     that opponent: date, competition/season, result, set score, and
     quality indicator, most recent first.
   - Reuse formatting helpers already defined for the Matches tab
     (`scoreLabel`, `resultLabel`, `gameResultLabel`) instead of
     duplicating date/score formatting logic.

6. **Translations**: add new keys to `src/i18n/en.js`, `es.js`, `ca.js`
   for the new column headers, sort controls, streak/trend labels, and
   head-to-head detail strings (follow existing `detail.*` naming under
   the opponent-analysis keys).

7. **Tests** (`src/pages/PlayerDetailPage.test.jsx`):
   - Extend fixtures with `games` (including `homeSetsWon`/`awaySetsWon`)
     on relevant matches to exercise quality indicators, streaks, and
     head-to-head detail.
   - Add cases covering: quality indicator rendering for a strong vs.
     close win/loss, streak computation, column sorting (asserting the
     `location.search` sort param and resulting row order, mirroring the
     existing sorting/category tests), and expanding the head-to-head
     detail for one opponent.

8. **Manual verification**: run the frontend dev server, open a player
   detail page with multiple matches against the same opponent across
   different set scores, and confirm indicators, streaks, sorting, and
   the head-to-head drill-down render correctly for both the
   Categorization and Search opponent views.

# Implementation Guidelines

- No backend/API changes; all data needed is already present on
  `match.games` (see `MatchOpponentDetails` for the existing per-game
  shape: `homeSetsWon`, `awaySetsWon`, `result`, `opponents`).
- Preserve existing behavior for matches without game-level data (legacy
  matches, `match.games == null`): quality indicators, set margin, and
  streaks degrade gracefully (fall back to match-level games-won margin,
  or a neutral win/loss/draw color with no strength tier when no score is
  available at all) rather than error.
- Keep the opponent identity logic (`opponentKey`) unchanged — do not
  alter how opponents are deduplicated across matches/games.
- Follow the existing URL-param-driven state pattern (`params`/`setParams`
  via `update`) for sort state (`opponentSort` query param) so links stay
  shareable/bookmarkable like the rest of the page.
- Keep `OpponentTable`'s show-more/expand accessibility pattern
  (`aria-describedby`, `role="list"`/`listitem` conventions used
  elsewhere on this page) for the new interactive elements.

# Notes
- Acceptance criteria updated to cover quality indicators (set-margin
  based), sorting, recent-form/streaks, and a head-to-head detail
  drill-down, matching the four improvement areas confirmed with the
  user for this build plan.
- Implemented: per-opponent history tracking with set-margin quality
  tiers (`strong-win`/`win`/`close-win`/`draw`/`close-loss`/`loss`/
  `strong-loss`), recent-form chips (last 5 results, color-coded by
  quality, with translated `title`/`aria-label`), current win/loss/draw
  streak text, an `opponentSort` URL param driving win%/matches/last-
  played sorting across both the categorization and search views, and an
  expandable per-opponent head-to-head history table (date, competition,
  result, score). All in `src/pages/PlayerDetailPage.jsx`, with matching
  CSS in `src/app.css` and translations added to `en.js`/`es.js`/`ca.js`.
- Legacy matches without `games` fall back to the match-level
  `homeGamesWon`/`awayGamesWon` margin for the quality tier; matches with
  no score at all (`playerSets`/`opponentSets` both null) get a neutral
  win/loss/draw tier with no strength gradient.
- Test suite extended with 4 new cases (quality indicator titles, streak
  label, sort-by-win-percentage reordering, head-to-head expand/collapse)
  in `PlayerDetailPage.test.jsx`; full suite (`npx vitest run`, 32 files /
  202 tests) and `eslint` on the touched files pass.
- Not verified in a running browser (no dev-server launch config in this
  repo checkout) — verification relied on the automated test suite and
  lint only. Manual browser verification is recommended before closing
  the feature.
- Review fix: the default row order within each categorization table only
  used win %/matches/name, so it didn't actually reflect the Description
  section's win/loss quality case analysis (3-0 better than 3-2, etc).
  Added a per-opponent `averageMargin` (mean set-score margin across that
  opponent's history) and two category-aware default comparators:
  `compareFavorableQuality` sorts the Favourable table by descending
  average margin (dominant wins first, close wins last), and
  `compareDifficultQuality` sorts the Difficult/Problematic tables by
  ascending average margin (worst losses first). Both fall back to the
  original `compareCategorizedOpponents` (win %, matches, name) when an
  opponent has no set-score data, and are still overridden by the
  `opponentSort` dropdown (win %/matches/last played) when the user picks
  an explicit sort. Added 2 tests covering both orderings; full suite
  (`npx vitest run`, 32 files / 206 tests) and `eslint` pass.
- Review fix: the expanded head-to-head panel visually blended into the
  opponent row below it (no clear ownership by the row that triggered
  it). Fixed by giving the triggering `<tr>` an `is-expanded` class that
  shares the detail panel's background and drops its bottom border (so
  the row and its detail read as one continuous block), adding a left
  accent border to `.opponent-history-detail` matching the existing
  `.opponent-game` convention, and using a visibly stronger bottom border
  on the detail row to separate it from the next opponent's row. See
  `.opponent-row.is-expanded`, `.opponent-history-row`, and
  `.opponent-history-detail` in `src/app.css`.
