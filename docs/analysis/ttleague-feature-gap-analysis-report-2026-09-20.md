# TT League — Feature Gap Analysis & Strategic Product Roadmap

**Platform:** https://ttleague.org
**Report date:** 2026-09-20
**Reviewer perspective:** Senior Table Tennis Performance Analyst & Sports Analytics UX Specialist
**Scope:** user-facing analytics, statistics and visualisation only. Authentication, account, settings, administration, import and user/role management surfaces are explicitly out of scope.

---

## 0. Method and a note on evidence

`https://ttleague.org` serves a login wall to anonymous visitors, so the analytical surface could not be crawled without credentials. Entering credentials is outside what I do on the user's behalf, so this analysis was instead conducted against **the source of record for what is deployed**: the `tt-data-league-frontend` React application (every analytical page, panel, chart and metric helper), the REST read models in `tt-data-league-api-rest`, the query handlers in `tt-data-league-core-domain`, and the domain model itself.

This is in practice a *stronger* basis for a gap analysis than clicking through the UI, because it also reveals **what data the platform already holds but does not yet expose** — which is where most of the cheap, high-value opportunity sits. Where a claim concerns pixels rather than logic (chart legibility on a phone, tap-target size), it is derived from the component markup and stylesheet and is flagged as such.

A companion document, `ttleague-app-review-report-2026-09-20.md`, covers the walk-through review. This report is the forward-looking roadmap counterpart and deliberately avoids repeating its page-by-page narration.

---

## 1. Current State Assessment

### 1.1 The data foundation — better than the UI suggests

This is the single most important finding in the report, so it leads.

The domain model captures table tennis **at four levels of granularity**:

| Level | Entity | Key fields held |
|---|---|---|
| Tie (team match) | `Match` | competition, season, group, round, phase, date/time, city, **venue**, home/away team, winner team, referee, games won each side, **sets won each side**, protested flag |
| Individual encounter | `Game` | game number, type (singles/doubles), **crossover**, home/away `PlayerSeason`, sets won each side, winner side, **cumulative tie score at the game**, not-played flag + reason |
| Set | **`SetScore`** | set number, **home points, away points** |
| Selection | `Lineup` | team, **letter (A/B/C…)**, position, player, **ranking snapshot (nullable)** |

Plus `DoublesPair`, `PlayerSeason` (player–season–club binding), and a canonical `Player` consolidating `FederatedPlayer` identities across two import sources (RFETM, BCNESA).

**Point-level set scores exist, are persisted, and are already exposed by the API** (`MatchDetailDto.SetDto(setNumber, homePoints, awayPoints)`). In the UI they are consumed by exactly one component — `MatchActaDialog`, which renders them as a static match-sheet grid. **No metric anywhere in the product is computed from a point score.** Every statistic the platform shows is derived from win/loss and set counts.

That is the headline gap, and it is a *presentation* gap, not a data-acquisition gap.

Similarly latent and unused:

- `Game.cumulativeHomeSetsWon` / `cumulativeAwaySetsWon` — the tie score as each rubber is played. This is the exact input needed for leverage and pressure analysis, and nothing reads it.
- `Lineup.letter` and `Lineup.position` — the A/B/C-vs-X/Y/Z ordering that is the core tactical lever in team table tennis. Read only to sort the lineup list alphabetically.
- `Lineup.ranking` — a point-in-time federation ranking snapshot, correctly modelled as nullable. Never surfaced.
- `Game.crossover` — order-of-play structure. Never surfaced.
- `Match.venue` / `Match.city` — never used analytically.

### 1.2 What is implemented, and how well

**Overview (`/`)** — community counters (players, clubs, matches, season status), global search across clubs/players/matches, quick-access cards, and an "Advanced analytics" banner promising AI-assisted analysis that links to `/settings`. *Assessment:* a competent landing page that makes a promise the product does not yet keep, and routes the interested user to a page that says the module is unavailable.

**Club detail — 4 tabs.** Summary (players, matches played, win %, competitions; recent matches; top players; top performer), Stats (per-competition W/D/L record bars plus a win-rate-by-season line chart), Players, Matches. Filters: source, season, competition. A separate club-competition detail page exists.

