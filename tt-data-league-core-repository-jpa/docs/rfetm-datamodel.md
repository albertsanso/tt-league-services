# RFETM Relational Data Model

## Scope and JPA conventions

This document describes the relational model implemented by the JPA entities in
`tt-data-league-core-repository-jpa`. It includes imported league data, import
bookkeeping, application authentication, settings, and consolidation audit
tables.

- Entities use Jakarta Persistence and application-assigned `UUID` identifiers.
  No entity configures `@GeneratedValue`.
- Enum fields use `@Enumerated(EnumType.STRING)`.
- Every `@ManyToOne` association is lazy and owns its explicit join column.
- The only `@OneToMany` collection is `ConsolidationActionJPA.clubs` (see
  [Consolidation audit tables](#consolidation-audit-tables)). League entities
  declare none: `MATCH` and `GAME` children are loaded through their
  repositories.
- Unless a column is explicitly marked otherwise below, its nullability and
  length are those declared by the entity.
- `source` is stored as a string enum with the values `RFETM`, `BCNESA`, and
  `FCTT`.
- `MATCH` is persisted as `match_record` to avoid a reserved-word collision.

## Enumerations

| Enum | Stored values |
| --- | --- |
| `Source` | `RFETM`, `BCNESA`, `FCTT` |
| `MatchStatus` | `SCHEDULED`, `PLAYED` |
| `GameType` | `INDIVIDUAL`, `DOUBLES` |
| `MatchResult` | `HOME`, `AWAY` |
| `Side` | `HOME`, `AWAY` |
| `UserRole` | `ADMIN`, `CLUB_MANAGER`, `ANALYST`, `PRACTITIONER` |
| `ResourceType` | `ACTAS`, `TEAMS` |
| `ImportResourceStatus` | `PENDING`, `PROCESSING`, `PROCESSED`, `ERROR` |
| `SettingCategory` | `GENERAL`, `IMPORT`, `NOTIFICATIONS` |
| `ConsolidationActionType` | `MERGE`, `SPLIT`, `RENAME` |
| `ConsolidationActionClubRole` | `SOURCE`, `TARGET` |

## League tables

### `resource`

Stored resource metadata.

| Column | Type | Null | Key/index |
| --- | --- | --- | --- |
| `id` | `UUID` | No | Primary key |
| `name` | `VARCHAR(255)` | No | — |
| `logic_path` | `VARCHAR(2000)` | No | `idx_resource_logic_path_name` |
| `physical_path` | `VARCHAR(2000)` | No | — |

The unique constraint `uk_resource_logic_path_name` covers
`(logic_path, name)`.

### `import_resource`

Import bookkeeping for a stored resource, per source and season.

| Column | Type | Null | Key/index |
| --- | --- | --- | --- |
| `id` | `UUID` | No | Primary key |
| `resource_id` | `UUID` | Yes | FK to `resource`; `idx_import_resource_resource_id` |
| `valid` | `BOOLEAN` | Yes | — |
| `type` | `VARCHAR` | No | `ResourceType` enum string |
| `created` | `TIMESTAMP WITH TIME ZONE` | No | — |
| `last_processed_date` | `TIMESTAMP WITH TIME ZONE` | Yes | — |
| `season` | `VARCHAR` | No | — |
| `source` | `VARCHAR` | No | `Source` enum string |
| `status` | `VARCHAR` | No | `ImportResourceStatus` enum string |

The unique constraint `uk_import_resource_resource_season_source` covers
`(resource_id, season, source)`. The `resource` association is a lazy
`@ManyToOne` with no cascade.

### `club`

Season-independent canonical club identity.

| Column | Type | Null | Key/index |
| --- | --- | --- | --- |
| `id` | `UUID` | No | Primary key |
| `name` | `VARCHAR(255)` | No | Unique; `idx_club_name` |

The table constraint is `uk_club_name`.

### `federated_club`

Source-specific club identity, optionally linked to a canonical `club`.

| Column | Type | Null | Key/index |
| --- | --- | --- | --- |
| `id` | `UUID` | No | Primary key |
| `source` | `VARCHAR(20)` | No | — |
| `name` | `VARCHAR(255)` | Yes | — |
| `club_id` | `UUID` | Yes | FK to `club`; `idx_federated_club_club_id` |

The unique constraint `uk_federated_club_source_name` covers
`(source, name)`. The indexes `idx_federated_club_name` and
`idx_federated_club_source_name` support name and source-scoped name lookup.
The `club` association is `@ManyToOne(fetch = LAZY)` with no cascade.

### `team`

Season-specific team registration, optionally linked to a source-specific
federated club.

| Column | Type | Null | Key/index |
| --- | --- | --- | --- |
| `id` | `UUID` | No | Primary key |
| `source` | `VARCHAR(20)` | Yes | — |
| `name` | `VARCHAR(255)` | Yes | — |
| `season` | `VARCHAR(10)` | Yes | — |
| `federated_club_id` | `UUID` | Yes | FK to `federated_club`; `idx_team_federated_club_id` |

The unique constraint `uk_team_name_season_source` covers
`(name, season, source)`. `idx_team_name_season` covers `(name, season)`.
The `federatedClub` association is `@ManyToOne(fetch = LAZY)` with no cascade.

### `player`

Season-independent canonical player identity. `license_id` stores the imported
source licence when available.

| Column | Type | Null | Key/index |
| --- | --- | --- | --- |
| `id` | `UUID` | No | Primary key |
| `name` | `VARCHAR(255)` | No | `idx_player_name` (not unique) |
| `license_id` | `VARCHAR(20)` | Yes | — |

Unlike `club`, `player` declares **no** unique constraint on `name`: the
`uk_player_name` declaration is commented out in `PlayerJPA`, so the database
does not prevent two canonical players with the same name. `findByName`
returns an `Optional`, so Spring Data throws
`IncorrectResultSizeDataAccessException` when duplicates exist.

### `federated_player`

Source-specific player identity, optionally linked to a canonical `player`.
`license_id` stores the imported source licence when available.

| Column | Type | Null | Key/index |
| --- | --- | --- | --- |
| `id` | `UUID` | No | Primary key |
| `source` | `VARCHAR(20)` | No | — |
| `name` | `VARCHAR(255)` | No | — |
| `license_id` | `VARCHAR(20)` | Yes | `idx_federated_player_source_license` with `source` |
| `player_id` | `UUID` | Yes | FK to `player`; `idx_federated_player_player_id` |

There is no table-level unique constraint on `(source, name)`. The indexes
`idx_federated_player_name` and `idx_federated_player_source_name` support
unscoped and source-scoped searches. The `player` association is
`@ManyToOne(fetch = LAZY)` with no cascade.

### `player_season`

Season-specific player registration. `license_id` is the source-system
registration identifier.

| Column | Type | Null | Key/index |
| --- | --- | --- | --- |
| `id` | `UUID` | No | Primary key |
| `source` | `VARCHAR(20)` | No | — |
| `name` | `VARCHAR(255)` | No | `idx_player_season_name` |
| `license_id` | `VARCHAR(20)` | No | — |
| `season` | `VARCHAR(10)` | Yes | `idx_player_season_season_license` |
| `federated_player_id` | `UUID` | Yes | FK to `federated_player`; `idx_player_season_federated_player_id` |

The unique constraint `uk_player_season_source_season_license` covers
`(source, season, name, license_id)`. The `federatedPlayer` association is
`@ManyToOne(fetch = LAZY)` with no cascade.

### `match_record`

Top-level team match event.

| Column | Type | Null | Key/index |
| --- | --- | --- | --- |
| `id` | `UUID` | No | Primary key |
| `source` | `VARCHAR(20)` | No | `idx_match_source_season_competition_status` |
| `external_id` | `VARCHAR(20)` | Yes | Unique; `idx_match_external_id` |
| `source_fixture_id` | `VARCHAR(100)` | Yes | `uk_match_source_fixture_id` |
| `source_checksum` | `VARCHAR(80)` | Yes | — |
| `competition` | `VARCHAR(255)` | Yes | `idx_match_competition_season_group_round`, `idx_match_source_season_competition_status` |
| `season` | `VARCHAR(9)` | Yes | `idx_match_competition_season_group_round`, `idx_match_source_season_competition_status` |
| `group_num` | `INTEGER` | Yes | `idx_match_competition_season_group_round` |
| `round` | `INTEGER` | No | `idx_match_competition_season_group_round` |
| `phase` | `VARCHAR(255)` | Yes | — |
| `match_date` | `DATE` | Yes | — |
| `match_time` | `TIME` | Yes | — |
| `city` | `VARCHAR(255)` | Yes | — |
| `venue` | `VARCHAR(255)` | Yes | — |
| `home_team_id` | `UUID` | No | FK to `team`; `idx_match_home_team_id` |
| `away_team_id` | `UUID` | No | FK to `team`; `idx_match_away_team_id` |
| `referee_name` | `VARCHAR(255)` | Yes | — |
| `referee_license` | `VARCHAR(20)` | Yes | — |
| `home_games_won` | `INTEGER` | Yes | — |
| `away_games_won` | `INTEGER` | Yes | — |
| `home_sets_won` | `INTEGER` | Yes | — |
| `away_sets_won` | `INTEGER` | Yes | — |
| `winner_team_id` | `UUID` | Yes | FK to `team`; `idx_match_winner_team_id` |
| `protested` | `BOOLEAN` | No | Database default `false` |
| `status` | `VARCHAR(20)` | No | Database default `'PLAYED'`; `idx_match_source_season_competition_status` |

`status` is `SCHEDULED` while the fixture is known but not yet played, and `PLAYED` once results are
stored. The database default is what lets `ddl-auto: update` add this column to an already-populated
table: PostgreSQL backfills every existing row with `'PLAYED'`, matching what those rows already mean.
The invariant `status = SCHEDULED` implies no `lineup`, `game`, `set_score`, or `doubles_pair` rows for
the match, and a null `winner_team_id`, `home_games_won`, `away_games_won`, `home_sets_won`, and
`away_sets_won`. The domain enforces the header half of this (winner and games/sets won) in
`Match.of(...)`; the child half (no lineups, games, set scores, doubles pairs) is enforced by the match
writers, not by a database `CHECK` constraint.

**FEAT-00078 backfill.** A `PLAYED` row is a backfill candidate when, within one `(source, season)`
scope, it has a null `winner_team_id`, `home_games_won`/`away_games_won` and `home_sets_won`/
`away_sets_won` that are each null or `0`, and no `game` row of its own with a result — a game has a
result when its `winner` is set, `home_sets_won`/`away_sets_won` is non-zero, or it has a `set_score`
row; a `not_played` game that still names a `winner` (a walkover) counts as a result. This covers legacy
empty RFETM actas and the "decided 0-0" administrative placeholders, and never a match with a real
winner or a game result of its own. Marking a candidate `SCHEDULED` (the `tt-data-league-import-runtime`
`--backfill-scheduled-matches` command, in one transaction per run) deletes its `doubles_pair`,
`set_score`, `game`, and `lineup` rows and nulls the four header score columns before setting
`status = SCHEDULED`, keeping the invariant above. The source actas are unaffected; a later import can
recreate the deleted child rows once the acta is actually published.

**FEAT-00079 read-side filtering.** Statistics, counts and search must never see unplayed fixtures, so
the read queries split into two groups. Query-level: the paginated `search`/`countSearch` carry a
mandatory `MatchSearchCriteria.status` (defaulting to `PLAYED`), and fragment search, all-matches-by-
source, distinct seasons, per-season count and the total match count all add `status = 'PLAYED'`.
The `(source, season, competition, status)` index supports these predicates. Handler-level: the team-id
traversals (`findAllByTeamIds`, `findAllByTeamIdsAndSource`,
`findAllByTeamIdsAndSourceAndSeasonAndCompetition`) stay deliberately **unfiltered** so consolidation
and future calendar reads can see `SCHEDULED` rows (risk K9); every statistics handler filters the
returned matches through `Match.isPlayed()` before aggregating (risk K1), and `MatchOutcome` returns
empty for any `SCHEDULED` match as the central safety net. The REST and MCP match DTOs expose the new
additive `status` field. `findAllSeasonsBySource`/`findAllCompetitionsBySourceAndSeason` (the API
option lists) are not status-filtered, so a season whose actas are all pending still appears in the
dropdown while its search results exclude scheduled rows.

**FEAT-00080 upgrade and reschedule.** `MatchRepository.replaceMatchContent(MatchContent)` upgrades a
`SCHEDULED` fixture to `PLAYED` or corrects an already-played match in one transaction: it requires an
existing match with the same id and the unchanged natural key
(`(competition, season, group_num, round, phase, home_team_id, away_team_id)`), flushes pending writes,
deletes the old children in FK order (doubles pairs → set scores → games → lineups, reusing the
`deleteAllByMatchIds` helpers), overwrites the header keeping the id, inserts the new children, and
flushes so a constraint failure rolls back the whole replacement (no half-written match). The domain
`MatchContent` record validates before any write that the header is `PLAYED` and every child belongs
to that match or one of its games. `MatchRepository.updateSchedule(UUID, MatchSchedule)` rewrites
only `match_date`, `match_time`, `city`, `venue`, `referee_name`, and `referee_license` and is guarded
by `status = 'SCHEDULED'` in the update itself: a `PLAYED` match or an unknown id fails with
`IllegalStateException` and status, teams, results, and children are never touched by either path.

The unique constraints are:

- `uk_competition_season_group_round_teams` on
  `(competition, season, group_num, round, phase, home_team_id, away_team_id)`.
  `group_num` is nullable: a BCNESA Veterans "Other"-phase fixture (playoffs, promotion/relegation,
  finals) carries no group, and per SQL's null-handling this constraint does not dedupe two such
  fixtures by group alone (round, phase, and both teams still must differ).
- `uk_match_external_id` on `(external_id)`.
- `uk_match_source_fixture_id` on `(source, source_fixture_id)`.

**FEAT-00083 source fixture id.** `source_fixture_id` stores the source-supplied `id_partido`
captured at import time. It is an **opaque** per-source key — its layout differs by federation —
so it is stored verbatim: never parsed, trimmed, padded, truncated, or derived from file names.
It is not `external_id` and does not widen or repopulate it. Legacy rows and files without an
`id_partido` keep it `NULL`; the nullable column and the unique constraint are safe to add to a
populated PostgreSQL table under `ddl-auto: update` because multiple `NULL`s never collide. BCNESA
assigns the file's `id_partido` only to fixture index 0 (the fixture named by `equipos`); an
inferred later fixture of a multi-fixture file never borrows it. The constraint's index also serves
`MatchRepository.findBySourceFixtureId(source, id_partido)`, which is always source-scoped.

**FEAT-00089 source checksum.** `source_checksum` stores the versioned (`v1:`) SHA-256 of the acta
*content* last applied to a `PLAYED` match — the canonical rendering of the header, lineups, games,
set scores, and doubles pairs an import would write, independent of generated UUIDs and child list
order. It is a content checksum, not a raw-file checksum: BCNESA splits one matchday file into several
fixtures, so file formatting or renames must never look like a change, while any change a re-apply
would write must change the value. It is `NULL` for `SCHEDULED` matches (the domain builder rejects a
`SCHEDULED` match with a checksum) and for legacy rows imported before the feature. A PLAYED match
created or upgraded by an import stores it; a later import that detects a different checksum for the
same stored PLAYED match re-applies the content through `replaceMatchContent` (FEAT-00089 amended-acta
detection), and a stored PLAYED match whose checksum is `NULL` or carries another prefix adopts the
incoming value as its baseline instead of being rewritten. The FEAT-00078 backfill also nulls it when
marking a match `SCHEDULED`, keeping the `SCHEDULED` ⇒ `NULL` invariant. The column is nullable, has
no index and no constraint, and is safe to add to a populated PostgreSQL table under
`ddl-auto: update`.

`homeTeam`, `awayTeam`, and `winnerTeam` are lazy `@ManyToOne` associations
to `team`. The winner association is nullable.

`competition` has no dedicated gender column; RFETM and FCTT both fold gender
into this value as `<competition-slug>-<masculino|femenino>` (for example
`tercera-nacional-masculino`, `copa-catalana-femenina-1a-femenino`). FCTT also
populates `phase` from the source payload's `fase` field, following BCNESA's
existing use of that column to disambiguate fixtures that reuse round numbers
across phases within the same group.

### `lineup`

Player assignment to a team and position in a match.

| Column | Type | Null | Key/index |
| --- | --- | --- | --- |
| `id` | `UUID` | No | Primary key |
| `source` | `VARCHAR(20)` | Yes | — |
| `match_id` | `UUID` | No | FK to `match_record`; `idx_lineup_match_id` |
| `team_id` | `UUID` | No | FK to `team`; `idx_lineup_team_id` |
| `letter` | `VARCHAR(2)` | No | — |
| `position` | `INTEGER` | No | — |
| `player_id` | `UUID` | No | FK to `player_season`; `idx_lineup_player_id` |
| `ranking` | `DECIMAL(10,2)` | Yes | — |

The unique constraint `uk_match_team_letter_position` covers
`(match_id, team_id, letter, position)`. `match`, `team`, and `player` are
lazy `@ManyToOne` associations.

### `game`

Individual singles or doubles game within a match.

| Column | Type | Null | Key/index |
| --- | --- | --- | --- |
| `id` | `UUID` | No | Primary key |
| `source` | `VARCHAR(20)` | No | — |
| `match_id` | `UUID` | No | FK to `match_record`; `idx_game_match_id` |
| `game_number` | `INTEGER` | No | Unique within match |
| `type` | `VARCHAR(10)` | No | `INDIVIDUAL` or `DOUBLES` |
| `crossover` | `VARCHAR(20)` | No | — |
| `home_player_id` | `UUID` | Yes | FK to `player_season`; `idx_game_home_player_id` |
| `away_player_id` | `UUID` | Yes | FK to `player_season`; `idx_game_away_player_id` |
| `home_sets_won` | `INTEGER` | Yes | — |
| `away_sets_won` | `INTEGER` | Yes | — |
| `winner` | `VARCHAR(4)` | Yes | `HOME` or `AWAY` |
| `cumul_home` | `INTEGER` | No | — |
| `cumul_away` | `INTEGER` | No | — |
| `not_played` | `BOOLEAN` | No | Database default `false` |
| `reason` | `VARCHAR(255)` | Yes | — |

The unique constraint `uk_match_game_number` covers `(match_id, game_number)`.
`match`, `homePlayer`, and `awayPlayer` are lazy `@ManyToOne` associations;
the player associations are nullable. `GameJPA` does not expose JPA
collections for sets or doubles pairs.

### `set_score`

Point score for one set in a game.

| Column | Type | Null | Key/index |
| --- | --- | --- | --- |
| `id` | `UUID` | No | Primary key |
| `source` | `VARCHAR(20)` | Yes | — |
| `game_id` | `UUID` | No | FK to `game`; `idx_set_score_game_id` |
| `set_number` | `INTEGER` | No | Unique within game |
| `home_points` | `INTEGER` | No | — |
| `away_points` | `INTEGER` | No | — |

The unique constraint `uk_set_score_game_set_number` covers
`(game_id, set_number)`. `game` is a lazy `@ManyToOne` association.

### `doubles_pair`

Player membership of a doubles game side.

| Column | Type | Null | Key/index |
| --- | --- | --- | --- |
| `id` | `UUID` | No | Primary key |
| `source` | `VARCHAR(20)` | Yes | — |
| `game_id` | `UUID` | No | FK to `game`; `idx_doubles_pair_game_id` |
| `side` | `VARCHAR(4)` | No | `HOME` or `AWAY` |
| `player_id` | `UUID` | No | FK to `player_season`; `idx_doubles_pair_player_id` |

The unique constraint `uk_doubles_pair_game_side_player_source` covers
`(game_id, side, player_id, source)`. `game` and `player` are lazy
`@ManyToOne` associations.

## Authentication tables

### `AppUser`

The entity declares the table name as `AppUser`.

| Column | Type | Null | Key/index |
| --- | --- | --- | --- |
| `id` | `UUID` | No | Primary key; not updatable |
| `username` | String | No | Unique; `idx_user_username` |
| `email` | String | No | Unique; `idx_user_email` |
| `password_hash` | String | No | — |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | No | — |
| `is_active` | `BOOLEAN` | No | — |

`username` and `email` are unique both through their column mappings and the
unique indexes declared by `UserJPA`.

### `AppUserRole`

An element-collection table for `UserJPA.roles`, declared with
`@CollectionTable(name = "AppUserRole", joinColumns = @JoinColumn(name =
"user_id"))`.

| Column | Type | Null | Key/index |
| --- | --- | --- | --- |
| `user_id` | `UUID` | No | Join column to `AppUser.id` |
| `role` | `VARCHAR(30)` | No | Enum string |

The collection is initialized with the default role `PRACTITIONER`. No
explicit cascade or orphan-removal setting is declared on the collection.

### `PasswordRecoveryToken`

One-time password-recovery records. The raw token is not persisted; only its
SHA-256 hash is stored.

| Column | Type | Null | Key/index |
| --- | --- | --- | --- |
| `id` | `UUID` | No | Primary key; not updatable |
| `user_id` | `UUID` | No | Scalar user identifier |
| `token_hash` | `VARCHAR(64)` | No | Unique; `idx_recovery_token_hash` |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | No | — |
| `expires_at` | `TIMESTAMP WITH TIME ZONE` | No | `idx_recovery_expiry` |
| `consumed` | `BOOLEAN` | No | — |

The table constraint is `uk_recovery_token_hash`. `user_id` is deliberately a
scalar UUID field; `PasswordRecoveryTokenJPA` does not declare a JPA
association to `UserJPA`.

## Repository lookup behavior

Spring Data helper repositories expose the following persistence lookups in
addition to ordinary CRUD operations:

| Repository | Lookup behavior |
| --- | --- |
| `ResourceRepositoryHelper` | Exact `(logic_path, name)`; all resources under a logic path. |
| `ImportResourceRepositoryHelper` | Exact `(source, type, season)`; rows by `(source, type)` newest first; rows by status; rows by source. |
| `ClubRepositoryHelper` | Exact canonical club name. |
| `PlayerRepositoryHelper` | Exact canonical player name (not unique; see `player`); specification queries. |
| `FederatedClubRepositoryHelper` | Source-scoped exact name, all rows by source, rows by canonical club id, and case-insensitive name searches (optionally source-scoped); list results can be sorted. Counts distinct trimmed, case-insensitive names. The adapter also supports fragment-based searches through specifications. |
| `FederatedPlayerRepositoryHelper` | Source-scoped exact name, source-scoped licence, and rows by canonical player id. Counts distinct trimmed, case-insensitive names. The adapter also supports fragment-based searches through specifications. |
| `TeamRepositoryHelper` | Exact `(name, season, source)`, first team by federated club and season, all teams by federated club (fetching the club), all teams by source, and case-insensitive name searches with optional season/source. Counts distinct federated clubs per season. |
| `PlayerSeasonRepositoryHelper` | Exact `(source, license, season)`, all rows by source, rows by federated player ids (fetching federated and canonical players), source-scoped players and their competitions for team ids through lineups. Counts distinct federated players per season. |
| `MatchRepositoryHelper` | Exact external id; natural-key lookup by competition, season, group, round, phase, home team, and away team (null group/phase match null); team-id searches optionally filtered by source, season, and competition — deliberately **not** filtered by status (FEAT-00079 consolidation exception; statistics handlers filter in memory); paginated source/season search with mandatory match-status predicate (default `PLAYED`), competition, date range, club-name and player-name fragments, player id, and home/away location, plus its count; paginated fragment search over team and player names restricted to `PLAYED`; all matches by source restricted to `PLAYED`; distinct seasons (`PLAYED` only overall, all statuses by source for option lists) and competitions by source and season (unfiltered); `PLAYED`-only match count per season and overall (`countAllPlayed`); FEAT-00078 backfill candidates by source and season (with per-match game/lineup counts), the same rule restricted to a match-id collection, and the bulk `SCHEDULED`-marking update; the FEAT-00080 schedule-only update guarded by `status = SCHEDULED`. |
| `LineupRepositoryHelper` | Rows for a match id (optionally ordered by team and position); rows for match ids or player-season ids (optionally paginated), fetching match, teams, clubs, and players; bulk delete by match ids (FEAT-00078 backfill). |
| `GameRepositoryHelper` | All games for a match id, or for a collection of match ids, ordered by match and `game_number` ascending; bulk delete by match ids (FEAT-00078 backfill). |
| `SetScoreRepositoryHelper` | Set scores for a collection of game ids, ordered by game and `set_number`; bulk delete by match ids (FEAT-00078 backfill). |
| `DoublesPairRepositoryHelper` | All doubles-pair rows for a collection of game ids, ordered by game, side, and id; bulk delete by match ids (FEAT-00078 backfill). |
| `ScheduledMatchBackfillRepositoryJpa` | Implements the domain `ScheduledMatchBackfillRepository` port by composing the helpers above: read-only candidate listing, and an all-or-nothing `markScheduled` that re-checks every id, deletes child rows in FK order, and updates the match header, chunking ids by 500 per transaction. |
| `UserRepositoryHelper` | Exact username/email lookup and existence checks; paginated case-insensitive username/email search, optionally filtered by active flag; count of active users per role. |
| `PasswordRecoveryTokenRepositoryHelper` | Active token lookup by hash; atomic conditional consumption by token id or user id. |
| `SettingRepositoryHelper` | Exact `(category, name)`; all settings in a category. |
| `ConsolidationActionRepositoryHelper` | All actions newest first; actions involving a club id snapshot, newest first. |

Repository queries that traverse teams, players, lineups, or matches apply
source predicates where the operation is source-scoped. The database natural
keys remain the final integrity boundary; repository method names do not
replace the declared constraints.

## Entity relationship summary

The associations are owned by the child entities:

```mermaid
erDiagram
    RESOURCE o|--o{ IMPORT_RESOURCE : tracked_by
    CLUB o|--o{ FEDERATED_CLUB : canonicalizes
    FEDERATED_CLUB o|--o{ TEAM : groups
    PLAYER o|--o{ FEDERATED_PLAYER : canonicalizes
    FEDERATED_PLAYER o|--o{ PLAYER_SEASON : registers
    TEAM ||--o{ MATCH_RECORD : home_team
    TEAM ||--o{ MATCH_RECORD : away_team
    TEAM o|--o{ MATCH_RECORD : winner_team
    MATCH_RECORD ||--o{ LINEUP : contains
    TEAM ||--o{ LINEUP : represents
    PLAYER_SEASON ||--o{ LINEUP : assigned
    MATCH_RECORD ||--o{ GAME : contains
    PLAYER_SEASON o|--o{ GAME : home_player
    PLAYER_SEASON o|--o{ GAME : away_player
    GAME ||--o{ SET_SCORE : scores
    GAME ||--o{ DOUBLES_PAIR : contains
    PLAYER_SEASON ||--o{ DOUBLES_PAIR : paired
    CONSOLIDATION_ACTION ||--|{ CONSOLIDATION_ACTION_CLUB : involves
    CONSOLIDATION_ACTION_CLUB {
        uuid club_id "snapshot, no FK to CLUB"
    }
    APP_USER ||--o{ APP_USER_ROLE : has
    PASSWORD_RECOVERY_TOKEN {
        uuid user_id "scalar, no FK to APP_USER"
    }
```

`SET_SCORE` and `DOUBLES_PAIR` point to `GAME` from their own entities.
Likewise, `MATCH_RECORD` and `GAME` do not expose inverse collection mappings.
Team and player season rows preserve season-specific identity; canonical club
and player links do not retarget historical match, lineup, game, or doubles
pair foreign keys.

`APP_USER`, `APP_USER_ROLE`, and `PASSWORD_RECOVERY_TOKEN` stand for the
`AppUser`, `AppUserRole`, and `PasswordRecoveryToken` tables. Two id columns
are deliberately drawn without a relationship line because they are not
foreign keys:

- `consolidation_action_club.club_id` is a snapshot of a club that the merge
  usually deletes; see [Consolidation audit tables](#consolidation-audit-tables).
- `PasswordRecoveryToken.user_id` is a scalar UUID with no JPA association to
  `UserJPA`; see [`PasswordRecoveryToken`](#passwordrecoverytoken).

## Settings

### `setting`

Application settings, one row per `(category, name)`. The table is separate
from deployment configuration and must never store datasource credentials, JWT
secrets, mail credentials, or other secrets.

| Column | Type | Null | Key/index |
| --- | --- | --- | --- |
| `id` | `UUID` | No | Primary key |
| `category` | `VARCHAR` | No | `SettingCategory` enum string; `idx_setting_category_name` |
| `name` | `VARCHAR(255)` | No | `idx_setting_category_name` |
| `value` | `VARCHAR(255)` | No | — |

The unique constraint `uk_setting_category_name` covers `(category, name)`.
`value` is a reserved word in several databases, so `SettingJPA` maps it as a
quoted identifier (`"value"`). There is no version column and no optimistic
locking.

## Consolidation audit tables

Every manual club consolidation (`POST /api/v1/clubs/consolidate`) writes one
historic record, so that a merge is no longer a silent, unrecoverable loss of
the merged-away clubs' identities. Automated import-time consolidation does not
write to these tables.

### `consolidation_action`

| Column | Type | Nullability | Notes |
|---|---|---|---|
| `id` | uuid | not null | Primary key |
| `type` | varchar | not null | `MERGE`, `SPLIT`, or `RENAME`; only `MERGE` is produced today |
| `occurred_on` | timestamptz | not null | Taken from the domain event, indexed by `idx_consolidation_action_occurred_on` |
| `performed_by_user_id` | uuid | nullable | The authenticated user; null when no principal was present |
| `performed_by_username` | varchar(255) | nullable | Username snapshot, kept when the user row is later deleted |
| `canonical_name` | varchar(255) | not null | The resulting name, i.e. the **post**-merge name |

### `consolidation_action_club`

One row per club taking part in the action, on either side.

| Column | Type | Nullability | Notes |
|---|---|---|---|
| `id` | uuid | not null | Primary key |
| `consolidation_action_id` | uuid | not null | FK to `consolidation_action` |
| `role` | varchar | not null | `SOURCE` (consumed by the action) or `TARGET` (survived it) |
| `club_id` | uuid | not null | See below; indexed by `idx_consolidation_action_club_club_id` |
| `club_name` | varchar(255) | not null | The club's name at the time of the action |

`club_id` is deliberately **not** a foreign key to `club`. A merge deletes every
`SOURCE` club row in the same operation that writes this record, so a foreign
key would either reject the insert or cascade away the audit trail these tables
exist to keep. Both `club_id` and `club_name` are snapshots: `SOURCE` ids
generally no longer resolve, and history must never be read back by resolving
them through `ClubRepository`.

`SOURCE` names are pre-merge while `canonical_name` is post-merge; that
asymmetry is intended and is what makes the record useful.

`ConsolidationActionJPA.clubs` is a lazy `@OneToMany(mappedBy = "action",
cascade = ALL, orphanRemoval = true)` collection, initialized to an empty list;
`ConsolidationActionClubJPA.action` is the owning lazy `@ManyToOne`. Saving an
action therefore persists its club rows, and deleting it removes them.
