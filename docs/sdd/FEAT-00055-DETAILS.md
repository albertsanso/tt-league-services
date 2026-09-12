# Build Plan
1. **Confirm no backend changes are needed.** `GET /api/v1/match/{id}` (`MatchController.java`,
   `MatchDetailDto.java`) already returns everything the wiremock needs:
   - `homeTeam`/`awayTeam` names, `dateTime`, `round`, `city`/`venue`, `refereeName`,
     `homeGamesWon`/`awayGamesWon` → score header + venue/referee footer.
   - `lineups[]` (`letter`, `position`, `player.license`, `ranking`) → per-row Lic/Rk.
   - `games[]` (`crossover` = `"A vs Y"` style pairing string, `homePlayer`/`awayPlayer`,
     `homeSetsWon`/`awaySetsWon` = parcial, `cumulativeHomeSetsWon`/`cumulativeAwaySetsWon` =
     acumulado, `winnerSide`, `sets[]` with `homePoints`/`awayPoints`) → the individual-matches
     table rows and the J1-J5 columns.
   No new query, DTO field, or endpoint is required for the acta reconstruction itself.

2. **Add the route and entry point.**
   - Add `MatchActaPage.jsx` under `tt-data-league-frontend/src/pages/`, lazy-loaded in
     `App.jsx` the same way `MatchDetailPage` is, at `partits/:matchId/acta`
     (`<ProtectedPage><MatchActaPage /></ProtectedPage>`, same `matches:read` permission as the
     existing match routes).
   - In `MatchesSearchPage.jsx`, add a "View acta" link per result row (next to/inside the
     existing `<Link to={`/partits/${match.id}`}>`), pointing to `/partits/${match.id}/acta`.
   - In `MatchDetailPage.jsx`, add the same link so the acta is reachable both from the search
     results and from the match detail page.

3. **Compute a lightweight acta view-model from `getMatchDetails` in `MatchActaPage.jsx`:**
   - `jornada_numero` = `round`; `fecha_partido`/`hora_partido` split from `dateTime`.
   - `equipo_local_nombre`/`equipo_visitante_nombre` = `homeTeam.name`/`awayTeam.name`;
     `juegos_local`/`juegos_visitante` = `homeGamesWon`/`awayGamesWon`.
   - Per row (sorted by `gameNumber`): split `crossover` (`"A vs Y"`) into
     `pos_local_letra`/`pos_cruce_letra`; resolve each player's `Lic`/`Rk` by matching
     `game.homePlayer`/`game.awayPlayer` against `lineups[]` on `playerSeasonId` (fall back to
     `license` if the id is absent); build `set1..set5` from `game.sets` ordered by `setNumber`,
     leaving trailing columns blank for matches that ended before 5 sets; `parcial` =
     `homeSetsWon`-`awaySetsWon` with the winning side flagged from `winnerSide` for the
     accent-color emphasis described in the wiremock; `acumulado` =
     `cumulativeHomeSetsWon`-`cumulativeAwaySetsWon`.
   - Totals footer: `juegos_total_local`/`juegos_total_vis` = sum of `homeSetsWon`/`awaySetsWon`
     across all games; `puntos_total_local`/`puntos_total_vis` = sum of every set's
     `homePoints`/`awayPoints` across all games. (Verified against the reference screenshot: both
     totals fall out of existing `games[].sets[]` data with no new backend support.)
   - `alineacion_local`/`alineacion_vis`: no dedicated "lineup rating" value exists anywhere in
     the domain today (only per-player `ranking`); compute it as the sum of `ranking` across each
     side's `lineups[]` entries as a best-effort approximation, and note the approximation in
     `# Notes` below rather than treating it as a verified match to the source figure.

4. **Render the acta view** (`MatchActaPage.jsx` + a co-located stylesheet/class names) following
   the section-by-section structure in
   [acta-simplified-rfetm.md](../frontend/actas/acta-simplified-rfetm.md): top jornada bar, score
   header (with a print icon wired to `window.print()`), the individual-matches table, the
   alignment/totals footer row, and the venue/referee lines. Reuse the existing `card`/
   `page-block` styling conventions used by `MatchDetailPage.jsx` rather than introducing a new
   design system.

5. **Handle the "no acta available" case** (AC3): if `getMatchDetails` returns a match with no
   `games` (empty lineups/games, e.g. a walkover or not-yet-imported report), render a
   "no acta available" message instead of an empty table, and disable/hide the "View acta" link
   in `MatchesSearchPage.jsx`/`MatchDetailPage.jsx` for such matches (`match.homeGamesWon == null
   && match.awayGamesWon == null`, mirroring how `MatchDto` already surfaces those fields in
   search results — extend `MatchDto`/`SearchMatchesQuery` only if the search payload turns out
   not to carry enough signal once this is implemented against real data).

6. **i18n**: add new keys (e.g. under a new `actaPage` namespace) to `en.js`/`es.js`/`ca.js` for:
   jornada, lugar, árbitro/referee, alineación, juegos, puntos, "view acta" link label, "no acta
   available", print/back actions — following the existing `matchesPage.*` naming convention.

