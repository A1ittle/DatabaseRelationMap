# Implementation progress

## Stage: P2 import + atomic CAS publish

**Goal:** POST/GET `/api/imports` and POST `/api/imports/{runId}/publish` with
schema+semantic validation, batchKey idempotency, quality_issue rows, and CAS
publish against real PostgreSQL. Java 8 / Spring Boot 2.7. No OIDC.

**Branch:** `feat/p2-import-cas-publish` (from `main@d1cefdb`).

### Done

- `ImportValidator` — JSON shape + duplicate ids, dangling endpoints, evidence
  refs, javaKind, relation uniqueness; warnings for self-loop / Java out-edge /
  directed cycle.
- `ImportService` + `ImportJdbcRepository` — ingest_run, unpublished snapshot
  staging (`object_version` / `relation_version` / `evidence`), quality_issue on
  failure (`status=failed`, no snapshot). Same scope+batchKey+payload SHA → same
  `runId`; different content → `IMPORT_INVALID`.
- Publish locks `catalog_scope` (`FOR UPDATE`), checks
  `expectedActiveSnapshotId`, sets `published_at`, switches `active_snapshot_id`,
  bumps `revision`. Mismatch → `PUBLISH_CONFLICT` 409. Failed runs cannot publish.
- HTTP: `/api/imports`, `/api/imports/{runId}` (issues cursor pagination),
  `/api/imports/{runId}/publish`. CSRF `X-CSRF-Token` on POST. Auth mode
  `lineage.security.mode=open` (default) or `demo-header`. `/api/health` still
  works without a datasource.
- JDBC IT `ImportPublishJdbcTest` uses `deploy/` docker compose PostgreSQL (no
  JDBC mock): fixture import → publish → rows persist; failed import leaves
  active unchanged; CAS conflict; concurrent publish one winner.
- Evidence: [evidence/implementation/p2-import-cas.txt](../evidence/implementation/p2-import-cas.txt).

### Commands actually run (this machine)

Environment: Temurin JDK 8u504-b01, Maven Wrapper, Spring Boot 2.7.18, Docker
PostgreSQL 17.11 (`deploy/docker-compose.yml`).

```text
cd apps/api && JAVA_HOME=/home/box/tools/jdk8u504-b01 ./mvnw test
# LineageApiApplicationTests: 5 run, 0 fail (health without DB)
# ImportValidatorTest: 6 run, 0 fail
# CanonicalJsonTest: 1 run, 0 fail
# ImportPublishJdbcTest: 9 run, 0 fail (real JDBC)
# Tests run: 35, Failures: 0; BUILD SUCCESS; exit 0
```

### Gaps / blocked

- No real SSO / OIDC (explicit open/demo-header only).
- Query context / search / projection APIs are later P2/P3 work.
- Do not treat fixtures as real lineage.

### Next

Query context, stable cursors, authorization on read APIs.

---

## Stage: P2 Flyway migration (storage.sql → V1)

**Goal:** convert `spec/v1/storage.sql` into Flyway V1, wire Boot so health still
starts without a datasource, and leave SRE a disposable-PG verify recipe.
Java 8 / Spring Boot 2.7. No SSO, no invented credentials. Live migrate against
PostgreSQL is **BLOCKED** on this agent host (no Docker).

**Branch:** `feat/p2-flyway-storage` (from `main@f4d425f`).

### Done

- `apps/api/src/main/resources/db/migration/V1__storage.sql` — faithful copy of
  `spec/v1/storage.sql` (single version; Flyway wraps it in one PG transaction).
- Default profile: `spring.flyway.enabled=${FLYWAY_ENABLED:false}`. Blank
  `SPRING_DATASOURCE_URL` excludes DataSource/Flyway auto-config via
  `OptionalDataSourceEnvironmentPostProcessor` so `/api/health` still works.
- Non-empty URL enables Flyway unless `FLYWAY_ENABLED=false`. Optional profile
  `db` (`application-db.yml`). flyway-maven-plugin for `flyway:migrate` / `info`.
- `deploy/scripts/migrate-verify.sh`: up compose → wait-pg → migrate twice →
  info → `down -v`. Exits **BLOCKED** without Docker and prints the SRE recipe.
