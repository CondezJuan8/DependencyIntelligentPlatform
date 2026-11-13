#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
docker compose exec -T database mkdir -p /tmp/di-database-verification
docker compose cp tools/database/. database:/tmp/di-database-verification >/dev/null
docker compose exec -T database sh -c 'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /tmp/di-database-verification/verify.sql'
