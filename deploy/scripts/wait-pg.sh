#!/usr/bin/env bash
# Wait until local PostgreSQL is accepting connections.
# Prefers `docker compose exec … pg_isready`, then host pg_isready.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-${DEPLOY_DIR}/docker-compose.yml}"
ENV_FILE="${ENV_FILE:-${DEPLOY_DIR}/.env}"
EXAMPLE_FILE="${DEPLOY_DIR}/.env.example"
TIMEOUT="${WAIT_PG_TIMEOUT:-60}"
INTERVAL="${WAIT_PG_INTERVAL:-2}"

load_env() {
  local file="$1"
  # shellcheck disable=SC1090
  set -a
  # shellcheck disable=SC1090
  source "$file"
  set +a
}

if [[ -f "${ENV_FILE}" ]]; then
  load_env "${ENV_FILE}"
elif [[ -f "${EXAMPLE_FILE}" ]]; then
  echo "warn: ${ENV_FILE} missing; using placeholders from .env.example" >&2
  load_env "${EXAMPLE_FILE}"
  ENV_FILE="${EXAMPLE_FILE}"
fi

POSTGRES_HOST="${POSTGRES_HOST:-localhost}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_USER="${POSTGRES_USER:-lineage}"
POSTGRES_DB="${POSTGRES_DB:-lineage}"

compose() {
  docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" "$@"
}

ready_via_compose() {
  command -v docker >/dev/null 2>&1 || return 1
  compose exec -T postgres pg_isready -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" >/dev/null 2>&1
}

ready_via_host() {
  command -v pg_isready >/dev/null 2>&1 || return 1
  pg_isready -h "${POSTGRES_HOST}" -p "${POSTGRES_PORT}" -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" >/dev/null 2>&1
}

echo "waiting for PostgreSQL (timeout=${TIMEOUT}s)…"
deadline=$((SECONDS + TIMEOUT))
while (( SECONDS < deadline )); do
  if ready_via_compose || ready_via_host; then
    echo "PostgreSQL is ready (${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB})"
    exit 0
  fi
  sleep "${INTERVAL}"
done

echo "error: PostgreSQL not ready within ${TIMEOUT}s" >&2
echo "hints: docker compose -f ${COMPOSE_FILE} ps   # or pg_isready -h ${POSTGRES_HOST} -p ${POSTGRES_PORT}" >&2
exit 1
