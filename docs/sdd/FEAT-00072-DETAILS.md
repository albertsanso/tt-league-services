# Build Plan
FEAT-00071 fixed the **team** strip on the Match details page: scoped to the
viewed match's own competition, and limited to matches played *before* it. The
per-player strip in the same lineup table never got either fix. This feature
applies both to `playerForm`.

All work is in
[FindMatchDetailsQueryHandler.java](../../tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/application/match/find/FindMatchDetailsQueryHandler.java);
no DTO shape, REST/MCP contract, or frontend logic changes.

## 1. Player form reports form going *into* the viewed match

1. In `playerForm(Lineup, Match current)` (currently lines 224-264), the
   `playerMatches` pipeline (lines 237-246) excludes the current match but never
   filters by date, so the last-5 window is simply the player's five most recent
   matches of the season — on a historical match that means results which had not
   happened yet.
2. Add `.filter(value -> isPlayedBefore(value, current))` to that pipeline. The
   helper already exists at lines 320-327 and is what `teamForm` uses at line
   177; reuse it rather than writing a second date comparison.
3. Undated matches are placed on neither side of the viewed match and therefore
   take no form slot — same semantics `teamForm` already has.

## 2. Player form scoped to the viewed match's own competition

1. Add `.filter(value -> Objects.equals(value.getCompetition(), current.getCompetition()))`
   to the same pipeline.
2. **No new repository port is needed**, unlike the team-level fix. The team case
   had to move to `findAllMatchesByTeamIdsAndSourceAndSeasonAndCompetition`
   because a `Team` carries no competition dimension. Here the handler already
   holds each `Lineup`'s `Match`, and `Match` exposes `getCompetition()`
   (`Match.java:269`), so the filter is in-memory alongside the existing
   `getMatch() != null` / outcome-present filters.
3. Source and season need no filter: `resolvePlayerSeasonIds` (lines 266-285)
   already restricts the registrations to the viewed match's source and season,
   and a `PlayerSeason`'s lineups cannot reach outside them.
4. Both new filters must sit **before** `.limit(RECENT_FORM_WINDOW)` (line 245),
   otherwise out-of-scope matches still consume form slots — that crowding-out is
   the exact failure mode FEAT-00071 diagnosed at team level.

## 3. Documentation of the changed contract

1. Add a Javadoc to `playerForm` stating the scoping — canonical player, source,
   season, **competition**, matches played before the viewed one — and that it
   deliberately stays cross-*team* so a mid-season transfer's matches still count.
2. Update the `PlayerFormReadModel` Javadoc in
   [MatchDetailReadModel.java:85-88](../../tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/application/match/find/dto/MatchDetailReadModel.java):
   it currently says the record "reus[es] the per-player match history that backs
   `PlayerDetailsDto.matches[]`", which stops being true once this strip is
   competition- and chronology-scoped.
3. FEAT-00071's Implementation Guidelines recorded "player form stays
   player-scoped across teams and competitions — a deliberate FEAT-00057
   decision". Record under `# Notes` here that the competition half of that
   decision is superseded; the cross-team half stands.

## 4. Tests

`playerForm` has **no** backend coverage today — grep for `playerForm` in
`FindMatchDetailsQueryHandlerTest.java` returns nothing, because every test
leaves `findAllLineupsByPlayerSeasonIds` unstubbed and the mock returns an empty
list. These are the first assertions on it, so each new test must stub three
lookups: `federatedPlayerRepository.findAllFederatedPlayersByPlayerId`,
`playerSeasonRepository.findAllPlayerSeasonsByFederatedPlayerIds`, and
`lineupRepository.findAllLineupsByPlayerSeasonIds`.

In
[FindMatchDetailsQueryHandlerTest.java](../../tt-data-league-core-domain/src/test/java/org/cttelsamicsterrassa/data/core/application/match/find/FindMatchDetailsQueryHandlerTest.java),
following the fixture style of `scopesTeamFormToTheViewedMatchsOwnCompetition`
(line 204) and `reportsFormGoingIntoTheViewedMatchRatherThanTheSeasonsLatestResults`
(line 254):

1. `scopesPlayerFormToTheViewedMatchsOwnCompetition` — the player has lineups in
   two competitions of the same source+season; assert only the viewed
   competition's results appear in `lastResults`, and that the other
   competition's match does not consume a window slot.
2. `reportsPlayerFormGoingIntoTheViewedMatch` — give the player matches on both
   sides of the viewed match's `dateTime`; assert only the earlier ones appear,
   oldest-to-newest.
3. `keepsPlayerFormAcrossTeamsWithinTheSameCompetition` — a mid-season transfer
   fixture (two `PlayerSeason` registrations, two teams, one competition);
   asserts the cross-team behaviour is preserved rather than regressed by the new
   filter. `resolvePlayerSeasonIds` is what makes this pass.
4. Assert `winRate` alongside `lastResults` in at least one of these — it is
   tallied over the same `playerMatches` window (lines 251-262) and so shifts
   with the fix.
5. Verify tests 1 and 2 fail before the change is applied, as FEAT-00071 did.

