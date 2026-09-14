package com.lineage.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class LineageApiApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private Environment environment;

	@Test
	void contextLoads() {
	}

	@Test
	void healthReturnsOkWithoutDatabase() throws Exception {
		mockMvc.perform(get("/api/health"))
			.andExpect(status().isOk())
			.andExpect(content().json("{\"status\":\"ok\"}"));
	}

	@Test
	void flywayDisabledByDefaultWithoutDatasourceUrl() {
		assertEquals("false", environment.getProperty("spring.flyway.enabled"));
	}

	@Test
	void flywayLocationsDefaultToClasspathMigration() {
		assertEquals("classpath:db/migration", environment.getProperty("spring.flyway.locations"));
	}

	@Test
	void datasourceAndFlywayAutoConfigurationExcludedWithoutUrl() {
		String exclude = environment.getProperty("spring.autoconfigure.exclude");
		assertTrue(exclude != null && exclude.contains("DataSourceAutoConfiguration"));
		assertTrue(exclude.contains("FlywayAutoConfiguration"));
	}

}
