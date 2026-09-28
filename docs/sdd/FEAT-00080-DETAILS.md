# Build Plan
Source task: **T5** in [analysis-incremental-actas-for-current-jornada-import.md](../analysis/analysis-incremental-actas-for-current-jornada-import.md), section 5 (Section 4.4; gap G7; risk K3).

## Acceptance Criteria
- [x] MatchRepository.replaceMatchContent replaces header and children (lineups, games, set scores, doubles pairs) of an existing match in one transaction, preserving its id
- [x] MatchRepository.updateSchedule (or a verified createExisting/saveMatch merge) updates date, time, city, venue and referee
- [x] JPA and in-memory implementations exist
- [x] Rollback tests prove a failed replace leaves no half-written match

## Baseline (verified 2026-09-28)

- `MatchRepository` (domain) exposes only reads plus `saveMatch`/`saveMatches`. The child ports
  (`LineupRepository`, `GameRepository`, `SetScoreRepository`, `DoublesPairRepository`) expose only
  `find*` and `save*` (gap G7). The only implementors of `MatchRepository` are `MatchRepositoryJpa`
  and `InMemoryRepositories.Matches` (import tests).
- `Match` is immutable; `Match.builder()...createExisting()` rebuilds a match with a given id and
  enforces the FEAT-00077 invariant "`SCHEDULED` ⇒ no winner and no game/set results".
- FEAT-00078 (uncommitted in the working tree, must land first) already adds bulk
  `deleteAllByMatchIds(Collection<UUID>)` to `LineupRepositoryHelper`, `GameRepositoryHelper`,
  `SetScoreRepositoryHelper` and `DoublesPairRepositoryHelper` (JPQL `@Modifying(clearAutomatically = true)`),
  and a `markScheduledByIds` update in `MatchRepositoryHelper`. Reuse those delete helpers; do not
  add parallel ones.
- `MatchRepositoryJpa` is annotated `jakarta.transaction.Transactional` at class level, so one port
  call is one transaction. `GameJPA` has no child collections (commented out), so there is no JPA
  cascade: children must be deleted explicitly in the order doubles pairs → set scores → games →
  lineups.
- `saveMatch` with a `createExisting` match does merge by id in JPA (`SimpleJpaRepository.save` with
  an assigned id and no `@Version` → `merge`), but it rewrites the whole header, has no status
  guard, and `InMemoryRepositories.Matches.saveMatch` appends instead of upserting. Decision: add
  an explicit `updateSchedule` port instead of relying on the merge (see Notes).

## Contracts

Domain additions (package `org.cttelsamicsterrassa.data.core.domain.match.model` unless stated):

1. `record MatchContent(Match match, List<Lineup> lineups, List<Game> games, List<SetScore> setScores, List<DoublesPair> doublesPairs)`
   - Compact constructor: `match` non-null; `match.getId()` non-null; `match.getStatus() == PLAYED`;
     every list non-null and defensively copied with `List.copyOf`.
   - Every `Lineup.getMatch().getId()` and `Game.getMatch().getId()` equals `match.getId()`.
   - Every `SetScore.getGame().getId()` and `DoublesPair.getGame().getId()` is the id of a game in
     `games`.
   - Violations throw `IllegalArgumentException` naming the offending child id. No partial
     acceptance.
2. `record MatchSchedule(ZonedDateTime dateTime, String city, String venue, String refereeName, String refereeLicense)`
   - All components nullable (the model already permits null date, city, venue and referee).
     `updateSchedule` writes them as given: it is a full replacement of the five schedule fields,
     and the caller (FEAT-00081) decides whether a change happened.
3. `Match.hasSameNaturalKeyAs(Match other)`: compares source, competition, season, group number,
   round, phase, home team id and away team id (null-safe for group and phase). Used by both
   adapters to reject a replacement that would move the match to another fixture.