- Tests without Docker: classpath V1 present and matches spec DDL; Flyway
  defaults; post-processor URL on/off. H2 is not used (PG-specific SQL).
- Evidence: [evidence/implementation/p2-flyway-migration.txt](../evidence/implementation/p2-flyway-migration.txt).

### Commands actually run (this machine)

Environment: Temurin JDK 8u504-b01, Maven Wrapper, Spring Boot 2.7.18. **No Docker.**

```text
cd apps/api && JAVA_HOME=/home/box/tools/jdk8u504-b01 ./mvnw test
# LineageApiApplicationTests: 5 run, 0 fail (health + Flyway defaults)
# FlywayMigrationResourceTest: 2 run, 0 fail
# OptionalDataSourceEnvironmentPostProcessorTest: 4 run, 0 fail
# P1CounterexampleSuiteTest: 8 run, 0 fail
# Tests run: 19, Failures: 0; BUILD SUCCESS; exit 0

command -v docker || echo 'docker: not found'
# docker: not found
./deploy/scripts/migrate-verify.sh
# BLOCKED: no Docker on this host; exit 1 (SRE recipe printed)
```

### Gaps / blocked

- **BLOCKED: no Docker on agent host** — live `flyway:migrate` against PG 17 was
  not executed here. SRE with Docker: `./deploy/scripts/migrate-verify.sh`.
- No real SSO / OIDC. No import/publish API yet (rest of HANDOFF P2).

### Next

Import/publish, query context, and JDBC integration tests on disposable PG.
Do not treat fixtures as real lineage.

---

## Stage: P1 domain graph algorithms (green)

**Goal:** replace `StubLineageGraphAlgorithms` with a real Java 8 adjacency-list
implementation so the P1 counterexample suite and `fixtures/v1` alignment pass.
No SSO / real lineage invention. Tests were not skipped or weakened.

**Branch:** `feat/p1-domain-graph-algorithms` (from `main@722cbe3`).

### Done

- `DefaultLineageGraphAlgorithms` implements `LineageGraphAlgorithms`:
  authorization-first usable edges, self-loop / Java out-edge exclusion,
  BFS reach + minHops, directed-cycle → `unavailable_cycle` (no force-rewire),
  DAG main-parent (parent rank+1 desc, strength desc, sourceOrder asc,
  relationId asc), tree/cross partition, shortest path with 256-object cap.
- `LineageGraphAlgorithmsFactory.create()` returns the real impl. Stub deleted.
- Evidence: [evidence/implementation/p1-domain-algorithms.txt](../evidence/implementation/p1-domain-algorithms.txt).

### Commands actually run (this machine)

Environment: Temurin JDK 8u504-b01, Maven Wrapper, Spring Boot 2.7.18.

```text
cd apps/api && JAVA_HOME=/home/box/tools/jdk8u504-b01 ./mvnw test
# LineageApiApplicationTests: 2 run, 0 fail
# P1CounterexampleSuiteTest: 8 run, 0 fail
# Tests run: 10, Failures: 0; BUILD SUCCESS; exit 0
```

### Gaps / blocked

- No real SSO / OIDC. No real lineage import.
- Query budget / incomplete classification is not a P1 acceptance case.

### Next

P2: Flyway from `spec/v1/storage.sql`, import/publish, query context, on a
real disposable PostgreSQL. Do not treat fixtures as real lineage.

---

## Stage: P1 counterexample acceptance suite (TDD red)

**Goal:** land independent graph counterexamples and a thin `domain/graph` façade
*before* the full BFS/classify/path algorithm. Embedded-tool stack remains
Java 8 + Spring Boot 2.7. No real lineage data, no SSO.

**Branch:** `feat/p1-graph-counterexamples` (from `main@b98eefb`).

### Done

- Hand-authored fixtures under [fixtures/v1/counterexamples/](../fixtures/v1/counterexamples/)
  (not generator output): parallel relations, self-loop, cycle tail, reorder
  stability, Java out-edges, hidden intermediate, path unknown / length cap.
