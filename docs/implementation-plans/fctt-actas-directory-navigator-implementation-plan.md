# FCTT Actas Directory Navigator Implementation Plan

## Overview

This implementation adds an FCTT-specific directory navigator to the import module. It walks FCTT
match-report exports, derives the season, competition, and group from the directory hierarchy, parses
each report, and dispatches a source-specific context to an extensible list of processors.

The navigator is responsible for discovery, validation, parsing, context construction, logging, and
traversal accounting. Processors translate a valid FCTT context into domain operations through
repository ports. They do not depend on JPA implementation classes or runtime wiring.

The requested import module and its dependency from `tt-data-league-import-runtime` already exist in the Maven reactor. This work must extend that existing setup; it must not add another module or alter the root module list.

## Directory Structure

The configured base directory is normally:

```text
C:\git\fctt-extract\resources\actas-json
```

The navigator must expect this layout:

```text
[base-folder]/
  [season]/
    [league-competition]/
      [group]/
        jornada_[day]_partido_[match_number].json
```

Examples:

```text
2023-2024/
  tercera nacional/
    G1/
      jornada_1_partido_42.json
```

The directory names are authoritative only for their own path context:

| Path part | Context field | Validation |
| --- | --- | --- |
| `[season]` | `season` | Require the existing `YYYY-YYYY` season-folder form. |
| `[league-competition]` | `leagueCompetition` | Preserve the folder name verbatim; do not infer it from the payload. |
| `[group]` | `group` | Preserve the folder name verbatim and require `G<number>` or `<number>` before dispatch, because match persistence requires an integer group number. |
| Report file | `matchReportFile` | Accept only `jornada_.*_partido_.*\.json` files; treat both suffixes as opaque identifiers. |

The report filename must never supply a match day, teams, club identities, or any other business value. The parsed JSON field `jornada` is the sole source for the round. A parseable report whose `jornada` is absent is skipped, logged, and counted because it cannot be assigned to a round.

Before implementation, compare representative FCTT JSON files with the current shared `Acta` model and `ActaParser`. The repository does not currently contain the prompt's stated `org.cttelsamicsterrassa.data.load.fctt.parse` package. Reuse `shared.parse.ActaParser` only if its JSON mapping correctly represents FCTT reports; otherwise, add FCTT parser value objects and a parser under `data.load.fctt.parse` without changing RFETM or BCNESA parsing behavior.

## Traverse/Navigation Component

Create `org.cttelsamicsterrassa.data.load.fctt.traverse.FcttActasDirectoryNavigator` in `tt-data-league-import`.

It should follow the established navigator pattern used by `RfetmActasDirectoryNavigator` and `BcnesaActasDirectoryNavigator`:

1. Be a Spring component with constructor-injected parser and processor list.
2. Expose `traverse(Path, List<FcttMatchReportProcessor>)` as the primary entry point, plus injected-processor overloads for `Path` and `String`.
3. Expose `traverseSeason(Path, String, List<FcttMatchReportProcessor>)` and an injected-processor overload to select one season.
4. Validate that the base path is a directory and throw `IOException` otherwise.
5. List directory entries and matching report files in deterministic filename order.
6. Validate season folders, recurse through competition and group folders, and log-and-skip unexpected layout entries.
7. Parse every matching report exactly once. Catch the parser's documented parse exception, log the report path and reason, increment the skipped count, and continue.
8. Reject a parsed report with no `jornada`; use the payload value as `round` for the processor context.
9. Dispatch a fully populated `FcttMatchReportContext` to every supplied processor. Catch `RuntimeException` around each individual processor invocation so one failure neither stops the traversal nor blocks its peers.
10. Return a source-appropriate summary. `TraversalSummary` can be reused if its four counters are sufficient; otherwise add a small immutable `FcttTraversalSummary` rather than changing unrelated navigator results.

The navigator must not perform persistence, derive identities from filenames, resolve clubs or players by unscoped names, or contain FCTT-to-JPA mapping logic.