4. `MatchRepository` gains two abstract methods (no defaults, so an implementor cannot silently
   no-op):
   - `void replaceMatchContent(MatchContent content)`: in one transaction, requires an existing match
     with `content.match().getId()` and the same natural key, deletes its doubles pairs, set scores,
     games and lineups, overwrites the header with `content.match()` (id preserved), and inserts the
     new children. Allowed for an existing `SCHEDULED` match (upgrade, FEAT-00081) and an existing
     `PLAYED` match (correction, FEAT-00089/T13). Throws `IllegalStateException` when the match does
     not exist or the natural key differs.
   - `void updateSchedule(UUID matchId, MatchSchedule schedule)`: updates `match_date`, `match_time`,
     `city`, `venue`, `referee_name` and `referee_license` of a `SCHEDULED` match only. Throws
     `IllegalStateException` when the match does not exist or is `PLAYED` (a played match is never
     rescheduled by import). Does not touch status, results, winner or children.
   - Javadoc on both states the transaction and failure semantics above.

## Implementation order

1. **Domain values.** Add `MatchContent` and `MatchSchedule` in
   `tt-data-league-core-domain/src/main/java/org/cttelsamicsterrassa/data/core/domain/match/model/`,
   and `Match.hasSameNaturalKeyAs`.
2. **Domain port.** Add `replaceMatchContent` and `updateSchedule` to
   `domain/match/repository/MatchRepository.java` with javadoc.
3. **Domain tests** (`tt-data-league-core-domain/src/test/java/.../domain/match/model/`):
   - `MatchContentTest`: accepts consistent content; rejects a `SCHEDULED` match, a lineup or game
     of another match, a set score or doubles pair whose game is not in `games`, and null lists;
     the lists are unmodifiable copies.
   - `MatchTest`: `hasSameNaturalKeyAs` is true for a rebuilt match with different results and
     schedule, and false when any key component differs (including null vs non-null group/phase).
4. **JPA helper.** In `MatchRepositoryHelper` add
   `@Modifying(clearAutomatically = true) int updateScheduleOfScheduledMatch(id, matchDate, matchTime, city, venue, refereeName, refereeLicense)`
   as a JPQL update with `where m.id = :id and m.status = MatchStatus.SCHEDULED`.
5. **JPA adapter** `MatchRepositoryJpa`:
   - Inject `LineupRepositoryHelper`, `GameRepositoryHelper`, `SetScoreRepositoryHelper`,
     `DoublesPairRepositoryHelper` and the four child `*To*JPAMapper`s (constructor via the existing
     `@AllArgsConstructor`).
   - `replaceMatchContent`: load the existing match by id (`IllegalStateException` if absent), map
     it and check `hasSameNaturalKeyAs`; then, on `List.of(id)`, call
     `doublesPairRepositoryHelper.deleteAllByMatchIds`, `setScoreRepositoryHelper.deleteAllByMatchIds`,
     `gameRepositoryHelper.deleteAllByMatchIds`, `lineupRepositoryHelper.deleteAllByMatchIds`; then
     `matchRepositoryHelper.save(mapped header)`, `saveAll` lineups, games, set scores, doubles
     pairs (in that order, respecting FKs), and finally `matchRepositoryHelper.flush()` so a
     constraint violation surfaces inside the method and rolls the whole transaction back instead
     of failing later at commit.
   - `updateSchedule`: convert `MatchSchedule.dateTime` exactly as `MatchToMatchJPAMapper` does
     (`toLocalDate()` / `toLocalTime()`, null → null date and time), call the helper; when it
     returns 0, distinguish "not found" from "not SCHEDULED" with `existsById` and throw
     `IllegalStateException` accordingly.
   - No broad catches: database exceptions propagate unchanged.
