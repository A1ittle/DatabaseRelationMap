# 本地运行摘记（SRE P0）

完整产品 RUNBOOK 可能在 Engineer PR 的 `docs/` 下。这里只覆盖 **PG 生命周期** 和 **Java 8 API 占位**。

## PostgreSQL 17

详见 [README.md](README.md)。

```bash
cp deploy/.env.example deploy/.env
docker compose -f deploy/docker-compose.yml up -d
./deploy/scripts/wait-pg.sh
docker compose -f deploy/docker-compose.yml down        # 停止
docker compose -f deploy/docker-compose.yml down -v     # 销毁数据
```

健康检查：`docker compose -f deploy/docker-compose.yml ps`；容器内 `pg_isready -U lineage -d lineage`。

## Java 8 + Spring Boot 2.7 API

**无** `SPRING_DATASOURCE_URL` 时 API 仍可起 `/api/health`。有 URL 时 Flyway V1 在启动时执行（除非 `FLYWAY_ENABLED=false`）。

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/lineage
export SPRING_DATASOURCE_USERNAME=lineage
export SPRING_DATASOURCE_PASSWORD=change-me-local   # 仅本地，来自 deploy/.env
export FLYWAY_ENABLED=true
cd apps/api
JAVA_HOME=/home/box/tools/jdk8u504-b01 ./mvnw spring-boot:run -Dspring-boot.run.profiles=db
```

可销毁验收（需 Docker）：`./deploy/scripts/migrate-verify.sh`。无 Docker 则该脚本 **BLOCKED** 并打印同样步骤。

嵌入方式：宿主系统把前端壳指向本地 API；API 再连上述 JDBC。不做独立 OIDC 产品部署。

## BLOCKED

真实 SSO、企业 OIDC、生产凭证、真实血缘数据：未提供，不在此编造。

## P5 scale + backup (local only)

```bash
./deploy/scripts/scale-sample.sh
./deploy/scripts/backup-restore-drill.sh
```

See `deploy/README.md` §P5. Evidence: `evidence/implementation/p5-scale-sample.txt`. Not production.

