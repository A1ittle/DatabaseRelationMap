# Local runbook (P0 empty stack)

Do not put secrets in the repo. Copy env values from a local untracked `.env`.

## Frontend (`apps/web`)

```bash
cd apps/web
npm install
npm run dev          # http://localhost:5173
npm run typecheck
npm run lint
npm run test         # placeholder until P3/P4
npm run build
```

## API (`apps/api`)

Requires Java 21. Maven Wrapper downloads its own distribution on first run.

```bash
cd apps/api
./mvnw -DskipTests package
./mvnw spring-boot:run
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
PostgreSQL. Local PG templates and deploy/ belong to SRE.

## Design checks (already in repo)

```bash
python3 tools/validate_design.py
```
