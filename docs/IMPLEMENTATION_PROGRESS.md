# Implementation progress

## Stage: P0 environment and empty stack

**Goal:** empty Vue 2 embedded frontend and empty Spring Boot 2.7 / Java 8 API
start locally. No real lineage data, no SSO, no production deploy.

**Baseline:** commit `4aefc3f` on `main`; work on `feat/p0-empty-stack`.
Stack follows PM direction (Java 8 + Vue 2 embedded tool), not the HANDOFF
React/TS + Java 21 / Spring Boot 4.1 default. Delta: [ADR 0001](adr/0001-java8-vue2-embedded-vs-handoff.md).

### Done

- `apps/web`: Vue 2 (`vue@2`) empty iframe-friendly shell. Title **程序血缘地图**.
  Scripts: `dev` / `build` / `preview` / `test` (placeholder). Bundler is Vite 4
  + `vite-plugin-vue2` (not a Vite React template).
- `apps/api`: Spring Boot **2.7.18**, Java 8 (source/target 1.8), Maven Wrapper
  (`./mvnw`). Packages: `domain/graph`, `application`, `infrastructure`,
  `interfaces`. `GET /api/health` → `{"status":"ok"}`.
- Datasource placeholders in `application.yml` (`SPRING_DATASOURCE_*`).
- Flyway on the classpath; **`spring.flyway.enabled=false`**; no business
  migrations (choice: skip until PostgreSQL exists). JDBC/Flyway auto-config
  excluded so the process is fail-soft without a database.
- Root `.gitignore` for Java / Node / IDE / `.env`.
- `docs/RUNBOOK.md`, `docs/DEPENDENCY_BASELINE.md`, ADR 0001.

### Commands actually run (this machine)

Environment: Node v22.23.2, npm 10.9.8, Temurin JDK 8u504-b01,
`JAVA_HOME=/home/box/tools/jdk8u504-b01`. Maven Wrapper uses Apache Maven 3.9.16
from Maven Central. Spring Boot parent `2.7.18` from Maven Central.

```text
cd apps/web && npm install && npm run build
# npm install: 126 packages (Vue 2.7.16 + vite-plugin-vue2 2.0.3 + Vite 4.5.14)
# vite build: dist/index.html + assets, built in ~448ms

cd apps/api && JAVA_HOME=/home/box/tools/jdk8u504-b01 ./mvnw -q -DskipTests package
# compile + package with javac target 1.8
# target/api-0.0.1-SNAPSHOT.jar

JAVA_HOME=/home/box/tools/jdk8u504-b01 ./mvnw -q test
# contextLoads, healthReturnsOkWithoutDatabase

java -jar target/api-0.0.1-SNAPSHOT.jar   # no SPRING_DATASOURCE_* set
curl http://localhost:8080/api/health      # {"status":"ok"}
curl http://localhost:8080/actuator/health # {"status":"UP"}
```

### Gaps / blocked

- **PostgreSQL 17** is owned by SRE. No local PG in this task; API health does
  not check the database.
- No real SSO / OIDC.
- No real lineage import or graph algorithm (P1+).
- No `deploy/` production templates (SRE).
- Frontend has no graph canvas yet.
- Contract client types from OpenAPI not generated yet.

### Next

P1 domain algorithm against `fixtures/v1` expected output; P2 Flyway from
`spec/v1/storage.sql` once PG is available.
