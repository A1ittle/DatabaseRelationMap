# Local runbook (P0 empty stack)

Do not put secrets in the repo. Copy env values from a local untracked `.env`.

Java 8 + Vue 2 embedded shell (PM). HANDOFF default stack is unchanged in
HANDOFF.md; see [ADR 0001](adr/0001-java8-vue2-embedded-vs-handoff.md).

## Frontend (`apps/web`)

Vue 2 empty page, iframe-friendly. Not a full SPA product shell.

```bash
cd apps/web
npm install
npm run dev          # http://127.0.0.1:5173
npm run test         # placeholder until P3/P4
npm run build
```

## API (`apps/api`)

Requires **Java 8**. Maven Wrapper downloads its own distribution on first run.

```bash
cd apps/api
JAVA_HOME=/home/box/tools/jdk8u504-b01 ./mvnw -DskipTests package
JAVA_HOME=/home/box/tools/jdk8u504-b01 ./mvnw spring-boot:run
curl http://localhost:8080/api/health
# {"status":"ok"}
```

Optional datasource (unused until JDBC auto-config is enabled in P2):

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/lineage
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...
```

P0 excludes DataSource/Flyway auto-configuration so `/api/health` works with no
PostgreSQL. Local PG templates live under [`deploy/`](../deploy/README.md)
(`docker-compose.yml`, `.env.example`, wait/smoke scripts). See also
[`deploy/RUNBOOK.md`](../deploy/RUNBOOK.md).

## Design checks (already in repo)

```bash
python3 tools/validate_design.py
```
