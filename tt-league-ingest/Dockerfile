# tt-league-ingest-rest image (FEAT-00116). Build with this directory as the context:
#   docker build -t tt-league/ingest tt-league-ingest
FROM ghcr.io/astral-sh/uv:0.12.23 AS uv

FROM python:3.12-slim-bookworm AS build
COPY --from=uv /uv /usr/local/bin/uv
ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PYTHON_DOWNLOADS=never
WORKDIR /app
COPY pyproject.toml uv.lock ./
COPY packages ./packages
# --frozen fails the build when uv.lock is stale and never re-resolves; --no-editable installs the workspace
# packages into the virtual environment so the runtime stage needs nothing else.
RUN uv sync --frozen --no-dev --no-editable --package tt-league-ingest-rest

FROM python:3.12-slim-bookworm AS runtime
ARG GIT_SHA=unknown
LABEL org.opencontainers.image.title="tt-league-ingest-rest" \
      org.opencontainers.image.source="https://github.com/albertsanso/tt-league-services" \
      org.opencontainers.image.revision="${GIT_SHA}"

RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin app \
    && mkdir -p /var/lib/tt-ingest/data \
    && chown -R 10001:10001 /var/lib/tt-ingest

COPY --from=build /app/.venv /app/.venv

ENV PATH="/app/.venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    TT_INGEST_REST_HOST=0.0.0.0 \
    TT_INGEST_REST_PORT=8091 \
    TT_INGEST_DATA_DIR=/var/lib/tt-ingest/data

USER 10001:10001
EXPOSE 8091
VOLUME ["/var/lib/tt-ingest/data"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8091/health', timeout=3).status == 200 else 1)"

ENTRYPOINT ["tt-league-ingest-rest"]
