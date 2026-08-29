# Build Plan

FEAT-00016 is in review. All thirteen requested Player Details fixes are
implemented in the frontend.

## Implemented scope

1. In `tt-data-league-frontend/src/pages/PlayerDetailPage.jsx`:
   - statistics rows are sorted by season descending in the table;
   - chart values are ordered by season ascending on the x-axis;
   - matches are sorted newest-first, with undated matches last and a stable
     ID tie-breaker;
   - matches are paginated locally at 10 rows per page;
   - match pagination resets on source/season/competition filter changes
     because the matches panel remounts per filter key;
   - opponent overflow appears only when a category has more than three rows,
     expands in the same table, and hides `Show more` after expansion;
   - selector defaults keep `Totes les temporades` and
     `Totes les competicions` first;
   - season selector width uses the longest available season label;
   - selector layout follows
     `docs/frontend/player-detail-selector-area-mockup-spec.md`: season spans
     the full first row, source is on the left of the second row, and
     competition is on the right of the second row.
   - selector changes retain the existing detail view while filtered data
     loads, avoiding a full-page refresh effect;
   - request and React context dependencies are stable, so selector changes
     trigger only the intended filtered request.
2. In `tt-data-league-frontend/src/app.css`:
   - `.club-filters` defines the two-row desktop selector grid with season on
     row 1 and source/competition on row 2;
   - smaller viewports stack selector controls to a single column while
     preserving responsive behavior;
   - season marks render as grey vertical lines;
   - `Show more` is right-aligned, lighter, and smaller than table text;
   - match pagination buttons use compact spacing (`0.45rem 0.7rem`), app
     design tokens, and explicit disabled state styling;
   - pagination controls use the page-standard `0.8rem` font size.
3. In `tt-data-league-frontend/src/pages/PlayerDetailPage.test.jsx`:
   - tests cover descending statistics order and ascending chart season order;
   - tests cover match sorting, 10-row pagination, and next-page navigation;
   - tests cover season selector dynamic width;
   - tests cover selector-area structure and mockup layout class assignments;
   - tests cover opponent table expansion and post-expansion `Show more` hide.
4. In `tt-data-league-frontend/src/hooks/usePlayers.test.jsx`:
   - tests verify that the previous player detail snapshot remains available
     while a replacement filter request is in flight.

# Implementation Guidelines

- Preserve the existing REST contract, URL filter semantics, and player,
  season, competition, and opponent identity handling.
- Keep pagination local to the Matches tab; do not persist its page in the URL.
- Keep undated matches after dated matches in deterministic order.
- Do not add backend, domain, persistence, configuration, dependency, or test
  framework changes.
- Use the existing Catalan UI copy and keyboard-accessible native buttons.

# Notes

- Decision: pagination remains local to the Matches tab and is not stored in
  the URL.
- Decision: undated matches appear after dated matches in deterministic order.
- Review resolution: opponent expansion uses one table and hides `Show more`
  after expansion.
- Reconciliation (2026-08-29): FEATURES.md had Fix 10 (pagination button
  styling) unchecked; implementation confirmed in `.pagination button` in
  `app.css` (compact padding, app variables, explicit disabled state), then
  synchronized as accepted.
- Historical clarification (2026-08-29): an intermediate interpretation placed
  season+competition on one row and source on a later row. This was superseded
  by Fix 12 and replaced with the mockup layout.
- Fix 12 resolution (2026-08-29): selector layout follows the reference mockup:
  season full first row; source left and competition right on second row.
- Fix 13 resolution (2026-08-29): filtered player-detail requests retain the
  previous data snapshot while loading. The initial request still displays the
  existing loading state, while subsequent selector changes update in place
  and announce the refresh accessibly without replacing the page.
- Validation (2026-08-29): frontend tests, lint, and production build pass;
  selector controls stack to one column below 768px while retaining the
  mockup's two-row desktop layout.

# Acceptance Criteria

- [x] In Player details, in Statistics Tab, the seasons list below the plot is sorted by season descending order, with the most recent season first.
- [x] In Player details, in Matches Tab, the matches list is sorted by match date descending order, with the most recent match first. If the list is longer than 10 matches, the list is paginated with 10 matches per page.
- [x] In Player details, in Opponent analysis Tab, the `Show more` component is a simple text, with a font size slightly smaller than the table font size, and a color that is slightly lighter than the table text color. The `Show more` component is aligned to the right of the table, and is displayed only if there are more than 3 opponents in the category.
- [x] In **player detail selector area**, the `season` selector has the option **Totes les temporades** as the first option, and the `competition` selector has the option **Totes les competicions** as the first option. The slider shows the season marks or scales in grey color as a vertical line.
- [x] The season selector width is dynamic, so that it can accommodate the longest season name without truncation.
- [x] The season selector occupies the full width of the first row, as defined
  by `docs/frontend/player-detail-selector-area-mockup-spec.md`.
- [x] The source selector occupies the left side of the second row, as defined
  by the selector mockup specification.
- [x] The competition selector occupies the right side of the second row, as
  defined by the selector mockup specification.
- [x] The **player detail selector area** remains responsive, with the season
  spanning both columns and the source and competition controls sharing the
  second row.
- [x] Changing a selector retains the current player detail view while the
  filtered request is loading instead of replacing the page with a full loading
  state.
- [x] React context dependencies and the request reload strategy trigger only
  the intended filtered request for selector changes.
- [x] The plot x-axis is ordered by season ascending order, with the oldest season first, and the most recent season last. The plot is responsive, so that it adjusts to the available width of the **player detail content area**.
- [x] In Matches Tab, the navigation buttons for the pagination of the matches list are smaller and aligned with the overall styling of the application.
- [x] In Matches Tab, the pagination buttons and page counter use the common font size of the page.
