# lineage-api

P0 empty Spring Boot 2.7.18 API (Java 8, Maven Wrapper).

```bash
JAVA_HOME=/home/box/tools/jdk8u504-b01 ./mvnw -DskipTests package
JAVA_HOME=/home/box/tools/jdk8u504-b01 ./mvnw spring-boot:run
curl http://localhost:8080/api/health
# {"status":"ok"}
```

Datasource is read from `SPRING_DATASOURCE_URL` / `USERNAME` / `PASSWORD`.
JDBC and Flyway auto-configuration is excluded in P0 so the process starts
without PostgreSQL. Flyway remains on the classpath (`spring.flyway.enabled=false`);
business migrations start in P2 after SRE provides PostgreSQL 17.
