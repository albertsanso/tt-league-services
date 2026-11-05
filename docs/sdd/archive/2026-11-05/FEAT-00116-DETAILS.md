# Build Plan
Target: one VM running Docker Engine with the Compose plugin (D9 in FEAT-00096). The reverse proxy serves both
frontends and the APIs they call, so neither frontend logs in cross-origin (D10).

The plan has five parts:
- Steps 1-3: small platform changes that a container needs (health endpoint, initial import folder).
- Steps 4-6: images for the platform API, the orchestrator runtime and `tt-league-ingest-rest`.
- Steps 7-8: the reverse-proxy image that serves both built frontends.
- Steps 9-11: the Compose project, its environment template and the PostgreSQL initialisation.
- Steps 12-14: documentation and validation.

There is no parent-POM change, no new Python dependency and no Flyway migration. One dependency is added to
`tt-data-league-api-runtime/pom.xml` only: `spring-boot-starter-actuator`, with its version from the Spring Boot BOM.

**Decisions proposed in this plan (2026-10-05), to confirm before `ready`.**
- **P1, Java images copy a host-built jar.** The `org.albertsanso:*` SNAPSHOT dependencies (`commons-core`,
  `commandbus-/querybus-/eventbus-synchronous-inmemory`) exist only in the developer's local `~/.m2`. No repository
  in the POMs serves them, so Maven cannot run inside a Docker build. The Java Dockerfiles therefore copy the Boot
  jar that `mvn -pl <module> -am package -DskipTests` built on the host. The build context is the module directory.
  Publishing those artifacts to a Maven repository, so that images can build from source, is a follow-up.
- **P2, frontends are built inside the proxy image.** They only need the npm registry, so a Node stage builds both
  SPAs and an `nginxinc/nginx-unprivileged` stage serves them. No frontend source or Vite change is needed: both
  SPAs call relative `/api/...` paths, and the pipeline SPA treats empty `VITE_*_BASE_URL` values as same-origin.
- **P3, one origin per frontend, on two proxy ports.** Both SPAs use `BrowserRouter` at `/` with no `basename`, and
  both call the platform at `/api/v1`. They cannot share one origin without changing their routing. The proxy
  listens on two ports: platform UI (default host port 8080) and pipeline UI (default 8081). Each origin also proxies
  the API paths its SPA calls, so there is no CORS and `PIPELINE_CORS_ALLOWED_ORIGINS` stays empty. Hostname-based
  virtual hosts can replace the ports later without touching the images' content.
- **P4, plain HTTP inside the VM; TLS is out of scope.** The proxy publishes HTTP only. TLS is terminated in front of
  the VM (or by a later change to the proxy). The README states that the stack must not be exposed to the internet
  over plain HTTP. See the open question in Notes.
- **P5, a separate database role for the orchestrator.** Both services use the `ttleaguedata` database (D8), but the
  orchestrator connects as a `pipeline` role that owns only the `pipeline` schema. This enforces the rule that the
  orchestrator never reads or writes platform tables. A first-start init script creates the role.
- **P6, the platform's initial import folder comes from the environment.** `ImportFolderSetting.DEFAULT_VALUE` is
  `c:\tt-repository`, which is a relative file name on Linux. An optional `IMPORT_REPOSITORY_FOLDER_INITIAL` value is
  used when the `IMPORT/repository-folder` setting is created for the first time. An existing setting is never
  overwritten, and leaving the variable unset keeps today's default.

**Contracts used (read from the code on 2026-10-05).**
- Spring Boot 3.5.8 (BOM imported in the root POM). Both runtimes use `spring-boot-maven-plugin` `repackage` and
  produce `target/<artifactId>-0.0.1-SNAPSHOT.jar`.
