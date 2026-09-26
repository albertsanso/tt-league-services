# Build Plan
Reference wireframe: `club-landing-wireframe.html` (published artifact, shared with the
user) shows the target layout, tab order, and sample copy for every section below.
The authoritative written spec derived from it is
[`/docs/frontend/club_details_landing.md`](../frontend/club_details_landing.md)
— layout tables, ASCII wireframes for the Summary and Stats tabs, and the
component legend there are binding for this feature; the HTML artifact is a
visual reference only.

1. **Tab model** (`tt-data-league-frontend/src/pages/ClubDetailPage.jsx`)
   - Extend the `VIEWS` enum with `SUMMARY` and `STATS`, ordered
     `Summary → Stats → Players → Matches`.
   - Change the default view (when no `view`/tab query param is present) from
     `VIEWS.PLAYERS` to `VIEWS.SUMMARY`.
   - Add the two new `.club-tab` buttons to the existing `role="tablist"` nav,
     reusing the current tab markup/CSS (`.club-tabs`, `.club-tab.is-active`)
     and `lucide-react` icons (e.g. `LayoutDashboard` for Summary, `BarChart3`
     for Stats). Keep tab state synced to the URL search param the same way
     Players/Matches already are, so each tab is shareable/deep-linkable.
   - Existing Players and Matches panels, their filter row
     (`.club-filters` — Source/Season/Competition selects), and their data
     fetching are unchanged.

2. **Data needed for Summary/Stats**
   - Reuse the already-fetched club detail payload
     (`normalizeClubDetailsResponse` in `src/api/clubs.js`): `playerCount`,
     `seasons[]`, `competitions[]` (`{name, source, season, matchCount, wins,
     draws, losses}` or `resultTotals`), `players[]`, and per-competition
     matches via `getClubMatchesByCompetitions`.
   - Add a small derivation module (e.g. `src/utils/clubSummary.js`) with pure
     functions, unit-testable in isolation:
     - `computeOverallRecord(competitions)` → `{wins, draws, losses,
       matchCount, winRate}` aggregated across the selected scope (default:
       current/latest season, matching the existing season-default logic used
       elsewhere on the page).
     - `computeWinRateBySeason(seasons, competitions)` → ordered array of
       `{season, winRate}` for the trend chart.
     - `getRecentMatches(matches, limit = 4)` → most recent matches sorted by
       `dateTime` desc, reusing the existing `normalizeMatch` shape
       (`homeTeam, awayTeam, result, round, dateTime, venue`).
     - `getTopPlayers(players, limit = 4)` — ranks players by **number of
       competitions played** (`player.competitions.length`, already present
       on the normalized player shape), not by win/loss record. See Notes
       for why per-player win/loss is out of scope for this feature.
   - No backend/API changes are required.

3. **Summary tab UI** (new `ClubSummaryPanel` component under
   `src/pages/club-detail/` or colocated with `ClubDetailPage.jsx`, matching
   existing component granularity in that folder)
   - Stat-tile row (4 tiles): players (current season / all-time sub-label),
     matches played, win rate (with W/D/L sub-label), competitions count.
   - Two-column card row (stacks to one column under the existing mobile
     breakpoint used elsewhere in `app.css`):
     - "Recent matches" card: W/D/L bar + legend, then up to 4 match rows
       (result pill, teams, competition/round/date, score), "See all →" link
       that switches to the Matches tab (preserving current filters).
     - "Most-capped players" card (originally "Top players" in the
       wireframe, relabelled — see Notes): up to 4 player rows (initials
       avatar, name, competition, record/stat), "See all →" link that
       switches to Players.
   - Empty/loading/error states follow the existing `.club-state` pattern used
     by Players/Matches.

4. **Stats tab UI** (new `ClubStatsPanel` component, same location convention)
   - Season selector reuses the existing `.club-filters` `<select>` pattern
     (same control as Players/Matches), not the segmented control shown in
     the wireframe — see Notes for rationale.
   - Per-competition rows: name + source, W/D/L counts, a proportional
     win/draw/loss bar, win-rate figure — one row per competition in the
     selected season, grouped/labeled by season when "All seasons" is chosen.
   - Win-rate-by-season trend as an inline SVG line chart (no charting
     library) sized to the card, with a scrollable wrapper for narrow
     viewports (`.trend-svg-wrap { overflow-x: auto }`), labeled points, and
     colors drawn from the existing CSS custom properties
     (`--accent-primary`, etc.) so it matches both light/dark theme tokens
     already defined in `src/index.css`.

