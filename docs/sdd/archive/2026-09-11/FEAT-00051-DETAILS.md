# Build Plan
Scope: `tt-data-league-frontend`, CSS + markup only, limited to the Matches
tab (`MatchHistoryPanel`) and Opponent Analysis tab (`OpponentTable`,
`OpponentHeadToHead`) on `src/pages/PlayerDetailPage.jsx`. No API changes.

1. **Diagnose the current behaviour**: both tabs render `.history-table`
   inside `.table-wrap { overflow-x: auto }`, so today a narrow viewport
   just gets a horizontally scrollable wide table instead of an adapted
   layout — the actual complaint. The Matches table has 10 columns; the
   Opponent Analysis table has 9-10 columns (6 base + category + recent
   form + streak + details button); both are unreadable on a phone even
   with scroll.

2. **Add a `data-label` attribute to every `<td>`** in the affected
   tables (`MatchHistoryPanel`'s table, `OpponentTable`'s table, and
   `OpponentHeadToHead`'s table), set to the same translated header text
   already used in the corresponding `<th>`. This is what a CSS
   `content: attr(data-label)` rule needs to relabel each cell once the
   table collapses to a stacked layout.

3. **Add a mobile breakpoint (`max-width: 640px`, consistent with the
   existing 520px/600px/767px breakpoints already in `app.css`) that
   turns these three tables into stacked cards**:
   - `thead` visually hidden (`.visually-hidden` pattern already used
     elsewhere) rather than `display: none`, so table semantics/captions
     stay available to screen readers.
   - `tr` becomes a block-level card (`display: block`, border, spacing,
     background) with `td` as `display: flex; justify-content:
     space-between; align-items: flex-start` rows inside it, no cell
     borders, and `td::before { content: attr(data-label); font-weight:
     600; color: var(--text-secondary) }` for the inline label.
   - Cells whose content is itself a list (`.match-game-list`,
     `.opponent-form`) keep their own layout but drop the `min-width`
     that forces horizontal scrolling on narrow viewports.
   - The opponent table's colspan-ed head-to-head detail row/column
     button keep working unchanged (they already span full width).

4. **Review other width offenders on these two tabs** while in there:
   - `.match-game-row` / `.match-game-list` (`min-width: 10rem`) — drop
     the min-width under the mobile breakpoint so a single opponent/result
     chip doesn't force extra width.
   - `.opponent-sort`, `.opponent-search` controls — confirm they already
     stretch to `100%`/`max-width` sanely on narrow screens (they do:
     `max-width: 18rem`/`26rem` with block layout); no change expected,
     verify only.
   - `.player-tabs`/`.club-tabs` — confirm the 3 player tabs
     (Statistics/Matches/Opponents) and the 2 opponent sub-tabs wrap or
     stay usable at 375px width; add `flex-wrap: wrap` to `.club-tabs` if
     they overflow (currently only `.opponent-tabs` has it).

5. **Tests** (`src/pages/PlayerDetailPage.test.jsx`): add `data-label`
   assertions are hard to test meaningfully via jsdom (no real viewport
   media-query evaluation), so keep automated coverage to verifying the
   `data-label` attributes are present with the expected translated text
   on a representative row in both tables, rather than testing the
   media-query-driven visual layout itself.

6. **Manual verification**: use the browser's responsive/mobile viewport
   emulation (e.g. 375×812) on the Matches and Opponent Analysis
   (categorization + search) tabs and confirm rows read as stacked
   label/value cards with no horizontal scroll needed for the primary
   content, then confirm the desktop table layout is unchanged above the
   breakpoint.

# Implementation Guidelines

- Keep the change additive/CSS-driven: do not change the underlying data
  model, column set, or desktop appearance above the breakpoint.
- Reuse the existing `.visually-hidden` declarations for hiding the
  `thead` on mobile rather than `display: none`, to preserve table
  semantics for assistive tech.
- Keep `data-label` values in sync with the visible `<th>` text (derive
  both from the same `t(...)` call per column) so mobile card labels
  never drift from the desktop header text.
- Scope new CSS rules to the specific table wrapper classes involved
  (`.match-history .history-table`, `.opponent-table .history-table`,
  `.opponent-history-detail .history-table`) rather than every
  `.history-table` in the app, since other pages' tables are out of
  scope for this feature.

# Notes
- Filed after the user reported the Player details page — specifically
  the Matches and Opponent Analysis tabs — being unusable on smartphone
  widths despite the existing generic `.table-wrap { overflow-x: auto }`
  fallback.
- Implemented: every `<td>` in `MatchHistoryPanel`'s table, `OpponentTable`,
  and `OpponentHeadToHead` (in `src/pages/PlayerDetailPage.jsx`) now
  carries a `data-label` matching its column header text. A new
  `@media (max-width: 640px)` block in `src/app.css` turns those three
  tables into stacked label/value cards: `thead` is visually hidden
  (not `display: none`), each row becomes a bordered card, and each cell
  becomes a flex row with `td::before { content: attr(data-label) }` for
  the inline label. The opponent table's expand/collapse head-to-head
  detail keeps working, with the trigger row and its detail panel
  visually joined into one card (matching the connected look fixed
  earlier for FEAT-00050) and `min-width` dropped from `.match-game-list`
  so per-game chips don't force width. `.club-tabs` also gained
  `flex-wrap: wrap` so the player/opponent sub-tabs don't overflow at
  narrow widths.
- Added 2 tests asserting the `data-label` attributes are present and
  correctly translated on a representative Matches row and Opponent
  Analysis row; full suite (`npx vitest run`, 32 files / 204 tests) and
  `eslint` on the touched files pass.
- Not verified in a real browser viewport (no runnable dev/backend stack
  in this checkout) — verification relied on the automated tests plus
  manual reasoning about the CSS. A manual mobile-viewport pass on the
  Matches tab and both Opponent Analysis views (categorization + search,
  including the expanded head-to-head detail) is recommended before
  closing this feature.
