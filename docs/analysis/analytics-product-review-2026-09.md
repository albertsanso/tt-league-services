# TT League — Analytics Product Review

**Date:** 2026-09-19
**Reviewed build:** `v1.0.0-alpha.1` at https://ttleague.org (account `test`)
**Reviewer perspective:** table-tennis performance analyst (player/team/match metrics, predictive analytics)
**Scope:** public/consumer surfaces only — Resum, Cerca de clubs, Cerca de jugadors, Cerca de partits, club/player/match detail pages and their tabs. Configuration and Administration were explicitly excluded.

---

## 1. Executive summary

TT League already has the two things that are hardest to get in amateur table tennis: **a multi-federation historical corpus** (8,428 players, 490 clubs, 37,036 ties, 7 seasons, sources BCNESA / FCTT / RFETM) and **acta-level granularity** (individual games A/B/C vs X/Y/Z, lineup letters, doubles slots, and — for RFETM — point-by-point set scores). That is a genuinely strong analytical base. Most public federation sites stop at the tie score.

What is missing is the layer that turns records into *analysis*:

1. **No rating system.** Every number on the site is an unadjusted win percentage. A 91% record in `Vet 3a _A_` and a 41% record in `divisio-honor-masculino` are displayed as directly comparable, which they are not. Without an opponent-strength-adjusted rating there is no cross-division comparison, no expected result, no prediction, and no "is this player improving or just playing weaker opponents?".
2. **No league context.** There is no standings table, no jornada view, no fixture list, no promotion/relegation position. A club page can tell me we went 19-25 in Tercera _B_ but not where that put us.
3. **No sample-size discipline.** Raw percentages are shown from n=1. The opponent classifier labels a single win as "Favorable". Every leaderboard on the club page is therefore dominated by tiny samples (the "Millor rendiment" card shows four 100% players, the top one on 21 games, with no minimum threshold).
4. **The player page conflates the player with their team.** "Partits jugats 75 / Victòries 56%" on a player page are *team tie* results. The player's own individual record (40% singles, 30% doubles) is shown underneath as a secondary stat. For an analyst this is the wrong way round and actively misleading.
5. **Point-level data is imported but almost never surfaced.** `SetScore(homePoints, awayPoints)` exists in the domain and renders in RFETM actas (11-7, 12-10, 13-11). Nothing anywhere aggregates it. The single richest analytical asset in the database is write-only.
6. **Entity resolution is incomplete and it is the blocking dependency for everything else.** The same human appears as `BARRAU Anna`, `ANNA BARRAU ESTEVEZ` and `ANNA BARRAU ESTÉVEZ`; `DANIEL CARACUEL BAHM` and `DANIEL CARACUEL BÖHM`; `BIEL AGUIRRE EGIDO` is listed twice in the same list. Any career metric, any rating, any H2H is wrong until this is fixed.

The good news: `MatchSummaryPage` (`/partits/:matchId`) already demonstrates the target quality level — team form with trend, per-player recent form, and **lineup stability with a comparison against team baseline**. That page is the template. It is also effectively unreachable from the UI (match search only opens the acta modal), which is a 30-minute fix for a large perceived-quality gain.

**Headline recommendation:** land entity resolution + a Glicko-2/TTR-style rating on individual games first (P0). Everything an analyst actually wants — expected results, lineup optimisation, tie simulation, strength of schedule, promotion probability — is a derivative of those two, and none of it is buildable without them.

---

## 2. What was crawled

| Surface | Route | Tabs / panels inspected |
|---|---|---|
| Overview | `/` | global search, quick access, community counters, "Analítica avançada" teaser |
| Club search | `/clubs` | name search |
| Club detail | `/clubs/:id` | Resum, Estadístiques, Jugadors, Partits + source/season/competition filters |
| Club competition | `/clubs/:id/competition/:season/:competition` | match list |
| Player search | `/jugadors` | name search |
| Player detail | `/jugadors/:id` | Estadístiques, Partits (+ expandable Jocs), Anàlisi d'oponents (+ expandable H2H), Historial estadístic |
| Match search | `/partits` | source/season/competition/date/location/player/club filters, acta modal (BCNESA + RFETM) |
| Match summary | `/partits/:matchId` | team form, lineups, alignment stability |
| Search results | `/cerca` | (stub) |
| — | mobile 375×812 | layout verification |

Excluded per instruction: `/settings`, `/administration/*`, club edit, user/role management, import.

---

## 3. Feature inventory — what already works

Credit where due. The following are implemented and are genuinely useful:

