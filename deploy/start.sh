#!/usr/bin/env bash
# One-command start of the single-VM deployment (FEAT-00116): builds the jars and images, creates deploy/.env with
# generated secrets when it does not exist, starts the stack, waits until every service is healthy and prints the two
# URLs. Works with Docker or Podman, on Linux and in Git Bash on Windows. See deploy/README.md.
#
#   deploy/start.sh                 build everything and start
#   deploy/start.sh --skip-build    start with the images already built
#   deploy/start.sh status          show the services and the URLs
#   deploy/start.sh down            stop (keeps the data volumes)
set -euo pipefail

cd "$(dirname "$0")/.."
ENV_FILE=deploy/.env
COMPOSE_FILE=deploy/compose.yaml
SERVICES=(postgres ingest api orchestrator proxy)

fail() { echo "error: $*" >&2; exit 1; }
info() { echo "==> $*"; }

# ---- container engine ---------------------------------------------------------------------------------------------
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    ENGINE=docker
elif command -v podman >/dev/null 2>&1; then
    ENGINE=podman
else
    fail "neither Docker (with the compose plugin) nor Podman is installed"
fi

# Compose gives an exported shell variable priority over deploy/.env. Unset every name the file defines, so that the
# stack always runs with the values of deploy/.env.
compose() {
    local unset_args=() name
    while IFS= read -r name; do
        if [ -n "${!name+x}" ]; then
            echo "warning: ignoring $name from your shell; deploy/.env is used" >&2
            unset_args+=(-u "$name")
        fi
    done < <(sed -n 's/^\([A-Za-z_][A-Za-z0-9_]*\)=.*/\1/p' "$ENV_FILE")
    env "${unset_args[@]}" $ENGINE compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"
}

env_value() { # env_value NAME DEFAULT
    local v
    v=$(sed -n "s/^$1=//p" "$ENV_FILE" | tail -1)
    echo "${v:-$2}"
}

random_hex() {
    if command -v openssl >/dev/null 2>&1; then openssl rand -hex 32; else head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n'; fi
}

# ---- deploy/.env ------------------------------------------------------------------------------------------------
ensure_env_file() {
    if [ -f "$ENV_FILE" ]; then
        if grep -q '=<' "$ENV_FILE"; then
            fail "$ENV_FILE still has <placeholder> values; replace them or delete the file to generate new ones"
        fi
        return
    fi
    info "creating $ENV_FILE with generated secrets (keep it private, it is ignored by Git)"
    local key sha
    key=$(random_hex)
    sha=$(printf '%s' "$key" | sha256sum | cut -d' ' -f1)
    awk -v pg="$(random_hex)" -v pipe="$(random_hex)" -v jwt="$(random_hex)" -v key="$key" -v sha="$sha" \
        -v ingest="$(random_hex)" '
        /^POSTGRES_PASSWORD=/                    { print "POSTGRES_PASSWORD=" pg; next }
        /^PIPELINE_DB_PASSWORD=/                 { print "PIPELINE_DB_PASSWORD=" pipe; next }
        /^JWT_SIGNING_SECRET=/                   { print "JWT_SIGNING_SECRET=" jwt; next }
        /^ORCHESTRATOR_PLATFORM_API_KEY=/        { print "ORCHESTRATOR_PLATFORM_API_KEY=" key; next }
        /^ORCHESTRATOR_PLATFORM_API_KEY_SHA256=/ { print "ORCHESTRATOR_PLATFORM_API_KEY_SHA256=" sha; next }
        /^PIPELINE_INGEST_API_KEY=/              { print "PIPELINE_INGEST_API_KEY=" ingest; next }
        { print }' deploy/.env.example > "$ENV_FILE"
    chmod 600 "$ENV_FILE" 2>/dev/null || true
}