## Processing Components

Add the source-specific contract in `org.cttelsamicsterrassa.data.load.fctt.process`:

```java
@FunctionalInterface
public interface FcttMatchReportProcessor {
    void process(FcttMatchReportContext context);
}
```

Add an immutable `FcttMatchReportContext` containing at least:

```text
season
leagueCompetition
group
round
matchReportFile
parsed report payload
```

The context should validate required path values and file path, expose `toSeason()` through the existing domain `Season` factory, and expose the competition identity derived from the authoritative directory context. Team, player, and any source-system identifiers must come from the parsed payload and be scoped by FCTT, season, competition, and group where required.

Do not reuse `shared.process.MatchReportContext` or `MatchContextProcessor`: they currently encode the RFETM day/sex hierarchy and `RfetmClubKey`. FCTT needs its own processor contract and context to avoid coupling FCTT imports to RFETM identity rules.

Concrete FCTT persistence processors are implemented as stateless, idempotent Spring beans using
domain repository ports:

1. `FcttTeamImportProcessor` is `@Order(10)` and stores FCTT-scoped clubs plus team
   registrations from payload teams.
2. `FcttPlayerImportProcessor` is `@Order(20)` and stores FCTT-scoped players plus player-season
   registrations from lineups and doubles participants.
3. `FcttMatchImportProcessor` is `@Order(30)` and stores an idempotent natural-key match, lineups,
   games, set scores, and doubles-pair members when present.

The processors use `ImportSource.FCTT`; source-scoped club and player lookup prevents cross-federation
merges, and no external-id fields are added to either entity. Spring injects the ordered processor list
into the navigator automatically. The runtime's `--source=fctt` dispatch therefore imports complete
reports without explicit processor wiring.

## Implementation Steps

1. Use `shared.parse.ActaParser` for the supported FCTT JSON fields.
2. Keep `FcttMatchReportContext` and `FcttMatchReportProcessor` separate from RFETM context types.
3. Traverse sorted report files, derive rounds from payload `jornada`, and reject malformed or
   missing-round reports.
4. Parse and validate the group folder before dispatch; invalid group names are logged and skipped
   rather than coerced.
5. Persist complete reports through the three ordered processors and verify club, player, match,
   lineup, game, set-score, doubles, and idempotency behavior with in-memory repositories.
6. Keep the runtime's `fctt` source dispatch and Spring auto-discovery enabled.
7. Run `mvn -pl tt-data-league-import -am test`, then run `mvn test` from the repository root.

## Testing

Add `FcttActasDirectoryNavigatorTest` alongside the existing navigator tests. It should cover:

1. A valid `[season]/[competition]/[group]` report produces one context with the three path values, the source file path, parsed payload, and `round` from `jornada`.
2. A filename whose apparent day differs from payload `jornada` dispatches the payload value, proving the filename is opaque.
3. Multiple valid opaque `jornada_*_partido_*.json` names are discovered, while unrelated JSON files are ignored.
4. A report without `jornada` is skipped and counted without invoking a processor.
5. Malformed JSON is skipped and a later valid report still reaches processors.
6. Invalid base directories throw `IOException`; invalid season folders and unexpected layout entries are skipped without dispatch.
7. A failing processor increments the processor-failure count while peer processors receive every valid context.
8. An explicit processor list overrides injected processors, and `traverseSeason` processes only the requested season.
9. Parsing tests demonstrate the chosen FCTT model captures the fields required by contexts and future processors without silently substituting malformed source values.
10. When runtime selection is added, focused runtime tests cover `--source=fctt`, optional `--season`, and rejection of unknown sources.

## Conclusion

The FCTT navigator will be a source-specific orchestration layer: it traverses the FCTT tree, reads the round and teams from report payloads, and hands validated contexts to independently extensible processors. Its contract deliberately keeps filename identifiers opaque, keeps FCTT identity and mapping rules out of shared RFETM types, and preserves the import module's separation from JPA and runtime concerns.
