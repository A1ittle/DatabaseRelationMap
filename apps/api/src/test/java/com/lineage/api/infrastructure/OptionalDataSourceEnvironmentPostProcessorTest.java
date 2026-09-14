package com.lineage.api.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class OptionalDataSourceEnvironmentPostProcessorTest {

	private final OptionalDataSourceEnvironmentPostProcessor processor = new OptionalDataSourceEnvironmentPostProcessor();

	@Test
	void excludesJdbcWhenUrlBlank() {
		MockEnvironment env = new MockEnvironment();
		env.setProperty("spring.datasource.url", "");
		env.setProperty("spring.flyway.enabled", "false");
		processor.postProcessEnvironment(env, new SpringApplication());
		String exclude = env.getProperty("spring.autoconfigure.exclude");
		assertTrue(exclude.contains("DataSourceAutoConfiguration"));
		assertTrue(exclude.contains("FlywayAutoConfiguration"));
		assertEquals("false", env.getProperty("spring.flyway.enabled"));
	}

	@Test
	void enablesFlywayWhenUrlSet() {
		MockEnvironment env = new MockEnvironment();
		env.setProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/lineage");
		env.setProperty("spring.flyway.enabled", "false");
		processor.postProcessEnvironment(env, new SpringApplication());
		assertEquals("true", env.getProperty("spring.flyway.enabled"));
		assertFalse(String.valueOf(env.getProperty("spring.autoconfigure.exclude"))
			.contains("DataSourceAutoConfiguration"));
	}

	@Test
	void respectsExplicitFlywayDisabledWhenUrlSet() {
		MockEnvironment env = new MockEnvironment();
		env.setProperty("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/lineage");
		env.setProperty("FLYWAY_ENABLED", "false");
		processor.postProcessEnvironment(env, new SpringApplication());
		assertEquals("false", env.getProperty("spring.flyway.enabled"));
	}

	@Test
	void springDatasourceUrlEnvAliasEnablesFlyway() {
		MockEnvironment env = new MockEnvironment();
		env.setProperty("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/lineage");
		processor.postProcessEnvironment(env, new SpringApplication());
		assertEquals("true", env.getProperty("spring.flyway.enabled"));
	}
}
