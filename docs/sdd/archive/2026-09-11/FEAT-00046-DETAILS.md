# Build Plan
1. Extend the domain read model
   `tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/application/player/find/dto/PlayerMatchReadModel.java`
   with `Integer groupNumber` and `String phase` fields (nullable, matching
   `Match.getGroupNumber()`/`Match.getPhase()`), placed next to the existing
   `round` field. Update the three secondary (overload) constructors to accept
   and forward the two new parameters, mirroring the existing overload chain
   rather than collapsing it.
2. Update `FindPlayerDetailsQueryHandler.toMatch`
   (`tt-data-league-core-domain/.../player/find/FindPlayerDetailsQueryHandler.java`,
   ~line 187) to pass `match.getGroupNumber()` and `match.getPhase()` into the
   new `PlayerMatchReadModel` constructor call.
3. Extend `PlayerDetailsDto.MatchDto`
   (`tt-data-league-api-rest/src/main/java/org/cttelsamicsterrassa/data/api/rest/player/PlayerDetailsDto.java`,
   ~lines 68-84) with `Integer groupNumber` and `String phase`, following the
   field ordering/style already used in `MatchDetailDto`
   (`tt-data-league-api-rest/.../match/MatchDetailDto.java`:
   `Integer groupNumber, int round, String phase`). Update `MatchDto`'s
   secondary constructors and `PlayerDetailsDto.fromObject` to map
   `value.groupNumber()`/`value.phase()` from the read model.
4. Update backend tests:
   - `FindPlayerDetailsQueryHandlerTest`
     (`tt-data-league-core-domain/src/test/...`): set `groupNumber`/`phase` on
     at least one `Match.builder()` fixture and assert they flow into the
     resulting `PlayerMatchReadModel`.
   - `PlayerControllerTest`
     (`tt-data-league-api-rest/src/test/.../player/PlayerControllerTest.java`):
     assert the REST response JSON includes `groupNumber` and `phase` for a
     match.
5. Add a `matches` table column pair in the frontend `MatchHistoryPanel`
   (`tt-data-league-frontend/src/pages/PlayerDetailPage.jsx`, ~lines 205-239):
   add `<th>`/`<td>` cells for round, group number, and phase, positioned
   after the competition column and before the opponent column. Render
   `item.round`, and fall back to `t('common.unavailable')` (used elsewhere,
   e.g. `MatchDetailPage.jsx`) when `item.groupNumber`/`item.phase` is
   null/undefined. Reuse the existing `matchesPage.round`, `matchesPage.group`,
   `matchesPage.phase` translation keys (already defined in
   `src/i18n/ca.js`, `es.js`, `en.js` and used by `MatchDetailPage.jsx`) for
   the column headers instead of adding new keys.
6. Confirm `normalizeMatch` in `tt-data-league-frontend/src/api/players.js`
   (~line 60) already passes `groupNumber`/`phase`/`round` through via its
   `...item` spread; no change needed there, but extend
   `tt-data-league-frontend/src/api/players.test.js` with a fixture assertion
   that a match payload including `groupNumber`/`phase` is preserved
   unchanged by `normalizePlayerDetailsResponse`.
7. Extend `PlayerDetailPage.test.jsx` with Matches-tab cases covering: round,
   group number, and phase rendering for a fully-populated match; the
   unavailable fallback when `groupNumber`/`phase` are null; and that column
   position/order does not break existing opponent/result/score column
   assertions.
8. Run the focused frontend tests, then `npm run lint` and `npm run build`
   from `tt-data-league-frontend`. Run the affected Maven modules
   (`tt-data-league-core-domain`, `tt-data-league-api-rest`) tests for the
   changed classes. Review the final diff for unrelated SDD or generated-file
   changes.

# Implementation Guidelines

- Mirror the existing `round`/`phase`/`groupNumber` field types and nullability
  used by `MatchDetailDto`/`MatchDetailReadModel`: `round` is a primitive
  `int` (always present), `groupNumber` is a nullable `Integer`, `phase` is a
  nullable `String`.
- Do not change the existing REST endpoint path, query parameters,
  loading/error behavior, URL state, pagination, sorting, filters, or the
  opponent/result/score rendering added by FEAT-00038.
- Do not add a new frontend network request, React state, or backend sorting;
  render the new fields directly from the already-fetched match payload.
- Keep column additions minimal: only add round/group/phase; do not
  restructure the existing table layout or reorder unrelated columns beyond
  inserting the new ones in one contiguous position.
- Reuse existing translation keys (`matchesPage.round`, `matchesPage.group`,
  `matchesPage.phase`, `common.unavailable`) rather than introducing
  duplicate keys; keep Catalan/Spanish/English parity since these keys
  already exist in all three locale files.

# Notes

- Player matches table lives in
  `tt-data-league-frontend/src/pages/PlayerDetailPage.jsx` (`MatchHistoryPanel`,
  ~line 205), with tests in `PlayerDetailPage.test.jsx`. Current columns: date,
  source, season, competition, opponent, result, score, opponent team. No
  round/group/phase column exists yet.
- `PlayerDetailsDto.MatchDto`
  (`tt-data-league-api-rest/.../player/PlayerDetailsDto.java`, ~lines 68-84)
  already exposes `round` but has no `groupNumber` or `phase` field.
- Backing domain read model `PlayerMatchReadModel`
  (`tt-data-league-core-domain/.../player/find/dto/PlayerMatchReadModel.java`)
  also only has `round`; needs `groupNumber`/`phase` added.
- Built by `FindPlayerDetailsQueryHandler.toMatch` (~line 187), which already
  reads `match.getRound()` from the `Match` domain entity
  (`tt-data-league-core-domain/.../match/model/Match.java`). That entity
  already has `getGroupNumber()` and `getPhase()` (added for FEAT-00039), so
  the source data is available — only the read model, DTO, and frontend need
  to be extended.
- For reference, the league-wide matches view's `MatchDto`
  (`tt-data-league-api-rest/.../match/MatchDto.java`, used by
  `MatchController`/`MatchesSearchPage.jsx`) already exposes `round` and
  `phase`, but not `groupNumber` — that view can be used as a styling/labeling
  reference but does not yet fully cover the acceptance criteria either.
- Implemented: `PlayerMatchReadModel` and `PlayerDetailsDto.MatchDto` now
  carry `groupNumber`/`phase` next to `round`, populated from
  `Match.getGroupNumber()`/`Match.getPhase()` in
  `FindPlayerDetailsQueryHandler.toMatch`. The Matches-tab table in
  `PlayerDetailPage.jsx` renders three new columns (round, group, phase)
  between competition and opponent, reusing the existing
  `matchesPage.round`/`group`/`phase` and `common.unavailable` translation
  keys; `normalizeMatch` in `api/players.js` already passed these fields
  through unchanged via its object spread.
- Validation: `FindPlayerDetailsQueryHandlerTest` and `PlayerControllerTest`
  extended and passing; `tt-data-league-core-domain` and
  `tt-data-league-api-rest` full Maven test suites pass. Frontend:
  `PlayerDetailPage.test.jsx` and `api/players.test.js` extended and passing;
  full frontend suite (192 tests), `npm run lint`, and `npm run build` all
  pass. Feature registry validated successfully.