*Assessment:* solid descriptive club reporting. The draw handling is unusually careful — `TieEligibleCompetitions` restricts draws to the four Super Divisió competitions and suppresses the *concept* of a draw everywhere else rather than showing a permanent "0 draws". That is the kind of domain precision that distinguishes a real analytics product from a results dump.

**Player detail — 3 tabs.**

- *Statistics:* career summary (matches, win %, current streak, longest win streak, **singles win % vs doubles win %**, average set margin), a connected scatter plot of matches played and win % by season on twin axes, a six-tier "match quality spectrum" chart (strong win → strong loss), and a season history table (matches, win %, average score).
- *Matches:* summary strip (played, win %, **home/away win-rate split**, pending), a last-5 form guide with current streak, "notable matches" (closest, biggest win, biggest loss), then paginated match cards (10/page) that expand to individual rubbers with opponents hyperlinked to their own player pages.
- *Opponent analysis:* the strongest surface in the product. Per-opponent head-to-head rows categorised **relative to the player's own overall win rate** into favourable / hard / problem; columns for matches, W, L, win %, margin-tiered recent-form chips, and streak. Each row expands to a head-to-head detail with insight tiles: closeness (decisive / competitive / nail-biter + average margin), singles record, doubles record, home/away split, longest win and loss streaks, last met with a relative-time label and meetings-per-season frequency, a trend arrow comparing recent vs prior win rate, and a per-competition breakdown. Filter chips with counts, free-text opponent search, and five sort orders.

*Assessment:* the opponent tab is genuinely good analyst-grade work — the relative categorisation, the margin-tiered form chips and the trend tile are all correct instincts. Two flaws limit it: **no sample-size discipline** (a 1–0 record renders as "favourable, 100.0%" with one decimal place of false precision, indistinguishable from a 12–3 record), and **only three rows render before a "show more" gate**, which defeats scanning for exactly the user who needs it most.

**Match search & Match summary.** Search requires **source and season**, then optionally competition, date range, player (by id, name or home/away location) and club name; fixed page size of 10. The match summary page is the most predictive surface in the product: both teams' recent form strips with win-rate trend, full lineups with per-player form, and an **alignment stability** analysis — how often this exact lineup has been fielded (new / rare / regular), and that alignment's win rate compared to the team's overall win rate. The acta dialog renders the complete set-by-set point scores.

*Assessment:* alignment stability is a sophisticated, sport-specific idea and the closest thing the platform has to a pre-match briefing. Requiring both source *and* season for any match search is, however, a hard analytical ceiling: multi-season and cross-source questions cannot be asked at all.

**Cross-cutting.** Trilingual (ca/es/en). Filter state lives in the URL, so views are shareable and back-navigable — an underrated strength. Accessibility is above average for a product at this scale: `role="tab"` with correct `aria-selected`, keyboard-activated tabs, `visually-hidden` table summaries, `aria-live` status regions, charts exposed as `role="img"` with descriptive labels, and `data-label` attributes on table cells that drive a card layout on narrow screens. There is also an **MCP server layer and OpenAPI v1 controllers** — a programmatic path into the data that the analytics UI never mentions.

### 1.3 Scorecard against the evaluation framework

| Domain | Capability | Status |
|---|---|---|
| **Core metrics** | Win/loss, win % | ✅ Implemented, multi-level (career, season, competition, home/away, singles/doubles) |
| | Head-to-head | ✅ Strong — categorised, with insight tiles |
| | Set scores | ⚠️ Displayed on the acta only; never aggregated |
| | Point scores | ⚠️ **Held and API-exposed; zero metrics derived** |
| | Streaks & form | ✅ Current streak, longest streak, last-5 strip, trend arrows |
| | Margin / closeness | ✅ Average set margin, closeness buckets, quality tiers |
| | Elo / Glicko / opponent-adjusted rating | ❌ **Absent** — no rating logic anywhere in the codebase |
| | Strength of schedule | ❌ Absent |
| | Serve / return win rates | ❌ Absent — and **not recoverable**: match reports do not record who served |
| | Clutch (deuce, 10-10, decider) | ❌ Absent — but **fully computable from existing data** |
| | Momentum / swing tracking | ❌ Absent — but computable at set and rubber level |
| **Predictive** | Match outcome probability | ❌ Absent |
| | Form indicators | ✅ Present (descriptive, not projective) |
| | H2H matchup modelling | ⚠️ Descriptive H2H only; no projection |
| | Surface / ball-type impact | ❌ Absent; venue is captured, ball type is not modelled |
| | Lineup / order optimisation | ❌ Absent despite `Lineup.letter`/`position` being available |
| **Viz & UX** | Tables | ✅ Clear, responsive via `data-label` card fallback |
| | Charts | ⚠️ Four hand-rolled SVG charts, fixed 640×220 viewBox, no tooltips, no interaction, no data-table alternative |
| | Comparative views | ❌ No side-by-side player or team comparison anywhere |
| | Mobile fast-scouting | ⚠️ Layout adapts; the scouting *workflow* does not exist |
| | Export / reproducibility | ❌ No CSV, no download, no API discoverable from the UI |
| | Standings / league context | ❌ **Absent** — no competition table anywhere in the product |

