# TT League — Application Review Report

**Site reviewed:** https://ttleague.org
**Review date:** 2026-09-20
**Reviewer perspective:** Table tennis analyst (statistics, performance metrics, predictive analytics)
**Access:** authenticated session (`test` / `test`)
**Scope:** public/analytical surface only — `Configuració` (`/settings`) and any administrative area were deliberately excluded.

---

## 1. Introduction

### 1.1 Purpose

TT League positions itself on the landing page as an *"open data platform for the table tennis community"* offering *"official results, player statistics and standings from recent seasons"*, with a stated roadmap item of an *"AI-based analytics and comparison layer"*.

This review walks the whole navigable product from a cold start, page by page and tab by tab, and evaluates it against what a table tennis analyst actually needs: measure player and team strength, contextualise results, detect form and matchup patterns, and project future outcomes.

### 1.2 Method

1. Logged in and crawled every main menu entry: `Resum` (overview), `Cerca de clubs`, `Cerca de jugadors`, `Cerca de partits`.
2. Opened representative entities and exercised **every tab** on each:
   - Club: `CLUB NATACIO SABADELL` (377 players, 7 seasons, 3 sources) → tabs `Resum`, `Estadístiques`, `Jugadors`, `Partits`.
   - Player: `WEISZ Joan (548)` (101 matches, 5 seasons) → tabs `Estadístiques`, `Partits`, `Anàlisi d'oponents`, `Historial estadístic`.
   - Match sheets (`acta`) from **two different sources** (BCNESA and RFETM) to compare data depth.
3. Exercised filters (source / season / competition / date range / home-away / player name / club name), pagination, global search, expandable per-match game detail, and mobile viewport rendering.
4. Benchmarked findings against state-of-the-art practice in racket-sport analytics (rating systems, point-level metrics, opponent-adjusted performance, leverage/clutch analysis, predictive modelling).

### 1.3 What the analyst role needs — the yardstick used in this review

The review prioritises capability areas in this order, because this is the order in which they create analytical value:

| # | Capability | Why it matters to the analyst |
|---|---|---|
| A1 | **Trustworthy, unified entity model** (one player = one identity across sources) | Nothing downstream is valid if the same human appears as three different players |
| A2 | **Point- and set-level granularity** | Rally-level data is where the signal lives; match W/L is a coarse, noisy outcome |
| A3 | **Opponent-adjusted strength rating** (Elo/Glicko/TrueSkill class) | Raw win% is meaningless without strength of schedule |
| A4 | **Standings / competition context** | A result only has meaning inside a table and a promotion/relegation race |
| A5 | **Head-to-head and matchup analysis** | Line-up decisions are matchup decisions |
| A6 | **Team line-up / order analytics** (A/B/C vs X/Y/Z, doubles) | The core tactical lever in team table tennis |
| A7 | **Form, streak, momentum, clutch** | Short-horizon predictive signal |
| A8 | **Comparison and cohort tools** | Analysis is comparative by nature |
| A9 | **Prediction / projection** | The stated end goal of the platform |
| A10 | **Export / API / reproducibility** | An analyst must be able to leave the UI |

---

## 2. Current State Assessment

### 2.1 Information architecture

The product is a four-entry application:

```
Resum (/)            → welcome, global search, community counters, "advanced analytics coming soon" teaser
Cerca de clubs       → club name search → club detail (4 tabs)
Cerca de jugadors    → player name search → player detail (4 tabs)
Cerca de partits     → multi-criteria match finder → match sheet modal ("acta")
Configuració         → out of scope
```

The mental model is clean and immediately legible: **three entities (club, player, match), one search each**. Navigation is consistent, the left rail is persistent, and every entity page carries the same global filter strip (`Font` / `Temporada` / `Competició`) — a genuinely good decision, since the analyst learns one filter idiom and reuses it everywhere.

### 2.2 Data footprint

| Dimension | Observed |
|---|---|
| Players | 8,428 |
| Clubs | 490 |
| Matches | 37,036 |
| Seasons | 2019-2020 → 2025-2026 (7) |
| Sources | RFETM (national), FCTT (Catalan federation), BCNESA (Barcelona municipal leagues) |
| Competitions | 20+ per large club, from `super-divisio` down to `Quarta` and veteran categories (`Vet 1a` … `Vet 4a`) |

