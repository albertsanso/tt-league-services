# UI Mockup: Club Details — Matches tab summary (proposal)

Proposes adding a **Matches summary** strip above the existing
source → season → competition → team drill-down hierarchy in the Matches tab.
Today that tab has no summary at all — just the expandable hierarchy list.
This strip is additive: the hierarchy, its toggles, and per-match cards are
unchanged.

Distinct from the club-wide `ClubSummaryPanel` ("Summary" tab,
[FEAT-00054](../../sdd/FEAT-00054-DETAILS.md)), which already shows an
overall record bar and a 4-item recent-matches list — this strip lives
inside the Matches tab itself, reflects the *currently applied*
Source/Season/Competition filters (same scope the hierarchy below already
uses), and adds match-centric stats that the state-of-the-art research below
calls out as commonly expected (form guide, streaks, close matches, pending
results) rather than duplicating the Summary tab's club-wide overview.

See [FEAT-00063-DETAILS.md](../../sdd/FEAT-00063-DETAILS.md) for the research
behind this proposal and the new requirements it produced.

## Section: Matches summary strip

### Stat tiles

| Tile            | Value | Sub-label                     |
|-------------------|-------|----------------------------------|
| Matches            | 78    | in current filters             |
| Win rate           | 64%   | 50W · 4D · 24L                 |
| Home / Away split  | 71% / 55% | win rate home vs away      |
| Pending            | 3     | results not yet entered        |

### Form guide (last 5)

Compact chip row, most recent last — the same `is-win`/`is-draw`/`is-loss`
result vocabulary already used by `MatchRow`/`club-match-result`.

```
Form:  L  W  W  D  W   (current streak: 1W)
```

### Notable matches card

Small mini-list surfacing matches worth a second look, instead of only the
plain chronological recent-matches list the Summary tab already has:

| Match                                | Note              | Score |
|----------------------------------------|---------------------|-------|
| CT Sant Cugat B vs FC Martorell        | Closest result      | 4–4   |
| CT Terrassa vs CT Sant Cugat A         | Biggest defeat      | 5–1   |
| CT Sant Cugat A vs Penya Esplugues     | Biggest win         | 5–0   |

---

## Visual Layout (ASCII)

```
+--------------------------------------------------------------------------+
| CLUB                                                        [ Edit ]     |
| # CT SANT CUGAT                                                          |
| FCTM · 4 seasons · 38 players                                            |
|                                                                            |
| [▦ Summary] [📈 Stats] [👥 Players] [⚔ Matches]                          |
|--------------------------------------------------------------------------|
| [ Source v ] [ Season v ] [ Competition v ]                              |
|                                                                            |
| Matches                                                                   |
|                                                                            |
| +------------+ +------------+ +------------------+ +------------+       |
| | Matches    | | Win rate   | | Home / Away      | | Pending    |       |
| |    78      | |    64%     | |   71% / 55%       | |     3      |       |
| | filters ▾  | |50W 4D 24L  | | win rate           | | no result  |       |
| +------------+ +------------+ +------------------+ +------------+       |
|                                                                            |
| Form: L W W D W        (current streak: 1W)                              |
|                                                                            |
| +------------------------------------------------------------------+    |
| | Notable matches                                                   |    |
| | CT Sant Cugat B vs FC Martorell      Closest result         4-4  |    |
| | CT Terrassa vs CT Sant Cugat A       Biggest defeat         5-1  |    |
| | CT Sant Cugat A vs Penya Esplugues   Biggest win            5-0  |    |
| +------------------------------------------------------------------+    |
|                                                                            |
| > FCTM (78 matches available)                                            |
|   > 2024/25 (22 matches available)                                       |
|     > Primera Catalana (22 matches available)                            |
|       [ View competition ]                                               |
|       > CT Sant Cugat A (12 matches available)                           |
|         W  CT Sant Cugat A vs CT Rubi B          5-3                     |
|         L  CT Terrassa vs CT Sant Cugat A        5-2                     |
|         ...                                                              |
+--------------------------------------------------------------------------+
```

---

## Component Legend

| Symbol / Notation      | Meaning                                          |
|--------------------------|---------------------------------------------------|
| `+---...---+` (stat tile) | `stat-tile` card, same component as the Summary tab |
| `Form: L W W D W`         | Chronological chip row, oldest→newest, reusing `club-match-result is-*` colors |
| `(current streak: 1W)`    | Longest active same-result run at the end of the form row |
| `> ... (N matches available)` | Existing collapsible hierarchy row (unchanged) |
| `[ View competition ]`    | Existing link to the competition detail page (unchanged) |

## Notes

- Sample club, matches, and scores are illustrative, not real data.
- The strip scopes to whatever Source/Season/Competition filters are already
  active — same data the hierarchy below renders — so no new filter state is
  introduced.
- "Pending" reuses the existing `pendingResult` concept
  (`match.homeGamesWon == null || match.awayGamesWon == null`) already
  handled by `MatchRow`/`CompetitionBody`.
- "Notable matches" (closest / biggest win / biggest defeat) needs the game
  score margin per match, which the match list already carries
  (`homeGamesWon`/`awayGamesWon`); no new API field required.
- Colors, tokens, and component classes should reuse `stat-tile`, the
  `club-match-result is-win/is-draw/is-loss` classes, and `mini-list` markup
  already shipped elsewhere on this page, per
  [FEAT-00063-DETAILS.md](../../sdd/FEAT-00063-DETAILS.md).

### Open questions

- Home/Away win-rate split assumes the match record can identify whether the
  club's team was the home or away side — confirm this is already derivable
  from `homeTeam`/`awayTeam` plus the club's own team names, not a new field.
- "Current streak" needs matches ordered by date across the *whole* filtered
  set, not just the last page fetched — confirm `useClubMatches` already
  returns them chronologically or a client-side sort is enough at expected
  data volumes.