---

## 2. Critical Feature Gaps

Ordered by analytical severity.

### G1 — No opponent-adjusted rating (Elo / Glicko-2)

Every strength statement the platform makes is a raw win percentage. A player going 30–5 in the fifth tier and a player going 18–17 in Super Divisió are presented on the same scale, and the second is shown as the weaker player. Nothing downstream — form, categorisation, trend, "top performer" — is trustworthy without this correction. It is the load-bearing absence: **fixing G1 raises the quality of a dozen existing features simultaneously**, which is why it is first.

Glicko-2 is the better fit here than plain Elo: it carries a rating deviation, which handles the long summer gaps and the sparse-appearance players that dominate a regional league, and it gives you the confidence interval needed to fix the sample-size problem in §1.2.

### G2 — Point-level data is collected and thrown away

`SetScore` holds every point of every set. From it, entirely without new data collection:

- **Points won %** — a far lower-variance strength signal than match win %, and the best predictor available at this data granularity.
- **Clutch / deuce record** — sets reaching 10-10, and the win rate in them. This is the explicit framework item and it is directly computable.
- **Decider record** — win rate in fifth/seventh sets.
- **Comeback and collapse rate** — rubbers won from 0-2 down, lost from 2-0 up, via set sequence.
- **Set-score distribution** — the 11-x shape of a player's wins: an 11-4/11-5 player and an 11-9/12-10 player have identical win rates and completely different profiles.
- **Momentum / swing tracking** — set-to-set point-margin trajectory within a rubber; combined with `Game.cumulativeHomeSetsWon`, tie-level swing across rubbers.

The gap between "we store this" and "we compute nothing from it" is the largest value-per-effort opportunity on the platform.

### G3 — No competition standings or league context

There is no league table. A result cannot be read against a promotion or relegation race, a team's position is unknown, and remaining-fixture difficulty is unknowable. For a league-data platform this is a conspicuous structural absence, and it is what most users arrive expecting to find.

### G4 — No predictive layer

The platform's own banner promises analysis, comparison and pattern discovery. Today nothing projects forward: no win probability for a scheduled tie, no expected rubber-by-rubber outcome, no projected final table. The match summary page's form strips and alignment stability are the correct *ingredients* assembled one step short of a prediction.

Note a prerequisite: `MatchOutcome`'s documentation states imports only ever create a `Match` for a report that was actually played. **There are therefore no future fixtures in the system**, which means pre-match prediction has nowhere to attach until a fixture feed exists. This dependency is easy to miss and should drive sequencing.

### G5 — No comparison tooling

Analysis is comparative by nature, and the product offers no comparison primitive: no two-player side-by-side, no team-vs-team pre-tie sheet, no percentile or cohort context ("this win rate is 78th percentile in this competition"). Every number is shown as an absolute with no reference class.

### G6 — No lineup / order analytics

`Lineup` records the letter and position of every selection, and `Game` records the crossover. The A-vs-X, A-vs-Y, B-vs-X matchup grid — the central tactical decision in team table tennis — is one query away and does not exist. Alignment stability on the match summary page proves the data path already works.

### G7 — No sample-size or data-quality discipline