- JUnit 5 suite `com.lineage.api.domain.graph.P1CounterexampleSuiteTest` plus
  an alignment hook for `fixtures/v1/import.json` → `expected.json`
  (seed/reach/tree/cross/minHops/parentEdgeIds/excludedRelationIds/pathToJava).
- Thin façade `com.lineage.api.domain.graph.LineageGraphAlgorithms` with a stub
  from `LineageGraphAlgorithmsFactory`. Stub is expected to fail the suite.
- Maven test resources copy `../../fixtures` onto the test classpath.
- Evidence: [evidence/implementation/p1-counterexample-suite.txt](../evidence/implementation/p1-counterexample-suite.txt).

### Commands actually run (this machine)

Environment: Temurin JDK 8u504-b01, Maven Wrapper, Spring Boot 2.7.18.

```text
cd apps/api && JAVA_HOME=/home/box/tools/jdk8u504-b01 ./mvnw test
# LineageApiApplicationTests: 2 run, 0 fail
# P1CounterexampleSuiteTest: 8 run, 8 fail (stub)
# Tests run: 10, Failures: 8; BUILD FAILURE; exit 1
```

Red is the intended TDD state. Assertions were not skipped or deleted.

### Gaps / blocked

- Real adjacency-list BFS, Java truncation, parent/tree/cross, shortest path
  (next P1 slice). Suite must stay failing until that work lands.
- No real SSO / OIDC. No real lineage import.

### Next

Implement `LineageGraphAlgorithms` against these fixtures and `expected.json`.
Do not treat generator output as expected values.

---

## Stage: P0 dependency baseline, design gates, contract types

**Goal:** run existing design gates with captured evidence; record the
toolchain actually used; generate OpenAPI client types into the Vue 2 app.
No real lineage data, no SSO, no production deploy.

