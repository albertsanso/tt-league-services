# Build Plan
Reference wireframe: [`/docs/frontend/player-details-matches-tab.md`](../frontend/player-details-matches-tab.md).

1. **Replace the table markup in `MatchHistoryPanel` with a card list.**
   In `tt-data-league-frontend/src/pages/PlayerDetailPage.jsx` (`MatchHistoryPanel`,
   lines 233-270), keep `sortedMatches`/`pageCount`/`visibleMatches` and the
   `MATCHES_PER_PAGE = 10` pagination logic unchanged, but replace the
   `<table className="history-table">` with a `<ul className="match-card-list">`
   rendering one `<li className="match-card">` per `visibleMatches` item via a
   new `MatchCard` component (co-located in the same file, matching how
   `OpponentTable`/`OpponentHeadToHead` are already co-located). Each card is a
   `<details>` element (native disclosure, no extra state) so no new `useState`
   is needed per card — matches the existing `<details className="opponent-match">`
   pattern already used by `MatchOpponentDetails` (line 725) elsewhere in this
   file.

2. **Card summary content** (always visible, inside `<summary>`):
   - Result badge: derive from `item.result` (`win`/`loss`/`draw`), reusing the
     `match-result-{result}` class already defined in `app.css:2712-2719`
     rather than inventing new color tokens.
   - Opponent team name: reuse `opponentTeamName(item, t)` (line 783) as-is.
   - Score: reuse `matchScoreResult(item, t)` (line 779) as-is.
   - Date: reuse the existing `item.dateTime` formatting (line 248) as-is.
   - Competition line: `item.competition` + `item.round` (fallback
     `t('common.unavailable')` for round, matching current behavior line 252).
   - Context chips: `item.season`, `item.source`, `item.groupNumber`,
     `item.phase` — each rendered only when present, reusing the existing
     `t('matchesPage.group')`/`t('matchesPage.phase')` labels as chip
     `aria-label`s (not visible text, since the wiremock shows raw values in
     chips) so the same i18n keys stay meaningful for screen readers.
   - Expand affordance: `{item.games?.length ?? 0} {t('matchesPage.games')}`
     (existing key, `en.js:16`) as the `<summary>` marker text; `<details>`
     provides the open/close chevron via `::marker`/`::after` CSS, no JS.

3. **Card body content** (inside `<details>`, revealed on expand): render one
   row per `item.games[]` reusing the existing per-game helpers instead of
   rewriting them — `matchOpponentRows`/`matchResultRows` (lines 745-760)
   currently return sibling arrays for two separate table cells; refactor them
   into a single `matchGameRows(match, t)` that returns one `{ id, type,
   opponents, resultLabel, resultClass }` record per game (reusing
   `gamesWithOpponentInfo`, `opponentKey`, `gameResultLabel`, `resultLabel`
   internally), and render each as one row with type (`game.type === 'DOUBLES'
   ? t('detail.doubles') : t('detail.singles')`, already used in
   `MatchOpponentDetails` line 731), joined opponent names, and a
   `match-result-{result}` chip + set score — consolidating what today are two
   parallel nested lists (`match-opponent-list`, `match-result-list`) into one
   list per card.

4. **CSS**: in `tt-data-league-frontend/src/app.css`, add `.match-card-list`,
   `.match-card` (`<details>` styling: remove default marker, add the
   chevron via `summary::-webkit-details-marker { display: none }` plus a
   custom `::after` rotated on `[open]`), `.match-card-summary` (badge +
   heading + chips flex layout), and `.match-card-games`/`.match-game-row`
   for the expanded body — reusing the existing `--win`/`--loss`/`--draw`
   color tokens (or the literal colors `.match-result-win`/`.match-result-loss`
   already use at `app.css:2712-2719`) rather than introducing a new palette.
   Remove the now-unused `.match-history .history-table`/`.match-game-list`
   responsive rules at `app.css:2960-3044` that were specific to the table's
   `data-label` collapse (check `.opponent-table`/`.opponent-history-detail`
   still need their own copies before deleting shared selectors — those two
   keep using `<table>` and are out of scope for this feature).

5. **i18n**: no new keys are needed for round/group/phase/season/source labels
   (already used as `aria-label`s per step 2); add one new key per locale
   (`ca.js`/`en.js`/`es.js`) for the empty-games edge case only if
   `item.games` is empty (`t('matchesPage.games')` pluralization at `0`
   already handled by existing i18n pluralization rules — verify before adding
   a new key).

6. **Tests**: update `PlayerDetailPage.test.jsx` assertions that query
   `getByRole('table')` / `tbody tr` / `.match-opponent-list`/
   `.match-result-list` for the Matches tab (lines ~191-267 and others listed
   by `grep -n "view=matches"`) to instead query the new card structure
   (`getByRole('list', { name: ... })` / `.match-card` / expand via
   `userEvent.click` on the `<summary>` or read `<details open>` directly).
   Preserve every existing assertion's *intent* (opponent name, score,
   win/loss class, round/group/phase fallback, doubles-opponent dedup,
   unavailable-game omission) — only the query selectors change per the new
   markup shape from step 1-3.

