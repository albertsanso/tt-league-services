# Build Plan

Reference wireframe: [`/docs/frontend/match-details-page.md`](../frontend/match-details-page.md).

1. **Backend: extend `MatchDetailDto` (or add a companion query) with recent-form and
   alignment-stability data.** `GET /api/v1/match/{id}` (`MatchController.java`,
   `MatchDetailDto.java`) today returns `homeTeam`/`awayTeam`, `lineups[]`
   (`letter`, `position`, `player`, `ranking`), `games[]`, and the score — enough for the
   header and lineup columns, but nothing about recent form or lineup history. Add:
   - `teamForm.home`/`teamForm.away`: last-5 (configurable N) match results for that
     team (source+season scoped), each `{matchId, dateTime, opponent, result, score}`,
     plus the previous-5 window's win rate for the trend note. Backed by a new query
     against the match repository filtering by team + season/source, ordered by
     `dateTime desc`, excluding the current match, limited to `2 * N`.
   - `playerForm[]` keyed by `playerSeasonId`/`canonicalPlayerId`: last-N match results
     for each lineup player, reusing the per-player match history that already backs
     `PlayerDetailsDto.matches[]` (`PlayerDetailsDto.java`) rather than a new query —
     add a lightweight "last N results" projection alongside the existing
     `statistics[]` aggregate.
   - `alignmentStability.home`/`.away`: for each team, group that team's past matches
     (same source/season, excluding the current match) by the **exact set** of
     `player.canonicalPlayerId` (fallback `license`) values in `lineups[]` for that
     team, independent of board letter/order. Report `{timesFielded, wins, draws,
     losses, winRate}` for the group matching the current match's lineup set, plus the
     team's overall win rate for the "above/below/in line with" comparison. No
     existing table models recurring lineups (`Lineup.java`/`LineupRepository.java`
     store one row per player-per-match only) — this is new on-the-fly aggregation,
     not a new persisted entity, since lineups are cheap to group in-memory per team
     per season.
   - Keep this additive: existing `MatchDetailDto` consumers (the acta dialog) are
     unaffected by new optional fields.

2. **Frontend: add the route and entry point.**
   - Add `MatchSummaryPage.jsx` under `tt-data-league-frontend/src/pages/`,
     lazy-loaded in `App.jsx` at `partits/:matchId`
     (`<ProtectedPage requiredPermission="matches:read"><MatchSummaryPage /></ProtectedPage>`,
     matching the permission used by the existing `/partits` search route). Register
     the path/meta in `src/config/routes.js` (`routePaths.matchSummary`,
     `routesMeta` entry, breadcrumb back to Matches search) — mirroring the
     `matchDetails`/`matchActa` entries that existed before FEAT-00055 removed them.
   - In `MatchesSearchPage.jsx`, wrap each result's `<div className="club-result-link">`
     block in a `<Link to={routePaths.matchSummary(match.id)}>` (matching the
     `club-result-link` anchor pattern already used by `PlayersSearchPage.jsx`/
     `ClubsSearchPage.jsx`), passing current filters as return search params the same
     way FEAT-00053's player links do. The existing "View acta" button stays a
     sibling action (`stopPropagation` on click so it doesn't also trigger the row
     link), unchanged.

