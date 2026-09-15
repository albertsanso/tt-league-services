# Build Plan
1. **Add `TieEligibleCompetitions` allowlist** in
   `tt-data-league-core-domain/src/main/java/.../domain/shared/model/TieEligibleCompetitions.java`:
   - A normalized (trim + case/accent-insensitive) exact-match `Set<String>`
     containing the confirmed allowlist:
     `super-divisio-femenino`, `super-divisio-masculino`,
     `fasc-super-divisio-femenino`, `fasc-super-divisio-masculino`.
   - A single method `isTieEligible(String competition)`.
   - Unit test covering: exact match (all four values), differing
     case/accent, unknown competition, `null`/blank input.
   - Verify these four values match the actual `Match.competition` strings
     stored in the database/import fixtures before wiring the allowlist in;
     adjust normalization (case, accents, hyphenation) if the stored values
     differ in form from the confirmed list.

2. **Add a shared match-outcome helper** (e.g.
   `domain/shared/model/MatchOutcome.java`) to replace the ~6 duplicated
   `winnerTeam == null` checks found across handlers:
   - `teamOutcome(Match match, Team perspective)` → `Optional<Outcome>`
     where `Outcome` is `WIN` or `LOSS` when there is a winner; `DRAW` when
     there is no winner AND `TieEligibleCompetitions.isTieEligible(match.getCompetition())`;
     and `Optional.empty()` when there is no winner and the competition is
     NOT tie-eligible — callers must treat an empty result as "this match
     does not exist" for stats and match-record display purposes (excluded
     from counts, excluded from match lists).
   - `playerOutcome(...)` → `WIN` / `LOSS` only; never returns `DRAW` or
     empty for a played match, regardless of competition (draws never
     apply at player level per acceptance criterion #1, including for the
     two tie-eligible competitions).
   - Unit tests for both methods across eligible/ineligible competitions
     and null-winner/non-null-winner matches, including the "excluded"
     case.

3. **Update backend query handlers** to use the new helper and to fully
   drop excluded matches from both stats and match-record lists (not just
   remove a "draw" label):
   - `application/player/find/FindPlayerDetailsQueryHandler.java`:
     `toMatch` (line ~179) and `toGame` (line ~231) — never label a
     player-facing match/game as `"draw"`; use `playerOutcome`.
     `toStatistics` (~279-309) already excludes draws — keep as is, just
     route through the shared helper for consistency.
   - `application/match/find/FindMatchDetailsQueryHandler.java`:
     `resultFor` (~290) is currently shared by team and player forms —
     split call sites so the team form uses `teamOutcome` (gated by
     competition) and the player form uses `playerOutcome`.
     `alignmentStability` (~159-192, team-level "fielded together" stat)
     — matches whose `teamOutcome` is empty must be skipped entirely from
     both the tally and any per-match breakdown, not just from the draws
     count.
   - `application/club/find/FindClubDetailsQueryHandler.java`:
     `summarizeCompetitions` (~258-296) — matches with an empty
     `teamOutcome` must be excluded from `matchCount`, wins, losses, and
     draws for that competition (i.e. the match is invisible to stats,
     not merely undercounted for draws). `toPlayerReadModel` (~163-200) —
     drop the always-zero `draws` plumbing entirely (player-scoped,
     game-based, ties never occur here).
   - `application/club/find/FindFederatedClubDetailsQueryHandler.java`
     (~130-172) — same exclusion behavior as `summarizeCompetitions`.
   - `application/club/find/FindClubsByStringInNameQueryHandler.java`
     (~160-218) — same exclusion behavior.
   - `application/club/find/FindFederatedClubCompetitionDetailsQueryHandler.java`
     (~158-162) — matches with an empty `teamOutcome` must not appear at
     all in the returned match list for that club/competition page.

