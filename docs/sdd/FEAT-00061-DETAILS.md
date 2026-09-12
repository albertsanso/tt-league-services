# Build Plan
> The metrics catalog and UX proposal this plan is built from — including
> the desktop/mobile "Opponent insights" panel mockups, the feasibility
> table of already-available fields, and what's explicitly out of scope —
> lives at
> [docs/frontend/oponent-analysis-metrics-expansion.md](../frontend/oponent-analysis-metrics-expansion.md).

Affected files:
- `tt-data-league-frontend/src/pages/PlayerDetailPage.jsx` — `buildOpponentRow`,
  `addOpponent`, `OpponentTable`, `OpponentHeadToHead`, `opponentComparator`,
  and the sort `<select>` in `OpponentAnalysisPanel`.
- `tt-data-league-frontend/src/utils/matchSummary.js` — reuse
  `computeTrendNote(currentWinRate, previousWinRate)` (already exported,
  used by `MatchSummaryPage.jsx`) instead of writing a second trend
  implementation.
- `tt-data-league-frontend/src/app.css` — new `.opponent-insights-panel`,
  `.opponent-insight-tile*` rules alongside the existing
  `.opponent-history-detail` block (~line 3065).
- `tt-data-league-frontend/src/i18n/en.js`, `ca.js`, `es.js` — new
  `detail.insight*` string group.
- `tt-data-league-frontend/src/pages/PlayerDetailPage.test.jsx` — new
  coverage for the insights panel and the Closeness sort option.

1. **Extend per-opponent aggregation in `addOpponent`/`buildOpponentRow`.**
   Every history entry already carries `margin`, `competition`, `dateTime`;
   extend what's captured per entry with `gameType` (`INDIVIDUAL`/`DOUBLES`,
   from the `game.type` already available where `addOpponent` is called,
   [PlayerDetailPage.jsx:296-337](../../tt-data-league-frontend/src/pages/PlayerDetailPage.jsx))
   and `isHome` (`match.playerTeam === match.homeTeam`, already computed in
   `gameSetsForPlayer`). `buildOpponentRow` derives from `sortedHistory`:
   - `closeness`: bucket `averageMargin` (already computed, currently
     unused) into `decisive` (`|avg| >= 2`), `competitive` (`1 <= |avg| < 2`),
     `nail-biter` (`|avg| < 1`); `null` when `averageMargin` is `null`.
   - `singlesRecord` / `doublesRecord`: `{ wins, losses, matches }` split by
     `gameType`; omit (`null`) when that split has zero matches.
   - `homeRecord` / `awayRecord`: same shape, split by `isHome`.
   - `longestWinStreak` / `longestLossStreak`: reuse the same run-length
     logic as the existing `currentStreak` helper, applied to the full
     `sortedHistory` instead of just the head.
   - `matchesPerSeason`: distinct `season` count on history entries vs.
     `matches`, or a simple `matches / distinctSeasons` average.
   - `competitionBreakdown`: `{ competition, wins, losses, matches }[]`,
     only populated when more than one distinct `competition` appears.
   - `trend`: split `sortedHistory` into two equal recent windows (e.g. most
     recent up to 5 vs. the preceding up to 5); compute win rate for each
     and call `computeTrendNote` from `utils/matchSummary.js`; only compute
     when both windows have at least 2 matches (mirrors the Match Summary
     page's minimum-sample gate).
2. **Render the "Opponent insights" panel.**
   Inside `OpponentHeadToHead` (or a new sibling component rendered next to
   it from `OpponentTable`'s expanded row), add a tile grid above the
   existing per-match table. Each tile is conditionally rendered per the
   proposal's "tiles only render when meaningful" rule — omit rather than
   show a placeholder. Reuse the category-badge/tile visual language
   already established for `.opponent-category-badge` (semantic
   success/warning/error tokens) for the closeness tile.
3. **Add the "Closeness" sort option.**
   Extend `OPPONENT_SORTS` with `CLOSENESS`, add a case to
   `opponentComparator` sorting by `Math.abs(averageMargin)` ascending
   (nulls last), and add the option to the existing sort `<select>` in
   `OpponentAnalysisPanel`.
4. **i18n.**
   Add `detail.insightsTitle`, `detail.insightCloseness*` (label + the three
   bucket labels), `detail.insightSingles`, `detail.insightDoubles`,
   `detail.insightHomeAway`, `detail.insightStreaks`, `detail.insightLastMet`,
   `detail.insightTrend`, `detail.opponentSortCloseness` to `en.js`/`ca.js`/`es.js`,
   following the existing pattern of base strings in `ca.js` and per-locale
   overrides in `en.js`/`es.js`.
5. **Update `PlayerDetailPage.test.jsx`.**
   Add fixtures/tests for: closeness bucketing at each threshold, singles-only
   and doubles-only opponents (tile omitted for the empty split), home/away
   split, longest vs. current streak divergence, competition breakdown
   appearing only with 2+ competitions, trend appearing/not appearing at the
   minimum-sample boundary, and the new Closeness sort option.
6. **Manual verification.**
   Confirm the insights panel renders correctly at desktop and mobile widths,
   tiles are omitted (not blank) when not meaningful, and the panel doesn't
   change the outer row/column layout, category chips, or toolbar shipped in
   FEAT-00060.

# Implementation Guidelines

- Follow the metrics catalog and panel layout in
  [docs/frontend/oponent-analysis-metrics-expansion.md](../frontend/oponent-analysis-metrics-expansion.md).
  This builds on [FEAT-00060](./FEAT-00060-DETAILS.md)'s unified opponent list — it
  does not change the row/column layout, category chips, or search/sort
  toolbar shipped there.
- Every metric is a client-side aggregation over data already available in
  `PlayerDetailPage.jsx` (`entry.margin`, `game.type`, `playerTeam` vs.
  `homeTeam`/`awayTeam`, `competition`/`season`, `dateTime`). No new API
  field or schema change is in scope.
- Tiles must be conditionally rendered: omit a tile rather than show a
  placeholder when the underlying metric isn't meaningful (e.g. no doubles
  games played against that opponent, only one competition faced, or too
  few matches to compare trend windows) — reuse the same minimum-sample
  threshold already used by the Match Summary page's trend logic.
- Ranking/ELO comparisons, set-by-set sequencing (e.g. comeback rate), and
  cross-player peer comparisons are explicitly out of scope — none of that
  data reaches this page today.

# Notes

- Proposal authored as a follow-up to FEAT-00060: the unified list fixed
  navigation, this expansion adds depth via a per-opponent "Opponent
  insights" panel inside the existing head-to-head expand. See
  [docs/frontend/oponent-analysis-metrics-expansion.md](../frontend/oponent-analysis-metrics-expansion.md)
  for the full feasibility analysis, metrics catalog, and wireframes.
- Implemented the Opponent insights panel (closeness, singles/doubles split, home/away split, longest streaks, competition breakdown, trend) and the Closeness sort option in PlayerDetailPage.jsx, per docs/frontend/oponent-analysis-metrics-expansion.md. Added i18n keys (ca/en/es) and CSS for .opponent-insights-panel/.opponent-insight-tile*. Added test coverage for closeness bucketing, singles/doubles omission, home/away omission, streak divergence, competition breakdown gating, trend minimum-sample boundary, and the Closeness sort. All 283 frontend tests and lint pass. Manual visual verification in a running browser was not performed (player detail routes require an authenticated session and live backend data not available in this session).