This is a **substantial and genuinely differentiated corpus**. Multi-source, multi-tier, seven-season coverage spanning national, regional and municipal competition is exactly the long-tail data that is not aggregated anywhere else. The raw material for serious analytics is present.

### 2.3 Page-by-page assessment

#### 2.3.1 `Resum` — Overview (`/`)

- **Global search** across clubs / players / matches, grouped into three result buckets (min. 2 characters).
- **Community counters** (players / clubs / matches / current season).
- Three "quick access" cards duplicating the left rail.
- An "ANALÍTICA AVANÇADA" teaser: *"Soon you will be able to analyse, compare and discover patterns with AI support."*

**Assessment.** Functional as a launcher, but analytically empty. It is a *directory front door*, not a *dashboard*. For an analyst returning daily it answers no question: no recent-results feed, no "what changed since my last visit", no watchlist, no league-wide leaderboards, no upsets of the round. The counters are vanity metrics.

Two concrete defects:
- Counters render as `0 / 0 / 0` during load with no skeleton, which reads as "empty database" for 1–2 s.
- The `Més informació` CTA under the AI-analytics teaser links to `/settings`, which is not an information page about the analytics roadmap. Dead-end CTA.
- Global search match results are plain text, not links to their match sheets, and carry neither date nor score.

#### 2.3.2 `Cerca de clubs` → club detail

Search is name-substring and returns a card per club annotated with **sources**, **player count** and **season count** — a good, dense result card.

**Tab `Resum`.** KPI row (players, matches played, win %, competitions), recent matches with W/D/L chips, "players with most competitions", and a "best performance (raw games)" list.

**Tab `Estadístiques`.** Per-season, per-competition breakdown with V/D counts and win%. The most analytically useful club view: it shows, for example, that CN Sabadell ran 22 competition entries in a single season across three federations, with win rates from 10% (`Preferent`) to 100% (`fasc-primera-divisio`).

**Tab `Jugadors`.** Player roster with per-competition distribution, "most active (historic)" ranked by individual games, and a searchable list.

**Tab `Partits`.** Matches KPI block with **home/away split** (50% / 42%), a **form string** (last 5 W/L plus current streak), and "notable matches" (closest result, widest win, widest loss), with source-level match counts (BCNESA 852 / FCTT 22 / RFETM 345).

**Assessment.** Solid descriptive reporting. The home/away split and form/streak indicators are real analyst-grade touches. But it is entirely **club-aggregate**: a club fielding eight teams across three federations is collapsed into a single win%, which averages a Honour Division side with a fourth-division side and is analytically meaningless.

Defects found:
- Headline KPIs print `0 de sempre` ("0 all-time") and `en 0 temporades` ("in 0 seasons") next to correct values — a broken denominator.
- The `Percentatge de victòries per temporada` chart on `Estadístiques` renders *"Not enough season data to show a trend"* on a club with **seven seasons** of data listed directly above it.
- Ties render inconsistently: most rows show `V · D`, some show `V · 0E · D`; aggregate headers show `0E` where ties do not apply.
- The `Jugadors` tab reports **163 players** while the page header reports **377**. Two unexplained denominators.
- Competition names are raw slugs (`divisio-honor-masculino`, `fasc-super-divisio-masculino`, `tercera nacional`) next to human labels (`Preferent`, `Segona _A_`); the `_A_` / `_B_` underscore convention leaks source formatting into the UI.

#### 2.3.3 `Cerca de jugadors` → player detail

Search returns cards with name, licence number, sources and seasons.

**Tabs `Estadístiques` / `Historial estadístic`.** `Resum de carrera` is the strongest analytical block in the product:

| Metric | Example (WEISZ Joan) |
|---|---|
| Matches played | 101 |
| Win % | 76.2% |
| Current streak | 2-loss streak |
| Longest win streak | 21 |
| **Singles win %** | 73.6% |
| **Doubles win %** | 66.7% |
| **Average set margin** | 0.9 |

Plus a dual-axis season chart (matches played + win%), a per-match sequence chart of set scores over time, and a season table (`Partits jugats`, `Victòries (%)`, `Puntuació mitjana`).

