# Build Plan

1. Extend the existing `FederatedPlayerRepositoryJpa` fragment predicate so
   every search fragment matches either the lower-cased federated-player name
   or the lower-cased `licenseId`, while preserving source scoping and the
   existing all-fragments/any-fragment semantics.
2. Preserve federated license IDs through the frontend player-search response
   normalization and render unique available IDs beside the canonical player
   name as `Name (license)`, without changing loading, empty, error, source,
   season, or navigation behavior.
3. Add focused regression coverage for repository search behavior and frontend
   response normalization/result rendering, then run the affected Maven and
   frontend checks.

# Acceptance Criteria

- [x] Player search matches the license ID included in the player's displayed name.
- [x] Searching by player name continues to work as before.
- [x] Search matching is applied consistently wherever players can be searched.

# Implementation Guidelines

Use the existing `FederatedPlayer.licenseId`/JPA `licenseId` fields and
repository port; do not add a new identity field or API contract. Preserve
name matching, source scoping, canonical result grouping, existing API
validation, and the frontend's existing loading, empty, error, source, season,
and navigation states. Keep the backend query case-insensitive and substring
based, matching the current name-search behavior.

# Notes

- This plan covers both backend search matching and frontend display of the
  license IDs that users search for; implementation should be validated against
  the acceptance criteria before moving the feature to `in-review`.
- Relevant implementation surfaces are
  `tt-data-league-core-repository-jpa/src/main/java/org/cttelsamicsterrassa/data/core/repository/jpa/player/impl/FederatedPlayerRepositoryJpa.java`,
  `tt-data-league-frontend/src/api/players.js`, and
  `tt-data-league-frontend/src/pages/PlayersSearchPage.jsx`.
- No schema or dependency change is required; the existing federated-player
  license mapping and frontend test/build toolchain are reused.
- Existing working-tree implementation changes may be present; they are
  preserved while this planning task documents the intended scope. Validation
  of the implementation belongs to the implementation/finalization step.
- The implementation is complete and the feature is in `in-review`; moving it
  to `done` requires explicit approval.
- User explicitly approved closure; the feature is now `done`.
