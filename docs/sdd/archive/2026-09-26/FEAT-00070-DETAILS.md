# Build Plan
1. `tt-data-league-frontend/src/pages/MatchSummaryPage.jsx`, `TeamPanel`
   (around line 153): inside the `match-summary-form-card` header, above the
   existing `<h3><TeamName .../></h3>`, add a small label element (e.g.
   `<p className="match-summary-team-side">{t(side === 'home' ?
   'matchSummaryPage.home' : 'matchSummaryPage.away')}</p>`) driven by the
   `side` prop already passed in (`"home"` / `"away"`).
2. `tt-data-league-frontend/src/app.css`: add a `.match-summary-team-side`
   rule near the existing `.match-summary-team*` rules (around line 4397),
   styled like the existing `.section-label` treatment (small, uppercase,
   `var(--text-muted)` gray) so it reads as a subtle header above the team
   name.
3. i18n: add `home` / `away` keys under `matchSummaryPage` in
   `tt-data-league-frontend/src/i18n/ca.js` (source of truth), and the
   `en.js` / `es.js` overrides, following the existing key style in that
   block.
4. Tests: extend `tt-data-league-frontend/src/pages/MatchSummaryPage.test.jsx`
   to assert the home panel shows the "Home" label and the away panel shows
   the "Away" label (translated via the test's i18n setup).

# Implementation Guidelines

- Reuse the `side` prop already threaded through `TeamPanel`; do not derive
  home/away from team IDs again.
- Keep the label purely presentational — no change to `match.homeTeam` /
  `match.awayTeam` data or ordering logic.
- Match the visual weight of other small gray meta labels already on the
  page (e.g. `.section-label`) rather than introducing a new color token.

# Notes

- Implemented: `TeamPanel` in `MatchSummaryPage.jsx` renders a
  `.match-summary-team-side` label ("Local"/"Visitant" ca, "Local"/"Visitante"
  es, "Home"/"Away" en) above the team name, styled like `.section-label`
  (small, uppercase, `var(--text-muted)`).
- `MatchSummaryPage.test.jsx`: added a test asserting both labels render.
  `npx vitest run src/pages/MatchSummaryPage.test.jsx`: 12 passed.
- Not verified in a running browser (would require the backend API); only
  the component test confirms rendering.
