# Local runbook (P0 empty stack)

Do not put secrets in the repo. Copy env values from a local untracked `.env`.

Java 8 + Vue 2 embedded shell (PM). HANDOFF default stack is unchanged in
HANDOFF.md; see [ADR 0001](adr/0001-java8-vue2-embedded-vs-handoff.md).

P5 local acceptance (what ran / what did not / BLOCKED) and how a host mounts
the shell (iframe or same-origin static + `/api` proxy): [IMPLEMENTATION_ACCEPTANCE.md](IMPLEMENTATION_ACCEPTANCE.md).
That document is **not** a production go-live claim.

## Frontend (`apps/web`)

Vue 2 embedded shell (iframe-friendly). Search / query / four views (tree,
overview, impact, path) / detail / **数据导入** (validate + publish) /
exception banners. URL restore via search params (see `apps/web/README.md`).
Not a full SPA product chrome. No React Flow.

```bash
cd apps/web
npm install
npm run generate:api # regenerates src/generated/openapi.d.ts from spec/v1/openapi.json
npm run dev          # http://127.0.0.1:5173  (proxies /api → VITE_API_BASE)
npm run test         # vitest
npm run build
```

Defaults: `VITE_API_BASE=http://127.0.0.1:8080`, POST header `X-CSRF-Token: dev`
(must match API `lineage.csrf.token`, default `dev`). Optional `VITE_EMBED_GROUPS`.
See `apps/web/.env.example` and `apps/web/README.md`. Preferred production mount is
same-origin reverse-proxy (`VITE_API_BASE=` empty); then the browser sends no
`Origin` and the API does not set `Access-Control-Allow-Origin`.

Deep-expand demo against a published fixture (exits 2 if API is down):

```bash
cd apps/web && ./scripts/demo-deep-expand.sh
```

P4 visual freeze (8 PNGs, fixture seed only; protocol in
`evidence/implementation/p4-visual/PROTOCOL.md`):

```bash
cd apps/web && npm run capture:p4-visual
# API 127.0.0.1:8080 + npm run dev|preview; imports fixtures/v1 if needed
```

`generate:api` is types-only (`openapi-typescript`). The committed
`src/generated/openapi.d.ts` is enough for CI-less clones; re-run after
OpenAPI edits and commit the result.

## API (`apps/api`)

Requires **Java 8**. Maven Wrapper downloads its own distribution on first run.

```bash
cd apps/api
JAVA_HOME=/home/box/tools/jdk8u504-b01 ./mvnw -DskipTests package
JAVA_HOME=/home/box/tools/jdk8u504-b01 ./mvnw spring-boot:run
curl http://localhost:8080/api/health
# {"status":"ok"}
```

Datasource / Flyway (P2):

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/lineage
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...
FLYWAY_ENABLED=true          # default false; URL present enables Flyway unless this is false
```

Without `SPRING_DATASOURCE_URL`, DataSource and Flyway auto-configuration stay
excluded so `/api/health` still works. With a URL, Boot runs
`classpath:db/migration` (`V1__storage.sql`) on startup. Optional profile `db`
(`application-db.yml`) is the explicit opt-in.

Live migrate against disposable PG 17 (Docker required):

```bash
./deploy/scripts/migrate-verify.sh
```

See [`deploy/README.md`](../deploy/README.md) and [`deploy/RUNBOOK.md`](../deploy/RUNBOOK.md).
Never commit `deploy/.env`.

### Import / CAS publish (P2)

Default `lineage.security.mode=open` — no OIDC (later task). POST `/api/imports`
and `/api/imports/{runId}/publish` require `X-CSRF-Token` equal to
`lineage.csrf.token` / `LINEAGE_CSRF_TOKEN` (local default `dev`; constant-time
compare). Hosts must set a non-guessable token for real embeds. Optional
`LINEAGE_SECURITY_MODE=demo-header` plus `X-Lineage-Demo-User`.

When `X-Embed-Groups` is **present**, import and publish also require
`scope_grant` permission `ingest` on the target scope (body `scopeId` for import;
the run's scope for publish). Missing grant → 403 `FORBIDDEN`. Omit the header in
open/local mode for full-open ingest (still needs matching CSRF).

```bash
# PostgreSQL from deploy/, then API with SPRING_DATASOURCE_URL set
curl -sS -H 'Content-Type: application/json' -H 'X-CSRF-Token: dev' \
  --data-binary @fixtures/v1/import.json http://localhost:8080/api/imports
