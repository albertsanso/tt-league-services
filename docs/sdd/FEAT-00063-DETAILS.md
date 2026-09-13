# Build Plan
Covers the newly proposed, still-unchecked acceptance criteria on FEAT-00063:
the Players summary strip and the Matches summary strip (see
[the Proposal section below](#proposal-2026-09-13-expand-the-players-and-matches-tab-summaries)
for the research and wireframes this plan implements —
[players.md](../frontend/club-details/players.md) and
[matches.md](../frontend/club-details/matches.md)). The original name-search
build plan for this feature already shipped; its steps and outcomes are
preserved under `# Notes` below and are not repeated here.

Feasibility check against the current codebase (resolves the wireframes' Open
Questions):

- Each player-season record in `club.players` already carries its own
  `matchCount`/`competitionResults` (`tt-data-league-frontend/src/api/clubs.js:191`,
  `normalizePlayerCompetitionResult`) — career totals for a canonical player
  can be summed client-side across that player's season records without any
  new match fetch or API change.
- There is no join/registration-date field on a player-season record — only
  `season` (a string like `'2023-2024'`). "Newest addition" is redefined as
  "most recent season on record" (max `season` string per canonical player),
  not a real join date.
- `clubMatches.js`'s existing `resolveClubTeam(match, teams, source, season)`
  already resolves which side of a match is the club's own team by matching
  `homeTeam`/`awayTeam` against `club.teams` — the same logic the Matches
  summary's home/away split needs; it just isn't exported yet.
- `match.result` is already computed server-side from the requesting club's
  point of view (`'win' | 'loss' | 'draw'`), so no new field is needed for
  the record tiles, form guide, or notable-matches logic.
- `useClubMatches(clubId, competitions)` accepts any competitions array, so
  the Matches summary can request the currently-filtered set exactly like
  `MatchesPanel` already does — no separate unscoped fetch is needed.

1. **Roster aggregation helpers** (`tt-data-league-frontend/src/utils/clubSummary.js`)
   - Add `aggregateRosterByCanonicalPlayer(players)`: groups the *full*,
     not-yet-deduped `players` array (i.e. every season record, before
     `dedupeByCanonicalPlayerId`) by `canonicalPlayerId`, and for each group
     returns `{ canonicalPlayerId, playerName, competitions (union of all
     records' competitions, deduped), seasons (sorted array of distinct
     season strings), matchCount (summed across records), resultTotals
     (summed wins/draws/losses across records) }`. Drop records without a
     `canonicalPlayerId`, matching the existing `dedupeByCanonicalPlayerId`
     contract in `ClubDetailPage.jsx`.
   - Add `getRosterByCompetition(rosterAggregates)`: returns
     `[{ competition, playerCount }]` sorted by `playerCount` descending
     (ties broken alphabetically), counting each canonical player once per
     competition their union list includes.
   - Add `getMostActivePlayers(rosterAggregates, limit = 4)`: sorts by
     `matchCount` descending (ties: more distinct `seasons`, then name), for
     the "most active (career)" mini-list.
   - Add `getNewestPlayers(rosterAggregates, limit = 1)` (or fold into a
     single `latestSeason` field consumers read directly): returns the
     canonical player(s) whose max `season` string is the most recent,
     ties broken by name. Document in a one-line comment that this is
     "latest season on record," not a real join date (no such field exists
     on the API player record).
   - Unit tests in `clubSummary.test.js` (create if it does not already
     exist — check first) for: summing across 2+ season records for the
     same canonical player, competitions union with duplicates removed,
     roster-by-competition counts, most-active ordering/tie-breaks, and
     newest-season selection.

2. **Export `resolveClubTeam`** (`tt-data-league-frontend/src/utils/clubMatches.js`)
   - Export the existing (currently module-private) `resolveClubTeam`
     function unchanged — it already does exactly what the home/away split
     needs. No behavior change to `groupMatchesHierarchy`/`buildTeams`.

3. **Match summary helpers** (`tt-data-league-frontend/src/utils/clubSummary.js`)
   - Add `computeHomeAwaySplit(matches, teams, source)`: for each match, use
     `resolveClubTeam` (imported from `clubMatches.js`) plus
     `match.homeTeam`/`match.awayTeam` to classify it as a home or away
     fixture for the club, then return
     `{ home: { wins, draws, losses, winRate }, away: { ...same } }` reusing
     the existing `winRateOf`-style calculation pattern already private to
     this file (extract/reuse rather than duplicate).
   - Add `countPendingMatches(matches)`: `matches.filter((m) =>
     m.homeGamesWon == null || m.awayGamesWon == null).length`, mirroring
     the existing pending-result check in `ClubDetailPage.jsx`'s `MatchRow`/
     `CompetitionBody`.
   - Add `getFormGuide(matches, limit = 5)`: reuse `getRecentMatches` to get
     the most recent `limit` matches, then reverse to chronological order
     (oldest → newest) for left-to-right rendering, mapping each to its
     `result`.
   - Add `getCurrentStreak(matches)`: walks the chronologically-sorted
     matches from most recent backwards, counting consecutive matches with
     the same `result`, and returns `{ result, count }` (e.g.
     `{ result: 'win', count: 1 }`); returns `null` when there are no
     matches.
   - Add `getNotableMatches(matches)`: from matches with both
     `homeGamesWon`/`awayGamesWon` set, compute the game-score margin
     (`Math.abs(homeGamesWon - awayGamesWon)`) and return
     `{ closest, biggestWin, biggestLoss }`, each either a match or `null`
     (closest = smallest margin among draws/decided matches, ties broken by
     most recent `dateTime`; biggestWin/biggestLoss = largest margin among
     `result === 'win'` / `result === 'loss'` respectively).
   - Unit tests in `clubSummary.test.js` covering home/away classification
     (including a match where the club plays away), pending count, form
     guide ordering, streak calculation (including an all-losses and an
     empty-matches case), and notable-match selection/ties.

4. **`PlayersPanel` — Players summary strip** (`ClubDetailPage.jsx`, currently
   the `PlayersPanel` function at line ~379)
   - Compute `rosterAggregates = useMemo(() =>
     aggregateRosterByCanonicalPlayer(players), [players])` from the full
     `players` prop (the roster-scoped, not-yet-deduped list already passed
     in as `rosterPlayers` from `ClubDetailContent`), replacing the existing
     inline `canonicalPlayers = dedupeByCanonicalPlayerId(players)` — fold
     dedup into the new aggregation step so there is one source of truth for
     "one row per canonical player."
   - Derive `byCompetition = useMemo(() =>
     getRosterByCompetition(rosterAggregates), [rosterAggregates])`,
     `mostActive = useMemo(() => getMostActivePlayers(rosterAggregates, 4),
     [rosterAggregates])`, and the newest-season figure.
   - Render a new `<PlayersSummaryStrip>` section (new component, same file)
     above the existing search input, only when `rosterAggregates.length >
     0` (reuse the existing `registeredPlayersEmpty` empty state otherwise —
     no new empty state needed for the strip itself):
     - 4 `StatTile`s (reuse the `StatTile` component already defined in
       `ClubSummaryPanel.jsx` — either import it or, if that file doesn't
       already export it, add and export it there so both panels share one
       implementation instead of duplicating markup): Players count,
       Competitions covered (`byCompetition.length`), Most represented
       competition (`byCompetition[0]`), Most recent season.
     - A `card-block` with the roster-by-competition proportional bar list
       (reuse `record-bar`/`record-bar-segment` CSS pattern from
       `ClubSummaryPanel.jsx`'s `RecordBar`, generalized to N segments of one
       color rather than W/D/L — confirm with a quick visual check whether
       the existing 3-color `record-bar` CSS supports an arbitrary segment
       count before deciding to reuse vs. add a small variant class).
     - A `card-block` with the `mostActive` mini-list, reusing the
       `mini-list`/`PlayerRow`-style markup from `ClubSummaryPanel.jsx`
       (career match count and season count as the two stat lines instead of
       season win rate).
   - The existing search input, shown/total summary line, and list below are
     unchanged except that `filteredPlayers`/`canonicalPlayers` now derive
     from `rosterAggregates` instead of the old inline dedupe.

5. **`MatchesPanel` — Matches summary strip** (`ClubDetailPage.jsx`, currently
   the `MatchesPanel` function at line ~437)
   - The panel already calls `useClubMatches(club.id, competitionsWithSource)`
     and has `matchGroups`; flatten it into a tagged match list once (same
     pattern `ClubSummaryPanel.jsx` uses for `taggedMatches`) for the new
     helpers to consume.
   - Compute, via `useMemo`, keyed off the flattened match list:
     `record = computeOverallRecord(competitions)` (already available from
     `clubSummary.js`, reuse as-is for the Matches-count/win-rate tiles),
     `homeAway = computeHomeAwaySplit(matches, club.teams, sourceFilter ||
     undefined)`, `pending = countPendingMatches(matches)`,
     `formGuide = getFormGuide(matches)`, `streak = getCurrentStreak(matches)`,
     `notable = getNotableMatches(matches)`.
   - Render a new `<MatchesSummaryStrip>` section (new component, same file)
     above the existing hierarchy `<ul>`, only when `matches.length > 0`
     (when `competitions.length === 0` the panel already returns its
     existing empty state before reaching this point — no change there):
     - 4 `StatTile`s: Matches, Win rate, Home/Away split, Pending.
     - A form-guide row: small chip list of `formGuide` results (reusing
       `club-match-result is-win/is-draw/is-loss` classes already defined
       for `MatchRow`) plus the `streak` figure as trailing text.
     - A `card-block` "Notable matches" mini-list rendering `notable.closest`
       /`notable.biggestWin`/`notable.biggestLoss` as `MatchRow`-style rows
       (reuse `MatchRow` from `ClubSummaryPanel.jsx` if it's exported, or
       inline the same markup) — skip any of the three that is `null`
       (e.g. a club with only decided/no-draw matches has no meaningful
       "closest" pick if every match margin is large; render nothing for
       that slot rather than a placeholder).
   - Hierarchy list, toggles, and `CompetitionBody` are unchanged.

6. **i18n** (`ca.js` base, `en.js`/`es.js` overrides)
   - Players strip: `detail.playersSummaryTitle`, `detail.playersCompetitionsCovered`,
     `detail.playersMostRepresented`, `detail.playersLatestSeason`,
     `detail.playersByCompetitionTitle`, `detail.playersMostActiveTitle`,
     `detail.playersMostActiveSub` (interpolated with season/match counts).
   - Matches strip: `detail.matchesSummaryTitle`, `detail.matchesHomeAwaySplit`,
     `detail.matchesPending`, `detail.matchesFormGuide`,
     `detail.matchesCurrentStreak` (interpolated with result/count),
     `detail.matchesNotableTitle`, `detail.matchesClosest`,
     `detail.matchesBiggestWin`, `detail.matchesBiggestLoss`.
   - Add every new key to all three locale files, matching the existing
     append pattern (mirroring how FEAT-00063's first increment added its
     keys to `ca.js`/`en.js`/`es.js` together).

7. **Tests** (`ClubDetailPage.test.jsx`)
   - Extend the `club.players`/`club.competitions`/matches fixtures with
     enough season-spanning data to exercise: a canonical player with 2+
     season records (career sum, competitions union), a distinguishable
     "most recent season" player, and match fixtures covering a home win, an
     away loss, a draw, and one pending (no score) match.
   - Players view: assert the 4 stat tiles render expected values, the
     roster-by-competition list matches the fixture's competition
     distribution, and the most-active list orders by career match count.
   - Matches view: assert the stat tiles (count/win rate/home-away/pending),
     the form-guide chip order and streak text, and that the notable-matches
     card shows the expected closest/biggest-win/biggest-loss picks (and
     that a slot renders nothing when the fixture has no match for it, e.g.
     no losses at all).
   - Re-run the existing Players/Matches tests unchanged to confirm the new
     strips don't disturb the existing search, empty-state, or hierarchy
     behavior.

8. **Verification**
   - `npx vitest run` in `tt-data-league-frontend/`.
   - `npx eslint` on all changed/added files.
   - Manually sanity-check in the dev server against a club with multiple
     seasons/competitions and at least one pending match: confirm both
     strips render above their respective existing UI, the Players strip
     stats stay stable while typing in the name search (they reflect the
     full roster, not the filtered view), and the Matches strip updates when
     the Source/Season/Competition filters change.

# Implementation Guidelines

Guidelines from the shipped name-search increment (still in force):

- Client-side only: no backend/API changes. This filters the `players` array
  the Players tab already receives; it does not call `usePlayerSearch` or any
  other network search endpoint (that hook powers the separate global
  `PlayersSearchPage`, which is out of scope here).
- Do not put the name-search text in the URL/`searchParams` — unlike
  Source/Season/Competition, this is a fast, ephemeral, client-side narrowing
  of an already-rendered list, not a shareable filter state. If a future
  request asks for a shareable/deep-linkable player search, that is a
  separate follow-up.
- Do not change the Source/Season/Competition filters or `PlayersPanel`'s
  existing per-player card markup/links.
- Reuse the existing `.club-search-input-wrap`/`.club-search-input`/
  `.search-summary` CSS classes and the `lucide-react` `Search` icon already
  used by `PlayersSearchPage.jsx` — no new CSS classes or hard-coded colors
  unless reuse turns out to be visually wrong once implemented.
- All new copy goes through `react-i18next`; no literal strings in JSX.
- Keep the `matchesQuery` helper dependency-free (no new npm packages) —
  `String.prototype.normalize('NFD')` covers accent-insensitivity without a
  library.

Additional guidelines for the Players/Matches summary strips (build plan
above):

- Still client-side only: both strips derive entirely from data their tab
  already fetches (`players`/`rosterPlayers` and `useClubMatches`'s
  `matchGroups`); no new endpoints or request params.
- The Summary/Stats tabs (`ClubSummaryPanel.jsx`/`ClubStatsPanel.jsx`,
  FEAT-00054) stay unchanged — the new strips are a different, tab-local
  concept and must not duplicate or replace that panel's club-wide overview.
- Prefer reusing `StatTile`, the `record-bar`/`record-bar-segment` CSS, and
  the `mini-list`/`PlayerRow`/`MatchRow` markup already shipped for
  `ClubSummaryPanel.jsx` over introducing new stat-display components;
  export what's needed from that file rather than copy-pasting JSX.
- "Newest addition"/"most recent season" is derived from the `season` string
  already on each player-season record — do not invent or request a real
  join/registration date field.
- Home/away classification must reuse `resolveClubTeam` (exported from
  `clubMatches.js` per step 2) rather than re-deriving club-side detection
  logic in a second place.
- All new copy goes through `react-i18next`, added to `ca.js`/`en.js`/`es.js`
  together, matching the existing append pattern.

# Notes

- Filed as a standalone feature (not folded into FEAT-00054's "Summary" tab)
  because this "summary" is a small shown/total count local to the Players
  tab's own list, distinct from the club-wide `ClubSummaryPanel` added in
  [FEAT-00054](./FEAT-00054-DETAILS.md).
- Builds on [FEAT-00053](./FEAT-00053-DETAILS.md), which added the
  `canonicalPlayerId` links in this same `PlayersPanel` — those links and
  their `source`/`season` query params are preserved unchanged.
- **Implementation notes (2026-09-13):** shipped as planned in the build plan.
  - Added `tt-data-league-frontend/src/utils/textSearch.js` (`matchesQuery`)
    with unit tests (`textSearch.test.js`) covering case-insensitivity,
    accent-insensitivity in both directions, substrings, empty/whitespace
    queries, and `null`/`undefined` text.
  - `PlayersPanel` (`ClubDetailPage.jsx`) gained local `nameQuery` state, a
    `filteredPlayers` derivation, the reused `.club-search-input-wrap`/
    `.club-search-input`/`search-summary` markup (plain `<div>`, not a
    `<form>`, since it filters on every keystroke with no submit step), and
    the new `detail.playersSearchEmpty` empty state shown only when
    `players.length > 0` but the filter yields nothing — the pre-existing
    `detail.registeredPlayersEmpty` state is unchanged and still shown when
    there are no players at all.
  - Added `detail.playersSearchLabel`, `detail.playersSearchPlaceholder`,
    `detail.playersShownCount`, `detail.playersSearchEmpty` to `ca.js` (base)
    and both `en.js`/`es.js` overrides.
  - Extended `ClubDetailPage.test.jsx`'s `club.players` fixture with a third
    player (`'Núria Pérez'`) and added tests for: shown/total summary
    narrowing as the user types, accent-insensitive matching, the distinct
    no-match empty state, clearing the input restoring the full list, and
    the no-players-at-all empty state still rendering (and hiding the search
    input) when `club.players` is empty. Updated the pre-existing roster-tab
    test's expected season-label count from 2 to 3 for the new fixture
    player.
  - Verified: `npx vitest run` — full frontend suite 307/307 passing
    (41 files); `npx eslint` clean on all changed files.
  - Not verified in a live browser: exercising the page end-to-end needs the
    `api-rest` backend running (same limitation noted in FEAT-00054); no
    `.claude/launch.json` dev-server config exists in this repo yet.
- **Scope change (2026-09-13): Players tab now shows canonical players
  only.** Requested after the initial implementation shipped. `PlayersPanel`
  now filters `players` down to those with a `canonicalPlayerId` before
  computing the search/summary/empty-state, so unconsolidated
  (non-canonical) players never appear in the list, count, or search results.
  - Rationale: an unconsolidated player has no player-details page to link
    to, so showing it in a tab whose whole point is now findability (search +
    a shown/total count) was more noise than signal; the entry existed only
    to represent a not-yet-merged federation record.
  - `canonicalPlayers = players.filter((p) => p.canonicalPlayerId)` replaces
    `players` as the base for `canonicalPlayers.length` (summary total),
    `filteredPlayers` (search), and the `players.length === 0` /
    `registeredPlayersEmpty` check — a club whose only players are
    unconsolidated now shows the same "no players registered" empty state as
    a club with zero players.
  - Since every rendered player now has a `canonicalPlayerId`, the per-player
    `<div>` (non-linked) branch in the list was dead code and was removed;
    every list item is now unconditionally a `Link` to
    `routePaths.playerDetails(...)`.
  - Out of scope: this does not change `ClubSummaryPanel`/`ClubStatsPanel`
    (their "Most-capped players"/player-count figures still use the full,
    unfiltered `players` list — e.g. `playerCount` sub-labels — since that
    scope wasn't part of this request) or the upstream Source/Season/
    Competition filtering in `ClubDetailContent`, which is unchanged.
  - Tests: added a fourth fixture player (`'Laia Player'`, canonical,
    `Copa` competition) so the competition-filter test still exercises two
    *canonical* players in different competitions now that the previous
    non-canonical `'Joan Player'` fixture can no longer appear; replaced the
    old canonical-vs-non-canonical link test with one asserting Joan is
    absent entirely, and added a test for the empty state when a club's only
    players are all non-canonical. Full suite: 308/308 passing; lint clean.
- **Scope change (2026-09-13, follow-up): Players tab list is no longer
  scoped by Season, and rows show only the player name.** Reported as a bug:
  a club's per-season player records meant a canonical player who only had a
  record for a season other than the currently-selected one silently
  disappeared from the Players tab whenever a specific season was selected
  (the tab's default season is the first available one, not "all seasons").
  - Root cause: `ClubDetailContent` computed one `players` list, filtered by
    both the selected season and competition, and fed it to `ClubSummaryPanel`
    *and* `PlayersPanel` alike — so the Players tab inherited a season scoping
    that made sense for the Summary tab's season-scoped stats but not for a
    roster whose whole point is "which people are members," independent of
    which season's data is currently being viewed elsewhere on the page.
  - Fix: added a second, Players-tab-only list, `rosterPlayers` (`ClubDetailPage.jsx`,
    computed alongside `players`): `sourcePlayers` filtered by the Competition
    selector only, deliberately omitting the season predicate. `players`
    (season+competition scoped) is unchanged and still feeds
    `ClubSummaryPanel`/`ClubStatsPanel` — this is scoped to the Players tab.
  - Because omitting the season filter can surface the same canonical player
    more than once (one row per season/federation record), `PlayersPanel`
    now dedupes via a new `dedupeByCanonicalPlayerId` helper (keeps the first
    matching record per `canonicalPlayerId`, drops any without one) before
    computing the search/summary/list — this also subsumes the earlier
    "canonical players only" filter, which is now folded into the same dedupe
    pass instead of a separate `.filter()`.
  - Since a deduped row no longer represents one specific season/license, the
    per-row subtext (`registrationName · Season: … · License: …`) was
    removed; each row now renders only `player.playerName ?? player.registrationName`
    as the link text, keeping the `→` affordance. The link's query string
    keeps the row's `source` but now always passes `season=all` (via the
    existing `ALL_SEASONS` constant) instead of one season's value, since the
    row itself is no longer season-specific.
  - Out of scope: the Source and Competition filters still apply to the
    roster (only Season was the reported problem); `ClubSummaryPanel` /
    `ClubStatsPanel` are untouched and remain season-scoped, as that scoping
    is meaningful for stats but not for "who is on the roster."
  - Tests: added a duplicate season record for the existing `'Maria Player'`
    canonical player (same `canonicalPlayerId`, season `'2023-2024'`) to
    exercise the dedupe path; added a test selecting a different season than
    any of the other players' records and asserting all three canonical
    players still render exactly once each; updated the roster-tab and
    canonical-link tests for the removed season subtext and the `season=all`
    link query respectively. Full suite: 309/309 passing; lint clean.
- **Plan rebuilt (2026-09-13):** replaced `# Build Plan` above with a
  concrete numbered plan for the two new proposed acceptance criteria
  (Players summary strip, Matches summary strip) from the Proposal section
  below. The previous build plan (name-search increment) is fully shipped;
  its steps and outcomes are preserved unchanged in this Notes section
  rather than re-listed in the new plan. Feature status stays `in-review`
  (unchanged) — this is a plan for additional, not-yet-approved scope, not a
  reopening of the shipped increment; do not move to `planned`/`ready` or
  begin implementation without explicit go-ahead, since the plan's roster
  and match aggregation helpers are new surface area this feature hasn't
  built before.
- **Implementation notes (2026-09-13): Players/Matches summary strips
  shipped as planned**, with the following adjustments made during build:
  - `clubMatches.js`'s private `resolveClubTeam` was exported unchanged and
    reused (no duplicated home/away detection logic).
  - `clubSummary.js` gained `aggregateRosterByCanonicalPlayer`,
    `getRosterByCompetition`, `getMostActivePlayers`, `getLatestSeasonPlayer`,
    `computeHomeAwaySplit`, `countPendingMatches`, `getFormGuide`,
    `getCurrentStreak`, `getNotableMatches` — all as planned, plus one fix
    found during implementation: `getNotableMatches` now excludes whatever
    was already picked as `biggestWin`/`biggestLoss` from the `closest`
    candidates, so a club with very few decided matches never has the same
    match rendered under two different "notable" labels (this surfaced
    immediately in the existing test fixtures, which reuse the same
    opponent/score for every match).
  - `PlayersPanel` (`ClubDetailPage.jsx`) now derives its whole list from
    `aggregateRosterByCanonicalPlayer` — this subsumes the old inline
    `dedupeByCanonicalPlayerId` (removed) since aggregation already dedupes
    while summing career stats.
  - `StatTile` and `MatchRow` were exported from `ClubSummaryPanel.jsx` and
    reused by both new strips instead of duplicating markup, per the
    Implementation Guidelines.
  - The Players summary strip's four tiles ended up as: Players (sub:
    federation/source count, via new `detail.playersSourcesCount`),
    Competitions covered (sub: most-represented competition + its player
    count), Most active (career) (sub: career match count), and Latest
    season (sub: the player on that season). The originally-drafted
    `detail.playersCompetitionsCovered` key was replaced by reusing
    `common.competitions` as the tile label once "competitions covered"
    became a tile value instead of a sub-label.
  - New CSS: `.record-bar-segment.is-fill` (single-color proportional bar
    for the roster-by-competition breakdown, distinct from the 3-color W/D/L
    `record-bar`), `.players-summary-strip`/`.matches-summary-strip` spacing
    wrappers, `.matches-form-guide`/`.form-guide-chip`/`.matches-current-streak`.
    The form-guide chips reuse `.match-row-result is-win/is-loss/is-draw`
    (the circular badge already used by `MatchRow`), not `.club-match-result`
    (a layout class, not a badge) as first sketched in the build plan.
  - i18n: all keys from the build plan were added to `ca.js`/`en.js`/`es.js`,
    minus `playersCompetitionsCovered` (superseded by `common.competitions`,
    see above) plus one addition, `playersSourcesCount`.
  - Tests: existing `ClubDetailPage.test.jsx` assertions that queried player
    names or match text by exact `getByText` broke once the summary strips
    started legitimately repeating names/matches also shown in the roster
    list / match hierarchy below (e.g. a small fixture roster's "most
    active" list repeats every player). Fixed by scoping those assertions
    with `within()` to the specific roster `<ul>`/match-hierarchy `<ul>`
    (both already had stable `aria-label`s) rather than changing behavior —
    the duplication itself is expected UI, not a bug. Added ~30 new unit
    tests in `clubSummary.test.js` for every new helper.
  - Verified: `npx vitest run` — full frontend suite 329/329 passing (41
    files); `npx eslint` clean on all changed files.
  - Not verified in a live browser: same limitation as the first increment
    and FEAT-00054 — exercising the page end-to-end needs the `api-rest`
    backend running, and no `.claude/launch.json` dev-server config exists
    in this repo.
- **Scope refinement (2026-09-13): Players-by-competition list capped at 5
  rows with a "See all" expander.** Requested after the summary strips
  shipped — the roster-by-competition card in `PlayersSummaryStrip`
  (`ClubDetailPage.jsx`) rendered every competition unconditionally, which
  gets long for a club spanning many competitions/divisions.
  - New `PlayersByCompetitionCard` component (`ClubDetailPage.jsx`) owns its
    own `expanded` boolean state, slices `byCompetition` to
    `COMPETITION_ROWS_LIMIT = 5` rows by default, and shows a `link-button`
    styled `{t('detail.seeAll')} →` (reusing the existing `detail.seeAll`
    key/`.link-button` class from `ClubSummaryPanel.jsx`'s "See all" pattern)
    when rows are hidden. Clicking it reveals every row and the button is
    then removed — same one-way "show more" pattern already used by
    `PlayerDetailPage.jsx`'s opponent table, not a collapsible toggle.
  - `PlayersSummaryStrip` now delegates the whole "Players by competition"
    card to this component instead of rendering it inline.
  - Tests: added a `ClubDetailPage.test.jsx` case with a 6-competition
    fixture asserting exactly 5 `listitem`s render by default, clicking
    "Veure-ho tot" reveals all 6, and the button then disappears. Full
    suite: 330/330 passing; lint clean.

# Proposal (2026-09-13): Expand the Players and Matches tab summaries

The current "summary" in this feature is a single shown/total count line
above the Players tab search box. Requested: expand the *summary concept* in
both the Players tab **and** the Matches tab into small stats/overview strips
— distinct from the club-wide `ClubSummaryPanel`/`ClubStatsPanel` added in
[FEAT-00054](./FEAT-00054-DETAILS.md), which are season-scoped and cover the
whole club rather than living inside these two tabs.

## State-of-the-art research (external sources)

Surveyed how table tennis platforms and apps present player- and
match-level stats at a glance:

- [ITTF/WTT Results platform](https://results.ittf.link/) — player profiles,
  head-to-head pages, and win/loss statistics
  ([Players Statistics](https://results.ittf.link/index.php/statistics/players-statistics),
  [Head to Head](https://results.ittf.link/index.php/head-to-head),
  [Player Profile](https://www.results.ittf.link/index.php/player-profile)).
  Common pattern: a compact career-record header (matches/wins/win rate),
  then head-to-head and recent-form sections below.
- [ITTF World Ranking (Wikipedia)](https://en.wikipedia.org/wiki/ITTF_World_Ranking)
  — background on how ranking points/rating summarize a player's level in one
  number, the pattern behind a single "headline" stat tile per player.
- [AiScore — Table Tennis H2H statistics](https://www.aiscore.com/head-to-head/table-tennis)
  and [Sofascore — Table tennis](https://www.sofascore.com/table-tennis) —
  live-score aggregators that foreground **form guides** (recent
  W/L/D chip rows) and head-to-head win rate as the primary at-a-glance
  stats for a match/player, ahead of any raw score list.
- [Table Tennis Totals — Live Stats & Analytics Dashboard](https://tabletennistotals.com/)
  — dashboard framing around over/under rates, sweep percentages, and
  head-to-head history for matches, i.e. match-level "notable outcome" stats
  rather than only a chronological list.
- [Stupa Analytics](https://apps.apple.com/us/app/stupa-analytics/id1480094754)
  and [TT Club Manager — Analytics](https://ttclubmanager.com/en/features/analytics)
  — club/team-oriented platforms; TT Club Manager in particular tracks
  wins/losses/sets/point differentials per player and per team as the
  standard "club roster" stat set, supporting a roster-composition summary
  (players per competition, most-active players) as a reasonable Players-tab
  addition.
- [PINGstory](https://play.google.com/store) and general club scorer apps
  (search: "table tennis club stats app form guide streak") — consistently
  surface **streaks** (current run of same-result matches) as a headline
  stat alongside win rate, motivating a "current streak" figure in the
  Matches tab strip.

Takeaway: across every source, the two stats that recur regardless of
platform are (a) a compact record/win-rate headline and (b) a short
chronological **form guide**; roster-composition and "notable match"
breakdowns (closest/biggest win/biggest defeat) are the next most common
secondary features. The proposal below leads with those.

## Proposed additions

Full layouts and content tables in the wireframes below:

- [`docs/frontend/club-details/players.md`](../frontend/club-details/players.md)
  — Players summary strip: player/competition/federation counts, a
  roster-by-competition breakdown, and a career "most active" mini-list.
- [`docs/frontend/club-details/matches.md`](../frontend/club-details/matches.md)
  — Matches summary strip: match/win-rate/home-vs-away/pending-result tiles,
  a last-5 form guide with current streak, and a "notable matches"
  (closest/biggest win/biggest defeat) mini-list.

Both strips are additive — they sit above the existing search box (Players)
or hierarchy (Matches) and read from data those views already fetch; neither
introduces new filter state or changes existing empty states.

## New acceptance criteria (proposed, not yet implemented)

See the corresponding unchecked criteria added to FEAT-00063 in
[FEATURES.md](./FEATURES.md). Open questions (per-player-season join date
for "Newest addition", career-wide match aggregation across seasons for
"Most active", and match ordering guarantees for "current streak") are
called out in the wireframe files' own Open Questions sections and should be
resolved before a build plan is written for this scope.
