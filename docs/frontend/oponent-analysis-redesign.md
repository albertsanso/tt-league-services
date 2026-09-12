# Opponent Analysis Tab — UX Redesign Proposal

## 1. Current design (as implemented today)

Source: [`PlayerDetailPage.jsx:299-484`](../../tt-data-league-frontend/src/pages/PlayerDetailPage.jsx#L299)

The "Opponent Analysis" tab inside Player Details is a **tab-within-a-tab** layout:

- Two inner sub-tabs: **Categorization** (default) and **Search**.
- Categorization stacks three independent tables, one per category: *Favourable opponents*, *Difficult opponents*, *Problematic opponents*, each with its own "Show N more" (only 3 rows visible by default).
- Search shows a single flat table of all opponents with a text filter, including a Category column.
- A single sort `<select>` (Default / Win % / Matches played / Last played) applies to whichever view is active.
- Each opponent row: Name, Matches, Wins, Draws, Losses, Win %, (Category, search view only), Recent form (5 dots), Streak, "Show head-to-head" toggle that expands an inline per-match table (Date / Competition / Result / Score).

### Problems this creates

1. **Duplicated table markup/content** — Categorization and Search views show almost the same columns; a user switching tabs loses their sort/expand state and has to re-learn the same table twice.
2. **No at-a-glance summary** — you must open the tab and scroll three stacked tables to learn "how many opponents are Problematic," there's no count/overview.
3. **Category is buried** — in the primary (Categorization) view, category is implied only by section heading, not shown as a visual badge on the row; in Search view it's a plain text cell competing with 7 other columns.
4. **Heavy, dense tables** — 8-9 columns per row is hard to scan, especially on narrower viewports (mitigated only by `data-label` responsive stacking).
5. **Redundant "show more" per section** — three separate pagination controls instead of one consistent affordance.
6. **Sort control is disconnected from the data it affects** — sits above both sub-tabs, its scope (which table it sorts) isn't visually obvious.

## 2. Redesign goals

- Keep **all existing content and data** (nothing removed): matches, W/D/L, win %, category, recent form, streak, head-to-head history.
- Replace the two-sub-tab split with **one unified, filterable opponent list**.
- Make category an **always-visible, color-coded badge** instead of a hidden grouping mechanism.
- Add a **summary strip** (counts per category) that doubles as a filter — clicking "Problematic (4)" filters the list, removing the need for a separate Search tab.
- Keep search, but make it a plain text filter combined with the same category chips and sort control, all in one toolbar.
- Keep the inline expand-to-reveal head-to-head pattern (it already works well) but restyle it as a card-style detail rather than a nested table row.
- Improve scannability by moving to a **row/card hybrid**: compact table on wide viewports, stacked cards on narrow ones (progressive enhancement of what already exists via `data-label`).

## 3. Proposed layout — wireframe (desktop, ≥1024px)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Opponent Analysis                                                           │
│                                                                               │
│  ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐    │
│  │  ALL           │ │ ● FAVOURABLE  │ │ ● DIFFICULT   │ │ ● PROBLEMATIC │    │
│  │  24 opponents  │ │      12       │ │       7       │ │       5       │    │
│  │  [active]      │ │               │ │               │ │               │    │
│  └───────────────┘ └───────────────┘ └───────────────┘ └───────────────┘    │
│   ^ filter chips — click to filter the list below, "ALL" clears filter       │
│                                                                               │
│  ┌───────────────────────────────┐  ┌────────────────────┐                  │
│  │ 🔎 Search opponent name...     │  │ Sort: Win % ▾       │                  │
│  └───────────────────────────────┘  └────────────────────┘                  │
│                                                                               │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │ Opponent        Cat.   Played  W   D   L   Win%   Form        Streak  │▸│  │
│  ├───────────────────────────────────────────────────────────────────────┤  │
│  │ Marc Puig       🟢Fav   8      6   0   2   75.0%  ●●●○●   W2         │▸│  │
│  ├───────────────────────────────────────────────────────────────────────┤  │
│  │ Laura Soler     🟡Hard  5      2   0   3   40.0%  ○●○○●   L1         │▾│  │
│  │  ┌─────────────────────────────────────────────────────────────────┐ │  │
│  │  │  Head-to-head vs. Laura Soler                                    │ │  │
│  │  │  Date        Competition        Result     Score                 │ │  │
│  │  │  2026-03-02   Lliga Catalana     Loss        1 — 3                │ │  │
│  │  │  2025-11-14   Copa Federació     Win         3 — 1                │ │  │
│  │  │  ...                                                              │ │  │
│  │  └─────────────────────────────────────────────────────────────────┘ │  │
│  ├───────────────────────────────────────────────────────────────────────┤  │
│  │ Pere Vidal      🔴Prob  6      1   0   5   16.7%  ○○○●○   L3         │▸│  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                               │
│                        [ Show 12 more opponents ▾ ]                         │
└─────────────────────────────────────────────────────────────────────────────┘
```

Legend: 🟢 Favourable · 🟡 Difficult · 🔴 Problematic · ⚪ Uncategorized (few matches / balanced)

## 4. Proposed layout — wireframe (mobile / narrow, <640px)

```
┌───────────────────────────────┐
│ Opponent Analysis              │
│                                 │
│ [ALL 24] [🟢12] [🟡7] [🔴5]     │  <- horizontally scrollable chip row
│                                 │
│ ┌─────────────────────────────┐│
│ │ 🔎 Search opponent...        ││
│ └─────────────────────────────┘│
│ Sort: Win % ▾                  │
│                                 │
│ ┌─────────────────────────────┐│
│ │ Marc Puig            🟢 Fav  ││
│ │ 8 played · 6W 0D 2L          ││
│ │ Win % 75.0%   Streak W2      ││
│ │ Form: ●●●○●                  ││
│ │ [ Show head-to-head ▾ ]      ││
│ └─────────────────────────────┘│
│ ┌─────────────────────────────┐│
│ │ Laura Soler          🟡 Hard ││
│ │ 5 played · 2W 0D 3L          ││
│ │ Win % 40.0%   Streak L1      ││
│ │ Form: ○●○○●                  ││
│ │ [ Show head-to-head ▾ ]      ││
│ └─────────────────────────────┘│
│                                 │
│      [ Show more ▾ ]           │
└───────────────────────────────┘
```

Each card is the same row data as desktop, just reflowed — this is a natural evolution of the existing `data-label` responsive table stacking, not a new pattern.

## 5. Interaction notes

- **Filter chips replace the Categorization/Search sub-tabs.** "ALL" is the default (equivalent to today's Search-without-filter). Selecting a category chip filters the same list — no separate view, no state loss.
- **Search box always visible**, combines with the active chip (e.g. "Difficult" + "puig" narrows within that category).
- **Sort `<select>`** keeps its 4 existing options and now visibly sits inside the same toolbar as search/filter, so its scope is unambiguous (it always sorts "the list below").
- **One "Show N more" control** for the whole filtered/sorted list (default page size can stay 3, or be raised now that there's only one list instead of three).
- **Head-to-head expand** keeps the existing inline-row behaviour (data and columns unchanged: Date / Competition / Result / Score) but is visually framed as a nested detail card for readability.
- **Category badge** is now a small color-coded pill shown on every row, always — not just in the Search view — so the categorization signal survives filtering/sorting by any column.
- **Accessibility**: chips are implemented as a `role="tablist"`/`radiogroup`-style toggle button group (not real tabs, since there's no separate panel per category — only a filter), each announcing its count (e.g. `aria-label="Problematic, 5 opponents"`); existing table semantics, `aria-expanded` head-to-head toggle, and `data-label` responsive behavior are preserved.

## 6. What stays the same (explicitly out of scope)

- All computed data: `opponentCategory`, `buildOpponentRow`, win %, streak, recent form quality tiers, head-to-head history — no changes to data/business logic, this is a presentation-layer redesign only.
- The 4 existing sort options.
- The inline head-to-head expand pattern (restyled, not replaced).
- i18n strings largely reusable; only new strings needed are chip labels with counts (e.g. `detail.opponentFilterAll`, `...FilterFavorable`, etc.) and updated copy where "sub-tab" language no longer applies.