4. **Update DTOs:**
   - Remove the `draws` field entirely from player-facing read models
     (`PlayerMatchReadModel`, `PlayerSeasonStatisticsReadModel`, and the
     always-zero `draws` threaded through `FindClubDetailsQueryHandler.toPlayerReadModel`)
     — acceptance criterion #3 requires draws to be removed from player
     stats, not just zeroed.
   - Keep `draws` on team-facing DTOs; it is `0` whenever the competition
     is not tie-eligible (since excluded matches never reach the tally),
     and reflects real ties only for the four allowlisted competitions.

5. **Update the REST layer** (`tt-data-league-api-rest`) only if any
   controller/response mapping directly references the removed player
   `draws` field; otherwise no REST contract change is expected beyond the
   payload shrinking.

6. **Frontend — remove draws from player-level UI:**
   - `tt-data-league-frontend/src/pages/PlayerDetailPage.jsx`: remove the
     `'draw'` entry from `SPECTRUM_TIERS` (~45), the draw sub-labels
     (~241, 246), opponent table draw column/cells (~525, 540), the
     `addOpponent` draw counter (~740-757), the draw branch in
     `streakLabel` (~881-887), draws in `aggregateCareerStatistics`
     (~889-913), draw handling in `qualityTier`/`qualityLabel`
     (~947-981), and draw result badges (~1075-1086).
   - `tt-data-league-frontend/src/utils/matchSummary.js`: split or
     parameterize `formatRecord`/`computeRecord`/`computeHomeAwaySplit`
     (~12-53) so player-level call sites never produce a draws bucket,
     while team-level call sites (used by Club pages) keep it.
   - Remove player-only draw i18n keys (`detail.streakDraw`,
     `detail.qualityDraw`, `detail.drawsAbbrev`, and `common.draws` if
     confirmed unused by team-scoped strings) from `en.js`, `ca.js`,
     `es.js`.
   - Remove now-unused player-scoped draw CSS classes from `app.css`
     after the JS changes land (keep team-scoped ones — see step 7).

7. **Frontend — keep draws at team level for the four allowlisted
   competitions only, backend-driven:**
   - `ClubDetailPage.jsx` (~658, 667/672, 898), `ClubStatsPanel.jsx`
     (~10, 20, 28), `ClubSummaryPanel.jsx` (~28, 39, 43, 186),
     `ClubCompetitionDetailPage.jsx` (~69), `MatchSummaryPage.jsx`
     (~38, 178, 262): no logic change needed beyond verifying that tied
     matches in non-allowlisted competitions no longer appear at all in
     the match lists/records these pages render, once the backend excludes
     them (steps 2-3).
   - `ClubSummaryPanel.jsx` (~121): remove the passthrough of
     `player.resultTotals.draws` (always 0, now a removed field per
     step 4).
   - `tt-data-league-frontend/src/utils/clubSummary.js`: no logic change
     expected; verify `sumResultTotals`, `winRateOf`, `sumCompetitionResults`,
     `computeHomeAwaySplit`, `aggregateRosterByCanonicalPlayer` still behave
     correctly once upstream data has excluded-tie matches already removed.

8. **Update existing tests** to reflect the new behavior:
   - Backend: `FindClubDetailsQueryHandlerTest.java`,
     `FindMatchDetailsQueryHandlerTest.java`,
     `FindPlayerDetailsQueryHandlerTest.java`, `PlayerControllerTest.java`,
     and `ImportProcessorsTest.java` if it asserts on draw-related output.
   - Frontend: `PlayerDetailPage.test.jsx`, `ClubDetailPage.test.jsx`,
     `ClubStatsPanel.test.jsx`, `ClubSummaryPanel.test.jsx`,
     `MatchSummaryPage.test.jsx`, `clubSummary.test.js`,
     `matchSummary.test.js`, `clubs.test.js`, `players.test.js`.
   - Add new tests: `TieEligibleCompetitionsTest`, `MatchOutcomeTest`, and
     at least one handler-level test per handler in step 3 covering: a
     draw in one of the four allowlisted competitions (must display and
     count), a draw in any other competition (must be fully absent from
     both stats and match-record lists, including `matchCount`), and a
     player-level match (never draws, in any competition).

