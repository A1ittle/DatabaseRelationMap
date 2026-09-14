# lineage-api

Spring Boot 2.7.18 API (Java 8, Maven Wrapper). Graph algorithms in
`domain/graph`. Flyway V1 lives at `src/main/resources/db/migration/V1__storage.sql`.

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
`../../deploy/scripts/migrate-verify.sh` (Docker required; BLOCKED without it).
