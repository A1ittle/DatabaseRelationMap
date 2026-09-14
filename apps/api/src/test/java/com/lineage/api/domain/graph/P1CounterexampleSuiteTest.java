package com.lineage.api.domain.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * P1 counterexample acceptance. Expected values are hand-authored literals
 * from {@code fixtures/v1/counterexamples}; generator output is never the
 * oracle. The stub implementation is supposed to fail these assertions.
 */
class P1CounterexampleSuiteTest {

	private final LineageGraphAlgorithms algorithms = LineageGraphAlgorithmsFactory.create();

	@Test
	@DisplayName("1. parallel relations: both ids kept; derives is tree, reads is cross")
	void parallelRelationsPreservedAndClassified() throws Exception {
		JsonNode fixture = CounterexampleFixtures.counterexample("parallel-relations.json");
		LineageGraph graph = CounterexampleFixtures.graph(fixture);
		ClassificationResult result = algorithms.classify(graph, fixture.get("seedId").asText(),
			CounterexampleFixtures.authorized(fixture, graph));

		assertClassification(fixture.get("expected"), result);

		Set<String> classified = union(result.getTreeIds(), result.getCrossIds());
		assertTrue(classified.contains("e-reads"), "reads parallel edge must be classified");
		assertTrue(classified.contains("e-derives"), "derives parallel edge must be classified");
		assertFalse(result.getTreeIds().contains("e-reads") && result.getCrossIds().contains("e-reads"),
			"tree and cross must be disjoint");
		assertEquals("e-derives", result.getParentEdgeIds().get("child"));
		assertTrue(result.getTreeIds().contains("e-derives"));
		assertTrue(result.getCrossIds().contains("e-reads"));
	}

	@Test
	@DisplayName("2. self-loop preprocessing: loop excluded before indegree; child still ranked")
	void selfLoopRemovedBeforeIndegree() throws Exception {
		JsonNode fixture = CounterexampleFixtures.counterexample("self-loop.json");
		LineageGraph graph = CounterexampleFixtures.graph(fixture);
		ClassificationResult result = algorithms.classify(graph, "seed",
			CounterexampleFixtures.authorized(fixture, graph));

		assertClassification(fixture.get("expected"), result);
		assertTrue(result.getExcludedRelationIds().contains("e-loop"));
		assertFalse(result.getTreeIds().contains("e-loop"));
		assertFalse(result.getParentEdgeIds().containsValue("e-loop"));
		assertFalse(result.getParentEdgeIds().containsKey("seed"), "root must have no parent");
		assertEquals(Integer.valueOf(1), result.getLayoutRank().get("child"));
		assertEquals("e-seed-child", result.getParentEdgeIds().get("child"));
	}

	@Test
	@DisplayName("3. cycle tail: no force-rewired parents; tail stays in reach")
	void cycleDoesNotForceRewireAndKeepsTail() throws Exception {
		JsonNode fixture = CounterexampleFixtures.counterexample("cycle-tail.json");
		LineageGraph graph = CounterexampleFixtures.graph(fixture);
		ClassificationResult result = algorithms.classify(graph, "seed",
			CounterexampleFixtures.authorized(fixture, graph));

		assertClassification(fixture.get("expected"), result);
		assertEquals(TreeStatus.UNAVAILABLE_CYCLE, result.getTreeStatus());
		assertTrue(result.getParentEdgeIds().isEmpty(), "cycles must not be force-rewired into parent edges");
		assertTrue(result.getTreeIds().isEmpty());
		assertTrue(result.getCrossIds().isEmpty());
		assertTrue(result.getReach().contains("tail"));
		assertEquals(Integer.valueOf(3), result.getMinHops().get("tail"));

		JsonNode pathSpec = fixture.get("pathQueries").get(0);
		PathResult path = algorithms.shortestPath(graph, pathSpec.get("from").asText(),
			pathSpec.get("to").asText(), CounterexampleFixtures.authorized(fixture, graph));
		assertPath(pathSpec, path);
	}

