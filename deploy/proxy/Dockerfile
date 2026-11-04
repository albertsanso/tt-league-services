# Reverse proxy image: serves both built frontends and proxies the APIs they call (FEAT-00116).
# Build with the repository root as the context (Compose does this); deploy/proxy/Dockerfile.dockerignore keeps
# everything except the two frontends and this folder out of the context:
#   docker build -f deploy/proxy/Dockerfile -t tt-league/proxy .
# The frontends only need the npm registry, so they are built here. Tests and lint stay in the Maven build.

# Node version pinned by both frontend POMs (frontend-maven-plugin).
FROM node:20.19.0-alpine AS platform-ui
WORKDIR /src
COPY tt-data-league-frontend/package.json tt-data-league-frontend/package-lock.json ./
RUN npm ci
COPY tt-data-league-frontend/ ./
# Empty values keep the single-origin behaviour: the SPA calls relative /api/... paths.
ARG VITE_USE_MOCK_STATS=
ENV VITE_USE_MOCK_STATS=${VITE_USE_MOCK_STATS}
RUN npm run build

FROM node:20.19.0-alpine AS pipeline-ui
WORKDIR /src
COPY tt-league-pipeline-orchestrator-frontend/package.json tt-league-pipeline-orchestrator-frontend/package-lock.json ./
RUN npm ci
COPY tt-league-pipeline-orchestrator-frontend/ ./
# Empty (same-origin) base URLs: the proxy routes /api/pipeline/ and /api/v1/ on the pipeline UI's own origin.
ARG VITE_ORCHESTRATOR_BASE_URL=
ARG VITE_PLATFORM_BASE_URL=
ENV VITE_ORCHESTRATOR_BASE_URL=${VITE_ORCHESTRATOR_BASE_URL} \
    VITE_PLATFORM_BASE_URL=${VITE_PLATFORM_BASE_URL}
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime
ARG GIT_SHA=unknown
LABEL org.opencontainers.image.title="tt-league-proxy" \
      org.opencontainers.image.source="https://github.com/albertsanso/tt-league-services" \
      org.opencontainers.image.revision="${GIT_SHA}"

COPY --from=platform-ui /src/dist/ /usr/share/nginx/platform/
COPY --from=pipeline-ui /src/dist/ /usr/share/nginx/pipeline/
# Makes the image entrypoint export NGINX_LOCAL_RESOLVERS from /etc/resolv.conf, which nginx.conf uses as its resolver.
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1

# A template: the image entrypoint renders it into /etc/nginx/conf.d (a tmpfs under Compose, whose root is read-only).
COPY deploy/proxy/nginx.conf /etc/nginx/templates/default.conf.template

# The base image already runs as uid 101 (nginx) on unprivileged ports.
EXPOSE 8080 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
