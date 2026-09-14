package com.lineage.api.domain.graph;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Loads hand-authored fixtures from {@code classpath:fixtures/v1/...}.
 */
final class CounterexampleFixtures {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private CounterexampleFixtures() {
	}

	static JsonNode json(String classpath) throws IOException {
		InputStream in = CounterexampleFixtures.class.getClassLoader().getResourceAsStream(classpath);
		if (in == null) {
			throw new IOException("Missing classpath resource: " + classpath);
		}
		try {
			return MAPPER.readTree(in);
		}
		finally {
			in.close();
		}
	}

	static JsonNode counterexample(String fileName) throws IOException {
		return json("fixtures/v1/counterexamples/" + fileName);
	}

	static LineageGraph graph(JsonNode fixture) {
		List<GraphObject> objects = new ArrayList<GraphObject>();
		for (JsonNode node : fixture.get("objects")) {
			objects.add(new GraphObject(
				node.get("id").asText(),
				node.get("type").asText(),
				textOrNull(node, "technicalName"),
				textOrNull(node, "javaKind")));
		}
		List<GraphRelation> relations = new ArrayList<GraphRelation>();
		for (JsonNode node : fixture.get("relations")) {
			relations.add(new GraphRelation(
				node.get("id").asText(),
				node.get("source").asText(),
				node.get("target").asText(),
				node.get("rel").asText(),
				node.path("sourceOrder").asInt(0)));
		}
		return new LineageGraph(objects, relations);
	}

	static LineageGraph importFixtureGraph() throws IOException {
		return graph(json("fixtures/v1/import.json"));
	}

	static Set<String> authorized(JsonNode fixture, LineageGraph graph) {
		JsonNode listed = fixture.get("authorizedObjectIds");
		if (listed == null || listed.isNull()) {
			Set<String> all = new LinkedHashSet<String>();
			for (GraphObject object : graph.getObjects()) {
				all.add(object.getId());
			}
			return all;
		}
		return stringSet(listed);
	}

	static Set<String> stringSet(JsonNode array) {
		Set<String> values = new LinkedHashSet<String>();
		if (array == null || array.isNull()) {
			return values;
		}
		for (JsonNode item : array) {
			values.add(item.asText());
		}
		return values;
	}

	static Map<String, Integer> intMap(JsonNode object) {
		Map<String, Integer> values = new LinkedHashMap<String, Integer>();
		if (object == null || object.isNull()) {
			return values;
		}
		Iterator<String> names = object.fieldNames();
		while (names.hasNext()) {
			String name = names.next();
			values.put(name, Integer.valueOf(object.get(name).asInt()));
		}
		return values;
	}

	static Map<String, String> stringMap(JsonNode object) {
		Map<String, String> values = new LinkedHashMap<String, String>();
		if (object == null || object.isNull()) {
			return values;
		}
		Iterator<String> names = object.fieldNames();
		while (names.hasNext()) {
			String name = names.next();
			values.put(name, object.get(name).asText());
		}
		return values;
	}

	static LineageGraph reversed(LineageGraph graph) {
		List<GraphObject> objects = new ArrayList<GraphObject>(graph.getObjects());
		List<GraphRelation> relations = new ArrayList<GraphRelation>(graph.getRelations());
		Collections.reverse(objects);
		Collections.reverse(relations);
		return new LineageGraph(objects, relations);
	}

	static LineageGraph chain(int objectCount, String idPrefix, String edgePrefix, String rel) {
		List<GraphObject> objects = new ArrayList<GraphObject>();
		List<GraphRelation> relations = new ArrayList<GraphRelation>();
		for (int i = 0; i < objectCount; i++) {
			String id = idPrefix + pad3(i);
			objects.add(new GraphObject(id, "table", id, null));
			if (i > 0) {
				String prev = idPrefix + pad3(i - 1);
				relations.add(new GraphRelation(edgePrefix + prev + "-" + id, prev, id, rel, 0));
			}
		}
		return new LineageGraph(objects, relations);
	}

	private static String pad3(int value) {
		if (value < 10) {
			return "00" + value;
		}
		if (value < 100) {
			return "0" + value;
		}
		return String.valueOf(value);
	}

	private static String textOrNull(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull()) {
			return null;
		}
		return value.asText();
	}
}