# ---- build ------------------------------------------------------------------------------------------------------
build() {
    command -v mvn >/dev/null 2>&1 || fail "Maven is needed to build the jars (or use --skip-build with built images)"
    info "building the platform and orchestrator jars"
    mvn -q -pl tt-data-league-api-runtime,tt-league-pipeline-orchestrator-runtime -am clean package -DskipTests

    local tag sha
    tag=$(env_value IMAGE_TAG local)
    sha=$(git rev-parse --short HEAD 2>/dev/null || echo unknown)
    info "building the images ($ENGINE, tag $tag)"
    $ENGINE build --build-arg GIT_SHA="$sha" -t "tt-league/api-runtime:$tag" tt-data-league-api-runtime
    $ENGINE build --build-arg GIT_SHA="$sha" -t "tt-league/orchestrator-runtime:$tag" tt-league-pipeline-orchestrator-runtime
    $ENGINE build --build-arg GIT_SHA="$sha" -t "tt-league/ingest:$tag" tt-league-ingest
    $ENGINE build --build-arg GIT_SHA="$sha" -f deploy/proxy/Dockerfile -t "tt-league/proxy:$tag" .
}

# ---- health and URLs --------------------------------------------------------------------------------------------
wait_healthy() {
    info "waiting for the services to become healthy (the Java services take about a minute)"
    local deadline=$((SECONDS + 300)) svc status pending
    while :; do
        pending=()
        for svc in "${SERVICES[@]}"; do
            status=$($ENGINE inspect --format '{{.State.Health.Status}}' "tt-league-$svc-1" 2>/dev/null || echo missing)
            [ "$status" = healthy ] || pending+=("$svc:$status")
        done
        [ ${#pending[@]} -eq 0 ] && return 0
        if [ $SECONDS -ge $deadline ]; then
            echo "error: not healthy after 5 minutes: ${pending[*]}" >&2
            for svc in "${pending[@]%%:*}"; do
                echo "---- last log lines of $svc" >&2
                $ENGINE logs --tail 20 "tt-league-$svc-1" >&2 2>&1 || true
            done
            exit 1
        fi
        sleep 5
    done
}

# A rootful Podman machine on Windows or macOS publishes ports inside its VM only: Windows' localhost does not reach
# them, the VM's own address does. Everywhere else the ports are on localhost.
url_host() {
    if [ "$ENGINE" = podman ] && podman machine inspect >/dev/null 2>&1 \
        && podman machine inspect --format '{{.Rootful}}' 2>/dev/null | grep -q true; then
        local ip
        ip=$(podman machine ssh "ip -4 -o addr show eth0" 2>/dev/null | awk '{print $4}' | cut -d/ -f1 | head -1)
        [ -n "$ip" ] && { echo "$ip"; return; }
    fi
    echo localhost
}

print_urls() {
    local host platform pipeline
    host=$(url_host)
    platform="http://$host:$(env_value PROXY_PLATFORM_PORT 8080)"
    pipeline="http://$host:$(env_value PROXY_PIPELINE_PORT 8081)"
    for u in "$platform" "$pipeline"; do
        curl -s -o /dev/null -m 5 "$u/healthz" || echo "warning: $u does not answer from this host" >&2
    done
    echo
    echo "  TT League UI:  $platform"
    echo "  Pipeline UI:   $pipeline"
    echo
    echo "  Both sign in with the platform's accounts (see 'Before exposing it' in deploy/README.md)."
    [ "$host" != localhost ] && echo "  $host is the Podman machine's address; it can change when the machine restarts."
    return 0
}

# ---- commands ---------------------------------------------------------------------------------------------------
case "${1:-up}" in
    up|--skip-build)
        ensure_env_file
        [ "${1:-}" = --skip-build ] || build
        compose config --quiet
        info "starting the stack"
        compose up -d --no-build
        wait_healthy
        print_urls
        ;;
    status)
        [ -f "$ENV_FILE" ] || fail "$ENV_FILE does not exist; run deploy/start.sh first"
        compose ps
        print_urls
        ;;
    down)
        [ -f "$ENV_FILE" ] || fail "$ENV_FILE does not exist"
        compose down
        ;;
    *)
        fail "usage: deploy/start.sh [--skip-build | status | down]"
        ;;
esac