	@Test
	@DisplayName("4. reorder stability: parent/tree/cross match hand-authored expected in both orders")
	void sameGraphReorderDoesNotChangeParentTreeCross() throws Exception {
		JsonNode fixture = CounterexampleFixtures.counterexample("reorder-stability.json");
		LineageGraph original = CounterexampleFixtures.graph(fixture);
		LineageGraph reversed = CounterexampleFixtures.reversed(original);
		Set<String> authorized = CounterexampleFixtures.authorized(fixture, original);

		ClassificationResult a = algorithms.classify(original, "seed", authorized);
		ClassificationResult b = algorithms.classify(reversed, "seed", authorized);

		assertClassification(fixture.get("expected"), a);
		assertClassification(fixture.get("expected"), b);
		assertEquals(a.getParentEdgeIds(), b.getParentEdgeIds());
		assertEquals(a.getTreeIds(), b.getTreeIds());
		assertEquals(a.getCrossIds(), b.getCrossIds());
		assertEquals(a.getMinHops(), b.getMinHops());
		assertEquals("e-left-join", a.getParentEdgeIds().get("join"));
	}

	@Test
	@DisplayName("5. Java out-edges truncated for display BFS; fact ids retained on the input graph")
	void javaOutEdgesTruncatedFactsRetained() throws Exception {
		JsonNode fixture = CounterexampleFixtures.counterexample("java-out-edges.json");
		LineageGraph graph = CounterexampleFixtures.graph(fixture);
		Set<String> factIdsBefore = relationIds(graph);

		ClassificationResult result = algorithms.classify(graph, "seed",
			CounterexampleFixtures.authorized(fixture, graph));

		assertClassification(fixture.get("expected"), result);
		assertTrue(result.getReach().contains("java-j"));
		assertFalse(result.getReach().contains("leaked"), "must not traverse Java out-edges");
		assertTrue(result.getExcludedRelationIds().contains("e-java-leaked"));
		assertEquals(factIdsBefore, relationIds(graph), "classify must not delete facts");
		assertNotNull(graph.relation("e-java-leaked"));
		assertNull(result.getMinHops().get("leaked"));
	}

	@Test
	@DisplayName("6. hidden intermediate: denied node is not walked; tail is not spliced back")
	void hiddenIntermediateDoesNotBridgeTail() throws Exception {
		JsonNode fixture = CounterexampleFixtures.counterexample("hidden-intermediate.json");
		LineageGraph graph = CounterexampleFixtures.graph(fixture);
		Set<String> authorized = CounterexampleFixtures.authorized(fixture, graph);
		assertFalse(authorized.contains("hidden"));
		assertTrue(authorized.contains("tail"));

		ClassificationResult result = algorithms.classify(graph, "seed", authorized);
		assertClassification(fixture.get("expected"), result);

		for (JsonNode forbidden : fixture.get("mustNotReach")) {
			assertFalse(result.getReach().contains(forbidden.asText()),
				"must not reach " + forbidden.asText() + " via a hidden intermediate");
		}
		Set<String> classified = union(result.getTreeIds(), result.getCrossIds());
		for (JsonNode forbidden : fixture.get("mustNotClassifyRelationIds")) {
			assertFalse(classified.contains(forbidden.asText()),
				"must not classify " + forbidden.asText());
		}

		JsonNode pathSpec = fixture.get("pathQueries").get(0);
		PathResult path = algorithms.shortestPath(graph, "seed", "tail", authorized);
		assertPath(pathSpec, path);
	}

	@Test
	@DisplayName("7. path unknown: 257-object chain is unknown/PATH_LENGTH_LIMIT, not not_found")
	void pathLengthLimitReturnsUnknown() throws Exception {
		JsonNode fixture = CounterexampleFixtures.counterexample("path-unknown.json");
		int objectCount = fixture.get("objectCount").asInt();
		LineageGraph graph = CounterexampleFixtures.chain(
			objectCount,
			fixture.get("idPrefix").asText(),
			fixture.get("edgePrefix").asText(),
			fixture.get("rel").asText());
		Set<String> authorized = new LinkedHashSet<String>();
		for (GraphObject object : graph.getObjects()) {
			authorized.add(object.getId());
		}

		PathResult path = algorithms.shortestPath(graph, fixture.get("seedId").asText(),
			fixture.get("targetId").asText(), authorized);

		JsonNode expected = fixture.get("expectedPath");
		assertEquals(PathStatus.fromWire(expected.get("status").asText()), path.getStatus());
		assertEquals(PathReason.PATH_LENGTH_LIMIT, path.getReason());
		assertTrue(path.getNodeIds().isEmpty());
		assertTrue(path.getEdgeIds().isEmpty());
		assertFalse(path.getStatus() == PathStatus.NOT_FOUND,
			"length cap must not be reported as not_found");
	}

