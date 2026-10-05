# Single-VM deployment

Docker Compose project that runs the platform, the pipeline orchestrator, `tt-league-ingest-rest`, PostgreSQL and a
reverse proxy that serves both frontends (FEAT-00116). It is outside the Maven reactor, and it is not the local
development database: that is `.podman/podman-compose.yaml`.

```text
                 :8080 platform UI          :8081 pipeline UI
                        |                          |
                        +----------- proxy --------+        (the only published ports)
                                      |  |
                          /api/        |  | /api/pipeline/   /api/v1/ (login)
                                      v  v
   backend network:   api:8080 <--- orchestrator:8095 ---> ingest:8091
                         |                 |
                         +---- postgres ---+     (database ttleaguedata; the orchestrator uses its own role)
```

| Service | Image | Port (internal) | Data volume |
|---|---|---|---|
| `postgres` | `postgres:16-alpine` | 5432 | `postgres-data` |
| `api` | `tt-league/api-runtime` | 8080, management 9090 | `platform-repository` (`/var/lib/tt-league/repository`, the import folder) |
| `orchestrator` | `tt-league/orchestrator-runtime` | 8095 | `pipeline-artifacts` (`/var/lib/tt-pipeline/artifacts`) |
| `ingest` | `tt-league/ingest` | 8091 | `ingest-data` (`/var/lib/tt-ingest/data`) |
| `proxy` | `tt-league/proxy` | 8080 (platform UI), 8081 (pipeline UI) | none |

Each UI has its own origin, so neither one logs in cross-origin and no CORS configuration is needed
(`PIPELINE_CORS_ALLOWED_ORIGINS` stays empty). The proxy routes `/api/` on the platform UI to the platform API, and
`/api/pipeline/` (including the live event stream) and `/api/v1/` on the pipeline UI to the orchestrator and the
platform. It does not route `/actuator`, Swagger UI or `/mcp`: the management ports and the orchestrator's
`/actuator/prometheus` are reachable only from inside the Compose network.

## Prerequisites

- Docker Engine with the Compose v2 plugin on the VM, and access to the npm registry and the base-image registries
  while building.
