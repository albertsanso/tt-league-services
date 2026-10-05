#!/bin/sh
# First-start initialisation of the PostgreSQL container (FEAT-00116).
#
# Creates the "pipeline" role and its schema, so that the orchestrator connects as a role that owns only the
# "pipeline" schema and cannot read or write the platform's tables. The postgres image runs every script of
# /docker-entrypoint-initdb.d only when the data volume is empty: on an existing volume this script does not run
# and the role has to be created by hand (see deploy/README.md).
set -eu

if [ -z "${PIPELINE_DB_PASSWORD:-}" ]; then
    echo "PIPELINE_DB_PASSWORD is required to create the pipeline database role" >&2
    exit 1
fi

# The password is passed as a psql variable and quoted by psql (:'pipeline_password'), never interpolated by the shell.
psql -v ON_ERROR_STOP=1 \
     -v pipeline_password="$PIPELINE_DB_PASSWORD" \
     --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
CREATE ROLE pipeline LOGIN PASSWORD :'pipeline_password';
CREATE SCHEMA IF NOT EXISTS pipeline AUTHORIZATION pipeline;
GRANT CONNECT ON DATABASE ttleaguedata TO pipeline;
SQL
