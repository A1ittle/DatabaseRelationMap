package com.lineage.api.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;

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
import com.lineage.api.infrastructure.DeployPostgres;

/**
 * Real JDBC: import+publish fixture, then lineage query APIs. Does not mock JDBC.
 */
@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/lineage",
	"spring.datasource.username=lineage",
	"spring.datasource.password=change-me-local",
	"spring.flyway.enabled=true",
	"lineage.security.mode=open"
})
@AutoConfigureMockMvc
class QueryApiJdbcTest {

	static {
		DeployPostgres.ensureRunning();
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

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
		jdbc.update("UPDATE policy_revision SET revision = 0 WHERE singleton = true");
	}

	@Test
	void searchQueryChildrenProjectionPathAgainstPublishedFixture() throws Exception {
		publishFixture();

		MvcResult search = mockMvc.perform(get("/api/lineage/search").queryParam("q", "root"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].object.id").value("root"))
			.andExpect(jsonPath("$.items[0].action").value("recenter"))
			.andExpect(jsonPath("$.page.hasMore").value(false))
			.andExpect(jsonPath("$.requestId").isNotEmpty())
			.andReturn();
		assertFalse(mapper.readTree(search.getResponse().getContentAsByteArray()).get("items").isEmpty());

		JsonNode created = createQuery("root");
		assertEquals("root", created.get("seed").get("id").asText());
		assertEquals(5, created.get("stats").get("downstream").asInt());
		assertEquals(1, created.get("stats").get("javaTerminals").asInt());
		assertEquals(1, created.get("stats").get("leaves").asInt());
		assertEquals(4, created.get("stats").get("crossEdges").asInt());
		assertEquals("exact", created.get("stats").get("countStatus").asText());
		assertEquals("available", created.get("meta").get("treeStatus").asText());
		assertEquals("lineage-tree-v1", created.get("meta").get("algorithmVersion").asText());
		String qid = created.get("meta").get("queryId").asText();
		JsonNode firstNodes = created.get("projection").get("nodes");
		assertEquals(3, firstNodes.size());
		assertEquals("root", firstNodes.get(0).get("object").get("id").asText());

		mockMvc.perform(get("/api/lineage/queries/" + qid + "/children").queryParam("parentId", "root"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].object.id").value("view-a"))
			.andExpect(jsonPath("$.items[1].object.id").value("proc-s"))
			.andExpect(jsonPath("$.page.total").value(2))
			.andExpect(jsonPath("$.page.hasMore").value(false));

		mockMvc.perform(get("/api/lineage/queries/" + qid + "/path").queryParam("targetId", "java-j"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("found"))
			.andExpect(jsonPath("$.nodes[0].id").value("root"))
			.andExpect(jsonPath("$.nodes[1].id").value("java-j"))
			.andExpect(jsonPath("$.edges[0].relation.id").value("e07"))
			.andExpect(jsonPath("$.edges[0].kind").value("cross"))
			.andExpect(jsonPath("$.reason").isEmpty());

		String projectionBody = "{\"candidateIds\":[\"root\",\"view-a\"],\"selectedId\":\"view-a\","
			+ "\"types\":[\"table\",\"view\",\"procedure\",\"java\"],\"revealSelectedPath\":false,"
			+ "\"clientRevision\":1}";
		mockMvc.perform(post("/api/lineage/queries/" + qid + "/projection")
			.contentType(MediaType.APPLICATION_JSON)
			.header("X-CSRF-Token", "dev")
			.content(projectionBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.clientRevision").value(1))
			.andExpect(jsonPath("$.nodes.length()").value(2))
			.andExpect(jsonPath("$.matchedDownstream").value(5))
			.andExpect(jsonPath("$.renderLimited").value(false));

		mockMvc.perform(get("/api/lineage/search").queryParam("q", "view").queryParam("queryId", qid))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].object.id").value("view-a"))
			.andExpect(jsonPath("$.items[0].action").value("reveal"));

		mockMvc.perform(get("/api/lineage/queries/" + qid + "/overview"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isArray())
			.andExpect(jsonPath("$.page.hasMore").value(false));

		mockMvc.perform(get("/api/lineage/queries/" + qid + "/relations/e02/evidence"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.relationId").value("e02"))
			.andExpect(jsonPath("$.items[0].id").value("ev-demo"));
	}

	@Test
	void unauthorizedAndForgedIdsDoNotLeak() throws Exception {
		publishFixture();
		jdbc.update("INSERT INTO scope_grant (scope_id, group_id, permission) VALUES ('demo', 'g-view', 'view')");
		jdbc.update("INSERT INTO object_grant (object_id, group_id, effect) VALUES ('java-j', 'g-view', 'deny')");

		mockMvc.perform(get("/api/lineage/search").queryParam("q", "java-j").header("X-Embed-Groups", "g-view"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(0));

		JsonNode created = createQuery("root", "g-view");
		String qid = created.get("meta").get("queryId").asText();
		assertEquals(4, created.get("stats").get("downstream").asInt());

		MvcResult deniedPath = mockMvc.perform(get("/api/lineage/queries/" + qid + "/path")
			.queryParam("targetId", "java-j")
			.header("X-Embed-Groups", "g-view"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"))
			.andExpect(jsonPath("$.retryable").value(false))
			.andReturn();
		MvcResult forgedPath = mockMvc.perform(get("/api/lineage/queries/" + qid + "/path")
			.queryParam("targetId", "forged-object")
			.header("X-Embed-Groups", "g-view"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"))
			.andReturn();
		JsonNode denied = mapper.readTree(deniedPath.getResponse().getContentAsByteArray());
		JsonNode forged = mapper.readTree(forgedPath.getResponse().getContentAsByteArray());
		assertEquals(denied.get("code").asText(), forged.get("code").asText());
		assertEquals(denied.get("message").asText(), forged.get("message").asText());

		mockMvc.perform(post("/api/lineage/queries/" + qid + "/projection")
			.contentType(MediaType.APPLICATION_JSON)
			.header("X-CSRF-Token", "dev")
			.header("X-Embed-Groups", "g-view")
			.content("{\"candidateIds\":[\"root\",\"java-j\"],\"selectedId\":\"root\","
				+ "\"types\":[\"table\",\"view\",\"procedure\",\"java\"],\"revealSelectedPath\":false,"
				+ "\"clientRevision\":0}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		mockMvc.perform(post("/api/lineage/queries")
			.contentType(MediaType.APPLICATION_JSON)
			.header("X-CSRF-Token", "dev")
			.header("X-Embed-Groups", "g-view")
			.content("{\"seedId\":\"java-j\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_SEED"));

		mockMvc.perform(post("/api/lineage/queries")
			.contentType(MediaType.APPLICATION_JSON)
			.header("X-CSRF-Token", "dev")
			.header("X-Embed-Groups", "g-view")
			.content("{\"seedId\":\"does-not-exist\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_SEED"));

		mockMvc.perform(get("/api/lineage/queries/" + qid + "/children")
			.queryParam("parentId", "forged-object")
			.header("X-Embed-Groups", "g-view"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void missingCsrfOnCreateQueryIsRejected() throws Exception {
		publishFixture();
		mockMvc.perform(post("/api/lineage/queries")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"seedId\":\"root\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
	}

	@Test
	void searchWithoutPublishedSnapshotIsLineageNotCollected() throws Exception {
		mockMvc.perform(get("/api/lineage/search").queryParam("q", "root"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("LINEAGE_NOT_COLLECTED"));
	}

	@Test
	void unknownQueryIdIsExpired() throws Exception {
		publishFixture();
		mockMvc.perform(get("/api/lineage/queries/missing-query/path").queryParam("targetId", "java-j"))
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.code").value("QUERY_EXPIRED"));
	}

	@Test
	void childrenPaginationIsStable() throws Exception {
		publishFixture();
		String qid = createQuery("root").get("meta").get("queryId").asText();
		MvcResult first = mockMvc.perform(get("/api/lineage/queries/" + qid + "/children")
			.queryParam("parentId", "root")
			.queryParam("limit", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].object.id").value("view-a"))
			.andExpect(jsonPath("$.page.hasMore").value(true))
			.andReturn();
		String cursor = mapper.readTree(first.getResponse().getContentAsByteArray()).get("page").get("nextCursor")
			.asText();
		mockMvc.perform(get("/api/lineage/queries/" + qid + "/children")
			.queryParam("parentId", "root")
			.queryParam("limit", "1")
			.queryParam("cursor", cursor))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].object.id").value("proc-s"))
			.andExpect(jsonPath("$.page.hasMore").value(false));
	}

	private JsonNode createQuery(String seedId) throws Exception {
		return createQuery(seedId, null);
	}

	private JsonNode createQuery(String seedId, String groups) throws Exception {
		org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req = post("/api/lineage/queries")
			.contentType(MediaType.APPLICATION_JSON)
			.header("X-CSRF-Token", "dev")
			.content("{\"seedId\":\"" + seedId + "\"}");
		if (groups != null) {
			req.header("X-Embed-Groups", groups);
		}
		MvcResult result = mockMvc.perform(req)
			.andExpect(status().isCreated())
			.andReturn();
		return mapper.readTree(result.getResponse().getContentAsByteArray());
	}

	private void publishFixture() throws Exception {
		JsonNode fixture = loadFixture();
		MvcResult created = mockMvc.perform(post("/api/imports")
			.contentType(MediaType.APPLICATION_JSON)
			.header("X-CSRF-Token", "dev")
			.content(mapper.writeValueAsBytes(fixture)))
			.andExpect(status().isAccepted())
			.andReturn();
		String runId = mapper.readTree(created.getResponse().getContentAsByteArray()).get("runId").asText();
		mockMvc.perform(post("/api/imports/" + runId + "/publish")
			.contentType(MediaType.APPLICATION_JSON)
			.header("X-CSRF-Token", "dev")
			.content("{\"expectedActiveSnapshotId\":null}"))
			.andExpect(status().isOk());
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