5. **Styling**
   - Add new classes (`.stat-grid`, `.stat-tile`, `.summary-grid`,
     `.card-block`, `.record-bar`, `.match-row`, `.mini-list-item`,
     `.comp-row`, `.trend-card`, etc.) to `src/app.css` following existing
     naming (`club-*` prefix / BEM-ish patterns already in the file) and reuse
     existing tokens from `src/index.css` (`--accent-primary`,
     `--surface-card`, `--surface-app`, semantic success/error/warning colors)
     — do not introduce new hard-coded colors.
   - No new fonts/libraries: keep `Inter` for UI text and `DM Mono` for
     numeric figures, consistent with the rest of the app.

6. **i18n**
   - Add all new UI strings (tab labels, stat labels, "See all", empty
     states, chart axis labels) to `src/i18n/{en,es,ca}.js`. No hardcoded
     copy.

7. **Tests**
   - Unit tests for the new `clubSummary.js` derivation functions (edge cases:
     no matches yet, single season, missing competitions).
   - Component tests for `ClubSummaryPanel` and `ClubStatsPanel` (render with
     sample normalized data; verify tile values, "See all" tab-switch
     behavior, empty states).
   - Update `ClubDetailPage` tests: default tab is now Summary, tab order
     includes Stats, deep-linking via the view query param still works for
     all four tabs.

8. **Rollout**
   - Ship behind no flag (internal club-detail page); land as one PR once the
     open questions below are resolved, or land Summary/Stats using only
     currently-available data and file a follow-up for per-player win/loss if
     that turns out to require backend changes.

9. **Top performer player (backend aggregation + frontend card)** — added
   2026-09-12, see Notes for why this was deferred then reopened.

   Backend (`tt-data-league-core-domain`, `tt-data-league-api-rest`):
   - In `FindClubDetailsQueryHandler.composeDetails`
     (`tt-data-league-core-domain/.../club/find/FindClubDetailsQueryHandler.java`),
     compute per-player win/draw/loss by mirroring the existing pattern in
     `FindPlayerDetailsQueryHandler.toStatistics`
     (`tt-data-league-core-domain/.../player/find/FindPlayerDetailsQueryHandler.java`):
     load each player-season's `Lineup`s via
     `LineupRepository.findAllLineupsByPlayerSeasonIds`, group by
     `playerSeason.getId()`, and for each lineup's match compare
     `match.getWinnerTeam()` to `lineup.getTeam()` to derive win/draw/loss.
     Scope the aggregation the same way `summarizeCompetitions` already
     scopes team-level totals (per competition name + source + season), so
     the per-player totals line up with the existing per-competition ones.
   - Extend `FederatedClubPlayerReadModel` (in the same `dto` package as
     `ClubDetailsReadModel.java`) with `matchCount, wins, draws, losses`,
     reusing the existing `ResultTotalsDto`/`ResultTotals` shape already used
     for `ClubCompetitionReadModel` — do not invent a second shape.
   - Extend `ClubDetailsDto.PlayerDetailsDto`
     (`tt-data-league-api-rest/.../club/ClubDetailsDto.java`) and its
     `fromObject` mapper with the same fields, mirroring how
     `CompetitionDetailsDto` already exposes `matchCount` +
     `ResultTotalsDto(wins, draws, losses)`.
   - Add backend tests for the new aggregation: a player who only played in
     one competition/season, a player active across multiple competitions/
     seasons, and a player with zero lineups (should report 0/0/0, not error
     or get omitted).

   Frontend:
   - Extend `normalizePlayer` in `src/api/clubs.js` to read the new
     `matchCount`/`resultTotals` (or `wins/draws/losses`) fields per player,
     defaulting to `{wins: 0, draws: 0, losses: 0, matchCount: 0}` when
     absent — mirrors `normalizeCompetition`'s existing fallback pattern and
     keeps the frontend working against an API version that hasn't deployed
     the backend change yet.
   - Add `getTopPerformers(players, limit = 4, minMatches = 5)` to
     `src/utils/clubSummary.js`: rank by win rate computed from each
     player's `wins/draws/losses`, excluding players with fewer than 5
     matches in the selected scope so a 1–0 record can't outrank a 30–5
     one.
   - Add a third card, **"Top performer player"**, to `ClubSummaryPanel` —
     alongside the existing "Most-capped players" card, not replacing it.
     Reuse the existing `.mini-list`/`.mini-list-item` markup; show win rate
     (and W/D/L) as the stat instead of the competitions-played count.
   - New i18n keys: `detail.summaryTopPerformer` (card title) and a stat
     label such as `detail.summaryPlayerWinRate` (e.g. `'{{winRate}}% · {{wins}}W-{{draws}}D-{{losses}}L'`).
   - Unit tests for `getTopPerformers` (ties, below-floor players excluded,
     empty input) and component tests for the new card (values, empty state,
     floor behaviour).

# Implementation Guidelines

- Implementation must follow the layout, tab order, and content specified in
  [`/docs/frontend/club_details_landing.md`](../frontend/club_details_landing.md);
  any deviation from it needs an explicit note here before merging.