**Data model & coverage**
- Three federations unified under one schema with source attribution visible in the UI (`Fonts: BCNESA, FCTT, RFETM`).
- Canonical vs federated identity is modelled (`Player` ↔ `FederatedPlayer` ↔ `PlayerSeason`; `Club` ↔ `FederatedClub` ↔ `Team`), and the player search page explicitly promises "identitat canònica".
- Acta-level detail: lineup letters, game order, crossover, cumulative tie score, doubles slots, not-played games with reason, `protested` flag, venue, referee.
- Point-level `SetScore` for RFETM.

**Club detail**
- Source / season / competition filter triad, applied consistently across all four tabs.
- Per-competition, per-season W/L balance table (Estadístiques) — this is a good primitive.
- Home/away win-rate split.
- Current form strip and streak.
- "Partits destacats": closest result, widest win, widest loss.
- Per-source match availability counts.

**Player detail**
- Career summary with current streak, longest win streak, **singles vs doubles win rate split**, average set margin, average score.
- Dual-axis season chart (matches played + win %) and a "result spectrum" chronological chart.
- Season-by-season statistical history table.
- Paginated match list with expandable individual games showing opponent and set score (3-0, 3-1…).
- **Opponent analysis**: 146 opponents, sortable by win %, matches, last meeting, closeness; per-opponent expandable H2H with average set margin ("Competitiu ±1.5"), singles/doubles split, home/away split, best/worst streaks, time since last meeting, matches per season, trend, and per-competition breakdown. **This is the strongest analytical feature on the site.**

**Match summary (`/partits/:matchId`)**
- Last-5 form strip per team with record and win rate.
- Recent-form trend vs the preceding window.
- Lineup table with per-player recent form and win rate.
- **Alignment stability**: how often this exact trio has played together this season, its record when it does, and whether that is above or below the team baseline. This is a real, non-obvious, coach-useful metric and I have not seen it on any comparable site.

**Cross-cutting**
- Clean responsive layout (mobile season filter becomes a slider — nice touch).
- Deep-linkable filter state in the query string.
- i18n scaffolding for ca / es / en with well-written metric help text.
- Accessible landmarks, `tablist`/`tabpanel` roles, `aria-label`s on charts.

---

## 4. Gap analysis vs state-of-the-art table-tennis analytics

Assessed against what is standard practice in federated table tennis (DTTB **TTR**, ETTU/ITTF rating methodology, Glicko-2 in club leagues) and in match analytics generally.

### 4.1 Ratings and strength adjustment — **absent, highest impact**

Nothing on the site adjusts for opponent quality. Consequences visible in the crawl:

- Club Estadístiques shows `Vet 3a _A_ 20V·2D 91%` next to `divisio-honor-masculino 9V·13D 41%` with no indication that these are different universes.
- The player season chart plots win % over time, so a player who is promoted looks like they got worse.
- Opponent classification (`Favorable` / `Difícil` / `Problemàtic`) is computed from raw H2H counts, not from the gap between expected and actual result.

**Recommendation — implement a rating engine over *individual games*, not ties.**

