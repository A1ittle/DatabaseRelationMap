package com.lineage.api.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lineage.api.infrastructure.DeployPostgres;

/**
 * Real JDBC against deploy/ PostgreSQL 17. Does not mock the database.
 */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/lineage",
	"spring.datasource.username=lineage",
	"spring.datasource.password=change-me-local",
	"spring.flyway.enabled=true",
	"lineage.security.mode=open"
})
@AutoConfigureMockMvc
class ImportPublishJdbcTest {

	static {
		DeployPostgres.ensureRunning();
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ImportService importService;

	@Autowired
	private ObjectMapper mapper;

	@BeforeAll
	static void postgresUp() {
		DeployPostgres.ensureRunning();
	}

	@BeforeEach
	void truncate() {
		jdbc.execute("TRUNCATE TABLE catalog_scope, ingest_run, snapshot, object_identity, object_version, "
			+ "evidence, relation_version, relation_evidence, quality_issue, scope_grant, object_grant "
			+ "RESTART IDENTITY CASCADE");
	}

	@Test
	void importFixturePublishDataSurvivesAndGetShowsPublished() throws Exception {
		JsonNode fixture = loadFixture();
		int objectCount = fixture.get("objects").size();
		int relationCount = fixture.get("relations").size();

		MvcResult created = mockMvc.perform(post("/api/imports")
			.contentType(MediaType.APPLICATION_JSON)
			.header("X-CSRF-Token", "demo")
			.content(mapper.writeValueAsBytes(fixture)))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value("ready"))
			.andExpect(jsonPath("$.errorCount").value(0))
			.andExpect(jsonPath("$.snapshotId").isNotEmpty())
			.andReturn();
		JsonNode createdJson = mapper.readTree(created.getResponse().getContentAsByteArray());
		String runId = createdJson.get("runId").asText();
		String snapshotId = createdJson.get("snapshotId").asText();

		mockMvc.perform(post("/api/imports/" + runId + "/publish")
			.contentType(MediaType.APPLICATION_JSON)
			.header("X-CSRF-Token", "demo")
			.content("{\"expectedActiveSnapshotId\":null}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.snapshotId").value(snapshotId))
			.andExpect(jsonPath("$.publishedAt").isNotEmpty());

		mockMvc.perform(get("/api/imports/" + runId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.run.status").value("published"))
			.andExpect(jsonPath("$.run.snapshotId").value(snapshotId))
			.andExpect(jsonPath("$.page.hasMore").value(false));

		assertEquals(snapshotId,
			jdbc.queryForObject("SELECT active_snapshot_id FROM catalog_scope WHERE scope_id = ?", String.class,
				"demo"));
		assertEquals(Integer.valueOf(objectCount),
			jdbc.queryForObject("SELECT COUNT(*) FROM object_version WHERE snapshot_id = ?", Integer.class,
				snapshotId));
		assertEquals(Integer.valueOf(relationCount),
			jdbc.queryForObject("SELECT COUNT(*) FROM relation_version WHERE snapshot_id = ?", Integer.class,
				snapshotId));
		assertEquals(Integer.valueOf(1), jdbc.queryForObject(
			"SELECT COUNT(*) FROM evidence WHERE snapshot_id = ? AND evidence_id = ?", Integer.class, snapshotId,
			"ev-demo"));
	}

	@Test
	void sameBatchKeySamePayloadIsIdempotent() throws Exception {
		JsonNode fixture = loadFixture();
		String first = importAccepted(fixture).get("runId").asText();
		String second = importAccepted(fixture).get("runId").asText();
		assertEquals(first, second);
		assertEquals(Integer.valueOf(1),
			jdbc.queryForObject("SELECT COUNT(*) FROM ingest_run WHERE scope_id = ? AND batch_key = ?",
				Integer.class, "demo", "demo-001"));
	}

	@Test
	void sameBatchKeyDifferentPayloadIsImportInvalid() throws Exception {
		JsonNode fixture = loadFixture();
		importAccepted(fixture);
		ObjectNode mutated = fixture.deepCopy();
		mutated.put("sourceVersion", "other");
		MvcResult result = mockMvc.perform(post("/api/imports")
			.contentType(MediaType.APPLICATION_JSON)
			.header("X-CSRF-Token", "demo")
			.content(mapper.writeValueAsBytes(mutated)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("IMPORT_INVALID"))
			.andExpect(jsonPath("$.retryable").value(false))
			.andReturn();
		assertNotNull(mapper.readTree(result.getResponse().getContentAsByteArray()).get("requestId"));
	}

	@Test
	void failedImportLeavesActiveSnapshotUnchanged() throws Exception {
		JsonNode fixture = loadFixture();
		JsonNode ready = importAccepted(fixture);
		String snapshotId = ready.get("snapshotId").asText();
		mockMvc.perform(post("/api/imports/" + ready.get("runId").asText() + "/publish")
			.contentType(MediaType.APPLICATION_JSON)
			.header("X-CSRF-Token", "demo")
			.content("{\"expectedActiveSnapshotId\":null}"))
			.andExpect(status().isOk());

		ObjectNode bad = fixture.deepCopy();
		bad.put("batchKey", "demo-failed");
		((ObjectNode) bad.get("relations").get(0)).put("target", "does-not-exist");
		JsonNode failed = importAccepted(bad);
		assertEquals("failed", failed.get("status").asText());
		assertTrue(failed.get("errorCount").asInt() >= 1);
		assertTrue(failed.get("snapshotId").isNull());

		mockMvc.perform(post("/api/imports/" + failed.get("runId").asText() + "/publish")
			.contentType(MediaType.APPLICATION_JSON)
			.header("X-CSRF-Token", "demo")
			.content("{\"expectedActiveSnapshotId\":\"" + snapshotId + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

		assertEquals(snapshotId,
			jdbc.queryForObject("SELECT active_snapshot_id FROM catalog_scope WHERE scope_id = ?", String.class,
				"demo"));
		assertEquals("published", jdbc.queryForObject("SELECT status FROM ingest_run WHERE run_id = ?",
			String.class, ready.get("runId").asText()));
		assertEquals("failed", jdbc.queryForObject("SELECT status FROM ingest_run WHERE run_id = ?", String.class,
			failed.get("runId").asText()));
	}

	@Test
	void casConflictWhenExpectedActiveDoesNotMatch() throws Exception {
		JsonNode fixture = loadFixture();
		JsonNode first = importAccepted(fixture);
		mockMvc.perform(post("/api/imports/" + first.get("runId").asText() + "/publish")
			.contentType(MediaType.APPLICATION_JSON)
			.header("X-CSRF-Token", "demo")
			.content("{\"expectedActiveSnapshotId\":null}"))
			.andExpect(status().isOk());

		ObjectNode secondBatch = fixture.deepCopy();
		secondBatch.put("batchKey", "demo-002");
		JsonNode second = importAccepted(secondBatch);
		mockMvc.perform(post("/api/imports/" + second.get("runId").asText() + "/publish")
			.contentType(MediaType.APPLICATION_JSON)
			.header("X-CSRF-Token", "demo")
			.content("{\"expectedActiveSnapshotId\":null}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PUBLISH_CONFLICT"))
			.andExpect(jsonPath("$.retryable").value(false));

		String active = jdbc.queryForObject("SELECT active_snapshot_id FROM catalog_scope WHERE scope_id = ?",
			String.class, "demo");
		assertEquals(first.get("snapshotId").asText(), active);
		assertNotEquals(second.get("snapshotId").asText(), active);
	}

	@Test
	void concurrentPublishOnlyOneWins() throws Exception {
		JsonNode fixture = loadFixture();
		final JsonNode a = importAccepted(fixture);
		ObjectNode other = fixture.deepCopy();
		other.put("batchKey", "demo-concurrent");
		final JsonNode b = importAccepted(other);

		final CountDownLatch start = new CountDownLatch(1);
		final CountDownLatch done = new CountDownLatch(2);
		final AtomicInteger wins = new AtomicInteger();
		final AtomicInteger conflicts = new AtomicInteger();
		final ObjectNode expectedNull = mapper.createObjectNode();
		expectedNull.putNull("expectedActiveSnapshotId");

		Runnable taskA = new Runnable() {
			@Override
			public void run() {
				publishRace(a.get("runId").asText(), expectedNull, start, done, wins, conflicts);
			}
		};
		Runnable taskB = new Runnable() {
			@Override
			public void run() {
				publishRace(b.get("runId").asText(), expectedNull, start, done, wins, conflicts);
			}
		};
		Thread ta = new Thread(taskA, "publish-a");
		Thread tb = new Thread(taskB, "publish-b");
		ta.start();
		tb.start();
		start.countDown();
		assertTrue(done.await(30, java.util.concurrent.TimeUnit.SECONDS));
		ta.join(5000L);
		tb.join(5000L);
		assertEquals(1, wins.get(), "exactly one publish must succeed");
		assertEquals(1, conflicts.get(), "the loser must be PUBLISH_CONFLICT");
		Integer published = jdbc.queryForObject("SELECT COUNT(*) FROM ingest_run WHERE status = 'published'",
			Integer.class);
		assertEquals(Integer.valueOf(1), published);
		assertNotNull(jdbc.queryForObject("SELECT active_snapshot_id FROM catalog_scope WHERE scope_id = ?",
			String.class, "demo"));
	}

	@Test
	void missingCsrfIsRejected() throws Exception {
		mockMvc.perform(post("/api/imports")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
	}

	@Test
	void unknownRunIsNotFound() throws Exception {
		mockMvc.perform(get("/api/imports/does-not-exist"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void issuesPaginationForFailedRun() throws Exception {
		JsonNode fixture = loadFixture();
		ObjectNode bad = fixture.deepCopy();
		bad.put("batchKey", "demo-issues");
		((ObjectNode) bad.get("relations").get(0)).put("target", "missing-a");
		((ObjectNode) bad.get("relations").get(1)).put("target", "missing-b");
		JsonNode failed = importAccepted(bad);
		assertEquals("failed", failed.get("status").asText());
		MvcResult page = mockMvc.perform(get("/api/imports/" + failed.get("runId").asText())
			.queryParam("limit", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.issues.length()").value(1))
			.andExpect(jsonPath("$.page.hasMore").value(true))
			.andExpect(jsonPath("$.page.nextCursor").isNotEmpty())
			.andReturn();
		String cursor = mapper.readTree(page.getResponse().getContentAsByteArray()).get("page").get("nextCursor")
			.asText();
		mockMvc.perform(get("/api/imports/" + failed.get("runId").asText())
			.queryParam("limit", "50")
			.queryParam("cursor", cursor))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page.hasMore").value(false));
		assertNull(failed.get("snapshotId").asText(null));
	}

	private void publishRace(String runId, JsonNode body, CountDownLatch start, CountDownLatch done,
			AtomicInteger wins, AtomicInteger conflicts) {
		try {
			start.await();
			importService.publish(runId, body);
			wins.incrementAndGet();
		}
		catch (ApiException ex) {
			if ("PUBLISH_CONFLICT".equals(ex.getCode())) {
				conflicts.incrementAndGet();
			}
			else {
				fail("unexpected ApiException " + ex.getCode() + " " + ex.getMessage());
			}
		}
		catch (Exception ex) {
			fail(ex);
		}
		finally {
			done.countDown();
		}
	}

	private JsonNode importAccepted(JsonNode body) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/imports")
			.contentType(MediaType.APPLICATION_JSON)
			.header("X-CSRF-Token", "demo")
			.content(mapper.writeValueAsBytes(body)))
			.andExpect(status().isAccepted())
			.andReturn();
		return mapper.readTree(result.getResponse().getContentAsByteArray());
	}

	private JsonNode loadFixture() throws Exception {
		InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("fixtures/v1/import.json");
		if (in == null) {
			throw new IllegalStateException("missing fixtures/v1/import.json");
		}
		try {
			return mapper.readTree(in);
		}
		finally {
			in.close();
		}
	}
}
