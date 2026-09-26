# Build Plan
1. **Backend domain — `MatchSearchCriteria`**
   (`tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/domain/match/model/MatchSearchCriteria.java`)
   - Replace the `phase` field with `clubName` (trim, blank → `null`, same normalization pattern
     currently used for `playerName`/`phase`).
   - Remove the phase-only convenience constructor overload (mirrors the current `playerName`-only
     overload) or update it to the new field list — keep exactly one legacy-shaped overload if any
     other caller still constructs the record positionally without `clubName`.
   - Update `MatchSearchCriteriaTest.java` (`tt-data-league-core-domain/src/test/java/.../match/model/MatchSearchCriteriaTest.java`):
     replace the phase-normalization tests with equivalent `clubName` normalization tests
     (blank → null, trims surrounding whitespace).

2. **Backend persistence — search query** — SHIPPED SHAPE (superseded the original single-string-LIKE
   plan; see step 10 below)
   (`tt-data-league-core-repository-jpa/src/main/java/org/cttelsamicsterrassa/data/core/repository/jpa/match/impl/MatchRepositoryHelper.java`,
   `tt-data-league-core-repository-jpa/.../match/impl/MatchRepositoryJpa.java`)
   - Drop the `:phase` predicate and `@Param("phase")` parameter from both `search`/`countSearch`.
   - Add a `clubName` match that is case-insensitive AND matches ANY whitespace-separated fragment of
     the search term against either team's name (not the whole phrase as one substring) — e.g.
     "oscar campos" must match a name containing "oscar" OR "campos". Implemented as up to 5 bounded
     fragment slots (`clubNameF0`..`clubNameF4`) OR'd together in JPQL, each checked against
     `m.homeTeam.name`/`m.awayTeam.name`; `MatchRepositoryJpa.nameFragments()` splits the raw
     `clubName`/`playerName` string on whitespace into these slots (unused slots are `""`, treated as
     "no fragment"). The same fragment scheme applies to `playerName` inside the existing lineup
     `EXISTS` subquery. Bounded to 5 fragments rather than a fully dynamic Criteria/Specification
     query, to stay close to the already-tested query shape (ordering/pagination) — see step 10.

3. **Backend API — controller and DTO wiring**
   (`tt-data-league-api-rest/src/main/java/org/cttelsamicsterrassa/data/api/rest/match/MatchController.java`)
   - Replace the `phase` request parameter with `clubName` (`@RequestParam(name = "clubName", required = false)`)
     and pass it into `MatchSearchCriteria` in place of `phase`.
   - `match.phase` continues to exist on `Match`/`MatchJPA`/`MatchDto`/`MatchSearchReadModel` for
     match identity and result display (round/phase/venue) — only the *search filter* parameter is
     removed; do not touch the stored `phase` column or result rendering.
   - Check `PlayerControllerTest`-style REST tests for a `MatchControllerTest`; if one exists, update
     any request built with `phase=` to use `clubName=` where it exercises the search endpoint, and
     add a case asserting partial/case-insensitive club-name matching.

4. **Frontend API client**
   (`tt-data-league-frontend/src/api/matches.js`)
   - In `searchMatches`, replace the `['phase', filters.phase]` optional param with
     `['clubName', filters.clubName]`.

5. **Frontend search page — filters and state**
   (`tt-data-league-frontend/src/pages/MatchesSearchPage.jsx`)
   - Replace `phase` with `clubName` in the `filters` memo (`params.get('clubName') ?? ''`) and in the
     abort-effect dependency list.
   - Remove the `<label>` for the Phase input; add a free-text "Club name" field (same shape as the
     existing Player name field) positioned after Player name in the filter grid, matching the layout
     recorded in `docs/frontend/match-search-filters-mockup-spec.md` (see step 7).
   - Result-row rendering already reads `match.phase` from the API response for display — leave that
     line (`MatchesSearchPage.jsx:215`) untouched; only the filter input is removed, not the phase data
     shown per result.

6. **i18n**
   (`tt-data-league-frontend/src/i18n/ca.js`, `en.js`, `es.js`)
   - Add a `clubName` key under `matchesPage` in all three locale files (Catalan primary, e.g.
     `clubName: 'Nom del club'`; English/Spanish equivalents).
   - Leave the existing `phase: 'Fase'` key in place — it is still used to label the phase shown in
     match results/details, only the filter field is being dropped.

7. **Design doc**
   (`docs/frontend/match-search-filters-mockup-spec.md`)
   - Update the ASCII mockup to add a "Club name" text field (same style as "Player name") and drop
     any remaining trace of a Phase filter field, so the file reflects the shipped layout: Font /
     Temporada / Competició / Date range, Player location, Player name, Club name.

