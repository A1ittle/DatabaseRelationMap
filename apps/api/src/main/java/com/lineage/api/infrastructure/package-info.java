/**
 * JDBC, Flyway, authorization, OIDC, and cache adapters.
 * PostgreSQL is owned by SRE; {@link com.lineage.api.infrastructure.OptionalDataSourceEnvironmentPostProcessor}
 * enables DataSource/Flyway only when {@code SPRING_DATASOURCE_URL} is set.
 */
package com.lineage.api.infrastructure;
