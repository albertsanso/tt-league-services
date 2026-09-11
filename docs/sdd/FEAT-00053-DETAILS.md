# Build Plan
1. **`tt-data-league-frontend/src/pages/ClubDetailPage.jsx` — `PlayersPanel`**
   - Change each `<li className="club-player-card card">` entry so that when
     `player.canonicalPlayerId` is present, the card content is wrapped in a
     `react-router-dom` `Link` to
     `routePaths.playerDetails(player.canonicalPlayerId, ` +
     `` `source=${encodeURIComponent(player.source)}&season=${encodeURIComponent(player.season)}` ``+ `)`
     (mirrors the `source=`/`season=` query built in `PlayersSearchPage.jsx:92-108`,
     but sourced from the player row's own `source`/`season` since a club-players
     row is already scoped to one federation record, unlike the aggregated
     search results).
   - When `player.canonicalPlayerId` is `null`/`undefined` (unmatched /
     unconsolidated player), keep the existing non-interactive `<li>` markup
     unchanged.
   - Keep the `<li>` as the list item in both cases; the `Link` (or a `<div>`
     in the non-linked case) goes inside it, carrying the `club-player-card
     card` classes so the visual card is unaffected. Add a trailing
     `<span aria-hidden="true">→</span>` affordance inside the link variant,
     matching `PlayersSearchPage.jsx`'s `club-result-link` treatment.
   - No prop plumbing is needed beyond what `players` already carries —
     `normalizePlayer` in `tt-data-league-frontend/src/api/clubs.js:178-205`
     already exposes `canonicalPlayerId`, `source`, and `season` on every
     club player.

2. **`tt-data-league-frontend/src/app.css`**
   - Add a rule so an anchor using the `club-player-card` class resets link
     styling (`text-decoration: none`, `color: inherit`) and gets a hover
     affordance, following the existing `.club-competition-card:hover`
     pattern (border-color/background shift) so the link only look
     interactive when it actually is one. Non-linked cards (`<div>` variant)
     are untouched.

3. **`tt-data-league-frontend/src/i18n/en.js`** (and the other locale files
   under the same `src/i18n/` directory, if any carry the `detail.*`
   namespace)
   - No new copy is strictly required — the card reuses the existing player
     name/registration/season/license text. If a hidden affordance label is
     wanted for screen readers, add `detail.viewPlayer` alongside the
     existing `detail.viewCompetition` key and apply it via
     `aria-label`/`sr-only` text on the link; otherwise the `→` stays
     `aria-hidden` and the link's own accessible name (the card text) is
     sufficient. Confirm the same key exists in every locale file used by
     `src/i18n/index.js` before relying on it.

4. **`tt-data-league-frontend/src/pages/ClubDetailPage.test.jsx`**
   - Extend the `club.players` fixture (around line 25) so one player has a
     `canonicalPlayerId` (e.g. `'canonical-player-id'`) and one does not,
     keeping distinct `source`/`season` values on the linked player so the
     built query string is verifiable.
   - Add a test in the Players view (`?view=players`) asserting:
     - the player with `canonicalPlayerId` renders as a link whose `href`
       equals `routePaths.playerDetails(...)` with the expected
       `source`/`season` query (assert via the rendered anchor's `href`
       rather than duplicating `routePaths` internals where practical), and
     - the player without `canonicalPlayerId` renders with the same visible
       text but has no enclosing link/anchor role.
   - Re-run the existing `'filters players by the selected competition'` test
     (line ~266) unchanged to confirm the new markup doesn't break filtering
     assertions; adjust its queries if they were relying on the previous flat
     `<li>` text structure.

5. **Verification**
   - `npm test -- ClubDetailPage` (or the project's equivalent vitest
     invocation) in `tt-data-league-frontend/`.
   - Manually sanity-check in the dev server: open a club with players that
     have and lack a `canonicalPlayerId`, confirm only the matched ones
     navigate to `/jugadors/:id` and that the target `PlayerDetailPage`
     loads with the passed `source`/`season` reflected in its own filters.

# Implementation Guidelines

# Notes
- Implemented: `PlayersPanel` in `tt-data-league-frontend/src/pages/ClubDetailPage.jsx`
  now renders each player with `canonicalPlayerId` as a `Link` to
  `routePaths.playerDetails(...)` carrying `source`/`season` query params;
  players without a `canonicalPlayerId` still render as a plain `div.club-player-card`.
- Added `a.club-player-card` hover/link-reset styles in
  `tt-data-league-frontend/src/app.css`.
- Extended `ClubDetailPage.test.jsx` fixtures with a `canonicalPlayerId`
  (linked) and `null` (unlinked) player plus a new test asserting the link
  `href` and the absence of an anchor for the unmatched player.
- Verified: `npx vitest run` (full frontend suite) — 32 files / 213 tests
  passing, including the updated `ClubDetailPage.test.jsx` (13 tests).
- No new i18n copy was added; the link reuses existing player text as its
  accessible name, matching the optional guidance in the build plan.
