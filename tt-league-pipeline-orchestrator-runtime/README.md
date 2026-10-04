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
| `PIPELINE_DB_URL` | `spring.datasource.url` | JDBC URL of the PostgreSQL database holding schema `pipeline` |
| `PIPELINE_DB_USERNAME` | `spring.datasource.username` | Database user (ideally limited to schema `pipeline`) |
| `PIPELINE_DB_PASSWORD` | `spring.datasource.password` | Database password |
| `PIPELINE_SERVER_PORT` | `server.port` | Optional, default `8095` |

`/actuator/health` and `/actuator/info` are exposed.

## Persistence

Flyway owns schema `pipeline` (it creates it and keeps its history table
there); Hibernate only validates (`ddl-auto: validate`). The tables are
documented in [docs/pipeline-datamodel.md](docs/pipeline-datamodel.md). The
module may share the platform database or use its own, and never touches
platform tables.

The persistence tests run against PostgreSQL through Testcontainers and need
Docker; without Docker they are reported as skipped, not passed.

## Build and run

```text
mvn -pl tt-league-pipeline-orchestrator-runtime -am test
java -jar tt-league-pipeline-orchestrator-runtime/target/tt-league-pipeline-orchestrator-runtime-0.0.1-SNAPSHOT.jar
```
