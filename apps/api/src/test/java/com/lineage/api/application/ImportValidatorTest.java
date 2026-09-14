package com.lineage.api.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class ImportValidatorTest {

	private final ImportValidator validator = new ImportValidator();
	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void fixtureHasNoErrorsAndRecordsJavaOutAndCycleWarnings() throws Exception {
		JsonNode fixture = loadFixture();
		List<ValidationIssue> issues = validator.validate(fixture);
		assertEquals(0, errorCount(issues), issues.toString());
		assertTrue(warningCount(issues) >= 1);
		assertTrue(hasCode(issues, "JAVA_OUT_EDGE"));
		assertTrue(hasCode(issues, "CYCLE"));
	}

	@Test
	void danglingEndpointIsError() throws Exception {
		ObjectNode fixture = (ObjectNode) loadFixture();
		((ObjectNode) fixture.get("relations").get(0)).put("target", "missing-object");
		List<ValidationIssue> issues = validator.validate(fixture);
		assertTrue(errorCount(issues) >= 1);
		assertTrue(hasCode(issues, "DANGLING_ENDPOINT"));
	}

	@Test
	void javaKindRequiredOnJavaAndForbiddenOnTable() throws Exception {
		ObjectNode fixture = (ObjectNode) loadFixture();
		ObjectNode java = findObject(fixture, "java-j");
		java.putNull("javaKind");
		ObjectNode table = findObject(fixture, "root");
		table.put("javaKind", "class");
		List<ValidationIssue> issues = validator.validate(fixture);
		assertTrue(hasCode(issues, "JAVA_KIND_REQUIRED"));
		assertTrue(hasCode(issues, "JAVA_KIND_FORBIDDEN"));
	}

	@Test
	void duplicateObjectIdIsError() throws Exception {
		ObjectNode fixture = (ObjectNode) loadFixture();
		((ObjectNode) fixture.get("objects").get(1)).put("id", "root");
		List<ValidationIssue> issues = validator.validate(fixture);
		assertTrue(hasCode(issues, "DUPLICATE_OBJECT_ID"));
	}

	@Test
	void unknownPropertyIsError() throws Exception {
		ObjectNode fixture = (ObjectNode) loadFixture();
		fixture.put("extra", "nope");
		List<ValidationIssue> issues = validator.validate(fixture);
		assertTrue(hasCode(issues, "UNKNOWN_PROPERTY"));
	}

	@Test
	void missingBatchKeyDoesNotPassSchema() {
		List<ValidationIssue> issues = validator.validate(mapper.createObjectNode());
		assertFalse(issues.isEmpty());
		assertTrue(errorCount(issues) >= 1);
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

	private static ObjectNode findObject(JsonNode fixture, String id) {
		for (JsonNode node : fixture.get("objects")) {
			if (id.equals(node.get("id").asText())) {
				return (ObjectNode) node;
			}
		}
		throw new IllegalArgumentException(id);
	}

	private static int errorCount(List<ValidationIssue> issues) {
		int n = 0;
		for (int i = 0; i < issues.size(); i++) {
			if (issues.get(i).isError()) {
				n++;
			}
		}
		return n;
	}

	private static int warningCount(List<ValidationIssue> issues) {
		return issues.size() - errorCount(issues);
	}

	private static boolean hasCode(List<ValidationIssue> issues, String code) {
		for (int i = 0; i < issues.size(); i++) {
			if (code.equals(issues.get(i).getCode())) {
				return true;
			}
		}
		return false;
	}
}
