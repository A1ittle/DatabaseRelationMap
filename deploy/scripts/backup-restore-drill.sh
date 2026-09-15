#!/usr/bin/env bash
# Backup + restore drill on the disposable local PostgreSQL 17 volume.
#
# Requires Docker Compose. Agent hosts without Docker: BLOCKED (exit 1).
# Does NOT touch production. Placeholders only — never commit deploy/.env.
#
# Usage (repo root):
#   ./deploy/scripts/backup-restore-drill.sh
# Optional:
#   KEEP_PG=1 ./deploy/scripts/backup-restore-drill.sh
#   SKIP_MIGRATE=1 KEEP_PG=1 ./deploy/scripts/backup-restore-drill.sh  # PG already migrated
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${DEPLOY_DIR}/.." && pwd)"
API_DIR="${REPO_ROOT}/apps/api"
COMPOSE_FILE="${COMPOSE_FILE:-${DEPLOY_DIR}/docker-compose.yml}"
ENV_FILE="${ENV_FILE:-${DEPLOY_DIR}/.env}"
EXAMPLE_FILE="${DEPLOY_DIR}/.env.example"
BACKUP_DIR="${BACKUP_DIR:-${REPO_ROOT}/evidence/implementation/p5-backup}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"

if ! command -v docker >/dev/null 2>&1; then
  echo "BLOCKED: no Docker on this host; backup/restore drill not run." >&2
  exit 1
fi

load_env() {
  local file="$1"
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

POSTGRES_USER="${POSTGRES_USER:-lineage}"
POSTGRES_DB="${POSTGRES_DB:-lineage}"
SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://localhost:5432/${POSTGRES_DB}}"
SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-${POSTGRES_USER}}"
SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-${POSTGRES_PASSWORD:-change-me-local}}"

if [[ -z "${JAVA_HOME:-}" && -d /home/box/tools/jdk8u504-b01 ]]; then
  export JAVA_HOME=/home/box/tools/jdk8u504-b01
fi

compose() {
  docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" "$@"
}

cleanup() {
  if [[ "${KEEP_PG:-}" == "1" ]]; then
    echo "KEEP_PG=1 — leaving postgres up"
    return 0
  fi
  compose down -v || true
}
trap cleanup EXIT

mkdir -p "${BACKUP_DIR}"
DUMP_FILE="${BACKUP_DIR}/lineage-${STAMP}.dump"
MARKER_TABLE="sre_backup_drill_marker"

echo "== backup-restore-drill ${STAMP} =="

compose up -d postgres
"${SCRIPT_DIR}/wait-pg.sh"

if [[ "${SKIP_MIGRATE:-}" != "1" ]]; then
  (
    cd "${API_DIR}"
    ./mvnw -q flyway:migrate \
      -Dflyway.url="${SPRING_DATASOURCE_URL}" \
      -Dflyway.user="${SPRING_DATASOURCE_USERNAME}" \
      -Dflyway.password="${SPRING_DATASOURCE_PASSWORD}"
  )
fi

# Insert a marker row into a disposable table (not part of product schema).
compose exec -T postgres psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -v ON_ERROR_STOP=1 <<SQL
CREATE TABLE IF NOT EXISTS ${MARKER_TABLE} (
  id serial PRIMARY KEY,
  note text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
TRUNCATE ${MARKER_TABLE};
INSERT INTO ${MARKER_TABLE} (note) VALUES ('p5-backup-restore-${STAMP}');
SQL

MARKER_BEFORE="$(compose exec -T postgres psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -Atc \
  "SELECT note FROM ${MARKER_TABLE} ORDER BY id DESC LIMIT 1")"
echo "marker_before=${MARKER_BEFORE}"

echo "pg_dump custom format → ${DUMP_FILE}"
compose exec -T postgres pg_dump -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -Fc \
  > "${DUMP_FILE}"
ls -lh "${DUMP_FILE}"

# Destroy data in place, then restore from dump (same volume; proves restore path).
compose exec -T postgres psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -v ON_ERROR_STOP=1 <<SQL
TRUNCATE ${MARKER_TABLE};
SQL
MARKER_CLEARED="$(compose exec -T postgres psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -Atc \
  "SELECT COUNT(*) FROM ${MARKER_TABLE}")"
echo "marker_count_after_truncate=${MARKER_CLEARED}"
[[ "${MARKER_CLEARED}" == "0" ]] || { echo "error: truncate failed" >&2; exit 1; }

echo "pg_restore --clean --if-exists"
# Stream dump into container. --clean drops objects before recreate.
compose exec -T postgres pg_restore -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" \
  --clean --if-exists --no-owner --no-acl < "${DUMP_FILE}"

MARKER_AFTER="$(compose exec -T postgres psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -Atc \
  "SELECT note FROM ${MARKER_TABLE} ORDER BY id DESC LIMIT 1")"
echo "marker_after=${MARKER_AFTER}"

if [[ "${MARKER_AFTER}" != "${MARKER_BEFORE}" ]]; then
  echo "RESULT: FAIL — marker mismatch after restore" >&2
  exit 1
fi

# Evidence sidecar (dump itself may be large; keep a tiny receipt next to it).
RECEIPT="${BACKUP_DIR}/restore-receipt-${STAMP}.txt"
{
  echo "DatabaseRelationMap P5 backup-restore drill"
  echo "date_utc: ${STAMP}"
  echo "host: $(hostname)"
  echo "postgres_image: postgres:17"
  echo "dump: ${DUMP_FILE}"
  echo "dump_bytes: $(wc -c < "${DUMP_FILE}" | tr -d ' ')"
  echo "marker_before: ${MARKER_BEFORE}"
  echo "marker_after: ${MARKER_AFTER}"
  echo "RESULT: PASS"
  echo "NOTE: disposable local volume only; not a production backup policy."
  echo "BLOCKED: real prod backup/PITR/SSO not exercised."
} | tee "${RECEIPT}"

echo "RESULT: PASS"