- **Model:** Glicko-2 (rating μ, deviation φ, volatility σ) or a TTR-style Elo variant. Glicho-2 is preferred here because amateur players have long inactivity gaps and very uneven game counts, and φ explicitly encodes "we don't know yet" — which is exactly the sample-size problem in §4.3.
- **Unit of observation:** one singles game (best-of-5 sets). Doubles gets a **separate** rating scale (pair-level and/or an individual doubles rating); mixing them is a known source of distortion.
- **Margin awareness:** weight the update by set margin (3-0 vs 3-2) and, where point data exists, by points ratio. A 3-2 win over a stronger opponent is different information from a 3-0.
- **Cross-source calibration:** BCNESA, FCTT and RFETM pools overlap only partially. Fit a per-source/per-division offset (or run one joint pool and let the shared players bridge it) and *report the offset and its uncertainty*. Do not silently merge scales.
- **Time decay:** σ inflation during inactivity; optionally a half-life on old results (2 seasons is a reasonable default for veterans' categories).
- **Outputs to surface:** current rating + confidence band, rating history sparkline per player, peak rating and when, rating percentile within division, and a **club rating** (weighted mean of fielded players' ratings).

Also: `Lineup.ranking` (`Float`) exists in the domain and in `LineupDto`, and the acta renders a `Rk:` column — it is `—` in every record I inspected, in both BCNESA and RFETM actas. Either backfill the official ranking from the source, or replace that column with the computed rating. An always-empty column is worse than no column.

### 4.2 Prediction and expectation — **absent**

There is no forward-looking number anywhere. The homepage promises an AI analytics layer "in later phases"; the classical statistical layer should come first, because an LLM layer on top of unrated, unresolved data will confidently produce nonsense.

Minimum viable predictive set, all derivable once §4.1 lands:

| Metric | Definition | Where it belongs |
|---|---|---|
| Game win probability | Logistic on rating difference, home/away term | Acta, H2H panel |
| Expected tie score | Monte Carlo over the actual game order (Swaythling 3v3 A/B/C×X/Y/Z, or the applicable format) | Match summary, pre-match |
| Tie win probability | Same simulation, aggregated | Match summary |
| Expected vs actual (points above expectation) | Σ(actual game wins − P(win)) per player per season | Player detail — *the* single most useful analyst metric |
| Strength of schedule | Mean opponent rating faced, vs division mean | Player + club |
| Opponent-adjusted win rate | Win rate a league-average player would have had against the same slate | Club Estadístiques, replaces raw % |
| Promotion / relegation probability | Season simulation from current standings + ratings | Standings page (§4.4) |

The **expected tie score** is worth calling out. Because the game order is fixed and known (the BCNESA acta I inspected ran A-Y, B-X, C-Z, A-X, C-Y, B-Z, then doubles), a tie is a small, exactly simulable object. A pre-match "we are 38% to win this tie, and the swing game is C vs Z" is achievable with ~200 lines of simulation code and is the kind of output that makes a site indispensable to a captain.

### 4.3 Sample-size and uncertainty handling — **absent, and actively producing wrong conclusions**

Evidence from the crawl:

- Club → Resum → "Millor rendiment (Jocs en brut)" lists four players at 100%: 21-0, 14-0, 12-0, 6-0. No minimum. The card title admits it ("en brut" / raw) but the card is still presented as a leaderboard.
- Player → Anàlisi d'oponents: `ACERO Gregorio — Favorable — 1 played — 100.0%`. A one-game sample is given a category label and a "Ratxa de 1 victòries".
- Every percentage on the site is a point estimate with no interval.

**Recommendations**

1. **Shrink every displayed rate.** Empirical-Bayes / beta-binomial shrinkage toward the relevant prior (division mean, or the player's own rating-implied expectation). Show the shrunk value as the headline and the raw count beside it.
2. **Show intervals** where a rate drives a ranking — Wilson or Jeffreys, not normal-approximation (it breaks exactly at the 0% and 100% cases this dataset is full of).
3. **Minimum-n gates on all leaderboards**, with the threshold stated in the UI ("mínim 10 jocs"), and an explicit "insufficient sample" state rather than silently including n=1.
4. **Replace the opponent classifier.** The current logic (`PlayerDetailPage.jsx:701`) is:
   ```
   wins > losses            → favorable
   wins == losses           → uncategorized
   winPct <= overall − 20   → problem
   otherwise                → hard
   ```
   This is why the crawl showed `Favorable 57 / Difícil 0 / Problemàtic 85` — with `overall` at 40%, nearly every losing matchup clears the −20pt threshold and `hard` is starved. It also can't distinguish "I lose to this player because they're better" from "I underperform against this player specifically". Recommended replacement, once ratings exist: classify on **actual minus rating-expected win rate**, with a shrunk estimate and an explicit `sense dades suficients` bucket. That makes "Problemàtic" mean *bogey opponent* (you do worse than your rating predicts) rather than *stronger than you*, which is what an analyst is looking for.

### 4.4 League context: standings, fixtures, jornada — **absent**

The main menu is Resum / Clubs / Jugadors / Partits / Analytics (Aviat) / Configuració. There is **no competition entity in the UI**. You cannot answer "who is top of Tercera _B_?", "what's the fixture list for jornada 23?", or "are we in a relegation place?".

`/clubs/:id/competition/:season/:competition` comes closest but it lists one club's matches from both its A and B teams interleaved, with no team separation and no table.

**Recommended additions**
- A `/competicions` section: competition → season → group, with a **standings table** (played, W/D/L, games for/against, sets for/against, points, position, form), a **jornada-by-jornada fixture/result grid**, and a **cross-table** (home team × away team result matrix).
- Tiebreaker rules made explicit per competition (they differ between BCNESA and RFETM, and the codebase already has a `TieEligibleCompetitions` concept and a `tieEligible` flag reaching the UI — surface it).
- Position-over-time chart per team.
- Promotion/relegation probability once §4.2 lands.
- **Team entity pages.** `CTT ELS AMICS TERRASSA A` and `... B` are distinct competitive units and are currently only strings inside match rows. They need their own page with roster, standings position, lineup history and alignment stability aggregated across the season (the per-match version already exists).

### 4.5 Point-level analytics — **data exists, zero exploitation**

RFETM actas carry full set scores. From one acta in the crawl: `8-11 11-9 11-5 11-4`, `11-7 12-10 13-11`, `11-2 11-0 11-0`. BCNESA/FCTT actas have the J1–J5 columns rendered but empty.

This unlocks a whole tier of metrics that no comparable site offers:

| Metric | Definition | Why an analyst wants it |
|---|---|---|
| Points won ratio (PWR) | points won / points played | Far lower variance than win % — usable from ~5 games where win % needs ~30 |
| Expected win % from PWR | Pythagorean-style mapping PWR → win % | Identifies over/under-performers relative to their point-level quality |
| Close-set conversion | record in sets decided by ≤2 points (incl. deuce) | The standard proxy for clutch/nerve in table tennis |
| Deuce record | record in sets reaching 10-10 | Same, sharper |
| Decider record | record in 5th sets | Same, at game level |
| Comeback / collapse rate | games won from 0-2 down; games lost from 2-0 up | Mental-resilience signal |
| Set-lead conversion | P(win game \| won set 1) vs baseline | Fast-start vs slow-start profile |
| Average points per set (for/against) | mean, and spread | Dominance profile — 11-3 wins vs 11-9 wins |
| Blowout / grind split | share of games at 3-0 or 0-3 | Style descriptor |
| Tie-clutch | performance in games played at 3-3 or otherwise decisive | Team-league-specific, high coach value |

Two prerequisites:
- **A per-source, per-season data-completeness matrix**, exposed in the UI (see §5.4). Point-level metrics must be visibly gated on availability, or users will read a RFETM-only PWR as a career number.
- Investigate whether BCNESA/FCTT actas carry set scores at source. If they do, the importer is dropping them and that is a high-value P1 fix. If they don't, say so in the UI.

### 4.6 Lineup optimisation — **the highest-value feature not on any roadmap**

Amateur team table tennis is, mathematically, an **assignment problem**. In a Swaythling 3v3 the captain chooses which player takes A, B and C; the opponent chooses X, Y, Z; the pairing schedule is fixed; the tie is decided by 6 (or 9) game outcomes. Given per-player ratings, the optimal ordering is solvable exactly (Hungarian algorithm over expected game wins, or brute force — it's only 6 permutations for 3 players, and even a 4-player squad with a doubles slot is trivially enumerable).

This is the one thing on this list that would make a club captain open the site every week:

- **Pre-tie lineup optimiser**: given our available squad and the opponent's likely lineup, rank all orderings by expected tie score and P(win).
- **Post-hoc lineup audit**: for each played tie, what would the optimal ordering have scored? Cumulative "points left on the table" per season.
- **Opponent lineup prediction**: from their alignment-stability history (already computed!) plus availability, predict their A/B/C.
- **Doubles pairing synergy**: pair win rate vs the rate predicted from the two individual ratings. Identifies pairs that over- or under-perform their parts. The domain already has `DoublesPair`.
- **Marginal value of a player**: ΔP(win tie) from swapping player i in for player j — a defensible "most valuable squad member" number.

Alignment stability, already on `MatchSummaryPage`, is the first brick of this. The rest is one rating model away.

### 4.7 Comparison, cohorts and discovery — **absent**

- **No player-vs-player comparison view.** An analyst's most basic tool. Two (or N) players side by side: rating history, PWR, singles/doubles split, home/away, form, common opponents, and the H2H if it exists.
- **No league-wide leaderboards.** There is no way to ask "who are the top 20 in Segona _A_ this season by win rate / rating / PWR". All leaderboards are club-scoped.
- **No filtering by cohort**: age/veteran category, gender category, division tier, licence vintage. The competition slugs encode `masculino` / `femenino` / `Vet 1a`–`Vet 4a` but nothing parses them into structured dimensions.
- **No club-vs-club comparison**, and no club-level H2H history (only per-player).
- **Discovery is search-only.** `/clubs` and `/jugadors` render "Escriu el nom … per començar la cerca" and nothing else. With 490 clubs and 8,428 players there should be a browsable directory: by division, by comarca/province, by federation, A–Z, by activity. Right now a user who doesn't already know a name cannot get in.
- **No global search.** `/cerca` returns literally `El mòdul de resultats s'integrarà amb /api/cerca en fases posteriors.` The homepage's most prominent control is a dead end. A `GlobalSearchController` exists server-side.

### 4.8 Missing dimensions in the data model

| Missing | Analytical cost |
|---|---|
| Date of birth / age | No age curves, no youth-development tracking, no age-adjusted rating, no verification of veteran-category eligibility |
| Playing hand, grip, style (attacker/blocker/defender), rubber | The single biggest driver of style-matchup effects in table tennis. Even self-reported, this would enable "you underperform vs left-handers / vs long-pips" — a killer feature and a well-known real effect |
| Gender / category as a structured field | Currently only inferable from competition slugs |
| Serve/receive indicator per point | Not in federation actas; would require manual capture. Out of scope, but note that without it "dominance ratio"-style serve metrics are impossible |
| Match time of day, table number | Minor scheduling effects; `dateTime` exists but BCNESA imports land at 00:00:00 |
| Injury / availability / unavailability reason | `Game.notPlayed` + `reason` exist per game; there is no player-level availability model |
| Official ranking / licence points | `Lineup.ranking` exists but is unpopulated everywhere |
| Venue normalisation | `venue` is free text; one RFETM acta listed a Cartagena venue for a Utebo home tie, which is worth a data-quality rule |

### 4.9 Data export and programmatic access — **absent for analysts**

There is no CSV/Excel export, no chart download, no public API documentation surfaced in the UI, and no bulk-data page. An analyst's first instinct on any of these tables is "give me this as a dataframe". A REST API (`/api/v1/...`), a GraphQL module and an MCP module all exist in the repo — expose at least a documented read-only slice plus per-table CSV export. This is cheap and disproportionately increases the site's value to exactly the audience this review represents.

---

## 5. Defects and data-quality issues found

Each item was observed in the crawl; root cause is given where I traced it in the code.

### 5.1 Entity resolution — P0, blocks all career analytics

`CTT ELS AMICS TERRASSA` → Jugadors tab reports **139 players**, and the list contains at minimum these unresolved duplicates:

| Same person, listed as |
|---|
| `BARRAU Anna` / `ANNA BARRAU ESTEVEZ` / `ANNA BARRAU ESTÉVEZ` |
| `CARACUEL Daniel` / `DANIEL CARACUEL BAHM` / `DANIEL CARACUEL BÖHM` |
| `AGUIRRE Biel` / `BIEL AGUIRRE EGIDO` / `BIEL AGUIRRE EGIDO` (twice, same spelling) |
| `MAMPEL Marc` / `MARC MAMPEL NIETO` / `MARC MAMPEL NIETO` (twice) |
| `GARCIA DE SORIA Albert` / `ALBERT GARCIA DE SORIA MIRAPEIX` / `ALBERT GARCÍA DE SORIA MIRAPEIX` |
| `CATALAN Oriol` / `ORIOL CATALAN FERNANDEZ`; `CATALÁN Roger` / `ROGER CATALÁN FERNÁNDEZ` |
| `ROCABERT Judit` / `JUDIT ROCABERT FONTANET`; `NAVACERRADA Estel` / `ESTEL NAVACERRADA SERRES` |

Two distinct problems are tangled here:
1. **Name-format divergence by source.** BCNESA uses `SURNAME First`, RFETM uses `SURNAME, First Second` (`PEREZ DALMAU, DARIO`), and some records are `FIRST SURNAME1 SURNAME2`. There is no canonical display form.
2. **Diacritic and transliteration variance** (`ESTEVEZ`/`ESTÉVEZ`, `BAHM`/`BÖHM`, `CATALAN`/`CATALÁN`) defeating exact-match linkage.

**Recommended approach**
- Normalise to NFD-stripped, case-folded, punctuation-free tokens as the blocking key; keep the source spelling for display.
- Block on licence ID first — it is present and appears stable within a source (`SANSO Albert (12087)`, `PERE FONS SANSO (41724)`). Cross-source, licence IDs collide, so pair them with the source.
- Then probabilistic linkage (Fellegi–Sunter or a simple scored rule set) on normalised surname set overlap + given-name overlap + club-season co-occurrence + competition co-occurrence.
- **Surface the merge state in the UI**: a player page should show "aquesta identitat agrupa 3 registres federats" with the list, and an analyst should be able to see the confidence. Silent merges are worse than visible unmerged records.
- Provide a review queue for the ambiguous band (there is already a clubs consolidation panel; players need the equivalent).

Also on clubs: the search for "terrassa" returned `CTT ELS AMICS DE TERRASSA1R3AA` (6 players, 1 season) alongside `CTT ELS AMICS TERRASSA` (411 players, 7 seasons). `TERRASSA1R3AA` is a parsing artefact — a category/division token concatenated onto the club name during import. Add an import-time validation rule for club names ending in division-code patterns.

### 5.2 Metric-definition defects — P0/P1

| # | Where | Observed | Problem |
|---|---|---|---|
| 1 | Player detail → Partits, and the career "Partits jugats 75 / 56%" | These are **team tie** results, not the player's results. The same player's own record is 40% singles / 30% doubles | A player's headline W/L must be their own games. Invert: individual record as headline, team record as a clearly-labelled secondary block. The per-match V/D chip should reflect the player's own game result, with the tie score shown separately |
| 2 | Club → Resum | `JUGADORS 411` | Club → Jugadors tab says `139`. 411 is federated player-season registrations, 139 canonical players. Both are legitimate numbers with different meanings; neither is labelled. Label them ("registres federats" vs "jugadors") and make them agree wherever both appear |
| 3 | Club → Resum | `411` with sub-label **`0 de sempre`** | Broken counter — placeholder/zero value shipped |
| 4 | Club → Resum | `PARTITS JUGATS 1396` with sub-label **`en 0 temporades`** | Same class of bug; the club demonstrably has 7 seasons |
| 5 | Club → Estadístiques | "Percentatge de victòries per temporada — *Encara no hi ha prou dades de temporades per mostrar una tendència*" | Shown for a club with 7 seasons and 12 competitions of data in the table directly above. Threshold logic is reading the wrong collection |
| 6 | Player → Anàlisi d'oponents | `Tots 146` but `Favorable 57 + Difícil 0 + Problemàtic 85 = 142` | 4 opponents fall in the `uncategorized` branch (`wins == losses`) and there is no chip for them — they are unreachable by filter. Add a "Sense categoria" chip, or fold the bucket in |
| 7 | Player → Anàlisi d'oponents | `Difícil 0` across 146 opponents | Classifier threshold makes `hard` near-unreachable — see §4.3.4 |
| 8 | Acta modal | `Jocs 7 / 12` (BCNESA), `Jocs 12 / 1` (RFETM) | Label/format is unintelligible. `12 / 1` for a 4-0 tie appears to be a total-sets figure rendered with a `/` separator. Needs a clear label ("Sets 12–1") and a verified computation |
| 9 | Player career summary | `Marge mitjà de sets 0.2`, `Puntuació mitjana 3.5` | Neither is defined in the UI. "Puntuació mitjana" of 3.5 in a column next to win % reads like an average set score but is unexplained. Every derived metric needs a tooltip — the i18n file already has excellent help text for the opponent insights; extend that pattern to the career block |
| 10 | Match summary | "Millora respecte als **60** anteriors (60% → 80%)" | Confirmed bug: `i18n/ca.js:284` reuses `{{previous}}` for both the prior-window *count* and the prior *percentage*: `'Millora respecte als {{previous}} anteriors ({{previous}}% → {{current}}%)'`. Needs a separate `{{count}}` placeholder |

### 5.3 Localisation and labelling defects — P1

| # | Where | Observed | Root cause |
|---|---|---|---|
| 11 | Match summary form strips | `W W W W L`, `L W W W W (80%)` in an otherwise Catalan UI | `utils/matchSummary.js:21` hardcodes `'W'`/`'L'`/`'D'` instead of using `detail.resultBadgeWin` (`'V'`) / `resultBadgeLoss` (`'D'`), which exist in `ca.js:385-386`. **This is worse than cosmetic:** `D` means *draw* in the hardcoded set but *Derrota* (loss) in Catalan. A Catalan reader will misread a draw as a loss |
| 12 | Match search | Location radios exposed as `HOME` / `AWAY` in the accessibility tree | The visible `Local`/`Visitant` text is not programmatically associated with the inputs, so assistive tech reads the raw enum values. Wire up `<label for>`/`aria-label` |
| 13 | Competition names | `divisio-honor-masculino`, `fasc-primera-divisio-masculino`, `primera-divisio-femenino`, `tercera nacional` displayed raw next to human names like `Preferent`, `Tercera _B_`, `Vet 3a _A_` | Raw source slugs leaking into the UI. Needs a competition display-name registry, plus parsing into structured dimensions (tier, gender, veteran band, phase) — which §4.7 also requires |
| 14 | Competition names | `Segona _A_`, `Tercera _B_`, `Vet 4a _B_` | Underscore-delimited group markers are an import artefact. Should render `Segona A` / group = `A` |
| 15 | Dates | BCNESA matches render `10/5/2026, 0:00:00` | Midnight placeholder time displayed as if it were a real start time. RFETM has genuine times (`10:00:00`, `18:30:00`). Suppress the time component when it is unknown, and add a "time not available" state |
| 16 | Acta modal | `Lloc: No disponible`, `Àrbitre: No disponible`, `Rk: —` for every BCNESA record | Fields are present in `MatchDetailDto` (`city`, `venue`, `refereeName`, `Lineup.ranking`) but unpopulated for this source. Hide unavailable fields per source rather than rendering a wall of "No disponible" |

### 5.4 Data-completeness transparency — P1

Availability differs sharply by source and the UI does not say so:

| Source | Ties (this club) | Point-level set scores | Venue / referee | Match time |
|---|---|---|---|---|
| BCNESA | 1,060 | ✗ (J1–J5 empty) | ✗ | ✗ (00:00) |
| FCTT | 22 | not verified | not verified | not verified |
| RFETM | 314 | ✓ | ✓ | ✓ |

An "all sources" aggregate silently mixes these. **Add a coverage/completeness view**: per source × season × competition, what fraction of ties, games, set scores, venues and times are present, with a last-import timestamp. Then gate every derived metric on it and badge metrics that are only computable on a subset. This is a prerequisite for trusting anything in §4.5, and it is the sort of thing that distinguishes a data product from a scraper.

Also flagged: one RFETM acta (`RIVER ANTIQUES UTEBO TM` home) listed `PABELLÓN MUNICIPAL CUATRO CANTOS … Cartagena, Murcia` as the venue. Whether that is a source error or a mapping error, a "home venue consistency" validation rule would catch it.

### 5.5 Navigation, performance and UX — P1/P2

| # | Issue |
|---|---|
| 17 | **`/partits/:matchId` is unreachable from the UI.** Match search only opens the acta modal; the acta modal has no link to the summary. The site's best analytical page is deep-link-only. Add "Veure resum" to the modal and to every match row |
| 18 | **N+1 request fan-out on club detail.** The network log shows one `GET /api/v1/club/{id}/competition/{season}/{competition}` per season×competition pair — 20+ requests for a single page load. Add a batch endpoint or a pre-aggregated club-summary read model |
| 19 | **Location filter cannot be cleared.** `MatchesSearchPage.jsx:211` renders the `Local` radio as `checked={filters.playerLocation !== 'AWAY'}`, so with the default empty value the UI shows `Local` selected while no filter is applied — and once a user picks one there is no way back to "both". Add an explicit "Totes" option and make the checked state reflect the real value |
| 20 | Match search requires a source before season and competition populate, with no indication that this is the required order. Progressive disclosure should be explained or removed |
| 21 | Club/player search have no browse fallback (see §4.7) and no empty-result guidance beyond the minimum-2-characters hint |
| 22 | Season filter is single-select; no multi-season range, so "last 3 seasons" is impossible |
| 23 | Notification bell reports "3 notificacions pendents" and opens nothing |
| 24 | Homepage "Analítica avançada → Més informació" links to `/settings`, which is unrelated |
| 25 | Club → Partits tab shows aggregates and highlights but no actual match list, while the tab is named "Partits". The list lives one level down under a competition |
| 26 | `/clubs/:id/competition/:season/:competition` interleaves the club's A and B team fixtures with no team column, so the same jornada appears twice with different opponents |
| 27 | "Partits destacats" (closest / widest win / widest loss) appears to draw only from the most recent season even under "Totes les temporades" — all three examples on a 7-season club page were from 2025-2026. Worth verifying; if intended, label the window |
| 28 | No dedicated match-detail *route* from the acta (modal only) means no shareable link to a specific tie from the search flow |
| 29 | `v1.0.0-alpha.1` and "Aviat" badges are honest, but there is no changelog or data-freshness indicator anywhere. Add "dades actualitzades el …" per source |
| 30 | i18n supports ca/es/en but no language switcher is visible outside the user menu; the en/es bundles are partially just spreads of `ca` |

---

## 6. Prioritised roadmap

Ordered by (analyst value) ÷ (effort), with hard dependencies respected.

### P0 — Foundations. Nothing else is trustworthy without these.

1. **Player entity resolution.** §5.1. Licence-ID blocking + normalised-name probabilistic linkage + a review queue + visible merge provenance on the player page. *Unblocks every career, H2H and rating metric.*
2. **Club/team name cleanup + team entities.** Fix the `TERRASSA1R3AA` class of import artefacts; promote `CLUB A` / `CLUB B` to first-class team entities. §4.4.
3. **Fix the player/team metric conflation.** §5.2.1 — individual record becomes the player headline.
4. **Rating engine (Glicko-2 over individual games), with separate singles and doubles scales and per-source calibration.** §4.1. *Unblocks all of §4.2, §4.3 and §4.6.*
5. **Uncertainty discipline pass.** §4.3 — shrinkage, Wilson intervals, minimum-n gates on every leaderboard, rebuilt opponent classifier.
6. **Counter and threshold bug fixes.** §5.2 items 3, 4, 5, 6, 10 and the `W/L`→`V/D` localisation bug (§5.3.11). Small, visible, cheap.

### P1 — The features that make the site indispensable.

7. **Competitions section with standings, fixtures by jornada, and cross-tables.** §4.4. Probably the single most-requested thing by ordinary users, and independent of the rating work.
8. **Expose `/partits/:matchId` properly**, and extend it with expected tie score and P(win) once ratings exist. §5.5.17, §4.2.
9. **Global search.** §4.7 — the homepage's primary control currently does nothing.
10. **Browsable directories** for clubs and players. §4.7.
11. **Data-completeness matrix**, per source × season × competition, with freshness timestamps and metric-availability badges. §5.4.
12. **Point-level metrics for RFETM** (PWR, close-set conversion, deuce record, decider record, comeback rate), clearly gated on availability. §4.5. Simultaneously: investigate whether BCNESA/FCTT set scores are recoverable at source.
13. **Player comparison view** (2–4 players side by side). §4.7.
14. **Competition metadata registry** — display names, tier, gender, veteran band, phase, group, format (3v3 Swaythling vs 4v4 vs doubles-inclusive), tiebreaker rules. §5.3.13. *Prerequisite for §4.6 simulation and for cohort filtering.*
15. **CSV export + documented read-only API.** §4.9.

### P2 — Differentiators.

16. **Lineup optimiser and post-hoc lineup audit.** §4.6. The highest-ceiling feature on this list; depends on 4 and 14.
17. **Tie Monte-Carlo simulation** surfaced pre-match and as season projections (promotion/relegation probability). §4.2.
18. **Doubles pairing synergy** (observed vs expected pair rate). §4.6.
19. **Strength of schedule and opponent-adjusted win rate** replacing raw % on club Estadístiques. §4.2.
20. **Expected-vs-actual ("points above expectation") player leaderboards**, league-wide and per division. §4.2.
21. **Extended player attributes** — DOB/age, handedness, style, rubber — with age curves and style-matchup analysis. §4.8. Needs a data-collection strategy (federation feed or player self-service), so start the conversation early.
22. **Career milestones and records** (most games, longest streaks, biggest upsets by rating gap, most improved). Cheap once ratings exist, and high engagement.
23. **The AI/LLM layer promised on the homepage** — natural-language querying over the metric layer, and narrative match/season summaries. *Deliberately last.* An LLM on top of unresolved identities and unadjusted percentages will produce fluent, confident, wrong analysis. On top of a calibrated rating model with explicit uncertainty it becomes genuinely powerful.

---

## 7. Appendix — metric definitions to adopt

Proposed canonical definitions, so that the same word means the same thing in every panel. Worth putting in a public glossary page; the i18n file's existing opponent-insight help text is a good model for the tone.

**Units.** *Set* = to 11. *Game* (Catalan: *joc*) = one singles or doubles encounter, best of 5 sets. *Tie* (Catalan: *partit*) = one team fixture, 6–9 games. The site currently uses *partit* for both a tie and, in places, a player's game — this must be disambiguated before any of the below is meaningful.

| Metric | Definition | Notes |
|---|---|---|
| Game win % | games won / games played, by the player | Split singles / doubles always. Shrunk for display |
| Tie participation | ties fielded in / ties available | Availability signal, distinct from win % |
| Sets ratio | sets won / sets played | Lower variance than game win % |
| Points won ratio (PWR) | points won / points played | Requires set scores. Lowest-variance quality estimate available |
| Rating (Glicko-2) | μ with deviation φ | Separate singles/doubles. Always display the confidence band |
| Expected game win % | mean of P(win) over games actually played | The strength-of-schedule-adjusted baseline |
| Points above expectation | Σ(actual − expected) game wins | The primary over/under-performance metric |
| Opponent-adjusted win % | win % a league-average player would post against the same slate | Replaces raw % for cross-division comparison |
| Strength of schedule | mean opponent rating faced, vs division mean | |
| Set margin | (sets won − sets lost) per game | Already on the site as "Marge mitjà de sets"; needs a definition tooltip |
| Close-set conversion | record in sets decided by ≤2 points | |
| Deuce record | record in sets reaching 10-10 | |
| Decider record | record in 5th sets | |
| Comeback rate | games won from 0-2 down / games trailing 0-2 | |
| Collapse rate | games lost from 2-0 up / games leading 2-0 | |
| Form (EWMA) | exponentially-weighted recent rating change | Replaces last-5 strips, which are noisy and window-arbitrary |
| Home advantage | model-estimated home coefficient, per competition | The current raw 60%/51% split is confounded by fixture strength |
| Alignment stability | ties this exact lineup has been fielded, and its record | **Already implemented** — keep, and aggregate to season level |
| Pair synergy | pair win % − win % predicted from the two individual ratings | |
| Marginal player value | ΔP(win tie) from substituting player i for j | Needs the simulator |
| Bogey index | actual − expected win % against a specific opponent, shrunk | Replaces the current `Favorable`/`Difícil`/`Problemàtic` classifier |

---

## 8. Closing assessment

TT League is not a thin scraper — the acta-level model, the canonical-identity design, the opponent-insights panel and the alignment-stability metric all show real analytical intent, and the last two are ahead of what comparable federation sites offer. The gap is not ambition; it is that the statistical foundation (identity resolution, opponent-strength adjustment, uncertainty handling) has not been laid, and a significant amount of already-imported data — point scores, venues, rankings, doubles pairs — is never read back out.

Fix the foundation first, in the order above. The AI layer advertised on the homepage will be worth far more once there is a calibrated model underneath it to be intelligent about.
