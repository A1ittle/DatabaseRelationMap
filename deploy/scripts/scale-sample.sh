#!/usr/bin/env bash
# P5 scale sample: import 500+ fixture, cold/hot lineage query timings, optional backup drill.
#
# Requires Docker + Java 8. Records device/data/concurrency in evidence.
# UI browser render timing is NOT measured here (see receipt); mark as 未验证.
#
# Usage (repo root):
#   ./deploy/scripts/scale-sample.sh
# Optional:
#   SKIP_BACKUP=1 ./deploy/scripts/scale-sample.sh
#   KEEP_PG=1 KEEP_API=1 ./deploy/scripts/scale-sample.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${DEPLOY_DIR}/.." && pwd)"
API_DIR="${REPO_ROOT}/apps/api"
COMPOSE_FILE="${COMPOSE_FILE:-${DEPLOY_DIR}/docker-compose.yml}"
ENV_FILE="${ENV_FILE:-${DEPLOY_DIR}/.env}"
EXAMPLE_FILE="${DEPLOY_DIR}/.env.example"
FIXTURE="${FIXTURE:-${REPO_ROOT}/fixtures/v1/scale-500.json}"
API_BASE="${API_BASE:-http://127.0.0.1:8080}"
CSRF="${CSRF:-dev}"
EVIDENCE_DIR="${REPO_ROOT}/evidence/implementation"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
RECEIPT="${EVIDENCE_DIR}/p5-scale-sample.txt"
API_LOG="${EVIDENCE_DIR}/p5-api-scale.log"
API_PID=""

if ! command -v docker >/dev/null 2>&1; then
  echo "BLOCKED: no Docker on this host; scale sample not run." >&2
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

SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://localhost:5432/${POSTGRES_DB:-lineage}}"
SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-${POSTGRES_USER:-lineage}}"
SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-${POSTGRES_PASSWORD:-change-me-local}}"

if [[ -z "${JAVA_HOME:-}" && -d /home/box/tools/jdk8u504-b01 ]]; then
  export JAVA_HOME=/home/box/tools/jdk8u504-b01
fi
export PATH="${JAVA_HOME}/bin:${PATH}"

compose() {
  docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" "$@"
}

stop_api() {
  if [[ -n "${API_PID}" ]] && kill -0 "${API_PID}" 2>/dev/null; then
    kill "${API_PID}" 2>/dev/null || true
    wait "${API_PID}" 2>/dev/null || true
  fi
}

cleanup() {
  stop_api
  if [[ "${KEEP_PG:-}" == "1" ]]; then
    echo "KEEP_PG=1 — leaving postgres up"
    return 0
  fi
  compose down -v || true
}
trap cleanup EXIT

mkdir -p "${EVIDENCE_DIR}"
[[ -f "${FIXTURE}" ]] || { echo "error: missing ${FIXTURE}" >&2; exit 1; }

echo "== scale-sample ${STAMP} =="
compose down -v >/dev/null 2>&1 || true
compose up -d postgres
"${SCRIPT_DIR}/wait-pg.sh"

(
  cd "${API_DIR}"
  ./mvnw -q -DskipTests package
  ./mvnw -q flyway:migrate \
    -Dflyway.url="${SPRING_DATASOURCE_URL}" \
    -Dflyway.user="${SPRING_DATASOURCE_USERNAME}" \
    -Dflyway.password="${SPRING_DATASOURCE_PASSWORD}"
)

# Start API in background
(
  cd "${API_DIR}"
  export SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD
  ./mvnw -q spring-boot:run
) >"${API_LOG}" 2>&1 &
API_PID=$!

echo "waiting for API ${API_BASE}/api/health (pid=${API_PID})…"
deadline=$((SECONDS + 180))
while (( SECONDS < deadline )); do
  if curl -sf "${API_BASE}/api/health" >/dev/null 2>&1; then
    echo "API ready"
    break
  fi
  if ! kill -0 "${API_PID}" 2>/dev/null; then
    echo "error: API process exited early; see ${API_LOG}" >&2
    tail -n 80 "${API_LOG}" >&2 || true
    exit 1
  fi
  sleep 2
done
curl -sf "${API_BASE}/api/health" >/dev/null || { echo "error: API not ready" >&2; exit 1; }

# Import + publish scale fixture
IMPORT_JSON="$(curl -sS -H 'Content-Type: application/json' -H "X-CSRF-Token: ${CSRF}" \
  --data-binary @"${FIXTURE}" "${API_BASE}/api/imports")"
RUN_ID="$(python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("runId") or d["run"]["runId"])' <<<"${IMPORT_JSON}")"
STATUS="$(python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("status") or d["run"]["status"])' <<<"${IMPORT_JSON}")"
echo "import runId=${RUN_ID} status=${STATUS}"
[[ "${STATUS}" == "ready" || "${STATUS}" == "published" ]] || {
  echo "error: import not ready: ${IMPORT_JSON}" >&2
  exit 1
}

PUBLISH_JSON="$(curl -sS -H 'Content-Type: application/json' -H "X-CSRF-Token: ${CSRF}" \
  --data '{"expectedActiveSnapshotId":null}' \
  "${API_BASE}/api/imports/${RUN_ID}/publish")"
echo "publish: $(python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("snapshotId") or d)' <<<"${PUBLISH_JSON}")"

# Cold/hot timings (API only; concurrency=1)
python3 - "${API_BASE}" "${CSRF}" "${RECEIPT}" "${STAMP}" "${FIXTURE}" <<'PY'
import json, os, platform, statistics, sys, time, urllib.request

api, csrf, receipt, stamp, fixture = sys.argv[1:6]