8. **Registry**
   - Update FEAT-00056's acceptance criteria reference (registry and details) to point at
     `docs/frontend/match-search-filters-mockup-spec.md` instead of the non-existent
     `matches_search_improvements.md`.

9. **Tests**
   - Update `MatchesSearchPage.test.jsx` (`tt-data-league-frontend/src/pages/MatchesSearchPage.test.jsx`):
     remove phase-filter assertions/interactions, add club-name filter interaction and assert it is
     sent as a `clubName` query param.
   - Update `tt-data-league-frontend/src/api/matches.test.js` if it asserts on the `phase` param.
   - Run backend tests for the touched modules (`core-domain`, `core-repository-jpa`, `api-rest`) and
     frontend tests (`tt-data-league-frontend`) after the change.

10. **Follow-up: fragment-based OR matching for `playerName`/`clubName`**
    - AC 3 ("partial fragment matches") means matching ANY fragment of a multi-word search term, not
      the literal multi-word substring. Implemented via `MatchRepositoryJpa.nameFragments()` (splits on
      whitespace, caps at `MAX_NAME_FRAGMENTS = 5`) feeding the bounded OR'd JPQL slots described in
      step 2. Real names/club names essentially never exceed 5 words.
    - Tests: rewrote `ImportSchemaTest.searchMatchesFiltersByClubNameCaseInsensitivelyAndByAnyFragment`
      (the earlier version had a false-positive: searching "no such club" would match "CLUB B" once
      fragments are OR'd) and added
      `ImportSchemaTest.searchMatchesFiltersByPlayerNameCaseInsensitivelyAndByAnyFragment`.

11. **Follow-up: Competition becomes optional — only Source and Season are required to search**
    - `MatchSearchCriteria` (`tt-data-league-core-domain/.../match/model/MatchSearchCriteria.java`):
      constructor now only requires `source`/`season`; `competition` normalizes blank → `null` like
      `playerName`/`clubName` instead of throwing.
    - `MatchRepositoryHelper`'s `search`/`countSearch` JPQL: `m.competition = :competition` becomes
      `(:competition is null or m.competition = :competition)`.
    - `MatchController` (`tt-data-league-api-rest/.../match/MatchController.java`): `competition`
      request param changed to `required = false`.
    - Frontend: `MatchesSearchPage.jsx`'s `canSearch` drops the `filters.competition` requirement
      (only `source && season`); `matches.js`'s `searchMatches` sends `competition` as an optional
      param instead of a required one. Competition remains selectable in the UI (still gated on
      Season being chosen) and, when set, still filters exactly as before.
    - Tests: `MatchSearchCriteriaTest.requiresSourceAndSeasonButNotCompetition`,
      `ImportSchemaTest.searchMatchesWithoutACompetitionFilterReturnsMatchesAcrossCompetitions`, and
      two `MatchesSearchPage.test.jsx` cases (search enabled/searches with only source+season; search
      stays disabled with only source).

12. **Follow-up: pagination for Matches search results**
    - Backend already returns everything needed per page (`MatchController.SearchResponse`:
      `matches`, `total`, `page`, `pageSize`, `hasNext`) — no backend change required.
    - `MatchesSearchPage.jsx`: replaced the accumulating `loadMore()` (which appended pages into one
      long list) with `goToPage(page)`, which fetches exactly one page and replaces `results` (mirrors
      the `search()` request/abort pattern). Added `totalPages = Math.ceil(total / pageSize)`.
    - Added a `<nav className="pagination">` block (reusing the existing `.pagination` CSS class and
      First/Previous/pageOf/Next pattern already used in `UsersRolesPage.jsx`, extended with a Last
      button) with four controls: First (`goToPage(0)`), Previous (`goToPage(page - 1)`), Next
      (`goToPage(page + 1)`), Last (`goToPage(totalPages - 1)`). First/Previous disabled on page 0;
      Next/Last disabled when `!hasNext`. The whole nav is hidden when `totalPages <= 1`, matching the
      `UsersRolesPage` convention. The nav markup is built once (`pagination` variable) and rendered
      twice — above the results `<ul>` and below it — so it doesn't have to be scrolled to from either
      end of a long results list.
    - i18n: added `common.first`/`common.last` (`ca`/`en`/`es`) alongside the existing
      `common.previous`/`common.next`/`common.pageOf`; added `matchesPage.paginationAriaLabel` and
      removed the now-unused `matchesPage.loadMore` key from all three locales.
    - Tests: `MatchesSearchPage.test.jsx` — pagination hidden on a single page; both copies present
      with First/Previous disabled and Next/Last enabled on page 1; clicking either Next instance
      requests page 1 and updates both page labels; clicking either Last instance requests the last
      page and flips the disabled state on both copies.

# Implementation Guidelines

- Scope decision: the Phase filter is removed end-to-end (frontend UI, API parameter, JPQL predicate,
  `MatchSearchCriteria` field) rather than just hidden in the UI, because it is described as
  unnecessary complexity in the feature goal and no other caller relies on `MatchSearchCriteria.phase()`
  for search. The stored `phase` column, `Match`/`MatchJPA`/`MatchDto`/`MatchSearchReadModel.phase()`,
  and its use in match identity/display are untouched.
- Club name matching targets `Team.name` (the value already surfaced to the frontend as
  `match.homeTeam` / `match.awayTeam`), not `FederatedClubJPA.name`, so the filter matches what users
  see in the results list.
- Follow the existing `playerName` pattern exactly for case-insensitivity/partial matching (`lower(...)
  like lower(concat('%', :param, '%'))`, empty string sentinel for "no filter") to keep the two text
  filters behaviorally consistent, per acceptance criterion 3.
- No backwards-compatibility shim for the removed `phase` search parameter: any client still sending
  `phase` on `/api/v1/match/search` will have it silently ignored (unknown query params are ignored by
  Spring `@RequestParam` binding) rather than erroring — this is acceptable since only the shipped
  frontend calls this endpoint.
- "Partial fragment match" means ANY whitespace-separated fragment of the term, OR'd together (e.g.
  "oscar campos" matches "Campos, Oscar"), not the literal multi-word phrase as one substring. Bounded
  to 5 fragments per field via fixed JPQL slots (not a fully dynamic Criteria/Specification query) to
  stay within the already-tested query shape, since this module's Spring-context tests currently
  cannot be executed to verify a larger rewrite (see the blocker note below).