- Do not change the Players/Matches tab implementations, their filters, or
  their data-fetching hooks — this feature only adds two new tabs and reuses
  existing fetched data.
- Do not add a charting library; the win-rate trend is a single small SVG
  polyline, consistent with keeping the frontend dependency-light.
- Do not invent club fields that don't exist in `ClubDto`/`Club.java` (e.g. no
  logo image, no address) — the wireframe's club "mark" avatar is initials-only
  and stays that way unless product asks for real logos.
- All new copy goes through `react-i18next`; no literal strings in JSX.
- Keep tab/view state in the URL (`useSearchParams`) exactly like the current
  Players/Matches tabs, so Summary/Stats are shareable links too.
- Match the current mobile breakpoint(s) already defined in `app.css` for the
  two-column Summary layout and the stat-tile grid.
- The backend aggregation for "Top performer player" must reuse the existing
  `Lineup`/`ResultTotalsDto` patterns from `FindPlayerDetailsQueryHandler` and
  the competition-level totals in `FindClubDetailsQueryHandler` — do not add a
  new per-player result entity/table; this is a derived aggregation, not new
  stored state.
- "Most-capped players" (ranked by competitions played) and "Top performer
  player" (ranked by win rate) are two distinct cards answering two distinct
  questions ("who shows up most" vs "who performs best") — do not merge them
  into one card or let one silently replace the other.

# Notes

- Source wireframe/mockup was reviewed and shared with the user as an
  Artifact before this feature was filed; it is the reference for layout,
  copy tone, and component boundaries, not for exact pixel values.
- **Open question (resolved):** per-player win/loss record (used for the
  wireframe's "Top players" card) is not currently exposed by
  `normalizePlayer` / the club API response (`src/api/clubs.js`). Before
  implementing "Top players" as a ranked-by-record list, confirm whether
  this requires a backend aggregation (`ClubDto`/`api-rest` change) or can
  be derived client-side from already-fetched match data. Until resolved,
  the build plan allows shipping with a fallback ranking signal instead of
  blocking the whole feature on a backend change.
  - **Resolution:** no backend change for this feature. "Top players" ranks
    by number of competitions played (`player.competitions.length`), which
    is already present on the normalized player shape. Per-player win/loss
    is deferred to a future feature if product wants a true performance
    ranking; the wireframe's "18–3"-style stat is illustrative only and not
    part of this feature's scope.
- **Open question (resolved):** should the Stats season selector be the
  segmented control shown in the wireframe, or reuse the existing `<select>`
  filter pattern for consistency with Players/Matches? Build plan defaults
  to reusing `<select>` pending explicit design confirmation.
  - **Resolution:** reuse the existing `.club-filters` `<select>` pattern.
    Keeps one filter idiom across all four tabs and avoids introducing a new
    segmented-control component for a single use case; the wireframe's
    segmented control was a visual sketch, not a hard requirement.
- **Implementation notes (2026-09-12):**
  - Shipped as planned: `VIEWS.SUMMARY`/`VIEWS.STATS` added to
    `ClubDetailPage.jsx`, default view changed to Summary, tab order is
    Summary → Stats → Players → Matches, and the existing `.club-filters`
    row (Source/Season/Competition) is shared by all four tabs — Stats has
    no separate season selector, it reads the same `season` filter.
  - Added `src/utils/clubSummary.js` (`computeOverallRecord`,
    `computeWinRateBySeason`, `getRecentMatches`, `getTopPlayers`) with unit
    tests; `computeWinRateBySeason` omits seasons with zero recorded matches
    rather than showing a misleading 0% trend point.
  - Added `ClubSummaryPanel.jsx` and `ClubStatsPanel.jsx` (flat under
    `src/pages/`, matching the existing one-file-per-component convention —
    there is no `src/pages/club-detail/` subfolder in this codebase).
    "See all" links are buttons that call the same `updateFilters({ view })`
    used by the tab bar, so filters are preserved for free; they are not
    route links.
  - Summary's "Competitions" stat tile and tile sub-labels intentionally
    avoid the wireframe's "2 divisions this season" phrasing — `ClubDto`
    does not model a division concept, so the sub-label just says "this
    season" / "all seasons".
  - Trend chart reuses the existing `.history-connected-chart` /
    `.chart-line.wins-line` / `.wins-point` classes from
    `PlayerDetailPage.jsx`'s connected scatter chart instead of inventing a
    separate `.trend-svg-wrap`; this already provides the required
    horizontal-scroll wrapper for narrow viewports.
  - New i18n keys were added to `ca.js` (base) plus `en.js`/`es.js`
    overrides, following each file's existing pattern (inline `{ ...ca.x }`
    spreads for the bulk keys, individual `xx.detail.key = '...'` append
    lines for the rest).
  - Added component tests for `ClubSummaryPanel`/`ClubStatsPanel` and
    extended `ClubDetailPage.test.jsx` for the new default tab, tab order,
    Stats deep-link, and the Summary "See all" tab switches.
  - Not verified in a live browser: exercising the page end-to-end needs the
    `api-rest` backend running (the Vite dev server proxies `/api` to
    `localhost:8080`); verification here is automated tests (44 passing
    across the new/updated files, 236 passing for the whole frontend suite)
    plus lint, not a manual click-through.
