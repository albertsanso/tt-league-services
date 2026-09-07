# Build Plan

1. Confirm the existing player-detail opponent-analysis data flow and preserve
   the current client-side aggregation in
   `tt-data-league-frontend/src/pages/PlayerDetailPage.jsx`: each opponent row
   is already reduced from the selected player matches to `matches`, `wins`,
   `losses`, `draws`, and `playerWinPercentage`. No new REST field or query
   parameter is required for this sorting-only change.
2. Replace the category-specific opponent comparators in
   `PlayerDetailPage.jsx` with one explicit ordering policy for the
   categorization tables: `playerWinPercentage` descending, `matches`
   descending, then opponent name ascending using the existing Catalan,
   case-insensitive comparison with its deterministic case-sensitive
   fallback. Keep the existing category assignment, draw-only handling,
   unavailable-opponent handling, three-row preview, and “show more” behavior
   unchanged.
3. Apply the comparator to the favorable, difficult, and problematic
   categorization table inputs. Keep the opponent search sub-tab’s
   name-search behavior and alphabetical presentation separate unless the
   product decision expands this feature beyond the categorized analysis
   tables.
4. Extend
   `tt-data-league-frontend/src/pages/PlayerDetailPage.test.jsx` with focused
   fixtures that create ties at each precedence level and assert rendered row
   order: percentage wins first, then higher match count, then ascending
   player name. Cover enough rows across the category tables to catch the
   current difficult/problematic reverse-percentage ordering, and retain
   coverage for filters and the collapsed/expanded table presentation.
5. Run the focused frontend test file, then the frontend lint and production
   build. Review the final diff to ensure only the comparator/test changes and
   this SDD plan are included; do not alter the REST DTO, controller, query
   handler, persistence, or import modules unless implementation discovers a
   missing value in the existing response contract.

# Implementation Guidelines

- Treat the acceptance order as a strict lexicographic comparator:
  percentage descending → matches played descending → normalized display name
  ascending. Do not sort by wins, losses, category label, source, season, or
  insertion order before these keys.
- Use the already-normalized numeric values produced by `winPercentage()`.
  The percentage is based on decided matches (`wins + losses`), while the
  secondary key is the full opponent `matches` count, including draws, as
  displayed in the table.
- Keep sorting derived at render time; do not add React state, URL parameters,
  backend sorting, or a second global state mechanism.
- Preserve the existing Catalan UI, accessible table structure, loading/error
  behavior, and responsive styling. The change must remain keyboard- and
  screen-reader-compatible because it changes row order only.
- Follow the frontend module conventions: JavaScript/JSX, two-space
  indentation, single quotes, no semicolons, existing Vitest and Testing
  Library patterns, and no new dependency.
- The backend already exposes the underlying match/game opponent identity and
  result data through `PlayerDetailsDto`; do not duplicate sorting logic in
  `PlayerController`, `PlayerDetailsDto`, `FindPlayerDetailsQueryHandler`, or
  domain read models for this presentation concern.

# Notes

- The registry goal and acceptance criteria describe the player-details
  opponent analysis, while the feature title says “Component analysis sorting
  players criteria”; this plan interprets “component analysis” as the existing
  opponent categorization component.
- Existing implementation inspected: the frontend aggregates opponents in
  `PlayerDetailPage.jsx`, currently sorts favorable rows by percentage
  descending and difficult/problematic rows by percentage ascending, and does
  not use match count as a tie-breaker. The REST/API and domain layers provide
  the required source-scoped opponent and result data but do not need a
  contract change for this feature.
- Focused validation:
  `npm test -- --run src/pages/PlayerDetailPage.test.jsx`,
  `npm run lint`, and `npm run build` from `tt-data-league-frontend`.
  If implementation changes cross the API/module boundary, additionally run
  `mvn -pl tt-data-league-frontend -am test` and the relevant REST/domain
  tests; the planned change is expected to remain frontend-only.
