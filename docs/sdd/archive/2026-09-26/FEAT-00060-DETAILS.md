# Build Plan
> The UX/layout proposal to build this plan from — including desktop and
> mobile ASCII wireframes, interaction notes, and explicit in/out-of-scope
> boundaries — lives at
> [docs/frontend/oponent-analysis-redesign.md](../frontend/oponent-analysis-redesign.md).

Affected files:
- `tt-data-league-frontend/src/pages/PlayerDetailPage.jsx` — `OpponentAnalysisPanel` and its child components (`OpponentCategoryTable`, `OpponentTable`, `OpponentFormChips`, `OpponentHeadToHead`), plus the `OPPONENT_VIEWS` constant and `opponentView`/`opponentSort` URL-param wiring.
- `tt-data-league-frontend/src/app.css` — `.opponent-tabs`, `.opponent-category`, `.opponent-search`, `.opponent-sort`, `.opponent-table`, `.opponent-more`, `.opponent-form*`, `.opponent-expand-button`, `.opponent-history-*` rule blocks (lines ~2870-3180) and the responsive stacking block for these tables (~3106-3179).
- `tt-data-league-frontend/src/i18n/en.js`, `ca.js`, `es.js` — `detail.opponent*` and `detail.category*` string groups.
- `tt-data-league-frontend/src/pages/PlayerDetailPage.test.jsx` — existing opponent-analysis test suite (currently asserts sub-tab behavior).

1. **Data/derivation layer — no changes.** Confirm `addOpponent`, `buildOpponentRow`, `opponentCategory`, `winPercentage`, `averageMargin`, and the sort comparators stay untouched; the redesign only changes what wraps and renders `categorizedRows`.

2. **Replace the sub-tab state with a category filter.**
   - Remove `OPPONENT_VIEWS` (`CATEGORIZATION` / `SEARCH`) and the two `club-tab` buttons.
   - Introduce an `opponentFilter` URL param (`all | favorable | hard | problem`) read the same way `opponentView`/`opponentSort` are today (`params.get(...)`, defaulting to `all`), so filter state stays shareable/bookmarkable like the existing params.
   - Compute category counts once from `categorizedRows` (`{all, favorable, hard, problem}`) for the chip labels.

3. **Build the filter-chip toolbar.**
   - Render four toggle buttons (All / Favourable / Difficult / Problematic) as a `role="group"` (not `role="tablist"`, since there is one panel, not one per chip), each chip showing its count and `aria-pressed`/`aria-label` per the interaction notes (e.g. `aria-label="Problematic, 5 opponents"`).
   - Keep the existing search `<input>` and sort `<select>` in the same toolbar row, both still updating URL params via `update(...)`.