**Tab `Partits`.** Match list with W/L chip, score, competition, round, phase (`1a Fase`, `TITOL`, `Play Off Títol`), date and source. Each row expands to `Jocs (n)` showing the player's own rubbers: `INDIVIDUAL` / `DOBLES`, opponent name(s), result, **set score** (`3-1`, `0-3`). Carries the same KPI block as the club (matches, win%, home/away, pending) plus form string and notable matches.

**Tab `Anàlisi d'oponents`.** Genuinely differentiated. Opponents are bucketed into `Favorable` / `Difícil` / `Problemàtic`, sortable by win%, matches played, last meeting and *"proximity"*, with a per-opponent table of played / W / L / win% / recent-form dots / streak. For this player: 91 opponents, 65 favourable, 0 difficult, 18 problematic.

**Assessment.** The singles/doubles split, longest-streak, average set margin and opponent-difficulty bucketing are all above the baseline federation sites provide. This is the part of the product that already speaks the analyst's language.

Defects and gaps found:
- **The player entity is source-scoped, not canonical.** The search page promises *"consulta la seva identitat canònica"* ("consult their canonical identity") but searching `WEISZ` returns **13 separate records** that are clearly ~7 humans duplicated across sources: `JOAN WEISZ ROMERO (1109)` (RFETM) and `WEISZ Joan (548)` (BCNESA); `PERE WEISZ ROMERO (1011)` and `WEISZ Pere (511)`; `IGNASI WEISZ ROMERO (20272)` and `WEISZ Ignasi (7539)`; and so on. The club roster shows the same internally — `FERRAN HUGUET TAGÜEÑA` appears twice, plus `HUGUET Ferran`, plus the accent variant `FERRAN HUGUET TAGUEÑA`. **This is the most damaging defect in the platform**: every career metric is computed over a fragment of the player's actual career.
- `Puntuació mitjana` ("average score") in the season table is undefined — it appears to be average team rubbers won (values 3.7–4.9 on a seven-rubber format), i.e. a *team* statistic sitting in a *player* table.
- The source filter does not reset when navigating with `source=all` in the URL; it stays pinned to the source the player record belongs to.
- `Marge mitjà de sets` (0.9) is presented without definition or distribution — a single number with no variance context.

#### 2.3.4 `Cerca de partits` → match sheet (`acta`)

A proper multi-criteria finder: source, season, competition (cascading — competitions populate only after source and season are chosen), date range, home/away, player name, club name. Results paginate (30 pages for one club-season); each row shows both teams, the six participating players with licences, date, competition, phase and aggregate score.

`Veure acta` opens the full match sheet in a modal: round, date, time, teams, aggregate score, and the **rubber-by-rubber grid** with position letters (A/B/C vs X/Y/Z), licence numbers, per-game columns `J1`–`J5`, set score, and running rubber score.

**Assessment — and the most important finding in this review.** The grid **has point-level columns, and whether they are populated depends entirely on the source**:

- **RFETM acta** — fully populated: `7-11 / 7-11 / 10-12 → 0-3`, `10-12 / 11-4 / 11-7 / 13-11 → 3-1`, `12-10 / 11-6 / 12-10 → 3-0`. Every point score is there.
- **BCNESA acta** — `J1`–`J5` are **entirely blank**; only the set score survives. Doubles rows show `—` for both player slots, so the pairs are not even identified.

The platform therefore **already holds point-level data for a large part of its corpus and surfaces no statistic derived from it anywhere.** Deuce games, points-won percentage, comeback rate from 0-2 down, point-margin distributions, closeness of defeats — none is computed. This is the largest block of unrealised analytical value on the site.

Further defects in this area:
- `Rk:` (ranking) appears on every player row of every acta and is **always `—`**. Ranking is never populated, for any source.
- `Lloc: Lugar:` — the venue field shows an untranslated raw source label instead of a value (a parsing failure); `Àrbitre: No disponible` (referee never populated).
- Timestamps render as `3/6/2026, 0:00:00` — a meaningless midnight time on every listing row.
- The home/away radio group is labelled with untranslated constants `HOME` / `AWAY` while the rest of the UI is Catalan.
- A search with a club name but no season silently returns nothing, with no message explaining that season is effectively required.

### 2.4 Cross-cutting UX observations