6. **JPA tests** (`tt-data-league-core-repository-jpa/src/test/java/.../jpa/match/`), reusing the
   fixture style of `ScheduledMatchBackfillRepositoryJpaTest` (`@SpringBootTest`,
   `@Import(JpaTestSupportConfiguration.class)`):
   - `MatchContentReplacementJpaTest` (`@Transactional`):
     - upgrade: a `SCHEDULED` match with no children → replace with a `PLAYED` match plus lineups,
       singles and doubles games, set scores and doubles pairs; the id is unchanged, status is
       `PLAYED`, results/winner are stored, and every child is readable through the existing
       `find*` ports.
     - correction: a `PLAYED` match with children → replace with different children; old child ids
       are gone, new ones present, no duplicates for the same `(match_id, game_number)`.
     - rejects an unknown id and a changed natural key with `IllegalStateException`, leaving data
       unchanged.
     - `updateSchedule` changes date, time, city, venue and referee of a `SCHEDULED` match and
       leaves status, teams, results and natural key unchanged; rejects a `PLAYED` match and an
       unknown id.
   - `MatchContentReplacementRollbackJpaTest` (**not** `@Transactional`, so the adapter's transaction
     commits or rolls back for real; clean up created rows in `@AfterEach`):
     - seed a `PLAYED` match with lineups, games, set scores and doubles pairs; call
       `replaceMatchContent` with content whose inserts violate `uk_set_score_game_set_number` (two
       set scores with the same game and set number) after the deletes have run; assert the
       exception propagates and that a fresh read returns the original header and the original
       children (ids and counts) — no half-written match (risk K3).
     - same with a header failure (e.g. `external_id` longer than 20 chars) to prove header and
       child deletes roll back together.
7. **In-memory implementation** (`tt-data-league-import/src/test/java/.../load/process/InMemoryRepositories.java`):
   - Give `Matches` an optional wiring constructor `Matches(Lineups, Games, SetScores, DoublesPairs)`,
     keeping the no-arg constructor for current tests. `replaceMatchContent` throws
     `IllegalStateException` when the child stores are not wired (no silent no-op).
   - `replaceMatchContent`: perform every check (existence, natural key) before mutating; then
     remove children by match id / game ids (same removal logic as `ScheduledMatchBackfill`, extracted
     to a shared private helper if it avoids duplication) and replace the match in `saved` at the
     same index.
   - `updateSchedule`: rebuild the stored match with `Match.builder()...createExisting()` copying
     every field and replacing the five schedule fields; same failure rules as JPA.
   - Update `ScheduledMatchBackfill` and the four test classes that build `Matches` only if they
     need the wired constructor.
8. **In-memory tests** (`tt-data-league-import/src/test/java/.../load/process/InMemoryMatchRepositoryTest.java`):
   the same upgrade, correction, reschedule and rejection cases as step 6, plus: a rejected
   replacement (unknown id, changed key, unwired stores) leaves matches and children unchanged.
9. **Documentation.** In `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md`:
   - under `match_record`, a paragraph "Upgrade and reschedule" describing `replaceMatchContent`
     (single transaction, explicit child delete order, id preserved, natural key immutable) and
     `updateSchedule` (`SCHEDULED` only, the six columns it writes);
   - extend the `MatchRepositoryHelper` row of "Repository lookup behavior" with the new
     schedule update, and mention reuse of the child `deleteAllByMatchIds` helpers.
   No schema change: no column, constraint or cascade is added.
10. **Validation.** `mvn -pl tt-data-league-core-repository-jpa -am test`,
    `mvn -pl tt-data-league-import -am test`, then the full `mvn test`. Review the diff for stray
    files.

# Implementation Guidelines

- Affected modules: domain, JPA, import (tests).
- Follow the repository and module `AGENTS.md` files; keep lookups source-scoped and never add external ids to `FederatedClub` or `FederatedPlayer`.
- Update `tt-data-league-core-repository-jpa/docs/rfetm-datamodel.md` for any schema change and module READMEs for CLI/configuration changes.
- Keep the domain module free of JPA/Spring: `MatchContent`, `MatchSchedule` and the port javadoc
  describe behaviour only; transactions live in the JPA adapter.
- Validate everything before the first delete. A replacement must never change the natural key
  (source, competition, season, group, round, phase, home/away team), otherwise it would silently
  move a fixture.
