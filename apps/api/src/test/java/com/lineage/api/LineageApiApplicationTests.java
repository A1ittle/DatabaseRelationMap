package com.lineage.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
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
	void lineageSearchWithoutDatabaseIsUnavailable() throws Exception {
		mockMvc.perform(get("/api/lineage/search").queryParam("q", "root"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("TEMPORARILY_UNAVAILABLE"))
			.andExpect(jsonPath("$.retryable").value(true));
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

	@Test
	void corsAllowsExactLocalViteOrigin() throws Exception {
		mockMvc.perform(get("/api/health").header("Origin", "http://127.0.0.1:5173"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5173"));
	}

	@Test
	void corsDoesNotEchoUnknownOrigin() throws Exception {
		mockMvc.perform(get("/api/health").header("Origin", "https://evil.example"))
			.andExpect(status().isOk())
			.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	@Test
	void corsSameOriginWithoutOriginHeaderNeedsNoAcao() throws Exception {
		mockMvc.perform(get("/api/health"))
			.andExpect(status().isOk())
			.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	@Test
	void corsPreflightWorksForAllowedOrigin() throws Exception {
		mockMvc.perform(options("/api/imports")
			.header("Origin", "http://localhost:5173")
			.header("Access-Control-Request-Method", "POST")
			.header("Access-Control-Request-Headers", "X-CSRF-Token"))
			.andExpect(status().isNoContent())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
	}

	@Test
	void corsPreflightDoesNotEchoUnknownOrigin() throws Exception {
		mockMvc.perform(options("/api/imports")
			.header("Origin", "https://evil.example")
			.header("Access-Control-Request-Method", "POST"))
			.andExpect(status().isNoContent())
			.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	@Test
	void wrongCsrfTokenIsRejected() throws Exception {
		mockMvc.perform(post("/api/imports")
			.contentType(MediaType.APPLICATION_JSON)
			.header("X-CSRF-Token", "wrong")
			.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
	}

}