9. **Manual verification** (per repo conventions, exercise the running
   app): open a player's details page and confirm no draws appear in any
   category, including the allowlisted ones; open a club/match page for one
   of the four allowlisted competitions and confirm draws still display in
   both stats and match records; open a club/match page for a
   non-allowlisted competition (e.g. a season 25/26 category) and confirm
   tied matches are entirely absent from both stats and match-record lists.

# Implementation Guidelines

- Do not introduce a persisted category/division domain concept for this
  feature — the allowlist approach (steps 1-2) is the agreed scope; a
  structured category field is out of scope unless the exact-string
  allowlist proves unmaintainable in practice.
- Consolidate outcome computation behind the new `MatchOutcome` helper
  rather than patching each handler's inline `winnerTeam == null` check
  independently, to avoid the current ~6-way duplication growing further.
- Player-level results must never expose or count a draw, in any
  competition, including the two allowlisted Superdivision competitions —
  draws are team-only.
- For non-allowlisted competitions, a tied match must disappear entirely
  from team stats (including `matchCount`) and from match-record lists —
  not merely lose its "draw" label. Confirmed by explicit user decision.
- Do not change the import pipelines
  (`RfetmMatchImportProcessor`/`BcnesaMatchImportProcessor`) or how
  `Match.competition` is populated; this feature only changes how existing
  data is read and displayed.
- Keep the `draws` field on team-facing DTOs (do not remove it there);
  it is simply always `0` for non-allowlisted competitions because the
  underlying tied matches are excluded upstream.

# Notes

- Decision confirmed by user: for a tied match in a non-allowlisted
  competition, the match disappears entirely from match records and from
  W/D/L counts (not just from the draws count) — resolves the open
  question from initial planning.
- Decision confirmed by user: the exact tie-eligible allowlist is
  `super-divisio-femenino`, `super-divisio-masculino`,
  `fasc-super-divisio-femenino`, `fasc-super-divisio-masculino`. These
  look like normalized/slug-form identifiers rather than the free-text
  `Match.competition` display strings seen during exploration (e.g.
  `"Superdivisión Masculina"`) — confirm during implementation whether
  these values match `Match.competition` directly or require a
  normalization step (lowercasing, accent stripping, space-to-hyphen) to
  compare correctly; adjust `TieEligibleCompetitions` accordingly.
- Decision recorded during planning: identify tie-eligible competitions via
  an **exact allowlist of known strings** (not substring/prefix matching,
  not a new persisted category field) — chosen by the user over the
  alternatives to avoid over-matching future competition names and to
  avoid a larger schema change.
- Decision recorded during planning: acceptance criterion #3 ("remove...
  from Player stats") is interpreted as removing the `draws` field/label
  entirely from player-facing DTOs and UI, not merely always reporting
  zero.

## Implementation notes (2026-09-14)

- Confirmed during implementation: `Match.competition` is composed at
  import time exactly as `"{leagueCompetition}-{sex}"` (see
  `MatchReportContext.competitionOf` for RFETM,
  `BcnesaMatchReportContext.competition()` for BCNESA) — e.g.
  `"super-divisio-masculino"` — so the confirmed allowlist strings match
  `Match.competition` verbatim; no normalization beyond trimming was
  needed in `TieEligibleCompetitions`.
- Added `TieEligibleCompetitions` and `MatchOutcome` (both in
  `domain/shared/model`) as the shared building blocks. `MatchOutcome`
  exposes `teamOutcome`/`playerOutcome`, both returning `Optional.empty()`
  for a match that must be invisible to stats and match records —
  `teamOutcome` for a tie outside the allowlist, `playerOutcome` for any
  tie regardless of competition (draws are team-only, always).
