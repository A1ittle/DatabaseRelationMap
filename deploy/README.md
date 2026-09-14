# 本地 PostgreSQL 17（可销毁）

本目录只提供**嵌入式工具**开发用的临时库，不是治理平台或生产部署。数据卷可随时删掉。

API 在**无** `SPRING_DATASOURCE_URL` 时仍可启动 `/api/health`。设置该 URL（以及可选 `FLYWAY_ENABLED=true`）后，启动时跑 Flyway `V1__storage.sql`。

## 启动 / 停止 / 销毁

在仓库根目录：

```bash
cp deploy/.env.example deploy/.env   # 只改本地副本，不要提交
docker compose -f deploy/docker-compose.yml up -d
./deploy/scripts/wait-pg.sh
```

或进入 `deploy/` 后 `docker compose up -d`（Compose 会读取同目录 `.env`）。

| 动作 | 命令 |
|---|---|
| 启动 | `docker compose -f deploy/docker-compose.yml up -d` |
| 停止（保留数据卷） | `docker compose -f deploy/docker-compose.yml stop` 或 `down` |
| **销毁并清空数据** | `docker compose -f deploy/docker-compose.yml down -v` |

`down -v` 会删除 named volume `lineage_pg_data`，本地库回到空白。这是预期行为。

## 连接串（无真实密钥）

占位密码仅为 `change-me-local`，见 `.env.example`。

```text
Host:     localhost
Port:     5432
Database: lineage
User:     lineage
JDBC:     jdbc:postgresql://localhost:5432/lineage
```

## 环境变量清单

| 变量 | 示例 | 用途 |
|---|---|---|
| `POSTGRES_HOST` | `localhost` | 宿主机连库 |
| `POSTGRES_PORT` | `5432` | 映射 `5432:5432` |
| `POSTGRES_DB` | `lineage` | 库名 |
| `POSTGRES_USER` | `lineage` | 用户 |
| `POSTGRES_PASSWORD` | `change-me-local` | **仅本地占位** |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/lineage` | API JDBC；非空则启动时跑 Flyway |
| `SPRING_DATASOURCE_USERNAME` | `lineage` | API |
| `SPRING_DATASOURCE_PASSWORD` | `change-me-local` | API，与 PG 占位一致 |
| `FLYWAY_ENABLED` | `true` | 有 URL 时默认视为 true；显式 `false` 可关迁移 |

真实 `deploy/.env` 已被 gitignore。不要把生产密码写进仓库。

## 健康检查

```bash
docker compose -f deploy/docker-compose.yml ps
docker compose -f deploy/docker-compose.yml exec postgres pg_isready -U lineage -d lineage
./deploy/scripts/wait-pg.sh
```

`ps` 中 `postgres` 应为 `healthy`。`wait-pg.sh` 会轮询 Compose `pg_isready` 或宿主机 `pg_isready`，默认 60s。

## Flyway 迁移验收（SRE，需要 Docker）

权威 DDL 是 `apps/api/src/main/resources/db/migration/V1__storage.sql`（与 `spec/v1/storage.sql` 同结构）。

```bash
cp deploy/.env.example deploy/.env   # 只改本地副本，不要提交
./deploy/scripts/migrate-verify.sh
```

脚本会 `up` → `wait-pg` → `./mvnw flyway:migrate`（跑两次证明幂等）→ `flyway:info` → `down -v`。`KEEP_PG=1` 可留下卷。无 Docker 的环境会打印同样步骤并以 **BLOCKED** 退出。

也可手动：

```bash
docker compose -f deploy/docker-compose.yml up -d
./deploy/scripts/wait-pg.sh
cd apps/api
# 从 deploy/.env 导出 SPRING_DATASOURCE_* ，切勿把密码写进仓库
./mvnw flyway:migrate -Dflyway.url="$SPRING_DATASOURCE_URL" \
  -Dflyway.user="$SPRING_DATASOURCE_USERNAME" \
  -Dflyway.password="$SPRING_DATASOURCE_PASSWORD"
./mvnw flyway:info -Dflyway.url="$SPRING_DATASOURCE_URL" \
  -Dflyway.user="$SPRING_DATASOURCE_USERNAME" \
  -Dflyway.password="$SPRING_DATASOURCE_PASSWORD"
# 或启动 API 让 Boot 跑迁移：
# FLYWAY_ENABLED=true ./mvnw spring-boot:run -Dspring-boot.run.profiles=db
docker compose -f deploy/docker-compose.yml down -v
```

## 可选：原始 DDL 冒烟（不是 Flyway）

```bash
./deploy/scripts/smoke-storage.sh
```

把 `spec/v1/storage.sql` 直接打进可销毁库。正式路径是 `migrate-verify.sh` / Flyway V1。失败时先 `down -v`。

## 回滚 / 停止

1. 停服务：`docker compose -f deploy/docker-compose.yml down`
2. 需要空白库：再加上 `-v`
3. 不改 `spec/**`、不改生产、不保留这份卷当备份

没有生产回滚对象：这不是已发布环境。

## 嵌入式运行（宿主应用如何指向本地）

形态是**已有系统里的嵌入工具**，不是独立 OIDC 产品。宿主侧后续只需把本地 API + 本 PG 指过去，例如：

- 浏览器/iframe 或反向代理打到本地 API（端口由 Engineer 分支的 API 决定）
- API 进程导出与上表同名的 `SPRING_DATASOURCE_*`，指向 `localhost:5432/lineage`

本仓库此阶段**不**设计整站平台发布、网关、多实例或生产清单。Java 8 启动示例见 [RUNBOOK.md](RUNBOOK.md)。

## BLOCKED（未提供，此处不编造）

- 真实 SSO / 企业 OIDC 与授权映射
- 生产主机、生产账号与凭证
- 真实血缘关系数据（`fixtures/` 不是生产数据）
- 本机无 Docker 时的 **live Flyway migrate**（agent host 为 BLOCKED；有 Docker 的开发机按上面的 recipe）
- 真实导入/发布 API（P2 本切片只做迁移与接线）

不要把占位密码或 fixture 当成试点已接通。
