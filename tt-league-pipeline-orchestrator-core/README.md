# tt-league-pipeline-orchestrator-core

Framework-free core of the pipeline orchestrator: run state machine, match-day
tracker rules, polling policy and the ports implemented by
`tt-league-pipeline-orchestrator-runtime`.

The `run` package contains the `PipelineRun` aggregate with its `RunStatus`
state machine (illegal transitions throw `IllegalRunTransitionException`), the
`PipelineStep`, `RunArtifact` and `ImportReport` values, and the repository
ports in `run.port`.

The `execution` package drives a run: the ports to ingest, the platform and the
artifact store (`execution.port`: `IngestGateway`, `ImportGateway`,
`ArtifactStore`, `RunDispatcher`, `RunClock`, `RunObserver`, `GatewayException`),
the settings (`RetryPolicy`, `StepTimeouts`, `PollIntervals`), `RunExecutor`,
`RunLauncher` (the single trigger path) and `RunRecovery`. It uses JDK types
only and logs through `System.Logger`. Time and sleeping go through `RunClock`,
so tests never wait.

The `trigger` package holds `TriggerRun`, the single path that creates manual and scheduled runs (one outcome per
source: created, queued, rejected or unavailable), the one-per-source `PendingTrigger` with its repository port, the
`OpenMatchDayScopeResolver` port and `PendingTriggerDrainer`. `CompositeRunObserver` fans a change out to several
observers; it logs and swallows an observer failure, the one deliberate broad catch, so a side channel never fails a run.

The `tracker` package holds the match-day tracker: the `MatchDay` and `MatchTracking` aggregates, the
`MatchDayWindow` (first date to last date plus the platform grace days), `TrackerRules` (the only place that maps a
platform `calendarState` to a match status and decides opening, closing and reopening), `MatchDayTracker` (one
recompute of a source and season from round progress and calendar reads, applied as a single change set or not at
all), `MatchDayActions` (close, reopen, ignore, unignore and note, each recorded with actor and time) and
`TrackerRunObserver` (requests a recompute when a run reaches a final status). Its ports are
`PlatformMatchGateway`, `MatchDayRepository` and `RecomputeRequests`; the platform is the only source of match
states and no result is ever stored.

The test sources publish the in-memory repositories, scripted gateways, fake
clock, `ExecutorHarness`, the pending-trigger and match-day repositories, scripted platform gateway, scope-resolver stub and event recorder (`execution.testing`) as the module's `test-jar`;
the runtime tests reuse them.

This module has no Spring, JPA or HTTP-client dependency and no dependency on
`tt-data-league-*` modules; a test enforces it.

```text
mvn -pl tt-league-pipeline-orchestrator-core -am test
```
