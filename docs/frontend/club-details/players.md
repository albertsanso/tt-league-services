# UI Mockup: Club Details — Players tab summary (proposal)

Proposes expanding the Players tab's current "shown vs total" line (shipped in
[FEAT-00063](../../sdd/FEAT-00063-DETAILS.md)) into a small **Players summary**
strip above the roster search box. This is additive: the search input, the
shown/total line, and the roster list below are unchanged.

Distinct from the club-wide `ClubSummaryPanel` ("Summary" tab,
[FEAT-00054](../../sdd/FEAT-00054-DETAILS.md)) — that panel is season-scoped
and covers the whole club; this strip lives inside the Players tab itself,
follows the tab's own season-independent roster (`rosterPlayers`), and is
about "who is on this roster and how is it composed," not season performance.

See [FEAT-00063-DETAILS.md](../../sdd/FEAT-00063-DETAILS.md) for the research
behind this proposal (state-of-the-art player-stats presentation) and the new
requirements it produced.

## Section: Players summary strip

### Stat tiles

| Tile                  | Value | Sub-label                          |
|------------------------|-------|--------------------------------------|
| Players                | 38    | 4 federations                        |
| Competitions covered   | 6     | across all seasons on record         |
| Most represented       | Primera Catalana | 14 players                |
| Newest addition        | Laia Puig | joined 2024/25                   |

### Roster breakdown card (by competition)

Small horizontal bar list — one row per competition the roster currently
touches, independent of the page's Season filter (mirrors `rosterPlayers`'s
own season-independent scope). Capped at 5 rows by default (sorted by player
count descending); a `See all →` link reveals the rest for clubs with more
than 5 competitions on record.

| Competition        | Players | Share |
|----------------------|---------|-------|
| Primera Catalana      | 14      | 37%   |
| Divisió Honor          | 9       | 24%   |
| Segona Catalana        | 8       | 21%   |
| Copa Catalunya         | 7       | 18%   |
| Tercera Catalana       | 5       | 13%   |
| *(2 more, hidden until "See all")* | — | — |

### Most active card (career, all seasons)

Reuses the `mini-list`/`PlayerRow` pattern from `ClubSummaryPanel`, but keyed
by career match count instead of a single season, since the roster itself is
season-independent.

| Player      | Seasons on record | Matches (career) |
|-------------|--------------------|-------------------|
| Marc Roig   | 4                  | 96                |
| Laia Puig   | 3                  | 71                |
| Jordi Vila  | 4                  | 88                |
| Anna Soler  | 2                  | 40                |

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
| Players                                                                   |
|                                                                            |
| +------------+ +--------------+ +------------------+ +-----------------+ |
| | Players    | | Competitions | | Most represented | | Newest addition| |
| |    38      | |      6       | | Primera Catalana  | |   Laia Puig    | |
| | 4 federat. | | all seasons  | |    14 players     | |   2024/25      | |
| +------------+ +--------------+ +------------------+ +-----------------+ |
|                                                                            |
| +---------------------------------------+ +----------------------------+ |
| | Roster by competition                  | | Most active (career)      | |
| | Primera Catalana  [=====37%=====] 14   | | Marc Roig   4 seasons 96  | |
| | Divisio Honor     [==24%==]       9    | | Laia Puig   3 seasons 71  | |
| | Segona Catalana   [==21%==]       8    | | Jordi Vila  4 seasons 88  | |
| | Copa Catalunya    [=18%=]         7    | | Anna Soler  2 seasons 40  | |
| | Tercera Catalana  [=13%=]         5    | |                            | |
| | Veure-ho tot -->                       | |                            | |
| +---------------------------------------+ +----------------------------+ |
|                                                                            |
| [ Source v ] [ Season v ] [ Competition v ]                              |
|                                                                            |
| [ 🔎  Cerca jugadors...                                            ]     |
| 38 de 38 jugadors                                                        |
|                                                                            |
| +------------------------------------------------------------------+    |
| | Marc Roig                                                    -->  |    |
| +------------------------------------------------------------------+    |
| | Laia Puig                                                    -->  |    |
| +------------------------------------------------------------------+    |
| | ...                                                                |    |
| +------------------------------------------------------------------+    |
+--------------------------------------------------------------------------+
```

---

## Component Legend

| Symbol / Notation      | Meaning                                          |
|--------------------------|---------------------------------------------------|
| `+---...---+` (stat tile) | `stat-tile` card, same component as the Summary tab |
| `[====NN%====]`          | Proportional bar segment (roster-by-competition share) |
| `-->`                     | Link affordance to the player's detail page       |
| `Veure-ho tot -->`        | `See all` expander (`detail.seeAll`/`.link-button`, same pattern as the Summary tab); reveals every hidden competition row and then disappears |
| `[ ... v ]`               | Existing Source/Season/Competition dropdown (unchanged) |
| `[ 🔎 ... ]`              | Existing name search input (unchanged, FEAT-00063) |

## Notes

- Sample club, players, and figures are illustrative, not real data.
- "Newest addition" needs a join/registration date per player-season record;
  flagged as an open question below if the API doesn't already expose one.
- The strip sits **above** the existing search input, so search still narrows
  only the roster list beneath it — the summary tiles reflect the full roster,
  not the current search text.
- Colors, tokens, and component classes should reuse `stat-tile`,
  `record-bar`/generic proportional-bar CSS, and `mini-list`/`PlayerRow`
  markup already shipped for the Summary tab, per
  [FEAT-00063-DETAILS.md](../../sdd/FEAT-00063-DETAILS.md).

### Open questions

- Does the club/players API expose a per-player-season join/registration
  date? Needed for "Newest addition"; if not, drop that tile or approximate
  it from the earliest/latest season on record.
- "Most active (career)" needs match counts aggregated across every season
  record for a canonical player, not just the currently-filtered `season` —
  confirm the club matches endpoint can be queried unscoped by season without
  a heavy client-side fetch of every season's matches.
