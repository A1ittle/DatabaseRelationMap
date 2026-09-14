# Local runbook (P0 empty stack)

Do not put secrets in the repo. Copy env values from a local untracked `.env`.

Java 8 + Vue 2 embedded shell (PM). HANDOFF default stack is unchanged in
HANDOFF.md; see [ADR 0001](adr/0001-java8-vue2-embedded-vs-handoff.md).

## Frontend (`apps/web`)

Vue 2 empty page, iframe-friendly. Not a full SPA product shell.

```bash
cd apps/web
npm install
npm run generate:api # regenerates src/generated/openapi.d.ts from spec/v1/openapi.json
npm run dev          # http://127.0.0.1:5173
npm run test         # placeholder until P3/P4
npm run build
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
and `/api/imports/{runId}/publish` require a non-empty `X-CSRF-Token` (any value
in open mode). Optional `LINEAGE_SECURITY_MODE=demo-header` plus
`X-Lineage-Demo-User`.

```bash
# PostgreSQL from deploy/, then API with SPRING_DATASOURCE_URL set
curl -sS -H 'Content-Type: application/json' -H 'X-CSRF-Token: demo' \
  --data-binary @fixtures/v1/import.json http://localhost:8080/api/imports
curl -sS http://localhost:8080/api/imports/{runId}
curl -sS -H 'Content-Type: application/json' -H 'X-CSRF-Token: demo' \
  --data '{"expectedActiveSnapshotId":null}' \
  http://localhost:8080/api/imports/{runId}/publish
```

JDBC integration tests (`ImportPublishJdbcTest`) bring up `deploy/` compose and
use real PostgreSQL. `./mvnw test` from `apps/api` with JDK 8.

### Query APIs and embed groups (P2)

POST `/api/lineage/queries` and POST `/api/lineage/queries/{qid}/projection`
also require `X-CSRF-Token` (same open-mode rule as import).

**Minimal embed auth (dev stub, not OIDC):**

| Header | Open mode behaviour |
|---|---|
| *(omit)* `X-Embed-Groups` | Authorize every object in the active snapshot (local/dev). |
| `X-Embed-Groups: g1,g2` | Resolve `scope_grant` (permission `view`) plus `object_grant`. **Deny wins.** No scope view and no object allow → empty set. |

Unauthorized or forged object ids return contract `Error` with `NOT_FOUND` (same
shape; existence is not leaked). A denied seed on create is `INVALID_SEED` for
both missing and unauthorized seeds.

```bash
# After publish, open mode (all objects):
curl -sS 'http://localhost:8080/api/lineage/search?q=root'
curl -sS -H 'Content-Type: application/json' -H 'X-CSRF-Token: demo' \
  --data '{"seedId":"root"}' http://localhost:8080/api/lineage/queries
# Then children / projection / path using the returned meta.queryId.

# Restricted embed (host-trusted groups):
curl -sS -H 'X-Embed-Groups: g-view' \
  'http://localhost:8080/api/lineage/search?q=root'
```

Queries bind the **active snapshot** and `policy_revision` at create time.
Changing embed groups or bumping `policy_revision` on a live query returns
`POLICY_CHANGED`. Expired in-memory contexts return `QUERY_EXPIRED` (410).

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
