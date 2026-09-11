# Build Plan

1. **Fix the per-opponent result source** in
   `tt-data-league-frontend/src/pages/PlayerDetailPage.jsx`,
   `OpponentAnalysisPanel` (function starting ~line 244):
   - In the `games.forEach((game) => game.opponents.forEach(...))` loop
     (~line 250-255), change the `addOpponent(opponents, key, opponent,
     match.result)` call to pass `game.result` instead of `match.result`.
   - Leave the two fallback branches untouched — they have no per-game
     data to draw from, so they must keep using `match.result`:
     - the `match.games == null || match.games.length === 0` legacy branch
       (~line 256-258)
     - the `opponentKeys.size === 0` unavailable-opponent branch
       (~line 259-260)
   - `opponentKeys` still dedupes by opponent within a match (so a player
     facing the same opponent in two games of the same match — e.g. two
     doubles legs — keeps counting that match once against that opponent,
     using the first game's result). This preserves existing dedup
     behavior; only the result value fed into `addOpponent` changes from
     match-level to game-level.
   - No changes needed to `addOpponent`, `winPercentage`, or
     `opponentCategory` — they already operate on whatever `result` they're
     given, so passing the corrected per-game result is sufficient to fix
     `wins`/`losses`/`draws`/`winPercentage`/`category` for each opponent.

2. **Add regression tests** in
   `tt-data-league-frontend/src/pages/PlayerDetailPage.test.jsx`:
   - New test: a match where `match.result` differs from an individual
     `game.result` against a given opponent (mirrors the reported bug —
     e.g. team match `result: 'win'` containing one game
     `result: 'loss'` against a specific opponent). Assert:
     - The Matches tab still shows the game's own result (existing
       behavior, already covered).
     - The Opponent Analysis tab (categorization and/or search view) shows
       that opponent's win/loss counts derived from `game.result`
       (`loss`), not `match.result` (`win`).
   - New test: two games within the same match against the same opponent
     with different results, to confirm the existing per-match dedup
     (`opponentKeys`) still applies and only the first game's result is
     counted — document this as intentional via the assertion.
   - Verify the existing legacy-path tests (matches without a `games`
     array, e.g. `categoryDetails`, `sortingDetails`, `manyOpponentsDetails`)
     still pass unchanged, since they exercise the untouched
     `match.games == null` branch.

3. **Manual verification against real data**: use a known repro (player +
   opponent + season/match combination where the Matches tab and Opponent
   Analysis tab currently disagree) to confirm the two tabs now agree after
   the fix. Record the repro and result under `# Notes` once found/verified.

4. **Run the frontend test suite** for `tt-data-league-frontend` (existing
   `PlayerDetailPage.test.jsx` suite plus the new tests) and confirm no
   regressions in the Matches tab, sorting, categorization, or search
   behavior.

# Implementation Guidelines
- Scope is limited to `OpponentAnalysisPanel` and its `addOpponent` call
  site in `PlayerDetailPage.jsx`; do not touch the Matches tab rendering
  (`matchResultRows`/`matchGameResultRow`), which is already correct and is
  the source of truth this fix aligns Opponent Analysis to.
- Do not change `addOpponent`, `winPercentage`, or `opponentCategory`
  signatures/logic — only the `result` value passed in for the per-game
  branch changes.
- Keep the existing per-match dedup (`opponentKeys`) behavior: an opponent
  faced multiple times within one match still counts as one match entry
  against them, using the first game found for that opponent in that
  match's game order.
- Out of scope: changing how `match.result` is used elsewhere (overall
  win-percentage computation, legacy/unavailable branches, statistics
  tab).

# Notes
- User-reported symptom: in Player details, the Matches tab correctly shows
  won/lost matches, but the Opponent Analysis tab is sometimes wrong and
  contradictory — for the same opponent, Matches tab shows a loss while
  Opponent Analysis shows a win (or vice versa).
- Root cause identified in `tt-data-league-frontend/src/pages/PlayerDetailPage.jsx`:
  - The Matches tab (`matchResultRows` → `matchGameResultRow`, lines ~450-470)
    renders the result **per game** (`game.result`) against each opponent in
    that game.
  - The Opponent Analysis tab (`OpponentAnalysisPanel`, lines ~244-262) calls
    `addOpponent(opponents, key, opponent, match.result)` inside the
    per-`game` loop, i.e. it records **the overall match result**
    (`match.result`) against every opponent found in any game of that match,
    not the individual game's own result.
  - In a team/doubles match, a player can win the overall match
    (`match.result === 'win'`) while losing an individual game against a
    specific opponent (`game.result === 'loss'`), or the reverse. That's
    exactly the contradiction reported.
- Likely fix direction: in `OpponentAnalysisPanel`'s per-game loop, pass
  `game.result` (falling back to `match.result` only for the legacy
  no-games branch at lines 256-258, where no per-game result exists) to
  `addOpponent` instead of `match.result`. Then re-derive `winPercentage`
  and `opponentCategory` from the corrected per-opponent wins/losses/draws.
- Needs a concrete repro (player + opponent + season/match) to verify the
  fix against real data before closing.
- **Implemented**: `OpponentAnalysisPanel`'s per-game `addOpponent(...)`
  call now passes `game.result` instead of `match.result`
  (`tt-data-league-frontend/src/pages/PlayerDetailPage.jsx`). Legacy
  (no-games) and unavailable-opponent branches were left on `match.result`
  as planned, since they have no per-game data.
- Added two regression tests to `PlayerDetailPage.test.jsx`: one asserting
  the Opponent Analysis tab now categorizes an opponent by the game's own
  result even when it contradicts the overall match result, and one
  confirming the existing per-match dedup still counts only the first
  game's result when the same opponent appears twice in one match.
- Full frontend suite run (`npx vitest run` in `tt-data-league-frontend`):
  32/32 in `PlayerDetailPage.test.jsx`, 198/198 across the whole project —
  no regressions.
- Still open: a live-data repro (real player/opponent/match combination)
  to confirm the fix against production data before closing to `done`.
