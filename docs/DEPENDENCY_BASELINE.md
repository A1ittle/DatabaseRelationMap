# Dependency baseline (P0)

Recorded 2026-09-14 from official sources. Versions below are what this checkout
intends to use; lockfiles / Maven BOM pin the resolved graph.

| Component | Version | Source |
|---|---|---|
| Node.js | v22.23.2 (runtime on this machine) | local toolchain |
| npm | 10.9.8 | bundled with Node |
| Vite | ^8.3.0 (lockfile pins install) | https://www.npmjs.com/package/vite |
| React / react-dom | ^19.2.8 | https://www.npmjs.com/package/react |
| TypeScript | ~6.0.2 | https://www.npmjs.com/package/typescript |
| JDK | 21.0.12.1+1 (Temurin) | `JAVA_HOME=/home/box/tools/jdk-21.0.12.1+1` |
| Maven Wrapper distribution | 3.9.16 | https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/ |
| Spring Boot | 4.1.1 | Maven Central `spring-boot-starter-parent` (start.spring.io 4.1.1.RELEASE maps to artifact `4.1.1`) |
| PostgreSQL | 17 (SRE-owned, not installed in this task) | HANDOFF default |

React Flow is deferred to later frontend work. Flyway is a compile/runtime
dependency but `spring.flyway.enabled=false` until a database exists.