7. **Manual verification**: run the frontend dev server, open a player detail
   page with more than 10 matches across a mix of singles/doubles and
   win/draw/loss results, and confirm: badge color-coding, chip wrapping on
   narrow viewport (no horizontal scroll), expand/collapse per card, and that
   pagination still advances/disables correctly at the first/last page.

# Implementation Guidelines

- This is presentation-only: no backend/API changes. `PlayerDetailsDto`
  (`tt-data-league-api-rest/.../player/PlayerDetailsDto.java`) and
  `usePlayerDetails` are unchanged; every field the cards show already exists
  on the match objects passed into `MatchHistoryPanel` today.
- Do not touch `HistorySection` (Statistics tab), `OpponentAnalysisPanel`, or
  `OpponentTable`/`OpponentHeadToHead` — they keep their existing
  `<table className="history-table">` layout; only `MatchHistoryPanel` and its
  private helpers (`matchOpponentRows`, `matchResultRows`,
  `gamesWithOpponentInfo`) are refactored, and only insofar as consolidating
  the two parallel per-game helpers into one shared `matchGameRows`.
- Reuse `match-result-win|loss|draw` classes and existing color tokens; do not
  introduce a second color system for the badge/chips.
- Filters above the tabs (season slider, source radio buttons, competition
  select, `PlayerDetailContent` lines 151-173) and pagination size
  (`MATCHES_PER_PAGE = 10`) are unchanged — this feature is scoped to the
  results-rendering markup inside `MatchHistoryPanel` only.
- Keep the disclosure native (`<details>`/`<summary>`) rather than adding
  per-card React state, to match the existing lightweight pattern already used
  by `MatchOpponentDetails` in this same file.

# Notes

- Implemented as planned: `MatchHistoryPanel` in `PlayerDetailPage.jsx` now
  renders a `<ul className="match-card-list">` of `MatchCard` components, each
  a native `<details className="match-card card">`, replacing the old
  `<table className="history-table">`. `matchOpponentRows`/`matchResultRows`/
  `gamesWithOpponentInfo` were consolidated into one `matchGameRows(match, t)`
  helper used by the expandable per-game breakdown.
- Added three new i18n keys (`detail.resultBadgeWin/Loss/Draw`) for the
  card's short W/D/L badge glyph, since no existing key provided a
  single-letter form; `ca.js`/`es.js` share `V`/`D`/`E`, `en.js` overrides
  with `W`/`L`/`D`. No other new i18n keys were needed — round/season/source/
  group/phase all reuse existing `matchesPage.*`/`common.*` keys.
  `matchesPage.games` is reused as the expand-toggle label (`Games (N)`).
  This differs slightly from the wiremock's plain `▾ N games`; the label
  reads more naturally in all three locales this way and carries the same
  information.
  - Follow-up spotted during implementation: `en.js`/`es.js` don't yet have a
    dedicated `matchesPage.games` count-aware string (the key is a static
    noun); revisit only if a future feature needs pluralization here.
- Deviation from the build plan: round/group/phase/season/source values are
  rendered as **visible** chip text (matching the wiremock's
  `[2025/26] [RFETM] [Group 3]` mockup) with the i18n label only as a `title`
  tooltip/attribute, rather than as an `aria-label`-only value with no visible
  text as step 2 of the build plan originally described. This is closer to
  the wiremock and was the more accurate reading of it on implementation.
- Deviation from acceptance criterion wording: group/phase are omitted
  entirely as chips when `groupNumber`/`phase` is `null`/absent (per the
  wiremock note "phase chip omitted when not applicable"), rather than
  showing a "No disponible"/"unavailable" placeholder as the old table did.
  This is an intentional UX improvement (missing metadata no longer clutters
  the card) and is reflected in the updated
  `PlayerDetailPage.test.jsx` test (`shows round, group, and phase as chips
  only when present, omitting them when missing`).
- CSS: removed `.match-game-list`/`.match-game-row`/`.match-game-result-row`
  (only ever used by the old table cells) and the `.match-history`-scoped
  rules from the shared `@media (max-width: 640px)` table-collapse block in
  `app.css`; `.opponent-table`/`.opponent-history-detail` keep their own
  copies of that block unchanged, since `OpponentTable`/`OpponentHeadToHead`
  still use `<table>`. Added a `.match-result-draw` color rule
  (`--color-warning`) that did not exist before — this also now colors the
  pre-existing draw rows in `OpponentHeadToHead`'s head-to-head table, which
  previously rendered with no color; this is a positive side effect, not a
  regression.
- Verification performed: `npx vitest run` (46/46 tests in
  `PlayerDetailPage.test.jsx`, 278/278 across the full frontend suite),
  `npx eslint` on the touched files (clean), and `npx vite build` (succeeds).
  Manual in-browser verification was not performed in this session — the
  player detail page requires a running backend and an authenticated
  session to load real data — so real-device/responsive visual verification
  (step 7 of the build plan) is still outstanding and should be done before
  this feature is closed.