- **New requirement (2026-09-12): "Top performer player."** The deferral
  above ("Top players ranks by competitions played, not by win/loss record")
  covered shipping without this; it did not rule it out permanently. Product
  asked for an actual best-results ranking alongside the existing
  "Most-capped players" card. Decisions made when adding this to the plan
  (see build plan step 9):
  - **Scope:** added to this feature (FEAT-00054) rather than filed as a
    separate feature, since FEAT-00054 was still open (in-progress) when the
    request came in.
  - **Data source:** backend aggregation (`ClubDto`/`api-rest` change), not
    client-side derivation from match lineups — confirmed via
    `FindPlayerDetailsQueryHandler.toStatistics` as the existing pattern to
    mirror. Rejected the client-side-only alternative (fetching full match
    details per match to tally lineups in the browser) because it would mean
    N extra detail requests per club instead of one aggregated field on the
    existing response.
  - **Open question (resolved):** minimum-matches floor before a player is
    eligible for "Top performer player" — without one, a player with a
    single win (100%) would outrank a player with 30 wins and 5 losses
    (86%).
    - **Resolution:** floor of 5+ matches in the selected scope. Players
      below the floor are excluded from the ranking (not shown with a 0%
      or dashed stat) — see build plan step 9's `getTopPerformers(players,
      limit = 4, minMatches = 5)`. Revisit if a club's roster is small
      enough that 5 matches excludes most/all players.
  - **Implementation notes (2026-09-12):** shipped as planned in build plan
    step 9.
    - Backend: `FindClubDetailsQueryHandler` gained a `LineupRepository`
      dependency and a `summarizePlayerResults` helper that dedupes lineups
      by `(playerSeasonId, matchId)`, restricts them to matches already in
      the club's scoped `matches` list (so a player's results from other
      clubs/teams never leak in), and reuses the existing `Totals` nested
      class (also used by `summarizeCompetitions`) for the win/draw/loss
      tally — draws are counted explicitly (winner team null), unlike
      `FindPlayerDetailsQueryHandler.toStatistics`, to stay consistent with
      `ClubCompetitionReadModel`'s existing wins+draws+losses=matchCount
      invariant.
    - `FederatedClubPlayerReadModel` and `ClubDetailsDto.PlayerDetailsDto`
      both gained `matchCount`/win-loss fields at the end of their record
      components, with a same-shape legacy constructor overload added
      (defaulting the new fields to 0) so every existing call site —
      including `FindFederatedClubDetailsQueryHandler` (a different
      page/feature that also builds `FederatedClubPlayerReadModel`, left
      untouched and reporting 0/0/0/0) and `ClubControllerTest` — kept
      compiling and passing with zero source changes there.
    - Added `FindClubDetailsQueryHandlerTest` (win/draw/loss across three
      matches for one player, zero totals for a player with no lineups).
      Ran the full `tt-data-league-core-domain` /
      `tt-data-league-api-rest` test suites (club/player filters): all
      green. A full reactor `mvn test` surfaces pre-existing, unrelated
      failures in `tt-data-league-core-repository-jpa`
      (`UserRepositoryJpaTest`, `CommunityStatisticsAggregateQueriesTest`,
      `ImportSchemaTest`, etc. — root cause: a missing `ImportRunRegistry`
      bean for `FindImportRunStatusQueryHandler`); confirmed via `git
      stash` that these fail identically on `main` without this feature's
      changes, so they are out of scope here.
    - Frontend: `normalizePlayer` (`src/api/clubs.js`) reads
      `matchCount`/`resultTotals` per player with the same zero-default
      fallback pattern `normalizeCompetition` already uses, so the page
      keeps working against an API build that hasn't deployed the backend
      change yet. Added `getTopPerformers(players, limit = 4, minMatches =
      5)` to `clubSummary.js` (with unit tests: ranking, floor exclusion,
      tie-break by matches-played then name, limit/minMatches params,
      empty states) and a third `ClubSummaryPanel` card, "Top performer"
      (`detail.summaryTopPerformer`), alongside — not replacing —
      "Most-capped players"; it reuses the `.mini-list`/`.mini-list-item`
      markup with a `{{winRate}}% · {{wins}}W-{{draws}}D-{{losses}}L` stat
      instead of the competitions-played count. No new CSS was needed.
    - Full frontend suite: 244 tests passing; lint clean.
