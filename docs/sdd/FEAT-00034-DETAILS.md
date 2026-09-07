# Build Plan

1. **Implement the Player details statistics surface.**
   - Use `tt-data-league-frontend/src/pages/PlayerDetailPage.jsx` as the
     implementation location.
   - Keep the Statistics tab derived from the selected seasons and render the
     connected-scatter presentation.

2. **Implement the chart data and visual behavior.**
   - Record the independent mapping used for `matchesPlayed` and
     `winPercentage`, including the percentage range of 0–100.
   - Render translated left and right vertical-axis labels and ticks, the
     accessible chart description, dataset legend, and tabular alternative
     alongside the SVG.

3. **Capture regression coverage.**
   - Use
     `tt-data-league-frontend/src/pages/PlayerDetailPage.test.jsx` to verify
     the default Statistics tab, connected-scatter URL state, both vertical
     axes, percentage ticks/grid, accessible chart description, and removal of
     the obsolete chart-type selector.
   - Retain the existing frontend test conventions.

4. **Synchronize the SDD artifacts.**
   - Keep the registry entry and this details file on the same `done`
     status, title, link, and acceptance criteria.
   - Move the complete registry block to Done after explicit user approval.

# Implementation Guidelines

- Keep the change within the existing frontend chart, i18n, and focused test
  boundaries. Do not alter APIs, Maven modules, or generated application files.
- Preserve the existing frontend boundaries and accessibility behavior:
  chart rendering remains in `PlayerDetailPage.jsx`, translations remain in
  the i18n files, and the text table remains the non-visual alternative.
- Treat the current implementation and tests as authoritative when describing
  delivered behavior. The SVG renders translated played-matches and
  win-percentage vertical-axis labels and ticks, a translated seasons X-axis
  label, percentage grid lines, and independently mapped series.
- The feature is complete and remains in `done` after explicit user approval.

# Acceptance Criteria

- [x] The Player details Statistics tab renders a connected scatter chart with
  the played-matches and win-percentage series for the selected seasons.
- [x] The chart calculates independent vertical positions for played matches
  and win percentage so both datasets remain visible even though their value
  ranges differ.
- [x] The chart exposes the win-percentage scale from 0% to 100%, translated
  left/right vertical-axis labelling, a legend for both datasets, and an
  accessible text table containing the underlying values.
- [x] The implemented chart behavior is covered by focused
  `PlayerDetailPage` tests, including default rendering, both vertical-axis
  labels and ticks, percentage grid lines, accessible chart description,
  removal of the chart-type selector, and the `connected-scatter` URL state.

# Notes

- 2026-09-07: Reimplemented the connected-scatter chart with independent
  played-matches and win-percentage mappings, left/right vertical-axis labels
  and ticks, translated accessible descriptions, and focused regression
  coverage. The feature was marked `done` after explicit user approval.