- Platform (`tt-data-league-api-runtime`): HTTP on 8080 (Boot default), `management.server.port: 9090`,
  `management.endpoints.web.exposure.include: "*"`. **There is no Actuator dependency**, in the POM or in the packaged
  jar, so the `/actuator/health` URL that its README documents does not exist today. Configuration variables:
  `DB_TTLEAGUEDATA_JDBC_URL`, `DB_TTLEAGUEDATA_CREDENTIAL_USERNAME`/`_PASSWORD`, `JWT_SIGNING_SECRET` (it has a
  dev placeholder default), `PASSWORD_RECOVERY_RESET_URL`, `MAIL_*`, `IMPORT_UPLOAD_MAX_FILE_SIZE`/`_REQUEST_SIZE`
  (100MB), and service credentials as `SECURITY_SERVICECREDENTIALS_0_NAME`/`_KEYSHA256`/`_PERMISSIONS` (FEAT-00101,
  only the key's SHA-256 is configured). `ddl-auto: update` creates the schema on an empty database.
  `InitialUserStartupInitializer` seeds two hard-coded ADMIN accounts.
- `ImportFolderSettingStartupInitializer` (a `CommandLineRunner` in `<api-runtime>/config`) calls
  `ImportFolderSettingProvisioningService.ensureDefaultExists()` (in `tt-data-league-core-domain`, `@Named`, no
  Spring), which creates the setting with `ImportFolderSetting.DEFAULT_VALUE` when it is absent.
- Orchestrator runtime: HTTP on `PIPELINE_SERVER_PORT` (8095), API under `/api/pipeline`, SSE events, Actuator
  `health,info,prometheus` on the same port, all three public in `SecurityConfiguration`. FEAT-00115 requires that
  `/actuator/prometheus` is not routed through the public proxy. Required variables: `PIPELINE_DB_URL`/`_USERNAME`/
  `_PASSWORD`, `PIPELINE_PLATFORM_URL`, `PIPELINE_PLATFORM_API_KEY`, `PIPELINE_INGEST_URL`,
  `PIPELINE_INGEST_API_KEY`, `JWT_SIGNING_SECRET`, `PIPELINE_ARTIFACTS_DIR` (an existing, writable directory). Its
  defaults (`PIPELINE_PLATFORM_URL` 8090, `PIPELINE_INGEST_URL` 8091) do not match the services' own defaults, so
  Compose sets every URL explicitly.
- `tt-league-ingest-rest`: Python 3.12 uv workspace, entry point `tt-league-ingest-rest` (`ingest_rest.main:main`),
  FastAPI and uvicorn. `TT_INGEST_REST_API_KEY` is required, `TT_INGEST_REST_HOST` defaults to `127.0.0.1` (it must
  be `0.0.0.0` in a container), `TT_INGEST_REST_PORT` defaults to 8090, and `TT_INGEST_REST_LOG_FORMAT` to `json`.
  `TT_INGEST_DATA_DIR` must be an existing directory. Unauthenticated `GET /health`. The routes under
  `/api/v1/ingest/...` need `X-API-Key`. `TT_LEAGUE_API_URL`/`_TOKEN` are used only by the `upload` step, which the
  orchestrator never requests (D5).
- Frontends: Vite builds to `dist/` with `npm run build`, Node v20.19.0 (pinned in both frontend POMs). The pipeline
  SPA calls `/api/pipeline/...` (including the `fetch`-streamed events in `RunEventsProvider`) and `/api/v1/auth/...`.
  The platform SPA calls `/api/v1/...`, including ZIP uploads.
- `.podman/podman-compose.yaml` is a local development database (with pgAdmin). It stays as it is.

**Steps**

1. **Platform health endpoint.** In `tt-data-league-api-runtime/pom.xml`, add `spring-boot-starter-actuator` (BOM
   version). In its `application.yml`, change `management.endpoints.web.exposure.include` from `"*"` to
   `health,info`, because adding Actuator would otherwise expose `env`, `heapdump` and others on port 9090. Enable
   `management.endpoint.health.probes.enabled: true`, keep `management.server.port: 9090`, and change
   `show-details: always` to `when-authorized`. Check whether the platform's Spring Security chain (in
   `tt-data-league-api-rest` `config/security`) also applies to the management port. If it does, permit
   `/actuator/health/**` and `/actuator/info` there and add a test for it. Add a runtime test (`@SpringBootTest` with
   a random management port, or the module's existing test style) that `GET /actuator/health/liveness` answers 200
   without a token, and that `/actuator/env` is not exposed.
2. **Configurable initial import folder (P6).** In `ImportFolderSettingProvisioningService`, add
   `ensureExists(String initialValue)`. It validates a non-blank value (else `IllegalArgumentException`) and creates
   the setting with that value when it is absent. `ensureDefaultExists()` delegates with `DEFAULT_VALUE`. In
   `ImportFolderSettingStartupInitializer`, inject `tt.league.import.repository-folder-initial`
   (`${IMPORT_REPOSITORY_FOLDER_INITIAL:}` in `application.yml`). Blank keeps `ensureDefaultExists()`. A value that
   is not an absolute path fails startup with a message that names the variable. The initializer does not create the
   directory; the image does (step 4). Tests: extend `ImportFolderSettingProvisioningServiceTest` (absent creates
   the given value, present keeps the stored value, blank rejected) and `ImportFolderSettingStartupInitializerTest`
   (configured value used, blank uses the default, relative path fails).
3. **Platform README and contract docs.** Correct the health line in `tt-data-league-api-runtime/README.md`
   (Actuator now exists; exposure is `health,info`). Add `IMPORT_REPOSITORY_FOLDER_INITIAL` to its configuration
   table. No JPA change, so `rfetm-datamodel.md` is unchanged.
4. **Platform image: `tt-data-league-api-runtime/Dockerfile` plus `.dockerignore`.** The context is the module
   directory, and `.dockerignore` excludes everything except `target/tt-data-league-api-runtime-*.jar`. Build
   argument `JAR_FILE`, defaulting to `target/tt-data-league-api-runtime-0.0.1-SNAPSHOT.jar`.
   - Stage `extract`: `eclipse-temurin:21-jre`. Run `java -Djarmode=tools -jar app.jar extract --layers --launcher
     --destination /layers` so dependency layers are cached apart from application classes.
   - Stage `runtime`: `eclipse-temurin:21-jre`. Install `curl` (`--no-install-recommends`, clean apt lists) for the
     health check. Create user and group `app` (uid/gid 10001). Create `/var/lib/tt-league/repository` (the import
     folder volume) owned by 10001. Copy the layers in dependency, spring-boot-loader, snapshot-dependencies,
     application order. Set `USER 10001:10001` and `EXPOSE 8080 9090`. Set
     `ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"` and
     `ENV IMPORT_REPOSITORY_FOLDER_INITIAL=/var/lib/tt-league/repository`. Use
     `HEALTHCHECK CMD curl -fsS http://localhost:9090/actuator/health/liveness || exit 1` and
     `ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]`.
   - Add OCI labels (`org.opencontainers.image.title`, `.source`, `.revision` from a `GIT_SHA` build argument).
5. **Orchestrator image: `tt-league-pipeline-orchestrator-runtime/Dockerfile` plus `.dockerignore`.** Same layout as
   step 4, with these differences. The jar is `tt-league-pipeline-orchestrator-runtime-0.0.1-SNAPSHOT.jar`. The
   directory is `/var/lib/tt-pipeline/artifacts`, owned by 10001, with
   `ENV PIPELINE_ARTIFACTS_DIR=/var/lib/tt-pipeline/artifacts`. Use `EXPOSE 8095` and
   `HEALTHCHECK ... http://localhost:8095/actuator/health/liveness` (enable `management.endpoint.health.probes`
   in its `application.yml` if the liveness group is not already available). There is no source change to the
   orchestrator other than that property. The image keeps `PIPELINE_LOG_FORMAT=logstash` (its default).
6. **Ingest image: `tt-league-ingest/Dockerfile` plus `.dockerignore`.** The context is `tt-league-ingest/`.
   `.dockerignore` excludes `.venv`, `.pytest_cache`, `__pycache__`, `tests` and `**/tests`.
   - Stage `build`: `python:3.12-slim-bookworm`, with `uv` copied from a pinned `ghcr.io/astral-sh/uv:<version>` image.
     Copy `pyproject.toml`, `uv.lock` and `packages/`. Set `UV_COMPILE_BYTECODE=1` and `UV_LINK_MODE=copy`, then run
     `uv sync --frozen --no-dev --package tt-league-ingest-rest` into `/app/.venv`. `--frozen` fails the build when
     `uv.lock` is stale; it never re-resolves.
   - Stage `runtime`: `python:3.12-slim-bookworm`. Create user `app` (10001). Copy `/app` from the build stage.
     Create `/var/lib/tt-ingest/data` owned by 10001. Set `PATH=/app/.venv/bin:$PATH`,
     `TT_INGEST_REST_HOST=0.0.0.0`, `TT_INGEST_REST_PORT=8091` and `TT_INGEST_DATA_DIR=/var/lib/tt-ingest/data`. Use
     `USER 10001:10001` and `EXPOSE 8091`. Use `HEALTHCHECK CMD python -c "import urllib.request,sys;
     sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8091/health', timeout=3).status == 200 else 1)"` and
     `ENTRYPOINT ["tt-league-ingest-rest"]`.
   - Verify that the `pdfplumber` dependencies (`pdfminer.six`, `pypdfium2`, `Pillow`) install as manylinux wheels on
     slim. If they do not, add only the system libraries they need and record them in Notes.
7. **Proxy image: `deploy/proxy/Dockerfile` plus `deploy/proxy/Dockerfile.dockerignore`.** The context is the
   repository root. The Dockerfile-specific ignore file lets in only `tt-data-league-frontend/` and
   `tt-league-pipeline-orchestrator-frontend/` (without `node_modules`, `dist`, `target`) and `deploy/proxy/`.
   This keeps `.claude/worktrees`, `target/` and secrets out of the context.
   - Stage `platform-ui` and stage `pipeline-ui`: `node:20.19.0-alpine` (the version the POMs pin). Run `npm ci`,
     then `npm run build`. Build arguments for the `VITE_*` variables default to empty (same-origin). Tests and lint
     stay in the Maven build.
   - Stage `runtime`: `nginxinc/nginx-unprivileged:<pinned 1.27 alpine tag>` (uid 101, listens on unprivileged
     ports). Copy the two `dist/` folders to `/usr/share/nginx/platform` and `/usr/share/nginx/pipeline`, and
     `deploy/proxy/nginx.conf` to `/etc/nginx/conf.d/default.conf`. Use `EXPOSE 8080 8081` and
     `HEALTHCHECK CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1`.
8. **Proxy configuration: `deploy/proxy/nginx.conf`.** Use the Compose service names as upstreams (`api:8080`,
   `orchestrator:8095`).
   - `server` on 8080 (platform UI): `location = /healthz` returns 200. `location /api/` proxies to `api:8080` with
     `client_max_body_size 110m` (above the platform's 100MB) and `proxy_read_timeout 300s` (synchronous ZIP
     validation). `location /` uses `try_files $uri /index.html` from `/usr/share/nginx/platform`, with long-lived
     cache headers for `/assets/` and `no-cache` for `index.html`.
   - `server` on 8081 (pipeline UI): `location /api/pipeline/` proxies to `orchestrator:8095` with
     `proxy_buffering off`, `proxy_cache off`, `proxy_http_version 1.1`, `proxy_set_header Connection ""` and
     `proxy_read_timeout` above `PIPELINE_EVENTS_TIMEOUT` (PT30M), so the SSE stream is not buffered or cut.
     `location /api/v1/` proxies to `api:8080` (login and `auth/me`). `location /` serves the SPA as above.
   - Both servers set `X-Forwarded-For`, `X-Forwarded-Proto` and `Host`, set `server_tokens off`, and add basic
     security headers (`X-Content-Type-Options`, `Referrer-Policy`, `X-Frame-Options DENY`). No location routes
     `/actuator`, Swagger UI or `/mcp`. The management ports are not reachable through the proxy (FEAT-00115).
9. **Compose project: `deploy/compose.yaml`.** The project is named `tt-league`. It has one internal network
   (`backend`), and the proxy is the only service with published ports.
   - `postgres`: `postgres:16-alpine`. `POSTGRES_DB=ttleaguedata`, `POSTGRES_USER`/`POSTGRES_PASSWORD` from the
     environment, and `TZ=Europe/Madrid`. Named volume `postgres-data`. Mount `deploy/postgres/init/` on
     `/docker-entrypoint-initdb.d:ro`. Health check `pg_isready`. No published port.
   - `api`: `build: ../tt-data-league-api-runtime`, image `tt-league/api-runtime:${IMAGE_TAG:-local}`. The datasource
     points at `postgres:5432`. Set `JWT_SIGNING_SECRET`, `PASSWORD_RECOVERY_RESET_URL`
     (`${PUBLIC_PLATFORM_URL}/reset-password`) and `MAIL_*`. The orchestrator's service credential is
     `SECURITY_SERVICECREDENTIALS_0_NAME=orchestrator`, `..._KEYSHA256=${ORCHESTRATOR_PLATFORM_API_KEY_SHA256}` and
     `..._PERMISSIONS=imports:write,matches:read`. Named volume `platform-repository` on
     `/var/lib/tt-league/repository`. `depends_on: postgres: condition: service_healthy`.
   - `orchestrator`: `build: ../tt-league-pipeline-orchestrator-runtime`. Set
     `PIPELINE_DB_URL=jdbc:postgresql://postgres:5432/ttleaguedata`, `PIPELINE_DB_USERNAME=pipeline` and
     `PIPELINE_DB_PASSWORD`. Set `PIPELINE_PLATFORM_URL=http://api:8080`, `PIPELINE_PLATFORM_API_KEY`,
     `PIPELINE_INGEST_URL=http://ingest:8091`, `PIPELINE_INGEST_API_KEY` and `JWT_SIGNING_SECRET`. Pass the schedule,
     polling, statistics and mail variables through, with empty defaults so they stay off. Named volume
     `pipeline-artifacts` on `/var/lib/tt-pipeline/artifacts`. `depends_on` `postgres`, `api` and `ingest` (healthy).
   - `ingest`: `build: ../tt-league-ingest`. Set `TT_INGEST_REST_API_KEY=${PIPELINE_INGEST_API_KEY}`. Named volume
     `ingest-data` on `/var/lib/tt-ingest/data`.
   - `proxy`: `build: {context: .., dockerfile: deploy/proxy/Dockerfile}`. Ports
     `${PROXY_PLATFORM_PORT:-8080}:8080` and `${PROXY_PIPELINE_PORT:-8081}:8081`. `depends_on` `api` and
     `orchestrator` (healthy).
   - Every service: `restart: unless-stopped`, `init: true` for the JVM and Python services,
     `security_opt: [no-new-privileges:true]` and `cap_drop: [ALL]`. Set `read_only: true` with a `tmpfs` on `/tmp`
     (plus `/var/cache/nginx` and `/var/run` for the proxy, and `/var/run/postgresql` for postgres, as their images
     need). Use bounded `json-file` logging (`max-size`, `max-file`), `TZ=Europe/Madrid` and a `stop_grace_period`.
   - Required secrets use `${VAR:?<VAR> is required, see deploy/.env.example}`, so `docker compose config` and
     `up` fail clearly when one is missing. No secret has a default value in `compose.yaml`.
10. **Environment template: `deploy/.env.example`, and `deploy/.env` added to the root `.gitignore`.** List every
    variable with a placeholder value and a comment. Required: `POSTGRES_USER`, `POSTGRES_PASSWORD`,
    `PIPELINE_DB_PASSWORD`, `JWT_SIGNING_SECRET` (at least 32 bytes), `ORCHESTRATOR_PLATFORM_API_KEY`,
    `ORCHESTRATOR_PLATFORM_API_KEY_SHA256` and `PIPELINE_INGEST_API_KEY`. Optional: `PUBLIC_PLATFORM_URL`,
    `PUBLIC_PIPELINE_URL`, `PROXY_*_PORT`, `IMAGE_TAG`, mail settings, and schedule/polling settings. Give the commands
    that generate each value (`openssl rand -base64 32`, and the SHA-256 command from the platform README's "Service
    credentials" section).
11. **PostgreSQL first-start script: `deploy/postgres/init/10-pipeline-role.sh`** (P5). The postgres image runs it
    with `POSTGRES_*` and `PIPELINE_DB_PASSWORD` in the environment. It uses `psql -v ON_ERROR_STOP=1` and fails when
    `PIPELINE_DB_PASSWORD` is empty. It creates role `pipeline` (LOGIN, with that password) and runs
    `CREATE SCHEMA IF NOT EXISTS pipeline AUTHORIZATION pipeline` and `GRANT CONNECT ON DATABASE ttleaguedata TO
    pipeline`. It grants nothing on `public`. On PostgreSQL 16, only the schema owner can create objects in `public`.
    Flyway (`create-schemas: true`, `schemas: pipeline`) then finds the schema already present. Document that init
    scripts run only on an empty data volume.
12. **Documentation.**
    - New `deploy/README.md`. Cover: prerequisites (Docker Engine with Compose v2, Java 21 and Maven with the
      `org.albertsanso` artifacts installed locally for P1, and uv not needed). Build order:
      `mvn -pl tt-data-league-api-runtime,tt-league-pipeline-orchestrator-runtime -am package -DskipTests`, then
      `docker compose -f deploy/compose.yaml --env-file deploy/.env build`. First start, health check
      (`docker compose ps`), and the two URLs. Before exposing the stack: change or remove the seeded ADMIN accounts,
      and terminate TLS in front (P4). Also: volumes and what each one holds, backup with `pg_dump` through
      `docker compose exec`, upgrade (rebuild, then `up -d`), logs, and the fact that `/actuator/*` and the management
      ports are internal only.
    - Add a "Container image" section to `tt-data-league-api-runtime/README.md`,
      `tt-league-pipeline-orchestrator-runtime/README.md` and `tt-league-ingest/README.md`. Each covers: build command,
      image paths and volumes, the variables the image presets, and a pointer to `deploy/README.md`. Add one line to
      each frontend README saying that production serving is the proxy image.
    - Root `AGENTS.md`: add `deploy/` (Compose project and proxy image, outside the Maven reactor) to the architecture
      list.
13. **Static checks.** `docker compose -f deploy/compose.yaml --env-file deploy/.env.example config` must succeed
    with the example values. With a required value removed, it must fail with that value's message. Run `hadolint` on
    the four Dockerfiles if it is available. This is a manual check: no new tool is added to the build.
14. **Validation.**
    - `mvn -pl tt-data-league-core-domain,tt-data-league-api-runtime -am test`, then the full `mvn test`. In
      `tt-league-ingest/`: `uv lock --check` and `uv run pytest` (unchanged, but run them to confirm the lock is still
      frozen-installable).
    - On a Docker host: build all images. `docker compose up -d`. All five services become `healthy`.
      `docker compose exec <svc> id -u` is not 0 for `api`, `orchestrator`, `ingest` and `proxy`.
    - Smoke test. Log in on the platform UI (8080) and on the pipeline UI (8081), with no CORS error. The pipeline
      runs page receives live events. Trigger a run for one source/season; it reaches a final state, and its ZIP is in
      the `pipeline-artifacts` volume. A ZIP over 1MB uploads through the platform UI.
      `curl http://<vm>:8081/actuator/prometheus` does not reach the orchestrator.
    - Persistence. `docker compose down` then `up -d` keeps users, runs, artifacts and ingest data.
      `docker compose down -v` is documented as destructive.
    - Review the final diff: no `.env`, no jars and no `target/` content.

## Acceptance Criteria

- [x] Container images exist for `tt-league-ingest-rest` and the orchestrator runtime (serving or alongside the built frontend)
- [x] A Docker Compose setup for a single VM runs the platform, orchestrator runtime and frontend, `tt-league-ingest-rest` and PostgreSQL, wired through environment variables and a reverse proxy, with no committed secrets
- [x] Images run as non-root, expose health checks, and keep data and artifact directories on volumes
- [x] READMEs document build and run commands

# Implementation Guidelines

- Avoid unrelated parent-POM plugin changes; build images with module-local configuration or Dockerfiles.
- Do not add a Docker or Jib Maven plugin, Testcontainers-based image tests, or a new build tool. Images are built
  with `docker build` / `docker compose build`. The Maven reactor and `mvn test` do not need Docker.
- Never commit `.env`, keys, key hashes for real deployments, or built jars. `compose.yaml` holds no secret default.
  A missing required value fails `docker compose config`/`up` (`${VAR:?...}`); it never falls back silently.
- Pin base images by tag, never `latest`: Temurin 21 JRE, `python:3.12-slim-bookworm`, `node:20.19.0-alpine`, a
  `nginx-unprivileged` 1.27 tag, `postgres:16-alpine`, and a uv release. Do not change the Node or Python versions
  the modules already pin.
- Run every image as a fixed non-root uid. Create volume mount points in the image, owned by that uid, so a new
  named volume starts writable.
- Publish only the proxy's ports. PostgreSQL, the platform's 8080/9090, the orchestrator's 8095 (with
  `/actuator/prometheus`) and ingest's 8091 stay on the internal network.
- Keep the services' module boundaries. The orchestrator still talks to the platform and ingest only over REST
  (service names on the Compose network), and its database role cannot reach platform tables.
- Code changes are limited to steps 1-2 (platform Actuator and the initial import folder) and the orchestrator's
  health-probe property. No change to frontend source, ingest source, orchestrator behaviour or Flyway.
- `.podman/podman-compose.yaml` stays the local development database. `deploy/` is the VM deployment; do not merge
  them.

Out of scope: TLS certificates and their automation, Kubernetes manifests, an image registry and CI image
publishing, publishing the `org.albertsanso` artifacts, database backup automation, monitoring stack
(Prometheus/Grafana) containers, migrating data from an existing database, and changing the seeded ADMIN accounts.

# Notes

Part of the pipeline orchestrator backlog; architecture decisions, gap analysis and open questions are in [FEAT-00096-DETAILS.md](./FEAT-00096-DETAILS.md).

Proposal item 6 (containers) and "Technology options". Kubernetes manifests only if the target is an existing cluster.

2026-10-04: deployment target decided as a single VM with Docker Compose (D9); Kubernetes manifests are out of
scope. The reverse proxy serves both frontends and avoids cross-origin login (D10).

2026-10-05: build plan written (status `planned`). The plan proposes decisions P1-P6 (see Build Plan); confirm them
before moving to `ready`. Findings from the code that shaped the plan:
- The platform runtime has no Actuator dependency, so the health URL in its README does not exist. Step 1 adds
  Actuator and narrows the exposure from `"*"` to `health,info`.
- The `org.albertsanso:*` SNAPSHOT dependencies are only in the local `~/.m2`, so Maven cannot build inside Docker
  (P1).
- `ImportFolderSetting.DEFAULT_VALUE` (`c:\tt-repository`) is a Windows path (P6).
- Both SPAs route at `/` with no `basename` and both call `/api/v1`, hence one origin per SPA (P3).
- FEAT-00115 requires that `/actuator/prometheus` is not routed through the public proxy (step 8).

Open questions:
- TLS: terminate in front of the VM (default in this plan), or add automatic certificates to the proxy (for example
  Caddy, or certbot with nginx) in this feature?
- Ports or hostnames for the two UIs? The plan uses two proxy ports. Hostnames need DNS names for the VM.
- Should the seeded ADMIN accounts (`InitialUserStartupInitializer`) be made configurable or disabled for this
  deployment, as a separate feature? This plan only documents the risk.
- Should the `org.albertsanso` artifacts be published to a Maven repository (for example GitHub Packages), so that
  images can build from source and in CI? This would be a follow-up.

2026-10-05: implementation started from `planned`, on the user's request to execute the plan. The plan's proposed
defaults (P1-P6, TLS terminated in front of the VM, two proxy ports, seeded ADMIN accounts only documented, no
published Maven artifacts) were implemented as written; revisit them if the open questions above are answered
differently. Delivered:
- Steps 1-3: `spring-boot-starter-actuator` in the platform runtime, exposure narrowed to `health,info`, probes enabled,
  `show-details: when-authorized`; `ImportFolderSettingProvisioningService.ensureExists(String)` and
  `IMPORT_REPOSITORY_FOLDER_INITIAL` (absolute path required, blank keeps the default); orchestrator
  `management.endpoint.health.probes.enabled`. The platform's security chain already permits `/actuator/**`, so it needed
  no change. Tests: `ImportFolderSettingProvisioningServiceTest`, `ImportFolderSettingStartupInitializerTest` and the
  new `ManagementEndpointExposureTest`, which pins the effective `application.yml` values (exposure, probes, port)
  instead of booting the application, because a full context needs PostgreSQL. No `@SpringBootTest` checks
  `/actuator/env` over HTTP.
- Steps 4-12: Dockerfiles and `.dockerignore` for the platform, orchestrator and ingest, `deploy/proxy/` (Dockerfile,
  `Dockerfile.dockerignore`, `nginx.conf`), `deploy/compose.yaml`, `deploy/.env.example`,
  `deploy/postgres/init/10-pipeline-role.sh`, `deploy/README.md`, the "Container image" README sections and the root
  `AGENTS.md` entry; `deploy/.env` is in `.gitignore`.
- Changes beyond the plan: a root `.gitattributes` that forces LF on Dockerfiles, `*.dockerignore`, `deploy/**/*.sh`
  and `deploy/**/*.conf` (a CRLF checkout on Windows would break them); nginx uses the container's resolver (read from
  `/etc/resolv.conf` by the image entrypoint, see the defects below) and variable upstreams so a recreated `api` or
  `orchestrator` is picked up without restarting the proxy; the platform upload
  location sets `proxy_request_buffering off` so a 100MB ZIP is not spooled to the proxy's tmpfs; the PostgreSQL
  service keeps `cap_drop: ALL` but adds back `CHOWN`, `DAC_OVERRIDE`, `FOWNER`, `SETGID` and `SETUID`, which its
  entrypoint needs; the ingest image installs with `--no-editable` so the runtime stage needs only the virtual
  environment.

