#!/usr/bin/env bash
# Optional DDL smoke: apply spec/v1/storage.sql to the disposable local DB.
# Official migrations are Flyway V1 — use deploy/scripts/migrate-verify.sh.
# Re-run on a dirty volume will fail (objects already exist); use down -v first.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${DEPLOY_DIR}/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-${DEPLOY_DIR}/docker-compose.yml}"
ENV_FILE="${ENV_FILE:-${DEPLOY_DIR}/.env}"
EXAMPLE_FILE="${DEPLOY_DIR}/.env.example"
SQL_FILE="${SQL_FILE:-${REPO_ROOT}/spec/v1/storage.sql}"

if [[ ! -f "${SQL_FILE}" ]]; then
  echo "error: SQL file not found: ${SQL_FILE}" >&2
  exit 1
fi

if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  set -a && source "${ENV_FILE}" && set +a
elif [[ -f "${EXAMPLE_FILE}" ]]; then
  echo "warn: ${ENV_FILE} missing; using placeholders from .env.example" >&2
  # shellcheck disable=SC1090
  set -a && source "${EXAMPLE_FILE}" && set +a
  ENV_FILE="${EXAMPLE_FILE}"
fi

POSTGRES_USER="${POSTGRES_USER:-lineage}"
POSTGRES_DB="${POSTGRES_DB:-lineage}"

"${SCRIPT_DIR}/wait-pg.sh"

echo "applying ${SQL_FILE} (DDL smoke only, not Flyway)…"

if command -v docker >/dev/null 2>&1; then
  docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" exec -T postgres \
    psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -v ON_ERROR_STOP=1 -f - < "${SQL_FILE}"
elif command -v psql >/dev/null 2>&1; then
  PGPASSWORD="${POSTGRES_PASSWORD:-}" psql \
    -h "${POSTGRES_HOST:-localhost}" \
    -p "${POSTGRES_PORT:-5432}" \
    -U "${POSTGRES_USER}" \
    -d "${POSTGRES_DB}" \
    -v ON_ERROR_STOP=1 \
    -f "${SQL_FILE}"
else
  echo "error: need docker compose exec or host psql to apply DDL" >&2
  exit 1
fi

echo "DDL smoke finished. Drop with: docker compose -f ${COMPOSE_FILE} down -v"