Win percentages print to one decimal on samples of one. Opponent categories (favourable / hard / problem) are assigned without a minimum-meetings threshold. Nothing distinguishes "0 %" from "no data", and nothing tells the user that a player's 2019-20 season is thin because the import only covers part of it. An analyst cannot calibrate trust in any number on the platform.

### G8 — No export and no discoverable API

There is no CSV download, no copy-as-table, no chart-data export. An analyst who wants to do anything the UI does not do is stuck. Meanwhile an **MCP server layer and OpenAPI v1 endpoints already exist** — capability that is built, unmentioned in the analytics UI, and therefore effectively absent.

### G9 — Search cannot span seasons or sources

`searchMatches` hard-requires both `source` and `season`, with a fixed page size of 10. "Every match this player has played against left-handers since 2022" is unaskable. The season slider on detail pages partially compensates, but the primary match-finding surface is scoped to a single season of a single federation.

### G10 — No scouting workflow

No favourites, no watchlist, no saved filters, no recent-entities list, no printable or shareable pre-match one-pager. The URL-encoded filter state is a good foundation for exactly this and is not built on. On a phone at a venue, preparing for the next tie means re-navigating from scratch.

### G11 — Charts are static images

Four hand-rolled SVG charts with a fixed 640×220 viewBox, no tooltips, no hover, no focus states, no zoom, no crosshair, and no data-table alternative behind them. The aria-labels are present and thoughtful, but a `role="img"` label does not let a screen-reader user — or any user — read a specific season's value off the connected scatter plot.

### G12 — No context dimensions

Age group, gender category, playing hand, veteran/junior status, venue and travel are either uncaptured or unused. The framework's "surface / ball type impact" item is partly out of reach (ball type is not in the source reports), but its accessible proxy — **venue and home-advantage effects, which `Match.venue` and `Match.city` fully support** — is not exploited beyond a binary home/away split.

### G13 — No methodology or glossary

"Average score", "average set margin", "match quality spectrum", "closeness", "problem opponent" are all product-invented terms with no definitions exposed. Any derived metric added later — especially a rating — will be ignored or mistrusted without a stated methodology.

---

## 3. Enhancement Roadmap

How the features that already exist should evolve. Grouped by the existing surface they attach to, so each item has an owner in the current IA.

### 3.1 Player detail → Statistics tab

| Current | Enhancement |
|---|---|
| Career summary tiles | Add **Glicko-2 rating with deviation band**, peak rating and date, points-won %, deuce-set record, decider record. Show every rate with an explicit n or confidence interval. |
| Season history table | Add columns: opponent-adjusted win % (expected vs actual wins, from opponent ratings), points won %, sets won %, rating at season end. Add a career-total row. |
| Connected scatter (matches + win % by season) | Replace the twin-axis scatter with a **rating trajectory over time** — x = date, not season index, so gaps and density are visible — with a deviation ribbon and match markers. Twin-axis charts with different units invite misreading; rating-over-time answers the same question unambiguously. |
| Match quality spectrum | Keep — it is a good original idea. Re-tier it on **opponent rating differential** rather than set margin alone, so a narrow win over a much stronger player reads as a strong result. Make points hoverable and linked to the match. |
| "Average score" | Define it, or replace it with points-won %, which is self-explanatory and better behaved. |

### 3.2 Player detail → Opponent analysis

| Current | Enhancement |
|---|---|
| Favourable / hard / problem categories | Gate on a **minimum number of meetings** (3 is the usual floor); below that show "insufficient sample" rather than a category. Compute the category against **rating-expected** results, not raw overall win %: losing 40 % to opponents rated 200 points above you is over-performance, and the current model calls it a problem. |
| 3 visible rows behind "show more" | Show 10–15; make the table sortable by header click; keep "show more" for the tail. |
| Insight tiles | Add **clutch split vs this opponent** (deuce sets, deciders), **set-score shape** (comfortable or narrow wins), and **rating-expected vs actual** as the headline tile. |
| Trend arrow | Show the n behind each side of the comparison; a trend computed from 2 vs 2 matches is noise. |
| — | Add **"scout this opponent"**: a one-screen, printable/shareable summary combining the H2H, the opponent's recent form, their clutch profile and their preferred set-score shape. This is the mobile fast-scouting artefact the platform currently lacks. |