	@Test
	@DisplayName("alignment hook: fixtures/v1/import.json vs expected.json (seed/reach/tree/cross/minHops/parent)")
	void importFixtureAlignsWithHandAuthoredExpected() throws Exception {
		LineageGraph graph = CounterexampleFixtures.importFixtureGraph();
		JsonNode expected = CounterexampleFixtures.json("fixtures/v1/expected.json");
		Set<String> authorized = new LinkedHashSet<String>();
		for (GraphObject object : graph.getObjects()) {
			authorized.add(object.getId());
		}

		ClassificationResult result = algorithms.classify(graph, expected.get("seedId").asText(),
			authorized);

		assertEquals(CounterexampleFixtures.stringSet(expected.get("reach")), result.getReach());
		assertEquals(CounterexampleFixtures.stringSet(expected.get("treeIds")), result.getTreeIds());
		assertEquals(CounterexampleFixtures.stringSet(expected.get("crossIds")), result.getCrossIds());
		assertEquals(CounterexampleFixtures.intMap(expected.get("minHops")), result.getMinHops());
		assertEquals(CounterexampleFixtures.stringMap(expected.get("parentEdgeIds")),
			result.getParentEdgeIds());
		assertEquals(CounterexampleFixtures.stringSet(expected.get("excludedRelationIds")),
			result.getExcludedRelationIds());
		assertEquals(TreeStatus.AVAILABLE, result.getTreeStatus());

		JsonNode pathToJava = expected.get("pathToJava");
		PathResult path = algorithms.shortestPath(graph, "root", "java-j", authorized);
		assertEquals(PathStatus.FOUND, path.getStatus());
		assertEquals(toStringList(pathToJava.get("nodeIds")), path.getNodeIds());
		assertEquals(toStringList(pathToJava.get("edgeIds")), path.getEdgeIds());
	}

	private static void assertClassification(JsonNode expected, ClassificationResult result) {
		assertEquals(TreeStatus.fromWire(expected.get("treeStatus").asText()), result.getTreeStatus());
		assertEquals(CounterexampleFixtures.stringSet(expected.get("reach")), result.getReach());
		assertEquals(CounterexampleFixtures.intMap(expected.get("minHops")), result.getMinHops());
		assertEquals(CounterexampleFixtures.intMap(expected.get("layoutRank")), result.getLayoutRank());
		assertEquals(CounterexampleFixtures.stringMap(expected.get("parentEdgeIds")),
			result.getParentEdgeIds());
		assertEquals(CounterexampleFixtures.stringSet(expected.get("treeIds")), result.getTreeIds());
		assertEquals(CounterexampleFixtures.stringSet(expected.get("crossIds")), result.getCrossIds());
		assertEquals(CounterexampleFixtures.stringSet(expected.get("excludedRelationIds")),
			result.getExcludedRelationIds());
	}

	private static void assertPath(JsonNode expected, PathResult path) {
		assertEquals(PathStatus.fromWire(expected.get("status").asText()), path.getStatus());
		if (expected.get("reason") == null || expected.get("reason").isNull()) {
			assertNull(path.getReason());
		}
		else {
			assertEquals(PathReason.fromWire(expected.get("reason").asText()), path.getReason());
		}
		assertEquals(toStringList(expected.get("nodeIds")), path.getNodeIds());
		assertEquals(toStringList(expected.get("edgeIds")), path.getEdgeIds());
	}

	private static List<String> toStringList(JsonNode array) {
		List<String> values = new ArrayList<String>();
		if (array == null || array.isNull()) {
			return values;
		}
		for (JsonNode item : array) {
			values.add(item.asText());
		}
		return values;
	}

	private static Set<String> union(Set<String> left, Set<String> right) {
		Set<String> values = new HashSet<String>(left);
		values.addAll(right);
		return values;
	}

	private static Set<String> relationIds(LineageGraph graph) {
		Set<String> ids = new LinkedHashSet<String>();
		for (GraphRelation relation : graph.getRelations()) {
			ids.add(relation.getId());
		}
		return ids;
	}
}
