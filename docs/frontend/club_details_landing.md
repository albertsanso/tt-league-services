# UI Mockup: Club Details Landing (Summary + Stats)

Companion spec to [`club-detail-view-mockup-spec.md`](./club-detail-view-mockup-spec.md).
Adds two new tabs — **Summary** (new default) and **Stats** — ahead of the
existing Players and Matches tabs. Tracked as
[FEAT-00054](../sdd/FEAT-00054-DETAILS.md); Players/Matches are unchanged.

## Page Header

Unchanged from the existing club detail page.

| Element        | Type              | Details                                   |
|-----------------|-------------------|--------------------------------------------|
| Section label   | Text (small)      | `CLUB`                                     |
| Club name       | Heading (H1)      | `CT SANT CUGAT`                            |
| Data source     | Text (caption)    | `FCTM · 4 seasons · 38 players`            |
| Aliases         | Chips             | `Sant Cugat T.T.`, `CET Sant Cugat`        |
| Edit button     | Button (outline)  | `Edit` — top-right corner                  |

---

## Navigation & Filters

### Tab Toggle

| Tab      | Icon | State                 |
|----------|------|------------------------|
| Summary  | ▦    | Active (new default)  |
| Stats    | 📈   | Inactive (new)         |
| Players  | 👥   | Inactive (unchanged)   |
| Matches  | ⚔    | Inactive (unchanged)   |

### Dropdown Selectors (Stats tab only)

| Selector | Purpose                                             |
|----------|------------------------------------------------------|
| Season   | `2024/25` / `2023/24` / `All seasons`                |

---

## Section: Summary tab

### Stat tiles

| Tile                       | Value | Sub-label            |
|-----------------------------|-------|------------------------|
| Players (current season)    | 24    | 38 all-time            |
| Matches played               | 312   | across 4 seasons       |
| Win rate                     | 61%   | 190W · 14D · 108L      |
| Competitions                 | 6     | 2 divisions this season|

### Recent matches card

| Result | Match                                   | Meta                                  | Score |
|--------|-------------------------------------------|-----------------------------------------|-------|
| W      | CT Sant Cugat A vs CT Rubí B               | Primera Catalana · Jornada 12 · 8 Mar   | 5–3   |
| L      | CT Terrassa vs CT Sant Cugat A             | Primera Catalana · Jornada 11 · 1 Mar   | 5–2   |
| D      | CT Sant Cugat B vs FC Martorell            | Segona Catalana · Jornada 10 · 22 Feb   | 4–4   |
| W      | CT Sant Cugat A vs Penya Esplugues         | Primera Catalana · Jornada 10 · 15 Feb  | 5–1   |

`See all →` switches to the **Matches** tab, preserving filters.

### Top players card

| Player      | Competition       | Stat  |
|-------------|--------------------|-------|
| Marc Roig   | Primera Catalana   | 18–3  |
| Laia Puig   | Primera Catalana   | 15–5  |
| Jordi Vila  | Segona Catalana    | 14–6  |
| Anna Soler  | Divisió Honor      | 12–7  |

`See all →` switches to the **Players** tab. *(Per-player record is not yet
exposed by the club API — see Open Questions in FEAT-00054-DETAILS.md.)*

---

## Section: Stats tab

### Record by competition (season: 2024/25)

| Competition          | Source | W  | D | L | Win rate |
|-----------------------|--------|----|---|---|----------|
| Divisió Honor          | FCTM   | 9  | 1 | 2 | 75%      |
| Primera Catalana       | FCTM   | 11 | 0 | 3 | 79%      |
| Segona Catalana        | FCTM   | 6  | 2 | 6 | 43%      |

### Win rate by season (trend)

| Season   | Win rate |
|----------|----------|
| 2021/22  | 52%      |
| 2022/23  | 56%      |
| 2023/24  | 64%      |
| 2024/25  | 67%      |

---

## Visual Layout (ASCII) — Summary tab