3. **Frontend: fetch and compute the view model.**
   - `MatchSummaryPage.jsx` calls `getMatchDetails(matchId)` (extended per step 1) via
     a new `useMatchSummary(matchId)` hook (co-located under `src/pages/` or
     `src/hooks/`, following `useClubDetails`'s pattern: loading/404/error states).
   - Derive presentation values in a pure `src/utils/matchSummary.js` module (mirrors
     `clubSummary.js`): `formatFormStrip(results)` → `W W L W D` chips,
     `computeTrendNote(last5, prev5)` → improved/declining/stable text,
     `computeAlignmentBadge(timesFielded)` → `Regular lineup` (>=3) / `Rarely used`
     (2) / `New combination` (1, no history) per the wiremock's three states.

4. **Render the page** following `/docs/frontend/match-details-page.md`: header (back
   link, competition/round/date, score, venue/referee, `View acta` button), two
   side-by-side team panels (stacked on narrow viewports) each with a form card,
   lineup card, and alignment-stability card, and a footer duplicating `View acta`
   and the back link. Reuse `card`/`page-block` styling and the `ui/` primitives
   (`SectionLabel`, `Button`) per `design-contract.md`, rather than introducing new
   layout primitives. Lineup player names link to
   `routePaths.playerDetails(canonicalPlayerId)` when present, matching FEAT-00053.

5. **Reuse the acta dialog as-is.** `View acta` on this page opens the existing
   `MatchActaDialog.jsx` with local `actaMatchId` state, exactly like
   `MatchesSearchPage.jsx` does today — no changes to `MatchActaDialog.jsx` or
   `acta-simplified-rfetm.md`.

6. **i18n**: add keys under a new `matchSummaryPage` namespace in `en.js`/`es.js`/
   `ca.js` for section labels, form-strip/trend copy, and the three alignment-badge
   states, following the existing `matchesPage.*`/`clubDetailPage.*` naming
   convention.

7. **Tests**:
   - `MatchesSearchPage.test.jsx`: assert each result row links to
     `/partits/:matchId` and that clicking "View acta" does not also navigate.
   - New `MatchSummaryPage.test.jsx`: render against a fixture extending the existing
     match-detail fixture with `teamForm`/`playerForm`/`alignmentStability`, and
     assert the header score, both team panels' form strips and win rates, lineup
     rows with player links, and all three alignment-stability badge states
     (regular / rarely used / new combination).
   - Backend: unit tests for the new team-form and alignment-stability aggregation
     query/service (exact-lineup-set grouping, trend window, exclusion of the current
     match from its own history).

8. **Docs**: if implementation surfaces a mismatch with
   `/docs/frontend/match-details-page.md` (e.g. a different N for "recent form", or a
   different alignment-badge threshold), record the deviation under `# Notes` below
   before merging, per the convention used in FEAT-00054/FEAT-00055.

# Implementation Guidelines

- Frontend layout, section order, and content come from
  [`/docs/frontend/match-details-page.md`](../frontend/match-details-page.md).
- Do not change `MatchActaDialog.jsx`'s own content/layout
  (`acta-simplified-rfetm.md` stays authoritative for the acta itself) — this
  feature only adds a new entry point/page around it.
- "Alignment stability" groups by the *exact set* of players fielded (ignoring board
  letter/order); a lineup with the same players in a different letter assignment
  still counts as the same lineup. Out of scope: partial-overlap similarity scoring
  between lineups (e.g. "2 of 3 players match") — only exact-set matches count
  toward `timesFielded`.
- Recent-form window (`N`) defaults to 5 matches for both teams and players; make it
  a named constant so it can be tuned without a contract change.
- No new persisted entity for lineup history; alignment-stability is computed
  on-the-fly from existing match/lineup data, scoped to the same source+season as
  the viewed match (do not aggregate across seasons unless a future feature asks for
  it).

# Notes

- Open question resolved: `teamForm`/`alignmentStability` are scoped strictly per
  source+season, matching how `ClubDetailsDto`/`PlayerDetailsDto` already scope
  their stats. Implemented as specified: past matches for a team are pulled via
  `MatchRepository.findAllMatchesByTeamIdsAndSource` and then filtered in-memory
  to `match.getSeason().equals(current.getSeason())`, excluding the viewed match.
  No mid-season roster-change break was added; revisit if product feedback asks
  for it.
- Open question resolved: `playerForm[]` is player-scoped (any team), matching
  `PlayerDetailsDto.statistics[]`'s existing player-centric scoping, as the
  build plan's default states. Implemented in
  `FindMatchDetailsQueryHandler.resolvePlayerSeasonIds`: when a lineup player has
  a canonical identity, all of that canonical player's `PlayerSeason`
  registrations for the same source+season as the viewed match are included
  (covering a mid-season transfer between clubs); without a canonical identity,
  only the single `PlayerSeason` on the lineup is used.
- Implementation detail not spelled out in the build plan: `alignmentStability`'s
  `timesFielded`/`wins`/`draws`/`losses` **include the viewed match itself**
  (not just prior occurrences), so the wiremock's "played together 7 times this
  season" reads as a total that already counts the match being viewed. A lineup
  fielded for the first time therefore has `timesFielded == 1` with no
  historical win rate (`winRate: null`), which is what `computeAlignmentBadge`
  (`src/utils/matchSummary.js`) treats as `new`. This was chosen because the
  wiremock's stat lines (e.g. "5W 1D 1L (71%)") only add up when the current
  match's own result is included in the aggregate.
- `RECENT_FORM_WINDOW` (team and player recent-form window `N`) is a named
  constant in `FindMatchDetailsQueryHandler`, set to 5 as specified.
- No deviation from `/docs/frontend/match-details-page.md`'s layout/content was
  needed; the alignment-badge thresholds (`>=3` regular, `2` rare, `1` new) and
  the recent-form window match the wiremock and the build plan exactly.
