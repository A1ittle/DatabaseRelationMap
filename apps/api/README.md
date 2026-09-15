# lineage-api

Spring Boot 2.7.18 API (Java 8, Maven Wrapper). Graph algorithms in
`domain/graph`. Flyway V1 lives at `src/main/resources/db/migration/V1__storage.sql`
(frozen checksum). V2 `V2__object_identity_scope_pk.sql` scopes `object_identity`
PK to `(scope_id, object_id)` so the same natural id can exist in two scopes.

```bash
JAVA_HOME=/home/box/tools/jdk8u504-b01 ./mvnw -DskipTests package
JAVA_HOME=/home/box/tools/jdk8u504-b01 ./mvnw spring-boot:run
curl http://localhost:8080/api/health
# {"status":"ok"}
```

Without `SPRING_DATASOURCE_URL` the process starts with DataSource/Flyway
auto-configuration excluded. Tests cover `/api/health` on that path.

When PostgreSQL is available (see `deploy/.env.example`):

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/lineage
export SPRING_DATASOURCE_USERNAME=lineage
export SPRING_DATASOURCE_PASSWORD=...   # from deploy/.env, never commit
export FLYWAY_ENABLED=true
JAVA_HOME=/home/box/tools/jdk8u504-b01 ./mvnw spring-boot:run -Dspring-boot.run.profiles=db
# or, without starting the app:
JAVA_HOME=/home/box/tools/jdk8u504-b01 ./mvnw flyway:migrate \
  -Dflyway.url="$SPRING_DATASOURCE_URL" \
  -Dflyway.user="$SPRING_DATASOURCE_USERNAME" \
  -Dflyway.password="$SPRING_DATASOURCE_PASSWORD"
```

`spring.flyway.enabled=${FLYWAY_ENABLED:false}` in `application.yml`. A non-empty
URL still enables Flyway unless `FLYWAY_ENABLED=false`. Live migrate recipe:
`../../deploy/scripts/migrate-verify.sh` (Docker required). JDBC import/publish
integration tests start `deploy/` PostgreSQL themselves.

## Import / publish (P2)

With PostgreSQL up:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/lineage
export SPRING_DATASOURCE_USERNAME=lineage
export SPRING_DATASOURCE_PASSWORD=change-me-local   # deploy/.env placeholder
JAVA_HOME=/home/box/tools/jdk8u504-b01 ./mvnw spring-boot:run
curl -sS -H 'Content-Type: application/json' -H 'X-CSRF-Token: dev' \
  --data-binary @../../fixtures/v1/import.json http://localhost:8080/api/imports
# 202 {"runId","status":"ready",...}
curl -sS -H 'Content-Type: application/json' -H 'X-CSRF-Token: dev' \
  --data '{"expectedActiveSnapshotId":null}' \
  http://localhost:8080/api/imports/{runId}/publish
```

Auth for this PR: `lineage.security.mode=open` (default) — no OIDC. Optional
`LINEAGE_SECURITY_MODE=demo-header` requires `X-Lineage-Demo-User`. POST import
and publish require `X-CSRF-Token` equal to `lineage.csrf.token` (default `dev`).
When `X-Embed-Groups` is present, import/publish also need `ingest` on the scope.
CORS allowlist is `lineage.cors.allowed-origins` (exact match; never echo Origin).
Prefer same-origin reverse-proxy. Do not invent production SSO.