```
+--------------------------------------------------------------------------+
| CLUB                                                        [ Edit ]     |
| # CT SANT CUGAT                                                          |
| FCTM · 4 seasons · 38 players                                            |
| (Sant Cugat T.T.) (CET Sant Cugat)                                       |
|                                                                            |
| [▦ Summary] [📈 Stats] [👥 Players] [⚔ Matches]                          |
|--------------------------------------------------------------------------|
| +------------+ +------------+ +------------+ +------------+             |
| | Players    | | Matches    | | Win rate   | | Competitions|            |
| |    24      | |    312     | |    61%     | |     6      |             |
| | 38 all-time| | 4 seasons  | |190W 14D108L| |2 divisions |             |
| +------------+ +------------+ +------------+ +------------+             |
|                                                                            |
| +---------------------------------------+ +----------------------------+ |
| | Recent matches           See all -->  | | Top players     See all -->| |
| | [========61%========][3%][===35%===]  | | Marc Roig   ...     18-3  | |
| | W 190   D 14   L 108                   | | Laia Puig   ...     15-5  | |
| |-----------------------------------------| | Jordi Vila  ...     14-6  | |
| | W  CT Sant Cugat A vs CT Rubi B   5-3   | | Anna Soler  ...     12-7  | |
| |    Primera Catalana - J12 - 8 Mar       | |                            | |
| | L  CT Terrassa vs CT Sant Cugat A  5-2  | |                            | |
| |    Primera Catalana - J11 - 1 Mar       | |                            | |
| | D  CT Sant Cugat B vs FC Martorell 4-4  | |                            | |
| |    Segona Catalana - J10 - 22 Feb       | |                            | |
| | W  CT Sant Cugat A vs P.Esplugues  5-1  | |                            | |
| |    Primera Catalana - J10 - 15 Feb      | |                            | |
| +---------------------------------------+ +----------------------------+ |
+--------------------------------------------------------------------------+
```

## Visual Layout (ASCII) — Stats tab

```
+--------------------------------------------------------------------------+
| CLUB                                                        [ Edit ]     |
| # CT SANT CUGAT                                                          |
| FCTM · 4 seasons · 38 players                                            |
|                                                                            |
| [▦ Summary] [📈 Stats] [👥 Players] [⚔ Matches]                          |
|--------------------------------------------------------------------------|
| Record by competition                    [2024/25] [2023/24] [All]      |
|                                                                            |
| Divisio Honor      FCTM   9W 1D 2L  [==========75%======][8%][17%] 75%  |
| Primera Catalana   FCTM  11W 0D 3L  [============79%========][21%] 79%  |
| Segona Catalana    FCTM   6W 2D 6L  [======43%======][14%][===43%] 43%  |
|                                                                            |
| +------------------------------------------------------------------+    |
| | Win rate by season                                                |    |
| |                                                                    |    |
| | 100% |                                                            |    |
| |      |                                                    o 67%   |    |
| |  50% | - - - - - - - - - - - - - - - - - - - -o 64% - - - - - - - |    |
| |      |                              o 56%                        |    |
| |      |                o 52%                                      |    |
| |   0% +----------------------------------------------------------|    |
| |        2021/22       2022/23       2023/24       2024/25         |    |
| +------------------------------------------------------------------+    |
+--------------------------------------------------------------------------+
```

---

## Component Legend

| Symbol / Notation      | Meaning                                          |
|--------------------------|---------------------------------------------------|
| `[▦ ...] [📈 ...] [👥 ...] [⚔ ...]` | Tab toggle group (Summary / Stats / Players / Matches) |
| `[ Edit ]`                | Outlined action button                            |
| `(...)`                   | Alias chip                                        |
| `+---...---+`             | Card / tile container (rounded, light border)     |
| `[====...====]`          | Proportional W/D/L bar segment                    |
| `See all -->`             | Link that switches to the target tab              |
| `W` / `D` / `L`           | Wins / Draws / Losses                             |
| `o NN%`                   | Data point on the win-rate trend line             |

## Notes

- Sample club, players, and scores are illustrative, not real data.
- Colors, tokens, and component classes are specified in
  [FEAT-00054-DETAILS.md](../sdd/FEAT-00054-DETAILS.md); this file documents
  layout and content only.
- Players and Matches tab layouts are unchanged — see
  [`club-detail-view-mockup-spec.md`](./club-detail-view-mockup-spec.md).
