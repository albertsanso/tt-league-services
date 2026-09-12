# UI Mockup: Player details — Matches tab redesign

Redesign of the Matches tab on the Player detail page
(`PlayerDetailPage.jsx`, `MatchHistoryPanel`, currently a single dense
`history-table` with 11 columns — Date, Source, Season, Competition, Round,
Group, Phase, Opponent, Result, Score, Opponent team — plus nested per-game
lists inside the Opponent/Result cells). Tracked as
[FEAT-00059](../sdd/FEAT-00059-DETAILS.md).

All data currently shown is kept; only its grouping and layout change.

## Problem with the current layout

- 11 flat columns force horizontal scrolling / `data-label` stacking on
  mobile, and bury the two things a user scans for first — **result** and
  **opponent** — among season/round/group/phase metadata.
- Per-game opponent/result breakdown is rendered as nested lists *inside*
  table cells, which reflows awkwardly at narrow widths and has no
  W/D/L color affordance at the match level, only per game.
- No visual hierarchy: a 5-game doubles+singles match and a single-game
  tie read identically until you read every cell.

## Proposed layout: match cards instead of a table row

One card per match, newest first (same sort as today), inside the existing
`.table-wrap` scroll container. Cards replace `<table>`/`<tr>`; the games
breakdown becomes a `<details>` disclosure inside the card instead of nested
list markup in two separate cells.

### Card — collapsed (default) state

| Element              | Source field(s)                              | Notes |
|----------------------|-----------------------------------------------|-------|
| Result badge         | `item.result` (per match, derived from games) | Colored chip `W`/`D`/`L`, leftmost, matches existing `match-result-*` colors |
| Opponent team name   | `opponentTeamName`                             | Primary heading of the card |
| Score                | `matchScoreResult` / `scoreLabel`              | Large, next to opponent name (`5 – 3`) |
| Date                 | `item.dateTime` (localized) or "no data"       | Top-right, secondary text |
| Competition line     | `item.competition` · `item.round`              | Caption line under the heading |
| Context chips        | `item.season`, `item.source`, `item.groupNumber`, `item.phase` | Small muted chips, wrap on narrow screens, only rendered when present |
| Expand toggle        | —                                               | `▾ N games` — opens the per-game breakdown; `N` = `item.games.length` |

### Card — expanded (per-game breakdown)

| Element        | Source field(s)                       | Notes |
|-----------------|-----------------------------------------|-------|
| Game row        | one per `item.games[]`                  | Replaces the nested `role="list"` spans in the old Opponent/Result cells |
| Game type       | `game.type` (singles/doubles)            | Small label, e.g. `Singles` / `Doubles` |
| Opponent(s)     | `game.opponents[].name`                  | Joined names for doubles, same join logic as `matchOpponentRows` |
| Game result     | `game.result` / sets                     | `match-result-{result}` colored chip, or the set score |

### Empty / pagination (unchanged behavior, restyled)

- Empty state keeps `t('detail.competitionEmpty')`, centered under the
  filters, no card list rendered.
- Pagination (`MATCHES_PER_PAGE = 10`) stays a Previous/Next control with
  page indicator, only shown when `pageCount > 1` — visually restyled to sit
  under the card list instead of a table footer, no behavior change.

## Visual Layout (ASCII)

```
+----------------------------------------------------------------------------+
| Matches                                                                     |
|------------------------------------------------------------------------------|
| [W] CT SANT CUGAT B              5 - 3                    12 Mar 2026      |
|     Primera Catalana - Jornada 12                                          |
|     [2025/26] [RFETM] [Group 3]                          v 5 games         |
|------------------------------------------------------------------------------|
| [D] CT RUBI A                    4 - 4                     5 Mar 2026      |
|     Primera Catalana - Jornada 11                                          |
|     [2025/26] [RFETM] [Group 3]                          v 5 games         |
|------------------------------------------------------------------------------|
| [L] CT TERRASSA C                 2 - 6                   26 Feb 2026      |
|     Primera Catalana - Jornada 10                                          |
|     [2025/26] [RFETM] [Group 3]                          ^ 5 games         |
|   +--------------------------------------------------------------------+   |
|   | Singles   A. Ferrer            [L] 1-3                             |   |
|   | Singles   M. Roig              [W] 3-1                             |   |
|   | Singles   J. Vila               [L] 0-3                             |   |
|   | Doubles   A. Ferrer / M. Roig   [L] 2-3                             |   |
|   | Doubles   J. Vila / P. Serra     [L] 1-3                             |   |
|   +--------------------------------------------------------------------+   |
|------------------------------------------------------------------------------|
|                        [ < Previous ]  Page 2 of 5  [ Next > ]             |
+----------------------------------------------------------------------------+
```

## Component Legend

| Symbol / Notation        | Meaning |
|---------------------------|---------|
| `[W]` / `[D]` / `[L]`      | Result badge/chip, colored via existing `match-result-win/draw/loss` classes |
| `[2025/26]` `[RFETM]` `[Group 3]` | Muted context chips for season/source/group (phase chip omitted when not applicable) |
| `v 5 games` / `^ 5 games`  | Collapsed/expanded disclosure toggle for the per-game breakdown |
| `+---...---+`              | Per-game breakdown panel, revealed on expand |

## Notes

- Sample teams, dates, and scores are illustrative, not real data.
- Filters above the tabs (season slider, source radio buttons, competition
  select) are unchanged; only the Matches tab's own results area is
  redesigned.
- Cards stack full-width on mobile with no column-collapse CSS needed,
  since there is no table to reflow — this removes the `data-label`
  responsive hack the current `history-table` relies on.
- Sorting (newest-first, `compareMatches`) and pagination size
  (`MATCHES_PER_PAGE = 10`) are unchanged; only presentation changes.
- Exact spacing, tokens, and disclosure default (collapsed vs. expanded) are
  specified in [FEAT-00059-DETAILS.md](../sdd/FEAT-00059-DETAILS.md); this
  file documents layout and content only.