4. **Collapse to a single opponent list.**
   - Replace the three `OpponentCategoryTable` renders + separate Search branch with one `OpponentTable` call fed by: `categorizedRows.filter(matchesChip).filter(matchesSearch).sort(sortComparator ?? compareCategorizedOpponents)`.
   - `OpponentTable` always receives `includeCategory` (category badge shown on every row now, not just in the old Search view) — drop the `includeCategory` prop's conditionality or default it to `true`.
   - Keep the single "Show N more" control (`OpponentTable`'s existing `expanded`/`maxVisible` logic) — no per-section pagination remains once there is only one list.

5. **Style the category badge and chips.**
   - Add badge classes (e.g. `.opponent-category-badge--favorable/hard/problem/unknown`) with color tokens matching the proposal's legend (favourable green, difficult amber, problematic red, uncategorized neutral), reusing existing CSS custom properties where the design system already defines semantic colors, or introducing scoped ones next to the `.opponent-*` block.
   - Add `.opponent-filter-chips` styles (active/inactive state, count sub-label) near the existing `.opponent-sort`/`.opponent-search` rules; remove the now-unused `.opponent-tabs` rule once the sub-tabs are gone.
   - Restyle `.opponent-history-row`/`.opponent-history-detail` as a nested detail card (per proposal §3) without changing the columns it renders (Date / Competition / Result / Score).

6. **i18n updates.**
   - Add `detail.opponentFilterAll`, `detail.opponentFilterFavorable`, `detail.opponentFilterHard`, `detail.opponentFilterProblem` (with count interpolation) to `en.js`, `ca.js`, `es.js`.
   - Remove or repurpose `detail.opponentCategorizationTab` / `detail.opponentSearchTab` (sub-tab labels no longer needed) and `detail.opponentViews` (tablist aria-label) — replace with a label for the new chip group (e.g. `detail.opponentFilterGroupLabel`).
   - Keep `detail.opponentSearch`, `detail.opponentSort*`, `detail.category*Label`, `detail.showHeadToHead`/`hideHeadToHead`, `detail.headToHeadTitle` as-is.

7. **Update `PlayerDetailPage.test.jsx`.**
   - Replace assertions that click/assert the Categorization and Search sub-tabs with assertions that click filter chips and check the resulting filtered list.
   - Add coverage for: chip counts matching category totals, chip + search combined filtering, category badge present on every row regardless of active filter, and that head-to-head expand/columns are unchanged.
   - Verify `opponentSort` behavior is unaffected (still reachable from the single list).

8. **Manual verification.**
   - Run the dev server, open a player's Opponent Analysis tab, and check: chip counts match categorized totals, chip filtering, search-plus-chip combination, sort options, expand/collapse of head-to-head, and the mobile card layout at <640px width, in both light and dark theme if the app supports it.

# Implementation Guidelines

- Follow the layout, filter-chip, and interaction design in
  [docs/frontend/oponent-analysis-redesign.md](../frontend/oponent-analysis-redesign.md).
  This is a presentation-layer redesign only: `opponentCategory`,
  `buildOpponentRow`, win %, streak, recent-form quality tiers, and
  head-to-head history computation in
  `tt-data-league-frontend/src/pages/PlayerDetailPage.jsx` must not change.
- Existing sort options (Default / Win % / Matches played / Last played) and
  the inline head-to-head expand pattern must be preserved, only restyled.

# Notes

- Redesign proposal authored and reviewed against the current implementation
  at `PlayerDetailPage.jsx` (`OpponentAnalysisPanel` and related components):
  see [docs/frontend/oponent-analysis-redesign.md](../frontend/oponent-analysis-redesign.md)
  for the full analysis of current pain points and the new wireframes.

## Implementation validation (2026-09-12)

- Implemented per the build plan: `OPPONENT_VIEWS`/`opponentView` replaced
  with an `opponentFilter` URL param and a filter-chip toolbar
  (`OpponentFilterChip`), the three `OpponentCategoryTable` sections
  collapsed into one `OpponentTable` fed by `categorizedRows` filtered by
  chip + search and sorted by the existing sort comparators, and every row
  now renders an always-visible `OpponentCategoryBadge`. `app.css` gained
  `.opponent-filter-chips`/`.opponent-filter-chip*`, `.opponent-category-badge*`,
  and `.opponent-toolbar` rules, reusing the existing `--color-success` /
  `--color-warning` / `--color-error` tokens for category tone; the dead
  `.opponent-tabs`/`.opponent-category` rules were removed.
- `PlayerDetailPage.test.jsx` rewritten: sub-tab tests replaced with
  filter-chip equivalents, and the three per-category empty-state/order
  assertions collapsed into unified-list assertions. Full suite passes
  (44/44 in this file, 276/276 project-wide) and `eslint` is clean.
- Deliberate behavior change from the old design: the per-category default
  quality sort (`compareFavorableQuality`/`compareDifficultQuality`, ordering
  favourable opponents by best average set margin and difficult/problematic
  opponents by worst average set margin) no longer applies, since there is
  only one flat list instead of three independent tables. The default sort
  is now `compareCategorizedOpponents` (win % desc, then matches desc, then
  name) across whichever opponents the active filter/search show — consistent
  with the explicit sort dropdown, which was already flat/unified in the old
  Search sub-tab.
- Corrected the registry acceptance criteria: the auto-generated criteria
  from feature creation referenced season/source/competition filters and a
  head-to-head link to the match summary page, neither of which is part of
  this redesign's scope (season/source/competition are pre-existing
  player-level filters; the head-to-head detail was never linked to the
  match summary page). Reworded to match the delivered category filter
  chips and inline head-to-head detail.
- Not verified in a live browser: `usePlayerDetails` requires an
  authenticated backend session not available in this environment. Manual
  verification (chip counts, filtering, search-plus-chip combination, sort
  options, expand/collapse, mobile stacking, light/dark theme) from build
  plan step 8 is still outstanding and should be done against a real
  environment before closing the feature.