- `FindClubDetailsQueryHandler`, `FindFederatedClubDetailsQueryHandler`,
  and `FindClubsByStringInNameQueryHandler` skip an ineligible tie before
  it reaches `matchCount`/wins/draws/losses at all (not just its draws
  slot). `FindFederatedClubCompetitionDetailsQueryHandler` filters such
  matches out of the returned match list entirely.
  `FindMatchDetailsQueryHandler`'s team form and alignment-stability
  computations are filtered the same way (an ineligible tie neither
  displays nor consumes a recent-form slot); its player-form computation
  uses `playerOutcome`, so a tie never appears there either, in any
  competition.
- `FindPlayerDetailsQueryHandler` drops any match with no winner
  (`winnerTeam == null` always means a genuine tie — imports only ever
  create a `Match` for an already-played report, so this is never "not
  yet played") before it reaches the match list, competitions summary, or
  season statistics. `PlayerMatchReadModel`/`PlayerSeasonStatisticsReadModel`
  had no `draws` field to remove — the only leak was the `result` string
  occasionally being `"draw"`, now impossible.
- Scoping call made during implementation: `FederatedClubPlayerReadModel`
  and `PlayerCompetitionResultReadModel` (the per-player rows nested in a
  **club** page, not the standalone Player details page) keep their
  `draws` field. It is computed from individual games
  (`FindClubDetailsQueryHandler.summarizePlayerResultsByCompetition`),
  which never produce a tie, so it was already always `0` — a
  behaviorally-correct, low-risk leftover rather than the criterion-#3
  leak the plan anticipated. Removing it would have meant a wider
  DTO/REST/frontend contract change for no behavior difference, so it was
  left in place; its one UI consumer (`ClubSummaryPanel`'s
  `PerformerRow`) was still updated to stop displaying it, since showing
  a permanent "0 draws" at a player-scoped view reads the same as
  leaking the concept.
- Frontend: removed all draw handling from `PlayerDetailPage.jsx`
  (spectrum tiers, quality tiers, opponent table column, streak label,
  career-stat aggregation, result badges) and the now-dead
  `common.draws`/`detail.streakDraw`/`detail.qualityDraw`/`detail.resultBadgeDraw`
  i18n keys and CSS rules. Left `detail.draw`, `detail.drawsAbbrev`, and
  the `record-bar-segment`/`match-row-result`/`club-match-result`/
  `match-form-chip` `.is-draw` styling untouched — all exclusively
  team-level. `matchSummary.js`'s generic record helpers needed no change:
  since the backend never sends a `'draw'` result for a player-scoped
  match, `draws` naturally computes to `0` there without any special-casing.
- Tests: added `TieEligibleCompetitionsTest`, `MatchOutcomeTest`, and one
  handler test per backend handler above covering an eligible draw, an
  ineligible tie (fully excluded), and a player-level match (never a
  draw). Updated the existing `FindPlayerDetailsQueryHandlerTest`,
  `PlayerDetailPage.test.jsx`, and `ClubSummaryPanel.test.jsx` cases that
  encoded the old "draws visible everywhere" behavior. Full
  `tt-data-league-core-domain`, `tt-data-league-api-rest`, and frontend
  (`vitest`) suites pass. `tt-data-league-core-repository-jpa`'s
  `SettingRepositoryJpaTest`/`CommunityStatisticsAggregateQueriesTest`/
  `ImportSchemaTest` fail on unmodified `main` too (a pre-existing
  `ImportRunRegistry` bean-wiring issue in that module's Spring test
  context, unrelated to this feature) — confirmed via `git stash` before
  concluding it wasn't a regression.
- Acceptance criteria verified: #1 and #3 by the player-level exclusion
  above (no draw ever reaches a player view, in any competition); #2 and
  #4 by the tie-eligible gating in every team-level handler (a draw
  displays as a draw only for the four allowlisted competitions, and is
  fully invisible — not just relabeled — everywhere else). Manual
  browser verification (build plan step 9) was not run in this session;
  recommend exercising the running app before closing the feature.

