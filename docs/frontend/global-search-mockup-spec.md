┌──────────────────────────────────────────────────────────────────────────────────┐
│  Cerca global                                                                     │
│  ┌──────────────────────────────────────────────────────────────────────┐        │
│  │ 🔍  terra                                                            │ [Cercar]│
│  └──────────────────────────────────────────────────────────────────────┘        │
│  ┌──────────────────────────────────────────────────────────────────────┐        │
│  │ JUGADORS                                                             │        │
│  │  → Terra Playera (RFETM, FCTT)                                       │        │
│  │  → Terrassa Roig                                                     │        │
│  │                                                                      │        │
│  │ CLUBS                                                                │        │
│  │  → CT Terrassa (RFETM · 3 equips)                                    │        │
│  │  Cap resultat de club                                                │        │
│  │                                                                      │        │
│  │ PARTITS                                                              │        │
│  │  → CT Terrassa vs Sant Cugat TT · 12/10/2025                         │        │
│  └──────────────────────────────────────────────────────────────────────┘        │
└──────────────────────────────────────────────────────────────────────────────────┘

## Behavior

- The dropdown opens as soon as the debounced query (300ms) reaches 2 characters, and closes on blur, outside click, Escape, or when a suggestion is selected.
- Results are grouped under three fixed section headers, in this order: Players, Clubs, Matches. Each section is capped at 5 results.
- A section with zero results while the overall response has at least one result elsewhere shows a "no results in this group" line instead of being hidden — the three headers always render together so the grouping is legible.
- When every group is empty, the dropdown shows a single overall "no results" message instead of the three empty sections.
- While the request is in flight, the dropdown shows a single loading line.
- Each result row is a direct link to that entity's existing detail/search-result target: a player row links to the player detail page when a canonical player id exists (unlinked, same as `PlayersSearchPage`, otherwise), a club row links to the club detail page, a match row links to the match summary page.
- Pressing Enter or clicking the search button keeps the existing behavior: it navigates to `/cerca?q=<query>` regardless of dropdown state; the dropdown itself never intercepts Enter/submit.