- No broad catches, no fallback to `saveMatch`, no success-shaped return on a missing match: fail
  with `IllegalStateException` / `IllegalArgumentException` and let database exceptions propagate.
- Reuse the FEAT-00078 `deleteAllByMatchIds` child helpers and the existing child mappers; do not
  add cascades or re-enable the commented-out `GameJPA` collections.
- Do not touch `consolidation_action*`, team or player-season identity; children of the new content
  reference the same `Team` and `PlayerSeason` rows the processor already resolved.
- Out of scope: changing the match processors (FEAT-00081), `findBySourceFixtureId` (FEAT-00083),
  checksum-based correction detection (FEAT-00089), and any schema change.

# Notes

- 2026-09-27: Created from the incremental actas analysis (revision 3). Analysis priority P0, size M, Slice 2: incremental import. Depends on: FEAT-00077 (T2).
- 2026-09-28: Build plan written; status `planned`.
  - Decision: an explicit `updateSchedule(UUID, MatchSchedule)` port instead of reusing
    `saveMatch` with `createExisting`. The JPA merge by id works, but it rewrites the whole header
    with no `SCHEDULED` guard, and the in-memory `saveMatch` appends rather than upserts, so the
    two implementations would disagree.
  - Decision: `replaceMatchContent` takes one validated `MatchContent` record instead of the
    five-argument signature sketched in analysis section 4.4. Same data, but the consistency checks
    (children belong to the match, `PLAYED` status) live in one domain place shared by JPA and
    in-memory.
  - Decision: `replaceMatchContent` accepts an existing `SCHEDULED` or `PLAYED` match so that
    FEAT-00089 (T13, amended actas) can reuse it; the "never downgrade" rule stays in the processor
    (FEAT-00081) and is enforced here by requiring the new content to be `PLAYED`.
  - Prerequisite: the FEAT-00078 child `deleteAllByMatchIds` helpers are still uncommitted in the
    working tree; commit FEAT-00078/79 before starting this feature.
  - Open question: when a pending acta omits a date the stored match already has, should
    reschedule keep the stored value? `updateSchedule` writes values as given; FEAT-00081 decides
    whether to call it.
- 2026-09-28: Implemented and validated (status `planned` → `ready` → `in-progress`).
  Delivered per plan: `MatchContent`/`MatchSchedule` records, `Match.hasSameNaturalKeyAs`,
  `replaceMatchContent`/`updateSchedule` on the port (abstract, no defaults), JPA adapter with the
  child helpers/mappers and `updateScheduleOfScheduledMatch` (status-guarded bulk update), in-memory
  `Matches` wired-constructor variant with the same contract, and docs.
  - Deviation from the plan: `replaceMatchContent` calls `matchRepositoryHelper.flush()` before the
    child deletes. The `@Modifying(clearAutomatically = true)` deletes only auto-flush pending writes
    touching their own tables, so unflushed parent INSERTs (match/team rows created earlier in the
    same transaction) were purged by the session clear and the header merge degraded into a fresh
    INSERT that violated the team FK. The pre-flush keeps the same single-transaction semantics.
  - Tests: `MatchContentUpgrade`/correction/reject + `updateSchedule` cases in
    `MatchContentReplacementJpaTest` (4), atomic-rollback cases (set-score duplicate, oversized
    header) in the non-transactional `MatchContentReplacementRollbackJpaTest` (2), domain
    `MatchContentTest` (5) and natural-key cases in `MatchTest`, and
    `InMemoryMatchRepositoryTest` (5) mirroring the JPA contract. Note: the doubles-pair read port
    inner-joins `player.federatedPlayer`, so JPA fixtures must give pair players a federated identity.
  - Validation: full `mvn test` reactor — domain 149 (only the 4 pre-existing auth failures), JPA 71
    green, import 237 (only the 8 pre-existing processor failures), runtime/rest/mcp/api-runtime/
    graphql/frontend green. No new failures vs the documented baseline.