## Follow-up fix (2026-09-15): draws leaking as a "0" label

Manual verification of the running app (a Match Summary page for a
non-Superdivision "Vet 1a" match) surfaced a gap the query-level exclusion
didn't cover: `FindMatchDetailsQueryHandler`'s team-form and
alignment-stability payloads still carry a numeric `draws` field (correctly
`0` for a non-eligible competition, since real ties are excluded upstream),
but the **frontend text templates unconditionally showed that field** —
e.g. "Últims 5: 3V · 0E · 2D" and "8V · 0E · 3D quan juguen junts" — so the
concept of draws was still visibly present for a category that should never
show it, even though the number itself was accurate.

Fixed by adding `isTieEligibleCompetition` to
`tt-data-league-frontend/src/utils/matchSummary.js` (a frontend mirror of
the backend `TieEligibleCompetitions` allowlist — duplicated rather than
fetched, since there is no API for it and the four values are static) and
gating the draws segment on it in two places:
- `MatchSummaryPage.jsx`'s `TeamPanel`: picks `lastRecord`/`alignmentRecord`
  vs. new `lastRecordNoDraws`/`alignmentRecordNoDraws` i18n keys (added to
  `ca.js`/`es.js`/`en.js`) based on `isTieEligibleCompetition(match.competition)`.
- `ClubStatsPanel.jsx`'s `CompetitionRow` (the Stats tab's per-competition
  breakdown row): omits the draws count and the `record-bar-segment.is-draw`
  bar segment when `isTieEligibleCompetition(competition.name)` is false.

`ClubCompetitionDetailPage.jsx` needed no change: it renders only a match
list, no win/draw/loss summary to gate.

Added regression tests: `MatchSummaryPage.test.jsx` (draws hidden for
'Primera Catalana' despite the fixture carrying a nonzero `alignment.draws`;
shown for `'super-divisio-masculino'`) and `ClubStatsPanel.test.jsx` (same
pattern for `CompetitionRow`).

## Follow-up fix #2 (2026-09-15): same leak in Club details' overall record

The user then flagged the identical "0E" leak on the Club details page's
**Resumen** tab (`ClubSummaryPanel.jsx`'s stat tile and `RecordBar`) and the
**Partidos** tab's summary strip (`ClubDetailPage.jsx`'s
`MatchesSummaryStrip`), specifically when filtering by source BCNESA or
FCTT.

Initial reasoning in the first follow-up ("the overall record aggregates
across all of a club's competitions, so a nonzero draws count there can be
legitimate — leave it alone") turned out to be an incomplete generalization.
It's true when the aggregate can mix RFETM's eligible Superdivisió
competitions with others, but the tie-eligible allowlist is RFETM-only by
construction (the slugs come from `MatchReportContext.competitionOf`, which
only RFETM's import pipeline produces) — so **when the current filter scope
is BCNESA or FCTT (or any RFETM competition set that excludes
Superdivisió), none of the aggregated competitions can ever be tie-eligible,
and the aggregate draws count is always structurally `0`.** Showing it was
the same leak as the match/competition-scoped cases, just for a set instead
of a single competition.

Fixed by extending the same `isTieEligibleCompetition` check to the
aggregate: both `ClubSummaryPanel`'s stat tile/`RecordBar` and
`ClubDetailPage`'s `MatchesSummaryStrip` now compute
`competitions.some(item => isTieEligibleCompetition(item.name))` over the
already-filtered competition list and hide the draws segment (text and bar
slice) entirely when that's `false` — i.e. when nothing in the current
filter selection could ever produce a real draw, not just when today's
count happens to be zero.

Added regression tests to `ClubSummaryPanel.test.jsx` and
`ClubDetailPage.test.jsx`, each covering both the hidden case (a
non-eligible competition with a nonzero draws value the backend would never
actually send — proving the check is about eligibility, not about the
current count) and the shown case (an eligible competition included in the
filtered set). Full frontend suite (345 tests) and lint pass.
