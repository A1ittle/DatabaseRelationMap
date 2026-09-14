# Dependency baseline (P0)

Recorded 2026-09-14 from official sources. Versions below are what this checkout
intends to use; lockfiles / Maven BOM pin the resolved graph.

PM stack (see [ADR 0001](adr/0001-java8-vue2-embedded-vs-handoff.md)); HANDOFF.md
still documents the design-default React/TS + Java 21 / Spring Boot 4.1 stack.

## Runtime stack (Vue 2 / Java 8)

| Component | Version | Source | Reason |
|---|---|---|---|
| Node.js | v22.23.2 (runtime on this machine) | local toolchain | Vite 4 + openapi-typescript 7 engines |
| npm | 10.9.8 | bundled with Node | lockfile installer for `apps/web` |
| Vue | ^2.7.16 (`vue@2`) | https://www.npmjs.com/package/vue | PM embedded-tool stack (ADR 0001); last Vue 2 line |
| vue-template-compiler | ^2.7.16 (must match `vue`) | https://www.npmjs.com/package/vue-template-compiler | required peer of `vue@2` SFC compile |
| Vite | ^4.5.14 | https://www.npmjs.com/package/vite | last Vite major with first-class Vue 2 plugin support |
| vite-plugin-vue2 | ^2.0.3 | https://www.npmjs.com/package/vite-plugin-vue2 | Vue 2 SFC in Vite 4; not a Vite React template |
| JDK | 8u504-b01 (Temurin) | `JAVA_HOME=/home/box/tools/jdk8u504-b01` | PM Java 8 requirement |
| Maven Wrapper distribution | 3.9.16 | https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/ | reproducible Maven without a global install |
| Spring Boot | 2.7.18 | Maven Central `spring-boot-starter-parent` (last 2.7.x line supporting Java 8) | last Boot line on Java 8 |
| PostgreSQL | 17 (SRE-owned, not installed in this task) | HANDOFF default | intended store; not a local P0 dependency |

Flyway is a compile/runtime dependency (`flyway-core`; Boot 2.7 has no `spring-boot-starter-flyway`) but
`spring.flyway.enabled=false` until a database exists. JDBC/Flyway auto-config
is excluded in P0 so the API starts without PostgreSQL.

## Design-gate toolchain

These tools validate the design package (`spec/v1`, `fixtures/v1`, `docs/`,
`reference/`). They do not implement lineage or SSO.

| Component | Version actually used | Source | Reason |
|---|---|---|---|
| Python | 3.13.5 (`/usr/bin/python3`) | local toolchain | runner for `tools/validate_design.py` |
| jsonschema | 4.26.0 | PyPI https://pypi.org/project/jsonschema/ (`jsonschema==4.26.0` in [tools/requirements.txt](../tools/requirements.txt)) | Draft 2020-12 + official OpenAPI 3.1 document schema; required by the design validator |
| jsonschema-specifications | 2025.9.1 | PyPI (jsonschema dependency) | meta-schemas used by jsonschema 4.26 |
| Node.js | v22.23.2 | local toolchain | runner for `verify-tree.mjs` and `evidence/probe-reference.mjs` |

Install the Python gate extra (PEP 668 hosts need `--user` / a venv):

```bash
python3 -m pip install --user -r tools/requirements.txt
python3 tools/validate_design.py
```

Evidence from this checkout: [evidence/implementation/](../evidence/implementation/)
(`validate_design.txt`, `verify_tree.txt`, `probe_reference.txt`; all `exit_code: 0`).

## OpenAPI client-gen toolchain

Types are generated from the design contract [spec/v1/openapi.json](../spec/v1/openapi.json)
into the Vue 2 app and **committed**, so a clone without running the generator
still has the types.

| Component | Version actually used | Source | Reason |
|---|---|---|---|
| openapi-typescript | 7.13.0 (`apps/web` devDependency, npm lockfile) | https://www.npmjs.com/package/openapi-typescript | official/common OpenAPI 3.0 & 3.1 → TypeScript types generator; types-only, no runtime HTTP client |
| TypeScript | 5.9.3 (`apps/web` devDependency, peer of openapi-typescript 7) | https://www.npmjs.com/package/typescript | required peer; the Vue 2 shell remains JavaScript |
| output | `apps/web/src/generated/openapi.d.ts` | generated 2026-09-14 | 14 paths / 32 schemas from OpenAPI 3.1.0 |

openapi-generator (Java CLI) was not used: it would pull a second JVM toolchain
and emit a full SDK the P0 Vue 2 JS shell does not call yet. Revisit if P3 needs
a generated fetch/axios client.

Regenerate (does not change product semantics):

```bash
cd apps/web
npm install
npm run generate:api
# openapi-typescript ../../spec/v1/openapi.json -o src/generated/openapi.d.ts
```

Commit the regenerated `openapi.d.ts` together with any `spec/v1/openapi.json`
change. Do not hand-edit the generated file.
