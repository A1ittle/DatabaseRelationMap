# Implementation progress

## Stage: P4 visual freeze (fixture screenshot protocol)

**Goal:** Align Vue shell CSS tokens with prototype `:root`, document a pixel-
stable screenshot protocol, capture 8 fixture-only PNGs (4 views × 1280/390).
No OIDC, no Ling/auto-heal CI, no screenshot-to-code rewrite of the tree.

**Branch:** `feat/p4-visual-freeze` (from `main@dabc628`).

### Done

- `apps/web/src/tokens.css` — prototype hex + oklch tokens; Vue `--tree` /
  `--cross` / `--unclassified` extras. `html, body` stay transparent; `.shell`
  uses `--bg`.
- Tabs / filter chips / panel surfaces / list hover follow prototype chrome
  (not flowchart topology). Three questions recorded in
  [CHANGELOG.md](../evidence/implementation/p4-visual/CHANGELOG.md) before the
  CSS edit.
- Protocol: [PROTOCOL.md](../evidence/implementation/p4-visual/PROTOCOL.md).
  Capture: `apps/web/scripts/capture-p4-visual.mjs` (`npm run capture:p4-visual`).
- Evidence dir `evidence/implementation/p4-visual/` (PNGs + `best/`「历史最佳」).

### Commands actually run (this machine)

See [p4-visual-freeze.txt](../evidence/implementation/p4-visual-freeze.txt).

### Gaps / blocked

- Fixtures are not real lineage. Do not Diff tree topology vs prototype SVG.
- No SSO / OIDC.

### Next

P5 scale and deploy. A01–A22 remaining evidence.

---

## Stage: P4 import / publish page + exception states

**Goal:** Vue 2 embedded **数据导入** page against existing import APIs, plus
distinct query-shell exception banners (HANDOFF P4). Four views already on
main. Not 视觉冻结. No React Flow. No OIDC.

**Branch:** `feat/p4-import-publish-exceptions` (from `main@5f2ba6c`).

### Done

- Nav **血缘工作台 | 数据导入**. Import page: paste/upload ImportBatch JSON
  (documented sample `fixtures/v1/import.json`, not production lineage).
  `POST /api/imports` → 202; `GET /api/imports/{runId}` issue pagination;
  publish when `ready` (`expectedActiveSnapshotId`, empty = null).
- Client `apps/web/src/api/importClient.js` (CSRF `X-CSRF-Token: dev`, optional
  `X-Embed-Groups`) + vitest (202, GET cursor, 413 `PAYLOAD_TOO_LARGE`,
  `IMPORT_INVALID`, `PUBLISH_CONFLICT`).
- Exception helper + `ExceptionBanner`: empty / no hits / `LINEAGE_NOT_COLLECTED`
  (未采集) / request failure / `TEMPORARILY_UNAVAILABLE` / `POLICY_CHANGED` and
  `QUERY_EXPIRED` (destroy query, 重新搜索) / coverage incomplete /
  `FORBIDDEN`/`UNAUTHENTICATED` only from API codes. Cycle 环→清单/路径 kept.
- Evidence: [evidence/implementation/p4-import-publish-exceptions.txt](../evidence/implementation/p4-import-publish-exceptions.txt).

### Commands actually run (this machine)

Environment: Node v22, npm 10, Vue 2.7.16, Vite 4.5, vitest 1.x.

```text
cd apps/web && npm test && npm run build
# vitest 1.6.1: 11 files, 42 tests, 0 fail
# vite build: 40 modules; dist/index.html; exit 0
```

### Gaps / blocked

- No real SSO / OIDC (open / demo-header + X-Embed-Groups only).
- Live import→publish click-through needs a running API + PostgreSQL.
- Do not treat fixtures as real lineage.

### Next

P4 视觉冻结 (separate) / P5 scale and deploy. A01–A22 remaining evidence.

---

## Stage: P4 four views + URL restore

**Goal:** Vue 2 embedded shell tabs for 树/图, 总览, 影响清单, 最短路径 against
existing lineage APIs, plus URL restore. Keep P3 tree/detail/cross/budget.
Not the import/exception page. No React Flow. No OIDC.

**Branch:** `feat/p4-four-views-url-restore` (from `main@bf02b21`).

### Done

- View tabs (`ViewTabs`) switch `tree | overview | impact | path`. Keyboard
  tablist with arrows / Home / End. Narrow layout still stacks panels.
- **总览** uses `GET .../overview` (layer×type clusters, count only) and
  `GET .../clusters/{cid}/members` pagination. Locating a member selects it and
  may POST projection with `revealSelectedPath`. No invented edges.