- Competition is optional for search: only Source and Season are mandatory. `MatchSearchCriteria`,
  the JPQL, the controller, and the frontend `canSearch` gate all treat a missing/blank competition as
  "no competition filter" rather than an error.

# Notes
- AC #4 originally referenced a non-existent `docs/frontend/matches_search_improvements.md`; the user
  confirmed reusing/updating `docs/frontend/match-search-filters-mockup-spec.md` instead. The registry
  and details acceptance criteria are updated accordingly as part of this plan.
- Implementation complete: `MatchSearchCriteria`, `MatchRepositoryHelper`/`MatchRepositoryJpa`,
  `MatchController`, `matches.js`, `MatchesSearchPage.jsx`, i18n (`ca`/`en`/`es`), and
  `match-search-filters-mockup-spec.md` all updated per the build plan. New/updated tests:
  `MatchSearchCriteriaTest` (clubName normalization), `ImportSchemaTest.searchMatchesFiltersByClubNameCaseInsensitivelyAndPartially`
  (replacing the old phase-filter test), and two new `MatchesSearchPage.test.jsx` cases (clubName sent
  as a query param; no Phase field rendered).
- Verified: frontend suite (256 tests, including the new ones) and the full `tt-data-league-api-rest`
  module suite (including `PlayerControllerTest`) pass; `MatchSearchCriteriaTest` passes standalone;
  all touched backend modules compile and test-compile cleanly.
