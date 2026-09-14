#!/usr/bin/env bash
# Manual / demo walk of search → query → children → projection along a path
# deeper than 3 layers against a running local API + published fixtures/v1.
# Does not invent lineage. Exits 2 if the API is not up.
set -euo pipefail

API="${VITE_API_BASE:-http://127.0.0.1:8080}"
CSRF="${VITE_CSRF_TOKEN:-dev}"

echo "API=$API"
if ! curl -sf "$API/api/health" >/dev/null; then
  cat <<'EOF'
BLOCKED: API is not reachable.
1. Start PostgreSQL from deploy/ and the Java 8 API (docs/RUNBOOK.md).
2. Import + publish fixtures/v1/import.json:
   curl -sS -H 'Content-Type: application/json' -H 'X-CSRF-Token: dev' \
     --data-binary @fixtures/v1/import.json http://127.0.0.1:8080/api/imports
   curl -sS -H 'Content-Type: application/json' -H 'X-CSRF-Token: dev' \
     --data '{"expectedActiveSnapshotId":null}' \
     http://127.0.0.1:8080/api/imports/{runId}/publish
3. Re-run this script, or in the Vue shell: search "root" → pick hit → expand
   view-a → proc-b → table-c (layoutRank 0..3+; java-j is layer 4 on the main tree).
EOF
  exit 2
fi

python3 - "$API" "$CSRF" <<'PY'
import json, sys, urllib.request

api, csrf = sys.argv[1], sys.argv[2]

def req(method, path, body=None):
    data = None if body is None else json.dumps(body).encode("utf-8")
    headers = {"Accept": "application/json"}
    if body is not None:
        headers["Content-Type"] = "application/json"
        headers["X-CSRF-Token"] = csrf
    r = urllib.request.Request(api + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(r) as resp:
        return json.loads(resp.read().decode("utf-8"))

search = req("GET", "/api/lineage/search?q=root")
print("search hits:", [h["object"]["id"] for h in search["items"]])
created = req("POST", "/api/lineage/queries", {"seedId": "root"})
qid = created["meta"]["queryId"]
print("queryId", qid, "default nodes", [n["object"]["id"] for n in created["projection"]["nodes"]])
candidates = [n["object"]["id"] for n in created["projection"]["nodes"]]
rev = 0
for parent in ("view-a", "proc-b", "table-c"):
    page = req("GET", "/api/lineage/queries/%s/children?parentId=%s" % (qid, parent))
    extra = [x["object"]["id"] for x in page["items"]]
    print("children of", parent, extra, "hasMore", page["page"]["hasMore"])
    for eid in extra:
        if eid not in candidates:
            candidates.append(eid)
    rev += 1
    proj = req("POST", "/api/lineage/queries/%s/projection" % qid, {
        "candidateIds": candidates,
        "selectedId": parent,
        "types": ["table", "view", "procedure", "java"],
        "revealSelectedPath": False,
        "clientRevision": rev,
    })
    ranks = [n.get("layoutRank") or 0 for n in proj["nodes"]]
    print("projection rev", proj["clientRevision"], "nodes", [n["object"]["id"] for n in proj["nodes"]],
          "maxRank", max(ranks) if ranks else 0)
print("UI: cd apps/web && npm run dev → search root → expand view-a, proc-b, table-c.")
PY