### 3.3 Match summary page → pre-tie briefing

The strongest predictive surface should become the product's flagship.

- Add a **projected rubber grid**: each plausible A/B/C × X/Y/Z pairing with a win probability from the two players' ratings, plus the H2H record where one exists. This is the single highest-value screen the platform could ship, and both inputs already exist.
- Promote **alignment stability** from a badge to a comparison: this alignment's record, the alternatives, and their records.
- Add **tie win probability** aggregated from the rubber grid.
- Add **momentum replay** for completed ties: the cumulative tie score after each rubber (already stored on `Game`) as a step chart, so a 4-0 collapse and a 4-3 fightback stop looking identical.
- Surface **venue and home advantage** for this fixture from the venue history.

### 3.4 Acta dialog → point-level analysis

The acta grid is currently a facsimile. Make it analytical: highlight deuce sets, mark the momentum turning point, show per-set point margins, and add a per-rubber points-won summary. Same data, an order of magnitude more insight.

### 3.5 Club detail → Stats tab

- Add the **competition standings table** (G3), with the club's position highlighted and remaining-fixture difficulty derived from opponent ratings.
- Add a **squad rating distribution** and a **contribution view**: which players are producing the rubber wins, by lineup letter.
- Add **doubles pair effectiveness** — `DoublesPair` is modelled; pair win rate versus the constituent players' singles expectations is a real, actionable and cheap metric.
- Extend the win-rate-by-season line to a **rating-weighted performance index**, so improvement is not confounded with division changes.

### 3.6 Match search

- Relax the `source` + `season` requirement to allow **multi-season and all-source** queries; add a season *range*.
- Add filters that matter analytically: opponent rating band, result margin, deuce-set presence, competition tier.
- Make page size selectable, and add **export of the result set**.

### 3.7 Visualisation and UX layer

- Introduce a **shared chart component** with tooltips, keyboard-navigable focus states, and a "view as table" toggle behind every chart. This resolves both the interactivity gap (G11) and the accessibility ceiling in one piece of work, and eliminates four hand-rolled SVG implementations.
- Adopt a **consistent, colour-blind-safe result palette** (win/loss/draw and the six quality tiers) with a redundant non-colour encoding — shape or letter — since results are currently distinguished by colour class alone in several places.
- Add **sparklines in tables** (form, rating trend) so scanning does not require opening rows.
- Build a **comparison mode**: pick two players or two teams, render the tile set side by side with the delta. Reuse the existing tile components.
- Add **favourites and recent entities** in the sidebar, and a **"copy link to this view"** affordance that exposes the already-excellent URL state.

### 3.8 Trust and transparency

- Ship a **methodology page**: how the rating works, how clutch is defined, what "average score" means, what each data source covers and from which season.
- Add **coverage indicators** per player and per season: "12 of 14 ties imported", "point scores available for 78 % of rubbers". This converts G7 from an invisible risk into visible, honest metadata.
- Deliver the **"Advanced analytics" banner's promise, or retire the banner**. Pointing it at `/settings`, which reports the module is unavailable, is worse than not promising.

### 3.9 Programmatic access

Publicise the existing OpenAPI and MCP surfaces from the analytics UI: an "API & data" entry with endpoint docs, a token flow and CSV export. The MCP layer in particular is unusual and genuinely differentiating — it means an analyst can point an LLM directly at the league's data. Nothing in the product tells them so.

---

## 4. Priority Matrix

Value = analytical value to the target user. Effort = implementation cost given what already exists.

### High priority