Container validation, 2026-10-05/06, with Podman 6.0.2 (rootful WSL machine) and the Docker Compose v5.1.0 provider,
since Docker is not installed. The plan's step 13 and 14 results:
- `docker compose config` succeeds with `deploy/.env.example` and with real values, and fails with the variable's own
  message for each required value left out (`JWT_SIGNING_SECRET`, `POSTGRES_PASSWORD`, `PIPELINE_INGEST_API_KEY`,
  `ORCHESTRATOR_PLATFORM_API_KEY_SHA256`). A value exported in the shell overrides the env file (documented).
- All four images build, including `Dockerfile.dockerignore` for the proxy, `uv sync --frozen` with the pinned
  `ghcr.io/astral-sh/uv:0.12.23` and the `nginx-unprivileged:1.27-alpine` and `postgres:16-alpine` tags. `hadolint` was not run.
- A cold start from empty volumes brings all five services to `healthy`. `id -u` is 10001 for `api`, `orchestrator` and
  `ingest`, and 101 for `proxy`. The root file system is read-only, `/tmp` and the three data volumes are writable by
  those users, and `cap_drop`/`no-new-privileges` are in effect.
- Both UIs serve their SPA (including deep links), and logging in works through both origins with no CORS. The
  orchestrator API with the token, the SSE stream (`ready` event arrives through the proxy), the orchestrator's service
  credential on the platform (`200`; a wrong key `401`), the ingest key (`200`/`401`) and a 5MB multipart upload (reaches
  the platform, which answers `400` for a non-ZIP; no proxy `413`) all work. `/actuator`, `/v3/api-docs`,
  `/swagger-ui.html` and `/mcp` answer `404` on the proxy, and `/actuator/prometheus` is not reachable through it.
  On the platform's management port `/actuator/env` and `/actuator/heapdump` are `404`.