- Podman works instead of Docker, see [Using Podman](#using-podman). The Compose file defines every health check itself,
  because an image built by Podman in OCI format drops the Dockerfile `HEALTHCHECK`.
- To build the two Java jars: Java 21 and Maven, with the `org.albertsanso` artifacts (`commons-core` and the
  `commandbus-`, `querybus-` and `eventbus-synchronous-inmemory` SNAPSHOTs) installed in the local Maven repository.
  No repository serves them, so Maven cannot run inside a Docker build; the Java images copy a jar built beforehand.
  `uv` is not needed: the ingest image installs its dependencies from `uv.lock` itself.

## Quick start

`deploy/start.sh` does every step below in one command, with Docker or Podman (on Linux, or in Git Bash on Windows):

```text
deploy/start.sh                 # build the jars and images, start, wait until healthy, print the two URLs
deploy/start.sh --skip-build    # start with the images already built
deploy/start.sh status          # services and URLs
deploy/start.sh down            # stop; the data volumes are kept
```

On the first run it creates `deploy/.env` from `.env.example` with generated secrets and the matching key hash. It never
overwrites an existing `deploy/.env`, and it stops when that file still has `<placeholder>` values. It runs Compose with
the values of `deploy/.env` even when your shell exports a variable of the same name (it warns about each one). It
needs Maven (unless `--skip-build`), `curl`, and `openssl` or `/dev/urandom`.

It prints `http://localhost:8080` (TT League UI) and `http://localhost:8081` (pipeline UI). On a rootful Podman machine
(Windows or macOS) Windows' `localhost` does not reach the published ports, so it prints the machine's own address
instead, for example `http://172.22.41.96:8080`; that address can change when the machine restarts.

The sections below are the same steps done by hand.

## Build

From the repository root:

```text
mvn -pl tt-data-league-api-runtime,tt-league-pipeline-orchestrator-runtime -am clean package -DskipTests
cp deploy/.env.example deploy/.env
```

Edit `deploy/.env`: replace every `<placeholder>`, using the commands in the comments (`openssl rand -base64 32`, and
the SHA-256 command for `ORCHESTRATOR_PLATFORM_API_KEY_SHA256`). `deploy/.env` is ignored by Git; never commit it.
Then check the configuration and build the images:

```text
docker compose -f deploy/compose.yaml --env-file deploy/.env config --quiet
docker compose -f deploy/compose.yaml --env-file deploy/.env build
```

`config` fails with the variable's name when a required value is missing. The jars are not committed and `target/` is
ignored, so rebuild the jars before every image build that should contain new Java code. Keep `clean` and `-am`: without
`clean`, Maven does not rebuild a jar that was already repackaged, and without `-am` it embeds the `tt-data-league-*`
SNAPSHOTs found in the local Maven repository, which may be older than your checkout (the platform then fails at
startup with `NoClassDefFoundError`).

## First start

```text
docker compose -f deploy/compose.yaml --env-file deploy/.env up -d
docker compose -f deploy/compose.yaml --env-file deploy/.env ps
```

All five services should become `healthy` (the Java services take about a minute). Then open:

- platform UI: `http://<vm>:8080` (`PROXY_PLATFORM_PORT`);
- pipeline UI: `http://<vm>:8081` (`PROXY_PIPELINE_PORT`). It signs in with the platform's accounts.

On an empty database the platform creates its schema (`ddl-auto: update`), the orchestrator's Flyway creates its tables
in the `pipeline` schema, and the platform stores `/var/lib/tt-league/repository` as its initial import folder.

## Using Podman

Podman runs the same project. Do the first two steps of [Build](#build) (the jars and `deploy/.env`) as they are, then
build the images and start the stack with these commands, from the repository root.

Build the images. This is the tested path: it builds each image with `podman build`, with that module's directory as
the context (the proxy is the exception, built from the repository root):

```text
podman build -t tt-league/api-runtime:local tt-data-league-api-runtime
podman build -t tt-league/orchestrator-runtime:local tt-league-pipeline-orchestrator-runtime
podman build -t tt-league/ingest:local tt-league-ingest
podman build -f deploy/proxy/Dockerfile -t tt-league/proxy:local .
```

The tags must match `tt-league/<name>:${IMAGE_TAG}` in `compose.yaml` (`local` unless you set `IMAGE_TAG`).
`podman compose -f deploy/compose.yaml --env-file deploy/.env build` should build the same images, but it has not been
tried.

Start, inspect and stop the stack. `--no-build` makes Compose use the images built above instead of building again:

```text
podman compose -f deploy/compose.yaml --env-file deploy/.env config --quiet
podman compose -f deploy/compose.yaml --env-file deploy/.env up -d --no-build
podman compose -f deploy/compose.yaml --env-file deploy/.env ps
podman compose -f deploy/compose.yaml --env-file deploy/.env down        # keeps the volumes
podman compose -f deploy/compose.yaml --env-file deploy/.env down -v     # deletes them
```

Notes:

- `podman compose` runs an external compose provider. It was tested with Docker Compose v5.1.0 (`docker-compose.exe`) on
  Podman 6.0.2; `podman-compose` was not tried.
- Podman names the containers and the network after the project: `tt-league-api-1`, `tt-league_backend`, and so on.
- On Windows or macOS the published ports may not reach the host, see "Podman on Windows or macOS" under
  [Operation](#operation).
- An exported shell variable overrides the same name in `deploy/.env`, see "Shell variables win over `deploy/.env`" under
  [Operation](#operation).

## Before exposing it

- **Seeded accounts.** On start the platform creates two fixed ADMIN accounts with passwords that are in the source
  code (`InitialUserStartupInitializer`). Change or remove them before anyone else can reach the VM. This deployment
  does not change them.
- **TLS.** The proxy publishes plain HTTP only. Terminate TLS in front of the VM (a load balancer or another reverse
  proxy) and do not expose these ports to the internet over HTTP: logins and JWTs would travel in clear text.
- **Secrets.** `deploy/.env` holds every secret; restrict its permissions (`chmod 600`). Container environments are
  visible to anyone who can run `docker inspect` on the VM.

## Operation

- **Volumes.** `postgres-data` holds the databases (platform tables in `public`, the orchestrator's in `pipeline`).
  `platform-repository` holds the import folder, `pipeline-artifacts` the run artifacts (ingest ZIPs and so on) and
  `ingest-data` the downloaded and parsed source data. `docker compose down` keeps them; **`down -v` deletes them**.
- **PostgreSQL first-start script.** `deploy/postgres/init/10-pipeline-role.sh` creates the `pipeline` role, which owns
  only the `pipeline` schema, so the orchestrator cannot read or write platform tables. The postgres image runs init
  scripts only when `postgres-data` is empty. On an existing volume, create the role by hand:
  `CREATE ROLE pipeline LOGIN PASSWORD '...'; CREATE SCHEMA IF NOT EXISTS pipeline AUTHORIZATION pipeline;
  GRANT CONNECT ON DATABASE ttleaguedata TO pipeline;` and use the same password as `PIPELINE_DB_PASSWORD`. Changing
  `PIPELINE_DB_PASSWORD` later does not change the role's password.
- **Logs.** `docker compose -f deploy/compose.yaml --env-file deploy/.env logs -f orchestrator`. The Java services and
  ingest write JSON lines; each service's log is capped at 5 files of 10 MB.
- **Backup.**

  ```text
  docker compose -f deploy/compose.yaml --env-file deploy/.env exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -Fc ttleaguedata' > ttleaguedata.dump
  ```

  Also back up the `platform-repository`, `pipeline-artifacts` and `ingest-data` volumes if their content matters.
  Backup automation is not part of this project.
- **Upgrade.** `git pull`, rebuild the jars, then `build` and `up -d` as above. Compose recreates only the services whose
  image changed. The proxy re-resolves its upstreams, so recreating `api` or `orchestrator` does not need a proxy restart.
- **Health and metrics.** `docker compose ps` shows each service's health check. The platform's management port (9090)
  and the orchestrator's `/actuator/prometheus` are internal; scrape them from a container on the `tt-league_backend`
  network.
- **Podman on Windows or macOS.** A rootful Podman machine publishes ports through firewall rules inside its VM,
  without a listening socket, so WSL's `localhost` forwarding does not see them and `http://localhost:8080` does not
  answer from Windows. Use the machine's address (`podman machine ssh "ip -4 -o addr show eth0"`; `deploy/start.sh`
  prints it), or a rootless machine. From inside the Compose network everything is reachable
  (`podman run --rm --network tt-league_backend --entrypoint curl <image> http://proxy:8080/healthz`). On a Linux VM
  this does not apply.
- **Shell variables win over `deploy/.env`.** Compose gives an exported variable (for example `JWT_SIGNING_SECRET` in
  your shell) priority over the same name in the env file. Unset it, or run Compose with `env -u NAME`.
- **Ports.** Change `PROXY_PLATFORM_PORT` and `PROXY_PIPELINE_PORT` in `deploy/.env` if 8080 or 8081 are taken.
  `PUBLIC_PLATFORM_URL` must be the URL users reach the platform UI at, because it goes into password-recovery e-mails.
- **Schedules.** Scheduled runs, adaptive polling and e-mail alerts are off until their variables are set in
  `deploy/.env`; see the [orchestrator README](../tt-league-pipeline-orchestrator-runtime/README.md).

## Image notes

- Each Java image runs as uid `10001` with a read-only root file system (only `/tmp` and the volumes are writable),
  `cap_drop: ALL` and `no-new-privileges`. The proxy runs as the nginx user (uid `101`) on unprivileged ports.
- Base images are pinned by tag (`eclipse-temurin:21-jre`, `python:3.12-slim-bookworm`, `node:20.19.0-alpine`,
  `nginxinc/nginx-unprivileged:1.27-alpine`, `postgres:16-alpine`, and a uv release for the ingest build).
- Per-module build commands and variables: the "Container image" sections of the
  [platform](../tt-data-league-api-runtime/README.md), [orchestrator](../tt-league-pipeline-orchestrator-runtime/README.md)
  and [ingest](../tt-league-ingest/README.md) READMEs.
