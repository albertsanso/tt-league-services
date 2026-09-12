# Opponent Analysis — Metrics Expansion Proposal

Builds on the unified-list redesign in
[oponent-analysis-redesign.md](./oponent-analysis-redesign.md) ([FEAT-00060](../sdd/FEATURES.md)).
That redesign fixed the *navigation* problem (three tables → one filterable
list). This proposal fixes the *depth* problem: today, beyond win/loss/streak,
there is nothing that says *how* a rivalry plays out — is it close? does the
player struggle more away from home? is doubles the weak spot against this
opponent? All of that already exists in the data flowing through
`PlayerDetailPage.jsx`; it's just never surfaced.

## 1. What data is already available

Every opponent row is built from `match`/`game` records already loaded for
the page (`PlayerDetailPage.jsx:296-337`). Per game/match entry we already
have, with no backend change:

| Field | Source | Already used for |
|---|---|---|
| `entry.margin` (`playerSets - opponentSets`) | `addOpponent`, [PlayerDetailPage.jsx:543](../../tt-data-league-frontend/src/pages/PlayerDetailPage.jsx#L543) | `averageMargin` is computed but **currently unused** in the UI |
| `game.type` (`INDIVIDUAL` / `DOUBLES`) | [PlayerDetailPage.jsx:619-620](../../tt-data-league-frontend/src/pages/PlayerDetailPage.jsx#L619) | Singles/doubles split elsewhere on the page, not per-opponent |
| `match.playerTeam` vs `match.homeTeam`/`awayTeam` | `gameSetsForPlayer`, [PlayerDetailPage.jsx:572-573](../../tt-data-league-frontend/src/pages/PlayerDetailPage.jsx#L572) | Home/away is resolved for scores, never surfaced as a stat |
| `match.competition` / `match.season` | carried on every history entry | Shown as plain text in the head-to-head table only |
| `match.dateTime` | `compareHistoryDesc`, `currentStreak` | Only used for ordering and the *current* streak |

Nothing below requires a new API field — every metric is a client-side
aggregation over data the opponent panel already receives.

## 2. Proposed metrics catalog

Ranked by implementation cost (all low — pure aggregation) and by how much
they change what a player can learn about a rivalry:

1. **Closeness score** — reuses the already-computed `averageMargin`.
   Bucket it into a label: *Decisive* (|avg margin| ≥ 2), *Competitive*
   (1 ≤ |avg margin| < 2), *Nail-biter* (< 1). Tells a player at a glance
   whether a matchup is usually a blowout or a coin flip.
2. **Singles vs. doubles split** — win % computed separately for
   `game.type === 'INDIVIDUAL'` vs `'DOUBLES'` entries against this opponent.
   Surfaces cases like "strong in singles, struggles in doubles against this
   pair."
3. **Home / away split** — win % when `match.playerTeam === match.homeTeam`
   vs away, against this specific opponent. Useful for identifying
   venue-sensitive rivalries.
4. **Longest streaks (not just current)** — parallel to the existing career
   `longestWinStreak` (`CareerSummary`, [PlayerDetailPage.jsx:225](../../tt-data-league-frontend/src/pages/PlayerDetailPage.jsx#L225)),
   compute the longest win streak *and* longest loss streak specific to this
   opponent, not just the current one.
5. **Rivalry frequency & recency** — matches-per-season average and "last
   played X days/months ago," distinguishing a frequent league rival from a
   one-off cup encounter.
6. **Competition breakdown** — win % split by `match.competition` (e.g.
   "3-1 in Lliga Catalana, 0-2 in Copa Federació"), for opponents faced across
   more than one competition.
7. **Result-quality trend** — reuse the same trend logic already shipped on
   the Match Summary page (`MatchSummaryPage.jsx`, `trend.improved` /
   `declining` / `stable`, comparing win % over the most recent N matches vs.
   the ones before). Applied per-opponent, it answers "am I getting better
   or worse against this person specifically?"

Explicitly **not proposed**: anything needing player ranking/ELO, shot-level
or set-by-set sequencing (e.g. comeback rate), or cross-player peer
comparisons — none of that data reaches `PlayerDetailPage.jsx` today, so it
would require backend work and is out of scope for a presentation-layer
expansion.

## 3. UX proposal

Cramming 6 more numbers into the existing row would defeat the point of the
FEAT-00060 redesign (fewer columns, more scannable). Instead:

- The **row stays exactly as redesigned** — name, category badge, W/D/L,
  win %, form, streak. No new columns.
- The **head-to-head expand panel becomes an "Opponent insights" panel**:
  still triggered by the same expand button, still shows the same per-match
  table below, but now opens with a row of compact metric tiles above it.
- A **new sort option**, "Closeness," is added to the existing sort dropdown
  (Default / Win % / Matches / Last played / **Closeness**) — sorts by
  `|averageMargin|` ascending, surfacing the tightest rivalries first.

### Expanded row — desktop wireframe

```
┌───────────────────────────────────────────────────────────────────────┐
│ Opponent        Cat.   Played  W   D   L   Win%   Form        Streak  │▾│
├───────────────────────────────────────────────────────────────────────┤
│ Laura Soler     ●Hard  9      4   0   5   44.4%  ●○●○●   L1          │▾│
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │  Opponent insights · Laura Soler                                 │ │
│  │                                                                   │ │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐    │ │
│  │  │ CLOSENESS  │ │ SINGLES    │ │ DOUBLES    │ │ HOME/AWAY  │    │ │
│  │  │ Nail-biter │ │ 60% (3/5)  │ │ 25% (1/4)  │ │ H 50%·A 40%│    │ │
│  │  │ avg ±0.8   │ │            │ │            │ │            │    │ │
│  │  └────────────┘ └────────────┘ └────────────┘ └────────────┘    │ │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────────────────────┐   │ │
│  │  │ STREAKS    │ │ LAST MET   │ │ TREND                       │   │ │
│  │  │ W3 / L3    │ │ 12 days ago│ │ ↘ Declining (67%→33%)       │   │ │
│  │  │ (best/worst)│ │ 6 matches/yr│ │  vs previous matches        │   │ │
│  │  └────────────┘ └────────────┘ └────────────────────────────┘   │ │
│  │                                                                   │ │
│  │  Head-to-head                                                    │ │
│  │  Date        Competition        Result     Score                │ │
│  │  2026-03-02   Lliga Catalana     Loss        1 — 3               │ │
│  │  2025-11-14   Copa Federació     Win         3 — 1               │ │
│  │  ...                                                              │ │
│  └─────────────────────────────────────────────────────────────────┘ │
├───────────────────────────────────────────────────────────────────────┤
│ Pere Vidal      ●Prob  6      1   0   5   16.7%  ○○○●○   L3          │▸│
└───────────────────────────────────────────────────────────────────────┘
```

### Expanded row — mobile wireframe (<640px)

```
┌─────────────────────────────┐
│ Laura Soler          ●Hard   │
│ 9 played · 4W 0D 5L          │
│ Win % 44.4%   Streak L1      │
│ Form: ●○●○●                  │
│ [ Hide head-to-head ▲ ]      │
│ ┌───────────────────────────┐│
│ │ Closeness   Nail-biter    ││
│ │ Singles     60% (3/5)     ││
│ │ Doubles     25% (1/4)     ││
│ │ Home/Away   50% / 40%     ││
│ │ Streaks     W3 best·L3 worst││
│ │ Last met    12 days ago   ││
│ │ Trend       ↘ Declining   ││
│ └───────────────────────────┘│
│ Head-to-head                 │
│ 2026-03-02 Lliga  L  1—3      │
│ 2025-11-14 Copa   W  3—1      │
│ ...                           │
└─────────────────────────────┘
```

## 4. Interaction & scope notes

- Tiles only render when the underlying metric is meaningful: a
  singles/doubles tile with 0 games of one type is omitted rather than
  shown as "—" or "0%"; competition breakdown only appears when the
  opponent was faced across more than one competition; trend only appears
  once there are enough matches to compare two windows (same threshold the
  Match Summary page already uses).
- This is additive to [FEAT-00060](../sdd/FEATURES.md)'s unified list —
  it does not reopen the row/column layout, category chips, or search/sort
  toolbar already shipped there.
- All computation is pure aggregation over `sortedHistory`/`history` already
  built in `buildOpponentRow`/`addOpponent` — no new fields requested from
  the API, no schema change.
