# Dependency baseline (P0)

Recorded 2026-09-14 from official sources. Versions below are what this checkout
intends to use; lockfiles / Maven BOM pin the resolved graph.

PM stack (see [ADR 0001](adr/0001-java8-vue2-embedded-vs-handoff.md)); HANDOFF.md
still documents the design-default React/TS + Java 21 / Spring Boot 4.1 stack.

| Component | Version | Source |
|---|---|---|
| Node.js | v22.23.2 (runtime on this machine) | local toolchain |
| npm | 10.9.8 | bundled with Node |
| Vue | ^2.7.16 (`vue@2`) | https://www.npmjs.com/package/vue |
| vue-template-compiler | ^2.7.16 (must match `vue`) | https://www.npmjs.com/package/vue-template-compiler |
| Vite | ^4.5.14 | https://www.npmjs.com/package/vite |
| vite-plugin-vue2 | ^2.0.3 | https://www.npmjs.com/package/vite-plugin-vue2 |
| JDK | 8u504-b01 (Temurin) | `JAVA_HOME=/home/box/tools/jdk8u504-b01` |
| Maven Wrapper distribution | 3.9.16 | https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/ |
| Spring Boot | 2.7.18 | Maven Central `spring-boot-starter-parent` (last 2.7.x line supporting Java 8) |
| PostgreSQL | 17 (SRE-owned, not installed in this task) | HANDOFF default |

Flyway is a compile/runtime dependency (`flyway-core`; Boot 2.7 has no `spring-boot-starter-flyway`) but
`spring.flyway.enabled=false` until a database exists. JDBC/Flyway auto-config
is excluded in P0 so the API starts without PostgreSQL.