7. **Tests**:
   - `MatchesSearchPage.test.jsx` / `MatchDetailPage.test.jsx`: assert the acta link renders with
     the right href when games exist, and is absent/disabled when they don't.
   - New `MatchActaPage.test.jsx`: render against a fixture built from a match-detail payload
     shaped like the reference screenshot (`ARTEAL SANTIAGO 4-1 UBU TPF BURGOS 2031`, 5 games)
     and assert the header score, all 5 table rows (including a <5-set row to check blank
     trailing set columns), the `Juegos`/`Puntos` totals (`13/8`, `210/195`), and the venue/
     referee lines match the source values.

8. **Docs**: leave `acta-simplified-rfetm.md` as the single source of truth for layout; if
   implementation surfaces a genuine mismatch (e.g. the alineación approximation above), record
   the deviation in `# Notes` here before merging, per the same convention used in FEAT-00054.

# Implementation Guidelines

- Frontend: the acta view/reconstruction must follow the wiremock spec in
  [acta-simplified-rfetm.md](../frontend/actas/acta-simplified-rfetm.md) — layout, `@variable`
  fields, and section structure (score header, individual-matches table, alignment/totals footer,
  venue/referee line) come from that document.

# Notes

- The "Alineación ABC/XYZ" total was implemented as the sum of `ranking` across each side's
  `lineups[]` entries. Verified against the reference screenshot values (ARTEAL SANTIAGO vs UBU
  TPF BURGOS 2031): `2465.1 + 1998.7 + 2293.1 = 6756.9` and `2448.0 + 2136.9 + 2070.7 = 6655.6`,
  matching the source exactly — this is the correct formula, not an approximation.
- "Selecting the acta action opens or downloads the acta file" was implemented as navigating to
  an in-app reconstructed acta view (`MatchActaPage`, per acta-simplified-rfetm.md), built from
  data already in `GET /api/v1/match/{id}`, rather than opening/downloading an original source
  file — the system does not store the original acta PDF/document anywhere. A "Print" action
  (`window.print()`) covers the download-as-PDF use case via the browser's native print dialog.
- Implemented in `MatchActaPage.jsx`, wired at `/partits/:matchId/acta`, linked from both
  `MatchesSearchPage.jsx` and `MatchDetailPage.jsx` (hidden/absent when the match has no games).
  `MatchesSearchPage.jsx`'s result list/card markup was aligned with the shared
  `club-result-list`/`club-result card`/`club-result-link` classes already used by
  `PlayersSearchPage.jsx`/`ClubsSearchPage.jsx`.
- **Follow-up (post-review) request:** remove the match detail page entirely and open the acta as
  a dialog rather than a separate route. Implemented as:
  - Deleted `MatchDetailPage.jsx` (and its route `/partits/:matchId`) — the Matches search results
    are now the only match-related listing; there is no other per-match page to navigate to.
  - Moved the acta view out of routing and into `src/components/matches/MatchActaDialog.jsx`, a
    modal (`role="dialog"`, `aria-modal="true"`) taking `{ matchId, onClose }` props instead of
    reading `useParams()`. It closes on its own Close button, on `Escape`, or on a backdrop click
    (clicks inside the panel are stopped from bubbling to the backdrop handler).
  - `MatchesSearchPage.jsx` keeps local `actaMatchId` state; each result's "View acta" affordance
    is now a `<button>` (not a `<Link>`) that sets it, and the dialog is rendered conditionally at
    the bottom of the page. The match summary itself is now a plain, non-interactive block (no
    link target remains for it).
  - Removed `routePaths.matchDetails`/`matchActa` and the corresponding `routesMeta` entries,
    dynamic-route regexes, and `routes.matchDetail` i18n key (now dead); `routes.matchActa` was
    kept as the dialog's `aria-label` text.
  - `acta-simplified-rfetm.md` still describes the acta's *content* layout accurately; only its
    presentation shell changed from a full page to a centered overlay dialog
    (`.acta-dialog-overlay`/`.acta-dialog` in `app.css`), including adjusted `@media print` rules.
- **Follow-up (post-review) request:** simplify the dialog by removing the Cerrar/Imprimir action
  bar and the Alineación ABC/XYZ and Puntos totals. Implemented as:
  - Removed the `.acta-actions` button bar (Close + Print) from `MatchActaDialog.jsx` entirely; the
    dialog now closes only via `Escape` or a backdrop click (focus moves to the dialog panel itself
    on open instead of a close button). The print action (`window.print()`) is gone — there is no
    remaining UI path to print the acta.
  - Removed the Alineación ABC/XYZ and Puntos totals from the totals footer row, keeping only
    Juegos. `computeTotals()` in `MatchActaDialog.jsx` no longer computes `pointsTotal`/lineup
    sums. Dropped the now-unused `actaLineupLocal`/`actaLineupVisitor`/`actaPointsTotal`/
    `actaPrint` i18n keys (`en.js`/`es.js`/`ca.js`); `actaGamesTotal` stays.
  - `acta-simplified-rfetm.md` is now stale on two points and was not rewritten (per its role as a
    layout wiremock spec, corrections are tracked here rather than in the doc): the score header's
    print icon (🖨️) is no longer implemented, and the "ALIGNMENT / TOTALS FOOTER ROW" section's
    `Alineación ABC`/`Alineación XYZ`/`Puntos` columns are no longer rendered — only the `Juegos`
    column from that row is implemented.
