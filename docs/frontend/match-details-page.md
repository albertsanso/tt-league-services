# UI Mockup: Match Summary landing page

New page reached by clicking a match result row/card in the Matches search
results (`MatchesSearchPage.jsx`, `/partits`). Today that row is a
non-interactive block with a separate "View acta" button that opens
`MatchActaDialog`; this spec adds a full landing page at `/partits/:matchId`
that the row itself links to, while keeping the existing "View acta" action
available from this new page. Tracked as
[FEAT-00057](../sdd/FEAT-00057-DETAILS.md).

## Entry point change (Matches search results)

| Element              | Before                                        | After                                                         |
|-----------------------|------------------------------------------------|-----------------------------------------------------------------|
| Result row            | Static block, no navigation                    | `<Link to="/partits/:matchId">` — whole row/card is clickable  |
| "View acta" button    | Opens `MatchActaDialog` from the search page   | Unchanged on the search page; also present on the summary page |

## Page Header

| Element          | Type              | Details                                                    |
|-------------------|-------------------|--------------------------------------------------------------|
| Back link         | Text link          | `← Back to search` — returns to `/partits` preserving filters |
| Section label     | Text (small)       | `MATCH`                                                       |
| Competition line  | Text (caption)     | `Primera Catalana · Jornada 12 · 8 Mar 2026`                  |
| Score heading     | Heading (H1)       | `CT SANT CUGAT A   5 – 3   CT RUBÍ B`                         |
| Venue/referee     | Text (caption)     | `Pavelló Municipal · Referee: J. Ferrer`                      |
| View acta button  | Button (outline)   | `View acta` — opens the existing `MatchActaDialog`            |

---

## Section: Team panels (side by side, Home | Away)

Repeated identically for the home and away team; each is its own card.

### Team form card

| Element           | Type            | Details                                              |
|--------------------|-----------------|---------------------------------------------------------|
| Team name          | Heading (H3)     | `CT SANT CUGAT A`                                        |
| Recent form strip  | Chips (last 5)   | `W W L W D` — most recent last, each chip colored by result |
| Recent win rate    | Text (stat)      | `Last 5: 3W · 1D · 1L (60%)`                             |
| Trend note         | Text (caption)   | `Improved vs previous 5 matches (40% → 60%)` or `Stable` |

### Lineup card

| Column       | Example                | Notes                                                        |
|---------------|--------------------------|-----------------------------------------------------------------|
| Letter        | `A`                      | Board letter, from `lineups[].letter`                            |
| Player        | `Marc Roig`              | Links to Player detail page when `canonicalPlayerId` exists       |
| Ranking       | `2465.1`                 | From `lineups[].ranking`                                          |
| Recent form   | `W W W L W (80%)`        | Player's last 5 matches, independent of this match/team           |

### Alignment stability card

| Element             | Type          | Details                                                             |
|-----------------------|---------------|--------------------------------------------------------------------|
| Badge                 | Chip           | `Regular lineup` / `New combination` / `Rarely used`                |
| Times fielded         | Text (stat)    | `This exact A-B-C lineup has played together 7 times this season`   |
| Historical win rate   | Text (stat)    | `5W · 1D · 1L when fielded together (71%)`                          |
| Comparison note       | Text (caption) | `Above the team's overall win rate (60%)` / `Below` / `In line with` |

`Alignment stability` is computed by grouping this team's past matches (same
source/season) by the exact set of players fielded (independent of board
letter/order) and reporting how often the current match's set recurs and its
aggregate result. A lineup used only once (this match) shows `New
combination` with no historical win rate.

---

## Section: Footer

| Element      | Type        | Details                                    |
|---------------|-------------|-----------------------------------------------|
| View acta     | Button      | Duplicate of header action, for long pages     |
| Back link     | Text link   | Duplicate of header back link                  |

---

## Visual Layout (ASCII)

```
+----------------------------------------------------------------------------+
| <- Back to search                                                          |
| MATCH                                                                      |
| Primera Catalana - Jornada 12 - 8 Mar 2026                                 |
|                                                                             |
| # CT SANT CUGAT A     5 - 3     CT RUBI B                [ View acta ]     |
| Pavello Municipal - Referee: J. Ferrer                                     |
|-----------------------------------------------------------------------------|
| +-------------------------------+   +-------------------------------+     |
| | CT SANT CUGAT A (home)        |   | CT RUBI B (away)               |     |
| | Form: W W L W D                |   | Form: L D L W L                |     |
| | Last 5: 3W 1D 1L (60%)         |   | Last 5: 1W 1D 3L (20%)         |     |
| | Improved vs previous 5 (40%->60%) | | Declining vs previous 5 (40%->20%)|  |
| |---------------------------------|   |---------------------------------|  |
| | Lineup                          |   | Lineup                          |  |
| |  A  Marc Roig     2465.1  W W W L W (80%) |  A  Pol Serra   2310.4  L W L L D (20%) |
| |  B  Laia Puig      1998.7  W L W W W (80%) |  B  Nuria Amat  2205.9  D L L W L (20%) |
| |  C  Jordi Vila     2293.1  L W W L W (60%) |  C  Bernat Riu  2140.2  L L D L L (0%)  |
| |---------------------------------|   |---------------------------------|  |
| | Alignment stability             |   | Alignment stability             |  |
| | [Regular lineup]                |   | [New combination]               |  |
| | Played together 7 times this    |   | First time this trio has been   |  |
| | season - 5W 1D 1L (71%)         |   | fielded together this season    |  |
| | Above team's overall rate (60%) |   | No historical win rate yet      |  |
| +-------------------------------+   +-------------------------------+     |
|                                                                             |
| [ View acta ]                                          <- Back to search  |
+----------------------------------------------------------------------------+
```

## Component Legend

| Symbol / Notation          | Meaning                                                   |
|------------------------------|--------------------------------------------------------------|
| `<- Back to search`           | Text link back to `/partits`, preserving prior filters        |
| `[ View acta ]`                | Outlined button, opens `MatchActaDialog` for this match id     |
| `Form: W W L W D`              | Last-5-matches result strip, oldest to most recent              |
| `W` / `D` / `L`                | Win / Draw / Loss                                               |
| `+---...---+`                  | Card / panel container (rounded, light border)                  |
| `[Regular lineup]` / `[New combination]` / `[Rarely used]` | Alignment-stability badge chip |

## Notes

- Sample teams, players, and stats are illustrative, not real data.
- Team panels stack vertically on narrow/mobile viewports (home above away),
  matching the responsive convention used by `ClubSummaryPanel`/
  `PlayerDetailPage`.
- Players in the lineup link to the Player detail page only when
  `canonicalPlayerId` is present, matching the pattern from FEAT-00053.
- "View acta" keeps opening the existing dialog (`MatchActaDialog.jsx`); this
  page does not replace or duplicate the acta's own layout
  (`acta-simplified-rfetm.md`).
- Colors, tokens, exact stat-computation rules, and API/data-availability
  constraints are specified in
  [FEAT-00057-DETAILS.md](../sdd/FEAT-00057-DETAILS.md); this file documents
  layout and content only.