curl -sS http://localhost:8080/api/imports/{runId}
curl -sS -H 'Content-Type: application/json' -H 'X-CSRF-Token: dev' \
  --data '{"expectedActiveSnapshotId":null}' \
  http://localhost:8080/api/imports/{runId}/publish
```

Embedded UI (same CSRF `X-CSRF-Token: dev`, optional `X-Embed-Groups`):

1. `cd apps/web && npm run dev` → http://127.0.0.1:5173
2. Nav **数据导入**. Paste or upload `fixtures/v1/import.json` (documented sample
   path; not production lineage). Submit → 202 runId / status / error & warning
   counts / snapshotId.
3. Refresh or wait for poll; paginate validation issues.
4. When status is `ready`, **发布** (`expectedActiveSnapshotId` empty = `null`).
   `PUBLISH_CONFLICT` is shown as its own banner. Oversize → `PAYLOAD_TOO_LARGE`
   / 413; schema errors → `IMPORT_INVALID` with the issues list.
5. Switch to **血缘工作台** and search. Distinct banners: empty / 未采集
   (`LINEAGE_NOT_COLLECTED`), request failure / `TEMPORARILY_UNAVAILABLE`,
   `POLICY_CHANGED` / `QUERY_EXPIRED` (destroy query, 重新搜索), incomplete
   coverage, cycle 环→清单/路径. `FORBIDDEN` / `UNAUTHENTICATED` only when the
   API returns them (embed groups / demo-header). No invented OIDC mappings.

JDBC integration tests (`ImportPublishJdbcTest`) bring up `deploy/` compose and
use real PostgreSQL. `./mvnw test` from `apps/api` with JDK 8.

### Query APIs and embed groups (P2)

POST `/api/lineage/queries` and POST `/api/lineage/queries/{qid}/projection`
also require `X-CSRF-Token` matching `lineage.csrf.token` (same rule as import).

**Minimal embed auth (dev stub, not OIDC):**

| Header | Open mode behaviour |
|---|---|
| *(omit)* `X-Embed-Groups` | Authorize every object in the active snapshot (local/dev). Import/publish stay full-open (matching CSRF still required). |
| `X-Embed-Groups: g1,g2` | Query: resolve `scope_grant` (permission `view`) plus `object_grant`. **Deny wins.** No scope view and no object allow → empty set. Import/publish: require `scope_grant` permission `ingest` on the target scope or 403 `FORBIDDEN`. |

Unauthorized or forged object ids return contract `Error` with `NOT_FOUND` (same
shape; existence is not leaked). A denied seed on create is `INVALID_SEED` for
both missing and unauthorized seeds.

```bash
# After publish, open mode (all objects):
curl -sS 'http://localhost:8080/api/lineage/search?q=root'
curl -sS -H 'Content-Type: application/json' -H 'X-CSRF-Token: dev' \
  --data '{"seedId":"root"}' http://localhost:8080/api/lineage/queries
# Then children / projection / path using the returned meta.queryId.

# Restricted embed (host-trusted groups):
curl -sS -H 'X-Embed-Groups: g-view' \
  'http://localhost:8080/api/lineage/search?q=root'
```

Queries bind the **active snapshot** and `policy_revision` at create time.
Changing embed groups or bumping `policy_revision` on a live query returns
`POLICY_CHANGED`. Expired in-memory contexts return `QUERY_EXPIRED` (410).

CORS: `lineage.cors.allowed-origins` / `LINEAGE_CORS_ALLOWED_ORIGINS` is a
comma-separated exact-match allowlist (local default
`http://127.0.0.1:5173,http://localhost:5173` plus preview 4173). Only listed
origins get `Access-Control-Allow-Origin`; unknown Origins are never echoed.
OPTIONS preflight succeeds for allowlisted origins. Prefer same-origin reverse
proxy so the browser omits `Origin` and ACAO is unnecessary.

JDBC tests: `QueryApiJdbcTest` (import+publish fixture, then search / query /
children / projection / path, plus unauthorized-id cases).

## Design checks (already in repo)

Requires Python `jsonschema` (`pip install --user -r tools/requirements.txt`).
Evidence from the last P0 baseline run lives in `evidence/implementation/`.

```bash
python3 tools/validate_design.py
node reference/open-design/program-lineage-handoff/spec/verify-tree.mjs
node evidence/probe-reference.mjs
```