| # | Recommendation | Value | Effort | Why now |
|---|---|---|---|---|
| H1 | **Glicko-2 rating engine** (batch-computed per source, exposed on player / club / match surfaces) | Very high | High | Unblocks H2's interpretation, H4, H5; retro-fixes the correctness of existing categorisation and "top performer" logic. Nothing else should be built on raw win % |
| H2 | **Point-level metrics from `SetScore`**: points won %, deuce/10-10 record, decider record, comeback/collapse rate, set-score shape | Very high | **Low** | Data is stored *and already API-exposed*. Best value-per-effort item in the report |
| H3 | **Sample-size discipline**: minimum-n gates on categories, confidence intervals or explicit n on every rate, "no data" ≠ "0 %" | High | Low | Cheap, and a credibility precondition for everything else |
| H4 | **Projected rubber grid + tie win probability** on the match summary page | Very high | Medium | Inputs exist once H1 lands; turns the best existing page into the flagship |
| H5 | **Competition standings tables** | High | Medium | The most conspicuous structural absence for a league platform |
| H6 | **Interactive chart component** (tooltips, focus, view-as-table) replacing the four bespoke SVGs | High | Medium | Resolves interactivity and accessibility together, and removes duplication |
| H7 | **CSV export + surface the existing OpenAPI/MCP access** | High | **Low** | The API exists. Largely documentation and a download button |
| H8 | **Opponent-analysis fixes**: raise the row cap to 10–15, sortable headers, rating-expected categorisation | High | Low | Small changes to the product's strongest surface |

### Medium priority

| # | Recommendation | Value | Effort |
|---|---|---|---|
| M1 | Lineup/order matchup grid (A/B/C × X/Y/Z) from `Lineup.letter` and `position` | High | Medium |
| M2 | Side-by-side player and team comparison mode | High | Medium |
| M3 | Momentum replay from `Game.cumulativeHomeSetsWon`; analytical acta with deuce and turning-point highlighting | Medium-high | Medium |
| M4 | Multi-season / multi-source match search, plus rating-band and margin filters | Medium-high | Medium |
| M5 | Percentile and cohort context on every headline metric | Medium-high | Medium |
| M6 | Methodology page, metric glossary, per-season coverage indicators | Medium | Low |
| M7 | "Scout this opponent" printable/shareable one-pager | Medium-high | Medium |
| M8 | Doubles pair effectiveness from `DoublesPair` | Medium | Low |
| M9 | Favourites, watchlists, saved views, "copy link to this view" | Medium | Low-medium |
| M10 | Colour-blind-safe palette with redundant non-colour encoding | Medium | Low |
| M11 | Resolve the "Advanced analytics" banner — deliver or retire | Medium | Low |
| M12 | Venue and home-advantage analytics from `Match.venue` / `city` | Medium | Medium |

### Low priority

| # | Recommendation | Value | Effort |
|---|---|---|---|
| L1 | Fixture/schedule ingestion — *prerequisite for pre-match prediction; re-prioritise to High the moment forward-looking projection is committed to* | Low today, unblocking later | High |
| L2 | Full projected final-table simulation (Monte Carlo) | Medium | High |
| L3 | Referee, protest and scheduling-context analytics | Low | Low |
| L4 | Age / gender / category dimensions (needs new source fields) | Medium | High |
| L5 | Ball-type and equipment modelling | Low | High — not present in source reports |
| L6 | Serve / return win rates | High if obtainable | **Not feasible** — match reports do not record service |
| L7 | Video, shot-placement or rally-level integration | High | Very high — out of scale for this platform |

### Suggested sequencing

1. **Foundation (now):** H2, H3, H7 — all low-effort, all immediately visible, none blocked by anything.
2. **Rating (next):** H1, then H8's rating-expected categorisation and the §3.1 trajectory chart.
3. **Flagship (after rating):** H4, H5, M1.
4. **Platform quality (parallel throughout):** H6, M6, M10 — these compound, and the longer the chart layer stays bespoke the more copies of it exist.

---

## 5. Closing assessment

TT League is not a thin results website. It has a carefully modelled four-level domain, genuine sport-specific reasoning (the tie-eligibility handling of draws, the crossover and cumulative-score modelling, alignment stability), an opponent-analysis surface that outclasses most amateur-league platforms, thoughtful accessibility, URL-addressable state, and an MCP layer that almost nobody in this space has.

Its weakness is not data, and it is not care. It is that **the analytical layer stops at counting wins**. Point scores are stored and never aggregated; selection letters are stored and never crossed; the tie score at each rubber is stored and never plotted; no number is adjusted for who it was earned against.

The corollary is that the highest-value work here is unusually cheap. H2 alone — deriving clutch, decider, comeback and points-won metrics from data that is already persisted and already served by the API — would move the platform further than any amount of new data collection. H1 makes every existing statistic mean what users already assume it means. Together they are the difference between a well-built results archive and the analytics product the landing page is already promising.