- **影响清单** uses independent `GET .../impact` (full reach, optional `types`).
  Never derived from on-screen tree nodes.
- **最短路径** uses `GET .../path?targetId=` and `GET .../relations/{rid}/evidence`.
  `sourceRef` is a readonly copyable string (no HTML).
- Cycle queries: banner **检测到循环依赖，已切换关系清单**; tree tab unavailable;
  list/path remain.
- URL search-param contract (`mode,seedId,snapshotId,selectedId,targetId,types`)
  in [apps/web/README.md](../apps/web/README.md). `queryId` is not encoded;
  restore recreates the query from `seedId` and re-fetches APIs.
- Vitest: URL encode/decode + view-state helpers; lineageClient GET paths.
- Evidence: [evidence/implementation/p4-four-views-url.txt](../evidence/implementation/p4-four-views-url.txt).

### Commands actually run (this machine)

Environment: Node v22, npm 10, Vue 2.7.16, Vite 4.5, vitest 1.x.

```text
cd apps/web && npm test && npm run build
# vitest 1.6.1: 9 files, 30 tests, 0 fail
# vite build: 34 modules; dist/index.html; exit 0
```

### Gaps / blocked

- No real SSO / OIDC (open / demo-header + X-Embed-Groups only).
- Import / exception page is a separate P4 slice.
- Live four-view click-through needs a running API + published fixture.

### Next

P4 import UI and exception states against the same APIs.

---

## Stage: P3 projection state, cross-list, change-root, budget gate

**Goal:** Finish leftover P3 on the Vue 2 shell: layout vs domain, atomic
projection replace, cross-branch locate, change-root, drawn stats, budget
keep-old. No React Flow. No OIDC.

**Branch:** `feat/p3-projection-cross-budget` (from `main@2a8a69c`).

### Done

- Layout (`apps/web/src/graph/layout.js`) uses `layoutRank` only; classify stays
  on the API. Vue 2 expandable tree is the canvas.
- Atomic replace via `resolveProjectionOutcome`: nodes+edges commit together;
  incomplete or stale responses leave the previous canvas.
- Cross panel lists cross + unclassified edges from projection kinds (same set
  regardless of expand order). **定位** pins the other endpoint and may POST
  projection with `revealSelectedPath`.
- **换根** cancels in-flight work (AbortController + revision epoch), clears
  expand pages/pins, creates a new query from the selected object, remounts the
  tree.
- Meta bar **绘制节点/边** from the current projection; server `stats` kept as
  **服务端 下游**.
- `PROJECTION_LIMIT` → banner `超预算，已保留原图`; candidateIds rolled back.
  Vitest covers budget keep-old, incomplete graph, stale revision, cross-list
  helper, layout stability. Synthetic oversize `candidateIds` in tests only.
- Evidence: [evidence/implementation/p3-projection-cross-budget.txt](../evidence/implementation/p3-projection-cross-budget.txt).

### Commands actually run (this machine)

Environment: Node v22, npm 10, Vue 2.7.16, Vite 4.5, vitest 1.x.

```text
cd apps/web && npm test && npm run build
# vitest 1.6.1: 8 files, 20 tests, 0 fail
# vite build: 23 modules; dist/index.html; exit 0
```

### Gaps / blocked

- No real SSO / OIDC (open / demo-header + X-Embed-Groups only).
- Four-view workspace, import UI, URL restore are P4.
- Live oversize projection against a published snapshot is not exercised here
  (fixture graph is far under 200/2000). Client path uses a mock 400.

### Next

P4 four views, import page, and exception states against the same APIs.

---

## Stage: P3 search + downstream tree UI

**Goal:** Vue 2 embedded shell vertical slice: search root → create query →
default one-level downstream tree → select detail → children pagination →
projection full replace. Existing `/api/lineage/*` on Java 8. No React Flow
(ADR 0001). No OIDC; no invented lineage.

**Branch:** `feat/p3-search-downstream-tree-ui` (from `main@171bb99`).

### Done

- `apps/web` calls `GET /api/lineage/search`, `POST /api/lineage/queries`,
  `GET .../children`, `POST .../projection`, `GET .../nodes/{id}` and
  `.../relations`. Config: `VITE_API_BASE` default `http://127.0.0.1:8080`,
  CSRF `X-CSRF-Token: dev` on POST, optional `VITE_EMBED_GROUPS`.
- Expandable tree + detail panel (no React Flow / G6). Tree vs cross CSS
  (`kind-tree` / `kind-cross` / `kind-unclassified`).
