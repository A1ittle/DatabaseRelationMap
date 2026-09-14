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

**BLOCKED on hosts without Docker** (this agent host included). The script prints
the SRE recipe and exits 1. See [`deploy/README.md`](../deploy/README.md) and
[`deploy/RUNBOOK.md`](../deploy/RUNBOOK.md). Never commit `deploy/.env`.

## Design checks (already in repo)

Requires Python `jsonschema` (`pip install --user -r tools/requirements.txt`).
Evidence from the last P0 baseline run lives in `evidence/implementation/`.

```bash
python3 tools/validate_design.py
node reference/open-design/program-lineage-handoff/spec/verify-tree.mjs
node evidence/probe-reference.mjs
```