Frontend: the per-player chip cell
([MatchSummaryPage.jsx:236-244](../../tt-data-league-frontend/src/pages/MatchSummaryPage.jsx))
has no test asserting it. Add one to
[MatchSummaryPage.test.jsx](../../tt-data-league-frontend/src/pages/MatchSummaryPage.test.jsx)
against the existing `playerForm` fixture (line 68-70). This guards the
rendering, not the fix — the fix is entirely server-side.

## 5. Verification

- `mvn -pl tt-data-league-core-domain test`
- `npx vitest run MatchSummaryPage` in `tt-data-league-frontend/`
- Manual check on the FEAT-00071 reproduction URL
  (`/partits/796cd27a-a111-445d-beb0-0aca6beda6f4?source=BCNESA&season=2024-2025&competition=Vet+1a`):
  every lineup player's strip must contain only Vet 1a matches dated before
  2024-11-05.

# Implementation Guidelines

- Reuse `isPlayedBefore`; do not add a parallel date comparison.
- Use `Objects.equals` for the competition comparison so a null competition only
  matches another null, mirroring the SQL equality the team-level lookup relies on.
- Player form stays **cross-team**: a mid-season transfer's matches for the
  previous club still count, which is what `resolvePlayerSeasonIds` exists for.
  Do not narrow it to `lineup.getTeam()`.
- Draws remain excluded from player form (FEAT-00066). The new filters do not
  change that.
- Out of scope: the Player detail page strip
  (`getFormGuide`/`getCurrentStreak` in `clubSummary.js`) — it has no "viewed
  match" to be chronological against and was already corrected for pending
  matches under FEAT-00071.
- Out of scope: `FindPlayerDetailsQueryHandler`, which shares the
  `findAllLineupsByPlayerSeasonIds` lookup but serves a different, intentionally
  unscoped history.
- No change to `LineupRepository` or its JPA implementation; keeping the port
  untouched avoids touching the five `findAllLineupsByPlayerSeasonIds` stubs in
  `FindPlayerDetailsQueryHandlerTest`.

# Notes

- **Why this is a separate feature from FEAT-00071.** That feature fixed the team
  strip and explicitly deferred player form, recording it as a deliberate
  FEAT-00057 scoping decision. Reviewing the shipped result, the same two defects
  were visible one row down in the same table, so the deferral was reversed for
  the competition dimension.
- **Decision (2026-09-17):** player form gets *both* the chronology filter and
  competition scoping, so the player chips and the team chips above them describe
  the same competition and the same point in time. Without the competition half,
  a club fielding teams in several competitions in one source+season would show a
  team strip and a player strip drawn from different match sets.
- **Decision (2026-09-17):** scope is the Match details page only. The Player
  detail page keeps its current behaviour.
- **Supersedes** FEAT-00071's guideline that "player form stays player-scoped
  across teams and competitions". The cross-**team** half still holds; the
  cross-**competition** half does not.
- **Implementation (2026-09-17).** Two filters added to the `playerMatches`
  pipeline in `playerForm`, before `.limit(RECENT_FORM_WINDOW)`. No repository,
  DTO, REST/MCP contract or frontend logic change was needed: a `Lineup` already
  carries its `Match`, and `Match.getCompetition()` exists, so the competition
  scope is an in-memory filter rather than the new port the team-level fix
  required. `PlayerFormReadModel`'s Javadoc was corrected — it no longer simply
  "reuses the per-player match history that backs `PlayerDetailsDto.matches[]`",
  because that history stays unscoped.
- **Regression tests verified to fail before the fix**, as FEAT-00071 did: with
  the two filters removed, `scopesPlayerFormToTheViewedMatchsOwnCompetition` and
  `reportsPlayerFormGoingIntoTheViewedMatch` each reported 2 results instead of
  1 — exactly one leaked match apiece.
  `keepsPlayerFormAcrossTeamsWithinTheSameCompetition` passes either way by
  design; it is a guard against over-narrowing, not a reproduction.
- `playerForm` had no backend coverage at all before this feature — every
  existing test left `findAllLineupsByPlayerSeasonIds` unstubbed, so the mock
  returned an empty list and the strip was silently empty in all assertions.
  That is why the defect survived FEAT-00071.
- The frontend test needed `getByText` with an element predicate rather than a
  plain string: the chips render as sibling text nodes inside the cell, and
  "Sense partits recents." also appears in the away team's empty form panel.
- Verification run 2026-09-17: `mvn -pl tt-data-league-core-domain test` →
  118 passed; full `npx vitest run` → 42 files, 350 passed.
- Carried over from FEAT-00071, unresolved and untouched here: on the
  reproduction match, round 4 (2024-10-21, CTT ELS AMICS DE TERRASSA 2-4 EL
  CIERVO A) still disagrees with the federation's own data. That record is
  internally consistent, so it is a data/import question (FEAT-00069 territory),
  not a form-computation defect — do not tune the form logic to make that one
  chip match.
- **Not verified against real data.** The manual check on the reproduction URL
  (build plan step 5) has not been run; the evidence here is unit-level only.
