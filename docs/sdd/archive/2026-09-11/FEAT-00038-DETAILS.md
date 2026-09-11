# Build Plan
1. Confirm the existing player-detail response contract and test fixtures:
   `PlayerDetailsDto.MatchDto.games[].opponents[]` already contains the
   source-scoped opponent player name and identity, while each game contains
   the selected player's game result. Preserve the existing match filters,
   pagination, score, date, competition, and team metadata.
2. Add a focused presentation helper in
   `tt-data-league-frontend/src/pages/PlayerDetailPage.jsx` that derives the
   match-row opponent from the normalized game opponents rather than
   `homeTeam`/`awayTeam`. Deduplicate names by the existing opponent identity
   key, retain deterministic game order, and render an explicit unavailable
   value when no opponent identity is available instead of mislabeling a team
   as a player. For doubles, display all resolved opposing player names in the
   same row.
3. Render opponent names and set scores as aligned per-game lists inside the
   Matches-tab cells, preserving deterministic game order so each opponent row
   lines up with its corresponding game result row. Show each game's set score
   such as `1-3` or `3-1` when available, with the qualitative win, loss, or
   draw result displayed to its right (or alone when the set score is
   unavailable). Color win rows green and loss rows red. Keep the global match
   result in the final score column numeric-only while retaining green win/red
   loss styling, and add a separate opponent team-name column immediately to
   its right. Omit both cells' rows when no opponent identity is available.
   Do not alter the existing match-level score calculation.
4. Extend
   `tt-data-league-frontend/src/pages/PlayerDetailPage.test.jsx` with focused
   Matches-tab cases covering home and away orientation, a player opponent
   whose team name differs, multiple games/doubles, unavailable opponent
   identity, a player-level result that differs from the team result, aligned
   multi-game cell rows matching the reference layout, removal of rows without
   opponent information, set-score result values, qualitative labels, and
   outcome colors.
   Retain regression coverage for sorting, pagination, filters, and existing
   opponent-analysis behavior.
5. Verify the normalized API boundary in
   `tt-data-league-frontend/src/api/players.test.js` still accepts the
   opponent/game payload used by the page and rejects malformed available
   opponents. If implementation exposes a missing backend contract field,
   update the corresponding REST/domain tests and mapping only; do not add a
   duplicate frontend lookup request.
6. Run the focused frontend tests, then `npm run lint` and `npm run build`
   from `tt-data-league-frontend`. Run the relevant Maven REST/domain tests
   only if the response contract or backend mapping changes, and review the
   final diff for unrelated SDD or generated-file changes.

# Implementation Guidelines

- Treat `games[].opponents[]` as the authoritative player identity for this
  view. Never infer a player's name from the opposing team's name and never
  resolve an opponent through an unscoped name lookup.
- Keep source and season identity attached to resolved opponents, use the
  existing normalized `available` flag for missing data, and preserve
  deterministic output for repeated opponents across singles and doubles
  games.
- Keep the existing REST endpoint, query parameters, loading/error behavior,
  URL state, responsive table structure, Catalan/Spanish/English
  translations, and keyboard/screen-reader behavior. Add or adjust only the
  smallest translation keys needed to distinguish player-versus-player and
  team-level result labels.
- Keep game-result derivation during rendering; do not add React state,
  additional network requests, backend sorting, or a new dependency.
- A game without available opponent identity must not render an opponent or
  result sub-row; never silently show the opponent club as a player.
- Opponent and result cells must use the same game order and row count so
  doubles and multi-game matches remain visually aligned.
- All match-table cells, including the stacked opponent and result cells, must
  align their content to the top edge of the match row.
- The final score cell must present only the numeric global match score, use
  green for wins and red for losses, and place the opponent team name in a
  separate column immediately to its right.

# Notes

- The current implementation at
  `tt-data-league-frontend/src/pages/PlayerDetailPage.jsx:205-227` renders
  `opponentName(item)` from team names and `resultLabel(item.result, t)` from
  the team result. The same page already renders game-level opponent names and
  results in `MatchOpponentDetails`, so the API contract appears sufficient
  for a frontend-only fix.
- The backend currently maps game opponents in
  `FindPlayerDetailsQueryHandler.toGame` and exposes them through
  `PlayerDetailsDto`; preserve that source-scoped identity flow.
- The registry description requires the Matches-tab result to represent the
  player-versus-player outcome. This plan therefore treats that behavior as
  part of the feature even though the initial registry criteria primarily
  mention the opponent name.
- The Matches tab now derives opponent labels from game-level identities,
  deduplicates doubles opponents, and shows an unavailable value when no
  identity is available. For matches with games, it renders every numbered
  game result instead of collapsing them into the global match result; the
  games without opponent identity are omitted from both aligned cell lists.
- The opponent and result columns now render matching bordered rows per game,
  following the supplied reference screenshot rather than comma-separated
  values. Available game results use set scores such as `3-1` followed by the
  qualitative label; rows are green for wins and red for losses.
- The supplied Players details screenshot is the layout reference: opponent
  names and results are shown as aligned stacked rows within their respective
  cells, while date, source, season, competition, and score remain match-level
  columns.
- Focused and full frontend tests, lint, production build, and feature
  registry validation completed successfully after the layout update.
- Focused and full frontend tests, lint, production build, and feature
  registry validation completed successfully.

# Reference Wireframe

```text
| Date       | Source | Season    | Competition | Opponent             | Result       | Score  | Opponent team |
|------------|--------|-----------|-------------|----------------------|--------------|--------|---------------|
| 13/01/2026 | BCNESA | 2025-2026 | Vet 1a      | +------------------+ | +----------+ | 4 — 2  | Club Beta     |
|            |        |           |             | | <opponent 1>    | | | 3-1 Win  | |        |               |
|            |        |           |             | +------------------+ | +----------+ |        |               |
|            |        |           |             | +------------------+ | +----------+ |        |               |
|            |        |           |             | | <opponent 2>    | | | 1-3 Loss | |        |               |
|            |        |           |             | +------------------+ | +----------+ |        |               |
```

Only games with available opponent information occupy the stacked rows; the
opponent and result cells always contain the same number of rows in the same
order.

Unavailable-opponent games are omitted from both cells rather than rendered as
placeholder rows.

All match-row cells are top-aligned so the stacked lists begin at the same
vertical position as the date, source, season, and competition values.

Game-result rows show the available set score (`homeSetsWon-awaySetsWon`) and
fall back to the qualitative game result only when either set value is absent.
Focused and full frontend tests, lint, production build, and feature
validation passed after this change.

Each result row now places the qualitative label to the right of the set score;
win rows use the success color and loss rows use the error color. The full
frontend suite now covers 153 tests.

The final score column now shows only the numeric global match score, colored
green for wins and red for losses, followed by a dedicated opponent-team
column.
