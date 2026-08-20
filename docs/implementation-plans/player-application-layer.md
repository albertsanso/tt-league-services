# Player application layer implementation plan

## Scope

Add the Player application surface under
`org.cttelsamicsterrassa.data.core.application.player`, mirroring the existing
Club application structure while using the Player and PlayerSeason repository
ports and preserving source-scoped identity.

## Planned packages and types

### `player.create`

- `CreatePlayerCommand`: command carrying occurred time, command id, player
  UUID when supplied by an importer, display name, and `ImportSource`.
- `CreatePlayerCommandHandler`: reject an existing source/name identity;
  otherwise create with `Player.createNew` and save through `PlayerRepository`.
- `CreatePlayerSeasonCommand`: command carrying source, player-season name,
  licence, parent player, and `Season`.
- `CreatePlayerSeasonCommandHandler`: resolve or receive the parent Player,
  reject an existing `(source, license, season)` registration, create with
  `PlayerSeason.createNew`, and save through `PlayerSeasonRepository`.

The parent Player should be represented by the domain `Player` relationship,
not an external id. If the command is intended for importer use, it should
carry the already resolved Player, matching the domain model and avoiding an
unscoped name lookup.

### `player.update`

- `ModifyPlayerNameCommand` and `ModifyPlayerNameCommandHandler`, following
  the Club equivalent and saving the modified aggregate.
- `ModifyPlayerSeasonNameCommand` and
  `ModifyPlayerSeasonNameCommandHandler`, following the same pattern for a
  `PlayerSeason`.

Handlers should preserve the existing response convention: successful
mutation returns `DomainCommandResponse.successResponse`, and a missing
aggregate returns a non-throwing “not found” success response as the Club
implementation currently does.

### `player.delete`

- `DeletePlayerCommand` and `DeletePlayerCommandHandler`.
- `DeletePlayerSeasonCommand` and `DeletePlayerSeasonCommandHandler`.

Each handler publishes the domain deletion event by calling `delete()` before
calling the matching repository deletion operation. The PlayerSeason repository
port currently exposes save/find only, so deletion requires adding an explicit
`deletePlayerSeasonById(UUID)` port method or deferring the PlayerSeason delete
handler until that port exists; do not silently substitute save or an
unscoped operation.

### `player.find`

- `FindPlayerByIdQuery` / handler -> `findPlayerById`.
- `FindPlayerByNameQuery` / handler -> `findPlayerByName` only if retaining
  exact Club API compatibility; source-aware lookup must also be available.
- `FindPlayerBySourceAndNameQuery` / handler -> `findPlayerBySourceAndName`.
- `FindPlayerSeasonByIdQuery` / handler -> `findPlayerSeasonById`.
- `FindPlayerSeasonByLicenseAndSeasonQuery` / handler ->
  `findPlayerSeasonByLicenseAndSeason(source, license, season)`.

The source-aware queries are required for federation-safe imports. Name-only
queries may remain as compatibility wrappers because the repository ports
currently expose them, but new importer-facing code must use source-scoped
queries.

## Validation and tests

Add focused JUnit 5 tests for command payloads and each handler’s success,
duplicate, missing, source-scoped lookup, mutation, event-triggering, and save/
delete behavior. First verify the existing module baseline, then run:

`mvn -pl tt-data-league-core-domain -am test`

Finally run the repository-required full build:

`mvn test`

## Open design decision before implementation

The Club reference has no Team command/update/delete handlers and its
name queries are not source-scoped, while PlayerSeason has a source-scoped
licence natural key and no delete repository operation. Implementation should
confirm whether to:

1. mirror only the existing Club application surface exactly, or
2. provide the complete aggregate surface above and extend
   `PlayerSeasonRepository` with deletion.
