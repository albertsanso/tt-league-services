# Build Plan

1. **Trace and define the canonical player-match set.**
   - Follow the player-details path through
     `tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/application/player/find/FindPlayerDetailsQueryHandler.java`,
     `tt-data-league-api-rest/src/main/java/org/cttelsamicsterrassa/data/api/rest/player/PlayerDetailsDto.java`,
     `tt-data-league-frontend/src/api/players.js`, and
     `tt-data-league-frontend/src/pages/PlayerDetailPage.jsx`.
   - Keep source and season identity explicit while filtering lineups. Treat
     one distinct match UUID as one player match after the active source,
     season, and competition filters.
   - Preserve the distinction between an unavailable season and a registered
     season with zero applicable matches.

2. **Implement the aggregation fix.**
   - Deduplicate matching lineups by match UUID before producing matches and
     season statistics, using deterministic first-lineup selection.
   - Register filtered player seasons before grouping matches so an available
     zero-match season returns `matchesPlayed: 0` and
     `winPercentage: null`.
   - Derive played matches, wins, losses, and win percentage from the same
     deduplicated match set.
   - Deduplicate frontend competition matches by match ID before aggregating,
     without changing the API contract or persistence schema.

3. **Add regression coverage.**
   - Cover duplicate lineup rows and zero-match registered seasons in
     `tt-data-league-core-domain/src/test/java/org/cttelsamicsterrassa/data/core/application/player/find/FindPlayerDetailsQueryHandlerTest.java`.
   - Cover visible played-match counts and duplicate-safe competition
     aggregation in
     `tt-data-league-frontend/src/pages/PlayerDetailPage.test.jsx`.
   - No REST mapping change was required; keep the existing nullable
     statistics contract intact.

4. **Validate and hand off for review.**
   - Domain focused tests pass with
     `mvn -pl tt-data-league-core-domain -am test`.
   - Frontend tests, lint, and build pass from
     `tt-data-league-frontend`.
   - REST-module validation remains a known environment blocker: the existing
     JPA test context fails because `ImportRunRegistry` is not configured in
     `JpaTestApplication`. Resolve that infrastructure issue before claiming
     full reactor validation.
   - Keep this file and `FEATURES.md` synchronized while the implementation is
     in review; do not close the feature as `done` without explicit approval.

# Implementation Guidelines

- Keep played-match counts and win percentages derived from the same applicable
  match set so the two values cannot disagree because of filtering or grouping.
- Preserve source-scoped identity and do not resolve player participation by
  unscoped names.
- Preserve the distinction between zero played matches and unavailable match
  data.
- Do not add domain fields or persistence columns for this fix.

# Acceptance Criteria

- [x] For every season with an available win percentage derived from match
  results, the corresponding played-match count is returned and displayed.
- [x] The underlying season-statistics query or aggregation no longer omits
  played matches because of inconsistent joins, filters, grouping, or source
  data handling.
- [x] Seasons with no played matches remain distinguishable from seasons whose
  match data is unavailable, without inventing a played-match count.
- [x] Regression coverage includes the reported mismatch and verifies that
  unaffected season statistics continue to display correctly.
- [x] The connected scatter plot uses larger crosses for played matches and
  triangles for win percentage.

# Notes

- 2026-09-07: Feature captured as an `idea`. The reported symptom is that some
  seasons show a win percentage while their played-match count is absent.
  Investigation and a concrete build plan are still required before
  implementation.
- 2026-09-07: Feature moved to `planned` after tracing the current
  player-statistics path. The implementation must first determine whether the
  mismatch originates in source-scoped lineup/match composition or in the
  frontend competition aggregation, then make both metrics use one canonical
  applicable match set.
- 2026-09-07: Implemented canonical match aggregation keyed by match UUID,
  deterministic lineup resolution, registered zero-match season statistics,
  and duplicate-safe frontend competition aggregation. Added domain and
  frontend regression coverage for duplicate lineups, zero-match seasons, and
  visible played-match counts.
- 2026-09-07: Focused domain tests and frontend tests, lint, and build pass.
  REST-module validation is blocked by the existing JPA test context failing
  because `ImportRunRegistry` is not configured in `JpaTestApplication`.
- 2026-09-07: Rebuilt the plan against the implementation currently present in
  the worktree. The delivered scope is the match-set/statistics consistency
  fix.
- 2026-09-07: Added distinct, enlarged connected-scatter markers: crosses
  represent played matches and triangles represent win percentage.
- 2026-09-07: Feature marked `done` after explicit user approval.