**Good.** Consistent filter strip across entities; consistent KPI card language; form/streak chips; expandable in-place detail; sensible pagination; mobile viewport (375 px) renders correctly with a readable stacked layout and a horizontal season slider; Catalan localisation is largely complete and natural.

**Weak.**
- **No standings anywhere.** The landing page explicitly advertises *"classificacions"* and there is no league table in the product. There is no competition entity page at all — competitions exist only as filter values.
- **No team entity.** `CLUB NATACIO SABADELL A` and `CLUB NATACIO SABADELL B` are distinct competitive units in different divisions, but they exist only as strings inside match rows. No team page, no team stats, no squad.
- **No comparison view.** Two players cannot be placed side by side anywhere.
- **No export.** No CSV, no copy-table, no documented public API (the `/api/v1/**` endpoints are session-gated).
- **No glossary / methodology page.** `Puntuació mitjana`, `Marge mitjà de sets`, `Proximitat` and the `Favorable / Difícil / Problemàtic` thresholds are undefined in the UI.
- **No data-freshness indicator.** Nothing states when each source was last ingested, or how complete a season is.
- Loading states are mostly absent (zeros flash before data arrives).
- The notification bell shows "3 pending notifications" with no evident analytical purpose.

---

## 3. Feature Gap Analysis

Scored against the A1–A10 yardstick from §1.3.

| # | Capability | Status | Evidence | Gap severity |
|---|---|---|---|---|
| A1 | Unified player identity | **Missing** | 13 `WEISZ` records for ~7 humans; duplicates inside one club roster; page claims "canonical identity" | **Critical** |
| A2 | Point-level analytics | **Data present, unused** | RFETM actas carry `J1`–`J5` point scores; zero derived metrics anywhere | **Critical** |
| A3 | Opponent-adjusted rating | **Missing** | Only raw win%; `Rk:` field exists and is always empty | **Critical** |
| A4 | Standings / competition context | **Missing** | No competition page, no table, despite being advertised | **High** |
| A5 | Head-to-head / matchup | **Partial** | Per-player opponent analysis exists; no H2H page, no player-vs-player view, no team-vs-team history | **High** |
| A6 | Line-up / order analytics | **Missing** | Acta shows A/B/C vs X/Y/Z positions; no analysis by position, no doubles-pair stats (BCNESA pairs not even identified) | **High** |
| A7 | Form / streak / clutch | **Partial** | Form string, current streak, longest streak present; no rolling-window form rating, no decisive-rubber (clutch) metric, no momentum modelling | **Medium** |
| A8 | Comparison / cohorts | **Missing** | No two-entity comparison anywhere; no league-wide leaderboards | **High** |
| A9 | Prediction / projection | **Missing** | Advertised as "coming soon"; nothing shipped | **High** |
| A10 | Export / API / reproducibility | **Missing** | No CSV, no chart download, no public API, no methodology docs | **Medium** |

### 3.1 Detailed gap notes

**G1 — Identity resolution (A1).** Every metric on every player page is currently computed over a *source-fragment* of a career. `WEISZ Joan` shows 101 matches from BCNESA only; his RFETM record (`JOAN WEISZ ROMERO (1109)`) is a separate page with separate numbers. No analyst can trust a career win% built this way. The fix requires an entity-resolution layer (licence cross-walk where available, plus name normalisation handling `SURNAME Firstname` vs `FIRSTNAME SURNAME1 SURNAME2`, accent folding, and club × season co-occurrence evidence), surfaced as a canonical player with a "known aliases / sources" panel.

**G2 — Point-level metrics (A2).** With `J1`–`J5` already stored for RFETM, the following are immediately derivable and entirely absent:
- Points won % and points per game.
- Deuce-game record (games decided at 12-10 or beyond) — a direct clutch proxy.
- Comeback rate (won after 0-2 or 1-2 down) and collapse rate.
- Point-margin distribution per game, and **expected vs actual win%** — a player losing 9-11, 10-12, 9-11 is not a 0-3 player.
- "Close-match" performance: record in rubbers decided by ≤2 points in the final game.

This is the highest value-per-unit-effort item on the list, because no ingestion work is required.

