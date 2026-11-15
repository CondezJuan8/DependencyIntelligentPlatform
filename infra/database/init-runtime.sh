#!/bin/sh
set -eu
: "${POSTGRES_APP_PASSWORD:?POSTGRES_APP_PASSWORD is required}"
export PGPASSWORD="${POSTGRES_PASSWORD}"
psql --no-psqlrc --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set ON_ERROR_STOP=1 --file /opt/dependency/provision-runtime.sql