- `clientRevision` guard drops stale projection responses.
- Children cursor pagination; after expand, candidateIds are posted and the
  projection **replaces** graph state.
- Open-mode CORS filter so the Vite shell on :5173 can call :8080.
- Vitest: revision guard, tree index from `fixtures/v1/query-response.json`,
  client headers. Demo: `apps/web/scripts/demo-deep-expand.sh` (root → view-a
  → proc-b → table-c, layoutRank 0..3+).
- Evidence: [evidence/implementation/p3-search-tree-ui.txt](../evidence/implementation/p3-search-tree-ui.txt).

### Commands actually run (this machine)

Environment: Node v22, npm 10, Vue 2.7.16, Vite 4.5, vitest 1.x.

```text
cd apps/web && npm install && npm test && npm run build
# vitest 1.6.1: 4 files, 10 tests, 0 fail
# vite build: 18 modules; dist/index.html; exit 0
```

### Gaps / blocked

- No real SSO / OIDC (open / demo-header + X-Embed-Groups only).
- Four-view workspace, import UI, URL restore are P4.
- Deep-expand e2e needs a running API + published fixture; script exits 2 if
  the API is down. Do not treat fixtures as real lineage.

### Next

P4 four views, import page, and exception states against the same APIs.

---

## Stage: P2 query API + minimal embed auth

**Goal:** Wire lineage query APIs to published snapshots and P1
`DefaultLineageGraphAlgorithms`. Minimal embed auth via optional
`X-Embed-Groups` (trust host / dev stub). No enterprise OIDC. Java 8 /
Spring Boot 2.7. Real JDBC against `deploy/` PostgreSQL.

**Branch:** `feat/p2-query-api-embed-auth` (from `main@4e8dcec`).

### Done

- Embed auth: omit `X-Embed-Groups` in open mode → authorize the full active
  snapshot. Header present → `scope_grant` view + `object_grant` allow, **deny
  wins**. Unauthorized/forged object ids share `NOT_FOUND` Error shape.
- `GET /api/lineage/search` — technical/display name search in the active
  snapshot within the authorized set (`reveal` vs `recenter` when `queryId` is
  set).
- `POST /api/lineage/queries` — query context bound to snapshot +
  `policy_revision` + seed; default first-layer projection; `QueryResponse`.
- `GET .../children` — stable cursor pagination of main-tree children.
- `POST .../projection` — replace projection from `candidateIds` using
  classify tree/cross; auth before traverse.
- `GET .../path` — `shortestPath` with the authorized set.
- Overview / impact / cluster members / node / relations / evidence served
  from the same in-memory classified graph (no extra traversal). CSRF on POST
  lineage matches import (`X-CSRF-Token`).
- JDBC IT `QueryApiJdbcTest`: import+publish fixture then query; unauthorized
  ID test. Health without DB still 200; lineage without DB is 503.

### Commands actually run (this machine)

Environment: Temurin JDK 8u504-b01, Maven Wrapper, Spring Boot 2.7.18, Docker
PostgreSQL 17 (`deploy/docker-compose.yml`).

```text
cd apps/api && JAVA_HOME=/home/box/tools/jdk8u504-b01 ./mvnw test
# LineageApiApplicationTests: 6 run, 0 fail (health without DB; lineage 503 without DB)
# QueryApiJdbcTest: 6 run, 0 fail (real JDBC)
# EmbedAuthzTest: 3 run, 0 fail
# ImportPublishJdbcTest: 9 run, 0 fail
# Tests run: 45, Failures: 0; BUILD SUCCESS; exit 0
```

### Gaps / blocked

- No real SSO / OIDC (open / demo-header + X-Embed-Groups only).
- Query cache is in-memory per process (idle 15 min / absolute 60 min).
- Do not treat fixtures as real lineage.

### Next

P3 downstream-tree UI against these APIs.

---


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

## Stage: P5 scale sample + backup/restore (SRE)

**Branch:** `feat/p5-scale-backup-restore`.

- Synthetic fixture `fixtures/v1/scale-500.json` (500+ reachable from root; not real lineage).
- `deploy/scripts/scale-sample.sh`: disposable PG → Flyway → API → import/publish → cold/hot search/children/projection (concurrency=1).
- `deploy/scripts/backup-restore-drill.sh`: `pg_dump -Fc` → truncate marker → `pg_restore --clean` on local volume.
- Evidence under `evidence/implementation/p5-*.txt` (+ local `.dump` gitignored).
- **BLOCKED / out of scope:** real data, SSO, production backup/PITR, multi-client load, UI browser paint timings.
- **No「可上线」claim.**