- Follow-up fix: "partial fragment match" means matching ANY whitespace-separated fragment of the
  search term (OR across fragments), not the whole phrase as one substring — e.g. searching
  "oscar campos" must match a player named "Campos, Oscar" even though the substring "oscar campos"
  never appears verbatim. Implemented in `MatchRepositoryJpa`/`MatchRepositoryHelper` by splitting
  `playerName`/`clubName` into up to 5 whitespace-separated fragments (`nameFragments()`) and OR-ing a
  `LIKE '%fragment%'` per fragment/field in the JPQL (bounded slots rather than a fully dynamic
  Criteria/Specification query, to stay within the already-tested query shape and pagination/ordering
  logic given this module's tests cannot currently be executed — see the blocker below). Real
  names/club names essentially never exceed 5 words, so this bound is not expected to be user-visible.
- Pre-existing, unrelated blocker: `tt-data-league-core-repository-jpa`'s `JpaTestApplication`-based
  tests (including the new `searchMatchesFiltersByClubNameCaseInsensitivelyAndPartially`) cannot run —
  `FindImportRunStatusQueryHandler` requires an `ImportRunRegistry` bean whose only implementation
  (`InMemoryImportRunRegistry`) lives in `tt-data-league-api-runtime`, which this module does not
  depend on. Confirmed via `git stash` that this breaks equally on unmodified `main` (also breaks
  unrelated tests like `SettingRepositoryJpaTest`), so it predates and is unrelated to this feature.
  The new club-name JPQL predicate was reviewed by hand against the existing, working `playerName`
  predicate it mirrors, but could not be exercised against a real database because of this blocker.
  Worth a separate fix (e.g. move `InMemoryImportRunRegistry` down to `core-domain`/`core-repository-jpa`,
  or provide a test-scoped stub bean) before relying on this module's Spring-context tests.
- Follow-up scope addition: Competition is no longer mandatory to run a search — only Source and
  Season are required. Changed `MatchSearchCriteria` to accept a `null`/blank `competition` (JPQL
  predicate `(:competition is null or m.competition = :competition)`), `MatchController`'s
  `competition` request param to `required = false`, and the frontend's `canSearch` gate
  (`MatchesSearchPage.jsx`) and `searchMatches` client (`matches.js`) to treat `competition` as
  optional alongside the other free-text filters. Competition remains selectable in the UI (still
  gated on Season being chosen) and, when set, still filters exactly as before. Added coverage:
  `MatchSearchCriteriaTest.requiresSourceAndSeasonButNotCompetition`,
  `ImportSchemaTest.searchMatchesWithoutACompetitionFilterReturnsMatchesAcrossCompetitions`, and two
  new `MatchesSearchPage.test.jsx` cases (search enabled/searches with only source+season; search
  stays disabled with only source). Verified: frontend suite (258 tests) and full `api-rest` suite
  pass; `MatchSearchCriteriaTest` passes standalone; the new persistence test could not be executed
  due to the same pre-existing blocker noted above.
- Follow-up scope addition: added first/previous/next/last pagination to the Matches search results
  (previously an accumulating "Load more" button). No backend change was needed — the search endpoint
  already returns `total`/`page`/`pageSize`/`hasNext`. Frontend-only change in `MatchesSearchPage.jsx`
  (`goToPage()` replacing `loadMore()`) plus new `common.first`/`common.last` and
  `matchesPage.paginationAriaLabel` i18n keys (`ca`/`en`/`es`), with the now-unused
  `matchesPage.loadMore` key removed. Added 4 new `MatchesSearchPage.test.jsx` cases covering: nav
  hidden on a single page, correct disabled state on the first page, Next navigation, and Last
  navigation with disabled-state flip. Verified: full frontend suite passes (262 tests) aside from the
  pre-existing, unrelated `MatchDetailPage.test.jsx` import failure; lint is clean.
- Follow-up refinement: duplicated the pagination controls above the results list as well as below it
  (previously bottom-only), so long results lists don't require scrolling back up to change page. Same
  `pagination` JSX rendered in both spots in `MatchesSearchPage.jsx`; both copies share the same
  `goToPage` handlers and disabled state, so they always stay in sync. Updated the 3 pagination tests
  in `MatchesSearchPage.test.jsx` to assert on both copies (`getAllByRole`/`getAllByText`) instead of a
  single instance. Verified: full frontend suite passes (262 tests, same pre-existing unrelated
  failure as before); lint is clean.
- Follow-up refinement: gave the pagination `<nav>` vertical breathing room from the results list by
  changing `.pagination`'s `margin-top: 1rem` to `margin: 1rem 0` in `app.css` (shared with
  `UsersRolesPage`'s pagination, which gets a harmless trailing-space improvement too).
- Follow-up refinement: the results panel (`<article className="card">` wrapping the "Results"
  heading, pagination, and result list in `MatchesSearchPage.jsx`) had no padding at all — the
  heading and each result card sat flush against the panel edges. Added a `.match-results-card` class
  (`padding: 1.25rem`, mirroring the existing `.match-filter-card`) applied alongside `card` on that
  `<article>`. Verified: full frontend suite passes (262 tests, same pre-existing unrelated failure);
  lint clean.
- Follow-up refinement: moved the Club name filter onto the same row as Player name, to its right,
  instead of each taking a full-width row of its own. Changed `.match-player-name`/`.match-club-name`
  in `app.css` from `grid-column: 1 / -1` (full row) to `grid-column: span 2` — with the 4-column
  `.match-filter-grid` and Player name/Club name adjacent in DOM order right after the full-row Player
  location fieldset, grid auto-placement puts Player name in columns 1–2 and Club name in columns 3–4
  of the same row. Dropped the now-unnecessary `max-width: 34rem` (width is bound by the 2-column span)
  and its `max-width: none` reset in the 800px media query. At ≤800px the grid drops to 2 columns, so
  `span 2` still gives each field its own full-width row there (unchanged from before); at ≤520px the
  existing `grid-column: auto` reset still applies. Not verified in a running browser (this repo's dev
  server needs a live backend for auth to reach this page) — verified by tracing the grid
  auto-placement against the existing column/row layout instead; full frontend suite still passes
  (262 tests, same pre-existing unrelated failure) and lint is clean.
