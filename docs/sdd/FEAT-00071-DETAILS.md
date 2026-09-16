# Build Plan

## 1. Match details team form scoped to its own competition (the reported bug)

1. In
   [FindMatchDetailsQueryHandler.java](../../tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/application/match/find/FindMatchDetailsQueryHandler.java),
   change `teamMatchesExcludingCurrent` to look matches up with
   `MatchRepository.findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition`
   (source + season + competition) instead of
   `findAllMatchesByTeamIdsAndSource` plus an in-memory season filter. Keep the
   "exclude the viewed match" filter and the most-recent-first ordering.
2. Update the method's Javadoc: the scoping is source + season + **competition**,
   and record *why* — a `Team` is keyed by source + name + season only.
3. Update the five `findAllMatchesByTeamIdsAndSource` stubs in
   [FindMatchDetailsQueryHandlerTest.java](../../tt-data-league-core-domain/src/test/java/org/cttelsamicsterrassa/data/core/application/match/find/FindMatchDetailsQueryHandlerTest.java)
   to the competition-scoped method, passing each fixture's own competition.
4. Add a regression test (`scopesTeamFormToTheViewedMatchsOwnCompetition`) that
   stubs *both* lookups: the unscoped one returning a later match from another
   competition, the scoped one returning only this competition's match. Assert
   the form strip contains only this competition's match.

## 2. Match details form reports form *going into* the viewed match

1. In the same handler, `teamForm` filters `sortedDescExcludingCurrentVisible` to
   matches played before `current` (new `isPlayedBefore` helper) before slicing
   the last/previous windows. An undated match can be placed on neither side of
   the viewed one, so it never takes a form slot.
2. `overallWinRate` and `alignmentStability` deliberately stay season-wide: the
   alignment card's own wording is "this season", and the comparison note reads
   against the team's whole season.
3. Regression test `reportsFormGoingIntoTheViewedMatchRatherThanTheSeasonsLatestResults`.

## 3. Player/Club detail form guide excludes pending matches

1. In [clubSummary.js](../../tt-data-league-frontend/src/utils/clubSummary.js):
   - `getFormGuide` and `getCurrentStreak` filter out pending matches
     (`isPendingMatch`) before calling `getRecentMatches`.
   - Do **not** change `getRecentMatches` itself —
     [ClubSummaryPanel.jsx](../../tt-data-league-frontend/src/pages/ClubSummaryPanel.jsx)
     calls it directly for a plain "recent matches" list that intentionally
     still shows pending matches.
2. Add unit tests in
   [clubSummary.test.js](../../tt-data-league-frontend/src/utils/clubSummary.test.js)
   for both functions, mirroring the existing `getNotableMatches` pending test.

## 4. Verification

- `mvn -pl tt-data-league-core-domain test`
- `npx vitest run` in `tt-data-league-frontend/` for `clubSummary`,
  `PlayerDetailPage`, `ClubDetailPage`, `ClubSummaryPanel`.

# Implementation Guidelines

- Prefer the existing competition-scoped repository port over a new in-memory
  filter: `findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition` already
  exists and is the pattern `FindFederatedClubCompetitionDetailsQueryHandler`
  uses for competition-scoped stats.
- Player form (`playerForm[]`) stays player-scoped across teams and
  competitions — that is a deliberate FEAT-00057 decision, not part of this fix.
- Out of scope: how pending matches are counted or displayed elsewhere
  (`countPendingMatches`, the "pending" stat tile, `ClubSummaryPanel`'s list).

# Notes

- **Reported symptom.** On
  `/partits/796cd27a-a111-445d-beb0-0aca6beda6f4?source=BCNESA&season=2024-2025&competition=Vet+1a`
  (TITUS BADALONA vs CTT ELS AMICS DE TERRASSA, Jornada 6, 5/11/2024), the away
  team's strip rendered `L W W L L` (2V·3D, 40%). Manual review against the
  federation's own data gives `W W L W W` for that team in Vet 1a.
- **Root cause.** A `Team` is resolved by `findTeamByNameAndSeasonAndSource`, so
  it carries no competition dimension: a club fielding teams in several
  competitions in one source+season shares a single `Team` registration.
  `teamMatchesExcludingCurrent` scoped its lookup to source+season only, so
  matches from every other competition that registration played landed in the
  same recency window and crowded out the Vet 1a ones.
- **Corroboration from the screenshot itself.** The panel reported the *previous*
  window at 80% — exactly the win rate of the correct Vet 1a last five
  (`WWLWW` = 4/5). The real matches had been pushed one window back by five
  more recent matches from another competition, which is the precise signature
  of the missing competition filter.
- The regression test was verified to fail before the fix (the other
  competition's match leaked into the strip: expected 1 result, got 2).
- **Second root cause, found by replaying the old and new logic as SQL against
  the dev database.** The windows were taken from the season's most recent
  matches rather than the matches preceding the viewed one, so the November
  2024 match was reporting March/April 2025 results. Measured on a random
  sample of 400 matches (800 team panels): the competition fix alone changes
  414 panels (52%), future matches appear in 696 panels (87%), and 732 panels
  (92%) change once both fixes are applied. Replaying the *old* logic
  reproduced the reported screenshot exactly (`LWWLL` for both teams), which is
  what validates these figures.
- **Open data question, not a code defect.** With both fixes the away team's
  strip is `WWLLW`; manual review against the federation gives `WWLWW`. The one
  differing chip is round 4 (2024-10-21, CTT ELS AMICS DE TERRASSA 2-4 EL
  CIERVO A). That record is internally consistent — the stored score matches
  its own per-game winners and the running cumulative score — so it is not a
  partially imported row. Either the acta was extracted with the sides swapped
  wholesale (FEAT-00069 territory, if this row predates that re-import) or the
  expected sequence is off by one match. Needs a check against that acta.
- Alignment stability is computed from the same match list, so it is now
  competition-scoped too. The added test asserts team form only; alignment
  keeps its existing coverage.
- Second, unrelated defect found while investigating and fixed under the same
  feature: `getFormGuide`/`getCurrentStreak` in `clubSummary.js` did not
  exclude pending matches, unlike `getNotableMatches` in the same module. One
  existing assertion in `PlayerDetailPage.test.jsx` had locked in the buggy
  output (`'NVVD'`, where `N` was a pending match's chip) and was corrected to
  `'VVD'`.
