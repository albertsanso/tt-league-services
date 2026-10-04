# tt-league-pipeline-orchestrator-runtime

Spring Boot orchestrator service. Depends on
`tt-league-pipeline-orchestrator-core`; the platform and `tt-league-ingest`
are reached over HTTP only.

## Configuration

All required variables have no default; startup fails when one is missing or
invalid.

| Variable | Property | Description |
| --- | --- | --- |
| `PIPELINE_PLATFORM_URL` | `tt.pipeline.platform.base-url` | Base URL of the platform REST API |
| `PIPELINE_INGEST_URL` | `tt.pipeline.ingest.base-url` | Base URL of `tt-league-ingest-rest` |
| `PIPELINE_INGEST_API_KEY` | `tt.pipeline.ingest.api-key` | `X-API-Key` for the ingest service (not blank) |
| `JWT_SIGNING_SECRET` | `tt.pipeline.security.jwt-secret` | Platform HS256 secret, at least 32 characters |
| `PIPELINE_SERVER_PORT` | `server.port` | Optional, default `8095` |

`/actuator/health` and `/actuator/info` are exposed.

## Build and run

```text
mvn -pl tt-league-pipeline-orchestrator-runtime -am test
java -jar tt-league-pipeline-orchestrator-runtime/target/tt-league-pipeline-orchestrator-runtime-0.0.1-SNAPSHOT.jar
```