- The platform stored `/var/lib/tt-league/repository` as its import folder. The `pipeline` role owns only the
  `pipeline` schema (14 tables by Flyway), is refused (`permission denied`) on a platform table and on `CREATE` in `public`.
- A real run (RFETM, `super-divisio`, match day 1) went `QUEUED` to `SUCCEEDED` in 41s: ingest, fetch package and a
  platform import job, with the ingest ZIP in the `pipeline-artifacts` volume, 48 games imported, the run's `runId` on the
  orchestrator and ingest log lines, and `pipeline_runs_finished_total` in the internal Prometheus output. A scoped FCTT
  run ended `FAILED` (`INGEST_FAILED`) because `fctt.cat` timed out from this network, the host included; that is the
  site, not the deployment.
- `down` then `up -d` kept the database, the artifact and the ingest data; `down -v` removed them (documented).

Defects found by that validation and fixed: (1) the proxy hard-coded Docker's resolver `127.0.0.11`, so every proxied call
was `502` on Podman; the config is now an nginx image template that takes the resolver from `/etc/resolv.conf`
(`NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1`) and works on both, with `/etc/nginx/conf.d` as a tmpfs because the root file system
is read-only; Podman rejects the tmpfs option `uid=`, so the tmpfs directories use `mode=1777`; (2) adding Actuator turned
the platform's `/actuator/health` `DOWN` while SMTP is unreachable (mail health indicator), so
`management.health.mail.enabled: false` (the orchestrator avoids it the same way); (3) `/actuator`, Swagger and `/mcp` on
the proxy returned the SPA's `index.html` with `200`; they are now `404`; (4) the proxy health check relied on the image
`HEALTHCHECK`, which Podman's default OCI image format drops, so `compose.yaml` now defines all four health checks. A
build without `clean` or `-am` can embed a stale jar or stale SNAPSHOT dependencies; the docs now say to keep both.

Follow-up outside this feature: the `IMPORT/rfetm-teams-folder` setting is created with the default
`import-rfetm	eams`, a Windows-style relative path that is a single odd file name on Linux (the RFETM teams import is not
configurable by environment variable in the container). `rfetm-teams-folder` was not part of this plan.

Maven and Python validation on 2026-10-05 (Windows): `mvn test -Dmaven.test.failure.ignore=true` over the full reactor built
every module; the platform runtime (36 tests), domain, API and orchestrator core tests pass, as do `uv lock --check`
and `uv run pytest` (351 passed). Two failures exist in code this feature does not touch, and neither is caused by it:
`BcnesaImportProcessorsTest.storesTheSetScoresOfEveryGameFromTheHtmlBasedActas` (the fixture
`acta_bcnesa_2026_published.json` is referenced by the test but not committed under
`tt-data-league-import/src/test/resources/actas`) and
`PipelineOrchestratorPropertiesTest.failsWhenTheStatisticsZoneIsMissingBlankOrInvalid` (the test expects the message
"is not a valid time zone", the code says "is not a valid IANA time zone id"). So plain `mvn test` is red until those two
are fixed. The Docker part of step 14 is covered by the container validation above (run with Podman).
