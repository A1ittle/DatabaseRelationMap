#!/usr/bin/env bash
# Apply Flyway V1 to the disposable local PostgreSQL 17, print info, destroy the volume.
#
# Requires Docker Compose. Agent hosts without Docker: BLOCKED (exit 1).
# Idempotent: a second flyway:migrate is a no-op; down -v returns a blank volume.
# Do not commit deploy/.env. Placeholders only — no production credentials.
#
# Usage (repo root):
#   ./deploy/scripts/migrate-verify.sh
# Optional:
#   KEEP_PG=1 ./deploy/scripts/migrate-verify.sh   # skip down -v
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${DEPLOY_DIR}/.." && pwd)"
API_DIR="${REPO_ROOT}/apps/api"
COMPOSE_FILE="${COMPOSE_FILE:-${DEPLOY_DIR}/docker-compose.yml}"
ENV_FILE="${ENV_FILE:-${DEPLOY_DIR}/.env}"
EXAMPLE_FILE="${DEPLOY_DIR}/.env.example"

print_sre_recipe() {
  cat >&2 <<'EOF'
SRE recipe (machine with Docker; never commit deploy/.env):
  cp deploy/.env.example deploy/.env
  docker compose -f deploy/docker-compose.yml up -d
  ./deploy/scripts/wait-pg.sh
  cd apps/api
  export SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD
  # values from deploy/.env — names match deploy/.env.example
  ./mvnw flyway:migrate -Dflyway.url="$SPRING_DATASOURCE_URL" \
    -Dflyway.user="$SPRING_DATASOURCE_USERNAME" \
    -Dflyway.password="$SPRING_DATASOURCE_PASSWORD"
  ./mvnw flyway:info -Dflyway.url="$SPRING_DATASOURCE_URL" \
    -Dflyway.user="$SPRING_DATASOURCE_USERNAME" \
    -Dflyway.password="$SPRING_DATASOURCE_PASSWORD"
  docker compose -f deploy/docker-compose.yml down -v
EOF
}

if ! command -v docker >/dev/null 2>&1; then
  echo "BLOCKED: no Docker on this host; live PostgreSQL migrate is not run here." >&2
  print_sre_recipe
  exit 1
fi

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
else
  echo "error: neither ${ENV_FILE} nor ${EXAMPLE_FILE} found" >&2
  exit 1
fi

SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://localhost:5432/${POSTGRES_DB:-lineage}}"
SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-${POSTGRES_USER:-lineage}}"
SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-${POSTGRES_PASSWORD:-change-me-local}}"

if [[ -z "${JAVA_HOME:-}" && -d /home/box/tools/jdk8u504-b01 ]]; then
  export JAVA_HOME=/home/box/tools/jdk8u504-b01
fi

compose() {
  docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" "$@"
}

cleanup() {
  if [[ "${KEEP_PG:-}" == "1" ]]; then
    echo "KEEP_PG=1: leaving compose stack running"
    return
  fi
  compose down -v
}

trap cleanup EXIT

compose up -d
"${SCRIPT_DIR}/wait-pg.sh"

flyway() {
  local goal="$1"
  (
    cd "${API_DIR}"
    ./mvnw -q "${goal}" \
      -Dflyway.url="${SPRING_DATASOURCE_URL}" \
      -Dflyway.user="${SPRING_DATASOURCE_USERNAME}" \
      -Dflyway.password="${SPRING_DATASOURCE_PASSWORD}"
  )
}

echo "flyway:migrate (1/2) url=${SPRING_DATASOURCE_URL} user=${SPRING_DATASOURCE_USERNAME}"
flyway flyway:migrate
echo "flyway:migrate (2/2, idempotent no-op if V1 already applied)"
flyway flyway:migrate
echo "flyway:info"
flyway flyway:info

echo "migrate-verify finished. Volume destroyed on exit unless KEEP_PG=1."
