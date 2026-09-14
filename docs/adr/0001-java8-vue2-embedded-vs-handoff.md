# ADR 0001: Java 8 + Vue 2 embedded shell vs HANDOFF default stack

- Status: Accepted
- Date: 2026-09-14
- Stage: P0 empty stack

## Context

[HANDOFF.md](../../HANDOFF.md) records the design-default implementation stack:

- Frontend: React + TypeScript + Vite, React Flow (open-source core)
- Backend: Java 21 + Spring Boot 4.1.x + Maven + JDBC + Flyway
- Database: PostgreSQL 17
- Form factor: single-origin deployable web app

HANDOFF also says that if a later user provides a different stack, record an ADR
and a contract-compatible approach, then change the engineering skeleton without
changing confirmed product semantics.

Projects Manager directed P0 to ship an **embedded tool** empty stack, not the
HANDOFF default product shell:

- Backend: **Java 8** (source/target 1.8) + Spring Boot **2.7.x** (last line
  that supports Java 8) + Maven Wrapper
- Frontend: **Vue 2** (`vue@2`), iframe-friendly empty page
- Do not use React, a Vite React template, Java 21, or Spring Boot 3/4 for this
  checkout

## Decision

P0 engineering in `apps/web` and `apps/api` follows the PM stack:

1. `apps/web` is a Vue 2 empty embedded shell titled「程序血缘地图」. It is
   meant to sit inside a host iframe. It is not a full SPA chrome/router/shell.
2. `apps/api` compiles with JDK 8 (`JAVA_HOME=/home/box/tools/jdk8u504-b01`),
   Spring Boot 2.7.18, Maven Wrapper. Packages remain
   `domain/graph`, `application`, `infrastructure`, `interfaces`.
3. `GET /api/health` returns `{"status":"ok"}` with no database.
4. Datasource is env placeholders only. Flyway is on the classpath and
   **disabled** until SRE provides PostgreSQL 17.

HANDOFF.md, docs/07, spec/, fixtures/, and reference/ are **not** rewritten.
Those files keep the original product defaults and semantics. This ADR is the
explicit delta between design-default stack and the P0 code that actually
builds.

## Product semantics (unchanged)

Unchanged from HANDOFF / docs/07 / spec/v1, regardless of UI framework or JDK:

- Downstream-only lineage; Java display endpoint; main-tree vs cross-branch
  classification; evidence belongs to relations; fixed query snapshots;
  authorize on the server before traverse.
- Import JSON, OpenAPI paths, and storage design stay the contract.
- PostgreSQL 17 remains the intended store (SRE-owned). No secrets in git.
- No invented SSO. No real lineage data in P0.

Vue 2 vs React, and Java 8 vs 21, are runtime/tooling choices. They do not
change those rules.

## Consequences

- Local run and CI for this branch must use JDK 8 and Vue 2 build scripts.
- React Flow is not the P0 UI kit; graph rendering is deferred and may use a
  Vue 2-capable library later, still bound to the same projection contract.
- Spring Boot 2.7 APIs (javax.servlet, `spring-boot-starter-web`, Boot 2
  autoconfig names) apply until an enterprise target change forces an upgrade.
- Revisit this ADR if the enterprise runtime becomes Java 17/21 or the host
  application is no longer an embedded/iframe tool. A stack upgrade then needs
  a new ADR; product semantics still should not silently change.

## Alternatives considered

1. Keep HANDOFF React/TS + Java 21 / Spring Boot 4.1 empty stack — rejected for
   P0 because PM required Java 8 + Vue 2 embedded form factor.
2. Rewrite HANDOFF defaults to Java 8 / Vue 2 — rejected. HANDOFF is the design
   handoff; the code delta belongs here.
