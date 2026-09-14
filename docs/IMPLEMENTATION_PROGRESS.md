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

- **PostgreSQL 17 / `deploy/`** — delivered on SRE branch `feat/p0-local-pg-deploy`
  (see section below). API health still does not require the database until P2.
- No real SSO / OIDC.
- No real lineage import or graph algorithm (P1+).
- Frontend has no graph canvas yet.
- Contract client types from OpenAPI may land in a follow-up Engineer PR.

### Next

P1 domain algorithm against `fixtures/v1` expected output; P2 Flyway from
`spec/v1/storage.sql` once PG is available.

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