**G3 — Rating system (A3).** No strength rating exists. Win% across a corpus spanning `super-divisio` to `Quarta` and veteran categories is not comparable between players. A Glicko-2 or TrueSkill rating computed **per rubber** (not per team match), with rating uncertainty and a division-tier prior, would make every other metric interpretable and is the prerequisite for any prediction feature. The empty `Rk:` column suggests this was already anticipated in the data model.

**G4 — Competition and standings (A4).** No competition entity. Required: competition page with classification table (match points, rubbers for/against, set and point difference as tiebreakers per each federation's actual regulations), round-by-round fixture grid, and promotion/relegation zones. This is table stakes for a "league services" product and is explicitly promised on the landing page.

**G5 — Team entity (A6).** Team A and Team B of the same club must be separable. Analysts reason about *teams*, not clubs: squad, line-up stability, position-by-position productivity (how often does the A player deliver the point?), home/away by venue, and doubles-pair chemistry.

**G6 — Line-up and order analytics (A6).** The acta grid encodes the full Swaythling/Olympic-style order. Derivable and missing: win% by position slot (A/B/C), a position-vs-position matchup matrix, the value of winning the doubles, rubber-order leverage (which rubber number most often decides the tie), and optimal line-up suggestions against a specific opponent — the single most requested analytical output among team captains.

**G7 — Comparison and leaderboards (A8).** No way to compare two players, two teams, or a player against a competition baseline. No league-wide leaderboards (best win%, longest streak, most improved, best against higher-rated opposition) — which are also the primary engagement driver for a community data platform.

**G8 — Prediction (A9).** Nothing shipped. Once G3 exists, per-rubber win probability, team-match win probability from a declared line-up, season projection and promotion/relegation probabilities all become straightforward.

**G9 — Data quality surface (cross-cutting).** No completeness indicator per source/season, no "last updated", no visibility into the BCNESA point-score void, no flagging of suspicious records (walkovers, 6-0 ties with no rubber detail). An analyst must know what is missing before trusting what is present.

**G10 — Export and methodology (A10).** No CSV/JSON export, no public API, no metric definitions. Serious analysis will happen outside the UI; the product should enable that rather than trap the data.

---

## 4. Enhancement Recommendations

Grouped by theme; each carries an ID used in the prioritisation matrix (§5).

### 4.1 Data integrity foundations

**R1 — Canonical player identity resolution.**
Build an entity-resolution pipeline producing a `canonical_player` with `player_source_records[]`. Match on: licence cross-walk where federations share numbers; normalised name (accent-folded, surname/forename order-agnostic, particle-aware); club × season co-occurrence; birth year where obtainable. Surface on the player page as a header panel — "Also known as: WEISZ Joan (BCNESA 548), JOAN WEISZ ROMERO (RFETM 1109)" — with a per-source breakdown toggle, a confidence score, and a user-reportable "same/not the same person" feedback loop.
*Without R1, R4, R6, R7 and R11 are all computed on broken denominators.*

**R2 — Data completeness and freshness surface.**
Per source × season × competition: last ingested, matches expected vs ingested, % of actas with point-level detail, % with identified doubles pairs. Present as a "Data quality" page plus an inline badge on any view whose underlying data is partial (e.g. a warning on BCNESA player pages that point-level metrics are unavailable).

**R3 — Fix the defects found.**
Concretely: `0 de sempre` / `en 0 temporades` denominators; the "not enough season data" trend chart on seven-season clubs; the 163-vs-377 player-count discrepancy; `Lloc: Lugar:` venue parsing; `Rk:` either populate or remove; `0:00:00` timestamps; `HOME`/`AWAY` untranslated radios; competition slug→label mapping (`divisio-honor-masculino` → "Divisió d'Honor masculina", `Segona _A_` → "Segona A"); the `Més informació` → `/settings` dead link; loading skeletons instead of flashing zeros; consistent tie (`E`) rendering; make season clearly required (or default it) in match search instead of returning silence; make global-search match results clickable.

### 4.2 Unlock the point-level data

**R4 — Point-level metric suite.**
From existing `J1`–`J5` data, compute and surface on the player page (new tab `Anàlisi de punts`):
- Points won %, points per game, average point margin in games won / lost.
- Deuce-game record and deuce win% (clutch index).
- Comeback rate from 0-2 / 1-2 down; collapse rate from 2-0 / 2-1 up.
- Expected vs actual rubber win% from point margins (an "unlucky/lucky" index).
- Game-by-game momentum: win% in G1 vs G5 (starter vs closer profile).

Gate the tab behind an availability check and explain when data is absent (ties to R2).

**R5 — Ingest missing point scores and doubles pairs for BCNESA.**
Either extend the scraper to capture per-game scores where the source publishes them, or document definitively that BCNESA does not publish them and mark the corpus accordingly. Resolving BCNESA doubles pairs (currently `—` / `—`) is separately necessary, since their absence silently voids all doubles analysis for that source.

### 4.3 Make numbers comparable

**R6 — Player rating system (Glicko-2 or TrueSkill).**
Rate **per rubber**, not per team match. Initialise by division tier; carry rating deviation; decay for inactivity. Surface as: current rating plus uncertainty band, rating-history sparkline on the player page, rating delta per match in the match list, and "quality of win" annotation (beat a +150-rated opponent). Add **strength of schedule** alongside every win% so that 76.2% against a weak field reads differently from 76.2% against a strong one.

**R7 — Opponent-adjusted performance metrics.**
Augment raw win% with a performance rating (average opponent rating plus margin adjustment) and "performance vs expectation" per season. Redefine the existing `Favorable / Difícil / Problemàtic` buckets on rating differential rather than raw head-to-head record — a player with 0 "difficult" opponents out of 91 indicates the current thresholds are not discriminating.

### 4.4 New entities and views

**R8 — Competition pages with standings.**
Classification table implementing each federation's actual tiebreak rules (match points → rubbers → sets → points), round-by-round results grid, form column, promotion/relegation zone shading, and a season-long position-trajectory chart. Link every match row and every team to it.

**R9 — Team entity pages.**
Squad and availability, line-up history, home/away by venue, per-position productivity, rubber-order leverage, doubles-pair record, and a team rating derived from R6. Separate `CLUB … A` from `CLUB … B` throughout the product.

**R10 — Head-to-head pages.**
Player-vs-player and team-vs-team: full meeting history, set and point aggregates, matchup trend over time, venue split, and a predicted next-meeting probability once R6 lands.

**R11 — Comparison tool and leaderboards.**
Side-by-side comparison of 2–4 players (or teams) across all metrics with radar and trend overlays. League-wide leaderboards with filters (season, competition, tier, age category): best win%, highest rating, longest streak, best deuce record, most improved, best performance vs expectation.

**R12 — Line-up optimiser.**
Given two declared squads, compute per-rubber win probabilities from R6 and return the expected tie outcome for each legal order assignment, ranked. This is the killer feature for team captains and a direct, defensible use of data the platform already holds.

### 4.5 Analyst workflow

**R13 — Analyst dashboard replacing the current `Resum`.**
Watchlist (follow players, teams, competitions), latest-round results for followed entities, biggest upsets by rating differential, rating movers, and upcoming fixtures. Turn the front door into a reason to return.

**R14 — Export and public API.**
CSV/JSON export on every table, PNG/SVG on every chart, and a documented, rate-limited read API with stable entity IDs — consistent with the platform's stated "open data" positioning.

**R15 — Methodology and glossary page.**
Define every metric, state the rating algorithm and its parameters, and document source coverage and known limitations. Non-negotiable for analyst trust, and cheap to produce.

**R16 — Prediction layer.**
Per-rubber and per-tie win probabilities, season simulation (Monte Carlo over remaining fixtures) yielding promotion/relegation/title probabilities, with calibration published openly (Brier score, reliability curve). This is the advertised "AI analytics" promise, and it should be delivered as a *calibrated statistical model first*, with LLM support reserved for natural-language explanation of model output rather than for the inference itself.

---

## 5. Prioritisation Matrix

**Impact** = analytical value unlocked for the target user. **Feasibility** = inverse of effort/risk given data already held (High = cheap, Low = expensive).

| ID | Recommendation | Impact | Feasibility | Priority | Depends on |
|---|---|---|---|---|---|
| R3 | Fix identified defects | Medium | **High** | **P0** | — |
| R4 | Point-level metric suite (existing RFETM data) | **Very high** | **High** | **P0** | R2 (for gating) |
| R1 | Canonical player identity resolution | **Very high** | Medium | **P0** | — |
| R15 | Methodology & glossary page | Medium | **High** | **P0** | — |
| R6 | Glicko-2 / TrueSkill rating per rubber | **Very high** | Medium | **P1** | R1 |
| R8 | Competition pages with standings | **High** | Medium | **P1** | — |
| R2 | Data completeness & freshness surface | High | Medium | **P1** | — |
| R14 | Export + public read API | High | Medium | **P1** | R1 |
| R9 | Team entity pages | High | Medium | **P1** | R8 |
| R7 | Opponent-adjusted performance metrics | High | Medium | **P2** | R6 |
| R11 | Comparison tool + leaderboards | High | Medium | **P2** | R1, R6 |
| R10 | Head-to-head pages | Medium-high | Medium | **P2** | R1 |
| R13 | Analyst dashboard + watchlist | Medium-high | Medium | **P2** | R8 |
| R5 | Ingest BCNESA point scores & doubles pairs | High | Low | **P2** | source dependent |
| R12 | Line-up optimiser | **Very high** | Low | **P3** | R6, R9 |
| R16 | Prediction & season simulation | High | Low | **P3** | R6, R8 |

### 5.1 Suggested sequencing

**Wave 1 — "Make it trustworthy" (P0).**
R3 + R1 + a first cut of R2 + R15. Nothing new is promised; what already exists becomes believable. R1 is the gate: every subsequent metric depends on a correct player denominator.

**Wave 2 — "Make it analytical" (P0/P1).**
R4 (the cheapest large win on the board — the data is already in the database), then R6, then R8 and R9. At the end of this wave the platform has opponent-adjusted ratings, real standings, team entities and rally-level metrics: it becomes an analytics product rather than a results archive.

**Wave 3 — "Make it comparative and open" (P1/P2).**
R7, R11, R10, R13, R14. This is the wave that produces recurring usage and lets external analysts build on top.

**Wave 4 — "Make it predictive" (P3).**
R12 and R16, with published calibration. The line-up optimiser (R12) should be prioritised over generic prediction: it is more defensible statistically, more differentiated competitively, and answers a question real users ask every week.

---

## 6. Conclusion

TT League has done the hard, unglamorous part well. It has assembled a **multi-source, seven-season, multi-tier corpus** — 8,428 players, 490 clubs, 37,036 matches across RFETM, FCTT and BCNESA — with a clean three-entity information architecture, a consistent filter idiom, and several touches (home/away splits, form strings, singles/doubles separation, opponent-difficulty bucketing) that already speak an analyst's language. The `Anàlisi d'oponents` tab in particular is better than what most federation sites offer.

What it has not yet done is **turn that corpus into analysis**. Three findings dominate everything else:

1. **Player identity is fragmented across sources.** Thirteen `WEISZ` records represent roughly seven people, and duplicates appear even within a single club roster. Every career statistic on the site is therefore computed over a fragment of a career. This invalidates the platform's own claim of showing a *"canonical identity"* and must be fixed before any other metric can be trusted.

2. **Point-level data is already in the database and completely unused.** RFETM match sheets carry full per-game point scores (`7-11 / 10-12 / 13-11`), and not one derived statistic — deuce record, comeback rate, points won %, expected vs actual — appears anywhere in the product. This is the largest pool of unrealised value on the site and requires no new ingestion.

3. **There is no opponent adjustment and no competition context.** Win percentages span `Super Divisió` to `Quarta` and veteran categories with no rating, no strength of schedule, and no standings table — despite standings being advertised on the landing page. Raw win% across incomparable tiers is not an analytical metric.

The encouraging read is that all three are addressable with data the platform already holds. The recommended path is therefore sequential rather than expansive: **fix identity and defects, then mine the point data, then add ratings and standings, and only then build the predictive layer the landing page promises.** Delivering that layer as a calibrated statistical model — with the LLM explaining model output rather than generating the inference — is what would separate TT League from a results archive with a chatbot attached.

The foundation is genuinely good. The gap is not data; it is derivation.

---

*Scope note: `Configuració` (`/settings`) and administrative sections were excluded as specified. Findings are based on the authenticated `test` account session on 2026-09-20.*
