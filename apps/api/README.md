# lineage-api

P0 empty Spring Boot 4.1.1 API (Java 21, Maven Wrapper).

```bash
./mvnw -DskipTests package
./mvnw spring-boot:run
curl http://localhost:8080/api/health
# {"status":"ok"}
```

Datasource is read from `SPRING_DATASOURCE_URL` / `USERNAME` / `PASSWORD`.
JDBC and Flyway auto-configuration is excluded in P0 so the process starts
without PostgreSQL. Flyway remains on the classpath (`spring.flyway.enabled=false`);
business migrations start in P2 after SRE provides PostgreSQL 17.
