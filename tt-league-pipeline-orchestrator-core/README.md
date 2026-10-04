# tt-league-pipeline-orchestrator-core

Framework-free core of the pipeline orchestrator: run state machine, match-day
tracker rules, polling policy and the ports implemented by
`tt-league-pipeline-orchestrator-runtime`.

The `run` package contains the `PipelineRun` aggregate with its `RunStatus`
state machine (illegal transitions throw `IllegalRunTransitionException`), the
`PipelineStep`, `RunArtifact` and `ImportReport` values, and the repository
ports in `run.port`.

This module has no Spring, JPA or HTTP-client dependency and no dependency on
`tt-data-league-*` modules; a test enforces it.

```text
mvn -pl tt-league-pipeline-orchestrator-core -am test
```
