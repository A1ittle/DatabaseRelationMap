# Implementation progress

## Stage: P0 environment and empty stack

**Goal:** empty React/Vite frontend and empty Spring Boot API start locally.
No real lineage data, no SSO, no production deploy.

**Baseline:** commit `4aefc3f` on `main`; work on `feat/p0-empty-stack`.

### Done

- `apps/web`: Vite + React + TypeScript empty shell. Title **程序血缘地图**.
  Scripts: `dev` / `build` / `lint` / `typecheck` / `test` (placeholder).
- `apps/api`: Spring Boot **4.1.1**, Java 21, Maven Wrapper (`./mvnw`).
  Packages: `domain/graph`, `application`, `infrastructure`, `interfaces`.
  `GET /api/health` → `{"status":"ok"}`.
- Datasource placeholders in `application.yml` (`SPRING_DATASOURCE_*`).
- Flyway on the classpath; **`spring.flyway.enabled=false`**; no business
  migrations (choice: skip until PostgreSQL exists). JDBC/Flyway auto-config
  excluded so the process is fail-soft without a database.
- Root `.gitignore` for Java / Node / IDE / `.env`.
- `docs/RUNBOOK.md`, `docs/DEPENDENCY_BASELINE.md`.

### Commands actually run (this machine)

Environment: Node v22.23.2, npm 10.9.8, Temurin JDK 21.0.12.1+1,
`JAVA_HOME=/home/box/tools/jdk-21.0.12.1+1`. Maven Wrapper uses Apache Maven 3.9.16
from Maven Central. Spring Boot parent `4.1.1` from Maven Central (start.spring.io
id `4.1.1.RELEASE` maps to that artifact version).

```text
cd apps/web && npm install && npm run typecheck && npm run lint && npm run test && npm run build
# npm install: 27 packages, 0 vulnerabilities
# typecheck / oxlint: exit 0
# test: "P0: frontend unit tests not added yet"
# vite build: dist/index.html + assets, built in ~294ms

cd apps/api && ./mvnw -q package
# compile + tests pass (contextLoads, healthReturnsOkWithoutDatabase)
# target/api-0.0.1-SNAPSHOT.jar

./mvnw spring-boot:run   # no SPRING_DATASOURCE_* set
curl http://localhost:8080/api/health      # {"status":"ok"}
curl http://localhost:8080/actuator/health # {"groups":["liveness","readiness"],"status":"UP"}
```

### Gaps / blocked

- **PostgreSQL 17** is owned by SRE. No local PG in this task; API health does
  not check the database.
- No real SSO / OIDC.
- No real lineage import or graph algorithm (P1+).
- No `deploy/` production templates (SRE).
- Frontend has no React Flow yet.
- Contract client types from OpenAPI not generated yet.

### Next

P1 domain algorithm against `fixtures/v1` expected output; P2 Flyway from
`spec/v1/storage.sql` once PG is available.
