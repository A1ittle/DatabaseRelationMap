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

## Java 8 + Spring Boot 2.7 API（本分支无 apps/）

API 工程由 Engineer 分支提供。**当前 API 允许无 PG 启动**；本库给 P2 Flyway 用。

环境变量名需与未来占位一致：

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/lineage
export SPRING_DATASOURCE_USERNAME=lineage
export SPRING_DATASOURCE_PASSWORD=change-me-local   # 仅本地，来自 deploy/.env
```

示意启动（路径以 Engineer 交付为准，此处不创建 `apps/`）：

```bash
# JDK 8
java -version
# 典型 Maven 启动，待 apps/api 存在后：
# mvn -f apps/api/pom.xml -DskipTests spring-boot:run
```

嵌入方式：宿主系统把前端壳指向本地 API；API 再连上述 JDBC。不做独立 OIDC 产品部署。

## BLOCKED

真实 SSO、企业 OIDC、生产凭证、真实血缘数据：未提供，不在此编造。