**Baseline:** rebased onto `main@467c074` (after PR #2 merge); branch `feat/p0-dependency-baseline`.
Stack remains Java 8 + Vue 2 embedded (ADR 0001). This stage does not revert
to React / Java 21.

### Done

- Design gates run on this machine; stdout/stderr and exit codes stored under
  [evidence/implementation/](../evidence/implementation/):
  - `python3 tools/validate_design.py` → `exit_code: 0` (`validate_design.txt`)
  - `node reference/open-design/program-lineage-handoff/spec/verify-tree.mjs` →
    `exit_code: 0` (`verify_tree.txt`; `OK seed=ods.trade_order reach=10 tree=9 cross=3`)
  - `node evidence/probe-reference.mjs` → `exit_code: 0` (`probe_reference.txt`)
- [docs/DEPENDENCY_BASELINE.md](DEPENDENCY_BASELINE.md) records runtime
  Vue 2 / Java 8 versions plus design-gate Python/jsonschema and the OpenAPI
  client-gen toolchain (sources and reasons).
- `apps/web`: `openapi-typescript@7.13.0` + `typescript@5.9.3` (dev only).
  Script `generate:api` writes [apps/web/src/generated/openapi.d.ts](../apps/web/src/generated/openapi.d.ts)
  from [spec/v1/openapi.json](../spec/v1/openapi.json). Generated file is
  committed. Vue 2 application code is still JavaScript and does not call the
  API yet.
- `tools/requirements.txt` pins `jsonschema==4.26.0` for the design validator.

### Commands actually run (this machine)

Environment: Node v22.23.2, npm 10.9.8, Python 3.13.5, jsonschema 4.26.0
(PyPI), Temurin JDK 8u504-b01 unused in this stage.

```text
python3 -m pip install --user jsonschema==4.26.0
python3 tools/validate_design.py
# status PASS; 14 openapiPaths; 32 schemas; exit 0

node reference/open-design/program-lineage-handoff/spec/verify-tree.mjs
# OK  seed=ods.trade_order  reach=10 tree=9 cross=3; exit 0

node evidence/probe-reference.mjs
# JSON probe report; fixtureCounts nodes=17 edges=18; exit 0

cd apps/web && npm install && npm run generate:api
# openapi-typescript 7.13.0
# ../../spec/v1/openapi.json → src/generated/openapi.d.ts
```

Gates were not lowered. Failures would have been recorded as-is.

### Gaps / blocked

- **PostgreSQL 17 / `deploy/`** — merged via PR #2 (see SRE section below). API health still does not require the database until P2. Agent host remains template-only without Docker.
- No real SSO / OIDC.
- No real lineage import or graph algorithm (P1+).
- Frontend has no graph canvas yet. Generated types are unused at runtime until P3+.
- Empty-stack start commands still apply (`docs/RUNBOOK.md`); this stage did not re-package the API.

### Next

P1 domain algorithm against `fixtures/v1` expected output (authorization-after
BFS, Java truncation, parent/tree/cross). P2 Flyway from `spec/v1/storage.sql`
once PG is available. Do not treat fixtures as real lineage.

---

## SRE P0 · 本地 PostgreSQL 17 + deploy 模板

**阶段目标：** 可销毁的本地 PG17、`deploy/` 模板、嵌入式工具运行摘记。不实现业务应用、不编造 SSO/生产数据。

**产品形态（PM）：** 嵌入已有系统的工具；API 为 Java 8 + Spring Boot 2.7，前端壳 Vue 2。本段不改 `apps/`，不改 HANDOFF 产品默认值。

### 本阶段新增

| 路径 | 说明 |
|---|---|
| `deploy/docker-compose.yml` | PostgreSQL 17、named volume `lineage_pg_data`、healthcheck、`5432:5432`、`restart: unless-stopped` |
| `deploy/.env.example` | 占位 `POSTGRES_*` 与 `SPRING_DATASOURCE_*`；真实 `.env` gitignore |
| `deploy/README.md` | 启动/停止/销毁、连接串、环境清单、健康检查、回滚、BLOCKED、嵌入说明 |
| `deploy/RUNBOOK.md` | Java 8 启动摘记 + PG 生命周期（薄指针；完整产品 RUNBOOK 见 `docs/RUNBOOK.md`） |
| `deploy/scripts/wait-pg.sh` | 等到 compose health / `pg_isready` |
| `deploy/scripts/smoke-storage.sh` | 可选：把 `spec/v1/storage.sql` 打进可销毁库（**不是** Flyway） |

未改：`HANDOFF.md`、`reference/**`、`spec/**`、`fixtures/**`。未提交真实 `.env`。

### 本地命令

```bash
cp deploy/.env.example deploy/.env
docker compose -f deploy/docker-compose.yml up -d
./deploy/scripts/wait-pg.sh
docker compose -f deploy/docker-compose.yml ps
docker compose -f deploy/docker-compose.yml exec postgres pg_isready -U lineage -d lineage
# 可选 DDL 冒烟（非生产迁移）：
./deploy/scripts/smoke-storage.sh
docker compose -f deploy/docker-compose.yml down -v
```

### 验证状态

| 项 | 结果 |
|---|---|
| 模板文件 | 已写入仓库 |
| `deploy/scripts/*.sh` 可执行位 | 已 `chmod +x` |
| 密码 | 仅占位 `change-me-local` |
| `docker compose … config` | **template-only on agent host**（无 Docker）。有 Docker 的开发机按 `deploy/README.md` 实跑。 |

### BLOCKED / 非本阶段

- 真实 SSO、企业 OIDC、生产凭证与主机
- 真实血缘数据
- Flyway 正式迁移与 API 连库验收（P2）

## Previous stage: P0 environment and empty stack

Empty Vue 2 embedded frontend and empty Spring Boot 2.7 / Java 8 API start
locally. See git history on `feat/p0-empty-stack` / PR #1. Stack follows PM
direction, not the HANDOFF React/TS + Java 21 default.
[ADR 0001](adr/0001-java8-vue2-embedded-vs-handoff.md).

---

## SRE · P2 migrate-verify (Docker)

- Host installed `docker.io` + compose plugin; ran `./deploy/scripts/migrate-verify.sh` on PR #6 tip.
- Result: **PASS** (`exit 0`). Evidence: `evidence/implementation/p2-migrate-verify.txt`.
- `deploy/.env` used from `.env.example` placeholders only; not committed.