def req(method, path, body=None):
    data = None if body is None else json.dumps(body).encode("utf-8")
    headers = {"Accept": "application/json"}
    if body is not None:
        headers["Content-Type"] = "application/json"
        headers["X-CSRF-Token"] = csrf
    r = urllib.request.Request(api + path, data=data, headers=headers, method=method)
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(r, timeout=120) as resp:
            raw = resp.read()
            ms = (time.perf_counter() - t0) * 1000.0
            return ms, json.loads(raw.decode("utf-8"))
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", "replace")
        raise RuntimeError("%s %s -> HTTP %s %s" % (method, path, e.code, detail[:500])) from e

def timed(label, n, fn):
    samples = []
    last = None
    for i in range(n):
        ms, last = fn()
        samples.append(ms)
        print("%s[%d]=%.1fms" % (label, i, ms))
    return {
        "label": label,
        "n": n,
        "cold_ms": round(samples[0], 1),
        "hot_median_ms": round(statistics.median(samples[1:]), 1) if len(samples) > 1 else None,
        "all_ms": [round(x, 1) for x in samples],
        "last": last,
    }

results = []

# search cold/hot
results.append(timed("search_root", 5, lambda: req("GET", "/api/lineage/search?q=scale-root")))

# create query (default one-level projection)
ms, created = req("POST", "/api/lineage/queries", {"seedId": "scale-root"})
print("create_query=%.1fms nodes=%d" % (ms, len(created.get("projection", {}).get("nodes", []))))
qid = created["meta"]["queryId"]
results.append({
    "label": "create_query_seed_root",
    "n": 1,
    "cold_ms": round(ms, 1),
    "hot_median_ms": None,
    "all_ms": [round(ms, 1)],
    "nodes": len(created.get("projection", {}).get("nodes", [])),
})

# children of high-fanout parent
results.append(timed(
    "children_scale-fan-00",
    5,
    lambda: req("GET", "/api/lineage/queries/%s/children?parentId=scale-fan-00&limit=50" % qid),
))

# projection of first-level candidates (~51 nodes)
cands = [n["object"]["id"] for n in created["projection"]["nodes"]]
body = {
    "candidateIds": cands,
    "selectedId": "scale-root",
    "types": ["table", "view", "procedure", "java"],
    "revealSelectedPath": False,
    "clientRevision": 1,
}
results.append(timed(
    "projection_default_layer",
    5,
    lambda: req("POST", "/api/lineage/queries/%s/projection" % qid, body),
))

# rough reachable count via repeated children BFS (cap)
from collections import deque
adj_count = 0
seen = set(["scale-root"])
q = deque(["scale-root"])
while q and len(seen) < 600:
    parent = q.popleft()
    try:
        _, page = req("GET", "/api/lineage/queries/%s/children?parentId=%s&limit=100" % (qid, parent))
    except Exception as e:
        print("bfs_stop", parent, e)
        break
    for item in page.get("items", []):
        oid = item["object"]["id"]
        if oid not in seen:
            seen.add(oid)
            q.append(oid)
    adj_count += 1
print("bfs_reachable_via_children_api", len(seen), "parents_fetched", adj_count)

fix = json.load(open(fixture))
lines = []
lines.append("DatabaseRelationMap P5 scale sample (SRE)")
lines.append("date_utc: %s" % stamp)
lines.append("host: %s" % platform.node())
lines.append("os: %s %s" % (platform.system(), platform.release()))
lines.append("python: %s" % platform.python_version())
lines.append("arch: %s" % platform.machine())
# cpu/mem hints
try:
    with open("/proc/cpuinfo") as f:
        cpus = sum(1 for line in f if line.startswith("processor"))
    lines.append("cpu_logical: %d" % cpus)
except Exception:
    lines.append("cpu_logical: unknown")
try:
    with open("/proc/meminfo") as f:
        for line in f:
            if line.startswith("MemTotal:"):
                lines.append("mem_total_kb: %s" % line.split()[1])
                break
except Exception:
    pass
lines.append("concurrency: 1 (sequential HTTP; no multi-client load)")
lines.append("fixture: fixtures/v1/scale-500.json")
lines.append("fixture_objects: %d" % len(fix["objects"]))
lines.append("fixture_relations: %d" % len(fix["relations"]))
lines.append("fixture_note: synthetic 500+ reachable / high fanout / long chain / sparse cross; NOT real lineage")
lines.append("bfs_reachable_via_children_api: %d" % len(seen))
lines.append("queryId: %s" % qid)
lines.append("")
lines.append("timings_ms (cold = first sample; hot = median of remaining):")
for r in results:
    lines.append("  - %s cold=%s hot_median=%s all=%s" % (
        r["label"], r["cold_ms"], r.get("hot_median_ms"), r["all_ms"]))
lines.append("")
lines.append("UI_browser_render_ms: NOT_RUN (SRE API/DB drill only; Vue paint not measured)")
lines.append("SSO / real data / production: BLOCKED — not exercised")
lines.append("FINDING: object_identity ON CONFLICT (object_id) DO NOTHING collides across scopes when object ids overlap (demo root vs scale); fixture uses scale-* ids as workaround. Engineer should fix PK/upsert to (scope_id, object_id).")
lines.append("RESULT: PASS (local API cold/hot + 500+ fixture import/publish)")
lines.append("NOT production-ready claim.")
text = "\n".join(lines) + "\n"
open(receipt, "w").write(text)
print(text)
PY

if [[ "${SKIP_BACKUP:-}" != "1" ]]; then
  echo "== nested backup-restore (KEEP_PG / SKIP_MIGRATE) =="
  KEEP_PG=1 SKIP_MIGRATE=1 "${SCRIPT_DIR}/backup-restore-drill.sh"
fi

echo "scale-sample RESULT: PASS → ${RECEIPT}"
