package com.lineage.api.application;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON Schema-shaped checks plus semantic rules from docs/07: unique ids,
 * endpoints exist, evidence refs exist, javaKind, relation uniqueness.
 * Self-loops, directed cycles, and Java out-edges are warnings (saved, not failed).
 */
public final class ImportValidator {

	private static final Set<String> BATCH_FIELDS = setOf("batchKey", "scopeId", "sourceVersion",
		"capturedAt", "coverage", "mode", "objects", "relations", "evidence");
	private static final Set<String> OBJECT_FIELDS = setOf("id", "type", "namespace", "technicalName",
		"displayName", "system", "owner", "javaKind");
	private static final Set<String> RELATION_FIELDS = setOf("id", "source", "target", "rel", "sourceOrder",
		"evidenceIds");
	private static final Set<String> EVIDENCE_FIELDS = setOf("id", "state", "sourceRef", "observedAt",
		"description");
	private static final Set<String> OBJECT_TYPES = setOf("table", "view", "procedure", "java");
	private static final Set<String> RELATION_TYPES = setOf("reads", "writes", "calls", "derives");
	private static final Set<String> EVIDENCE_STATES = setOf("observed", "parsed", "inferred", "confirmed",
		"unknown");
	private static final Set<String> JAVA_KINDS = setOf("class", "job", "service");
	private static final Set<String> COVERAGES = setOf("complete", "partial", "unknown");

	public List<ValidationIssue> validate(JsonNode root) {
		List<ValidationIssue> issues = new ArrayList<ValidationIssue>();
		if (root == null || !root.isObject()) {
			issues.add(ValidationIssue.error("SCHEMA_INVALID", "/", "ImportBatch must be a JSON object"));
			return issues;
		}
		rejectUnknown(root, BATCH_FIELDS, "/", issues);
		requireString(root, "batchKey", "/", 1, 200, issues);
		requireString(root, "scopeId", "/", 1, 200, issues);
		requireString(root, "sourceVersion", "/", 1, 200, issues);
		requireDateTime(root, "capturedAt", "/", issues);
		requireEnum(root, "coverage", "/", COVERAGES, issues);
		requireEnum(root, "mode", "/", Collections.singleton("full"), issues);

		JsonNode objects = requireArray(root, "objects", "/", 1, 100000, issues);
		JsonNode relations = requireArray(root, "relations", "/", 0, 500000, issues);
		JsonNode evidence = requireArray(root, "evidence", "/", 0, 500000, issues);

		Map<String, JsonNode> objectById = new LinkedHashMap<String, JsonNode>();
		if (objects != null) {
			Set<String> seen = new HashSet<String>();
			for (int i = 0; i < objects.size(); i++) {
				String path = "/objects/" + i;
				JsonNode item = objects.get(i);
				if (!item.isObject()) {
					issues.add(ValidationIssue.error("SCHEMA_INVALID", path, "object must be a JSON object"));
					continue;
				}
				rejectUnknown(item, OBJECT_FIELDS, path, issues);
				String id = requireString(item, "id", path, 1, 200, issues);
				requireEnum(item, "type", path, OBJECT_TYPES, issues);
				String namespace = requireString(item, "namespace", path, 1, 300, issues);
				requireString(item, "technicalName", path, 1, 1000, issues);
				requireString(item, "displayName", path, 0, 1000, issues);
				requireString(item, "system", path, 1, 200, issues);
				requireNullableString(item, "owner", path, 200, issues);
				requireNullableEnum(item, "javaKind", path, JAVA_KINDS, issues);
				if (namespace != null && namespace.indexOf('/') < 0) {
					issues.add(ValidationIssue.error("NAMESPACE_MISSING_ENVIRONMENT", path + "/namespace",
						"namespace must include an environment segment"));
				}
				if (id != null) {
					if (!seen.add(id)) {
						issues.add(ValidationIssue.error("DUPLICATE_OBJECT_ID", path + "/id",
							"duplicate object id: " + id));
					}
					else {
						objectById.put(id, item);
					}
				}
				applyJavaKindRules(item, path, issues);
			}
		}

		Map<String, JsonNode> evidenceById = new LinkedHashMap<String, JsonNode>();
		if (evidence != null) {
			Set<String> seen = new HashSet<String>();
			for (int i = 0; i < evidence.size(); i++) {
				String path = "/evidence/" + i;
				JsonNode item = evidence.get(i);
				if (!item.isObject()) {
					issues.add(ValidationIssue.error("SCHEMA_INVALID", path, "evidence must be a JSON object"));
					continue;
				}
				rejectUnknown(item, EVIDENCE_FIELDS, path, issues);
				String id = requireString(item, "id", path, 1, 200, issues);
				requireEnum(item, "state", path, EVIDENCE_STATES, issues);
				requireString(item, "sourceRef", path, 0, 2000, issues);
				requireDateTime(item, "observedAt", path, issues);
				requireString(item, "description", path, 0, 4000, issues);
				if (id != null) {
					if (!seen.add(id)) {
						issues.add(ValidationIssue.error("DUPLICATE_EVIDENCE_ID", path + "/id",
							"duplicate evidence id: " + id));
					}
					else {
						evidenceById.put(id, item);
					}
				}
			}
		}

		List<Rel> rels = new ArrayList<Rel>();
		if (relations != null) {
			Set<String> seenIds = new HashSet<String>();
			Set<String> logicKeys = new HashSet<String>();
			for (int i = 0; i < relations.size(); i++) {
				String path = "/relations/" + i;
				JsonNode item = relations.get(i);
				if (!item.isObject()) {
					issues.add(ValidationIssue.error("SCHEMA_INVALID", path, "relation must be a JSON object"));
					continue;
				}
				rejectUnknown(item, RELATION_FIELDS, path, issues);
				String id = requireString(item, "id", path, 1, 200, issues);
				String source = requireString(item, "source", path, 1, 200, issues);
				String target = requireString(item, "target", path, 1, 200, issues);
				String rel = requireEnum(item, "rel", path, RELATION_TYPES, issues);
				requireInt(item, "sourceOrder", path, 0, Integer.MAX_VALUE, issues);
				JsonNode evidenceIds = requireArray(item, "evidenceIds", path, 1, 100, issues);
				if (id != null && !seenIds.add(id)) {
					issues.add(ValidationIssue.error("DUPLICATE_RELATION_ID", path + "/id",
						"duplicate relation id: " + id));
				}
				if (source != null && !objectById.containsKey(source)) {
					issues.add(ValidationIssue.error("DANGLING_ENDPOINT", path + "/source",
						"source object does not exist: " + source));
				}
				if (target != null && !objectById.containsKey(target)) {
					issues.add(ValidationIssue.error("DANGLING_ENDPOINT", path + "/target",
						"target object does not exist: " + target));
				}
				if (source != null && target != null && rel != null) {
					String logic = source + "\0" + target + "\0" + rel;
					if (!logicKeys.add(logic)) {
						issues.add(ValidationIssue.error("DUPLICATE_RELATION_LOGIC", path,
							"duplicate source/target/rel combination"));
					}
				}
				if (source != null && source.equals(target)) {
					issues.add(ValidationIssue.warning("SELF_LOOP", path,
						"self-loop is stored but excluded from display traversal"));
				}
				if (source != null && objectById.containsKey(source)
					&& "java".equals(text(objectById.get(source), "type"))) {
					issues.add(ValidationIssue.warning("JAVA_OUT_EDGE", path,
						"Java out-edge is stored but excluded from display traversal"));
				}
				if (evidenceIds != null) {
					Set<String> unique = new HashSet<String>();
					for (int e = 0; e < evidenceIds.size(); e++) {
						JsonNode ev = evidenceIds.get(e);
						String evPath = path + "/evidenceIds/" + e;
						if (!ev.isTextual()) {
							issues.add(ValidationIssue.error("SCHEMA_INVALID", evPath, "evidence id must be a string"));
							continue;
						}
						String evId = ev.asText();
						if (evId.length() < 1 || evId.length() > 200) {
							issues.add(ValidationIssue.error("SCHEMA_INVALID", evPath, "evidence id length out of range"));
						}
						if (!unique.add(evId)) {
							issues.add(ValidationIssue.error("DUPLICATE_EVIDENCE_REF", evPath,
								"evidenceIds must be unique"));
						}
						if (!evidenceById.containsKey(evId)) {
							issues.add(ValidationIssue.error("MISSING_EVIDENCE", evPath,
								"evidence id does not exist: " + evId));
						}
					}
				}
				if (id != null && source != null && target != null) {
					rels.add(new Rel(id, source, target, path));
				}
			}
		}

		if (hasDirectedCycle(rels)) {
			issues.add(ValidationIssue.warning("CYCLE", "/relations",
				"directed cycle present; stored as fact, tree classification unavailable"));
		}
		return issues;
	}

	private static void applyJavaKindRules(JsonNode item, String path, List<ValidationIssue> issues) {
		String type = text(item, "type");
		if (type == null) {
			return;
		}
		boolean hasKind = item.has("javaKind") && !item.get("javaKind").isNull();
		if ("java".equals(type)) {
			if (!hasKind) {
				issues.add(ValidationIssue.error("JAVA_KIND_REQUIRED", path + "/javaKind",
					"java objects must have javaKind"));
			}
		}
		else if (hasKind) {
			issues.add(ValidationIssue.error("JAVA_KIND_FORBIDDEN", path + "/javaKind",
				"non-java objects must have javaKind null"));
		}
	}

	private static boolean hasDirectedCycle(List<Rel> rels) {
		Map<String, List<String>> out = new HashMap<String, List<String>>();
		Set<String> nodes = new HashSet<String>();
		for (int i = 0; i < rels.size(); i++) {
			Rel r = rels.get(i);
			if (r.source.equals(r.target)) {
				continue;
			}
			nodes.add(r.source);
			nodes.add(r.target);
			List<String> list = out.get(r.source);
			if (list == null) {
				list = new ArrayList<String>();
				out.put(r.source, list);
			}
			list.add(r.target);
		}
		Set<String> visiting = new HashSet<String>();
		Set<String> done = new HashSet<String>();
		for (String node : nodes) {
			if (dfsCycle(node, out, visiting, done)) {
				return true;
			}
		}
		return false;
	}

	private static boolean dfsCycle(String node, Map<String, List<String>> out, Set<String> visiting,
			Set<String> done) {
		if (done.contains(node)) {
			return false;
		}
		if (visiting.contains(node)) {
			return true;
		}
		visiting.add(node);
		List<String> next = out.get(node);
		if (next != null) {
			for (int i = 0; i < next.size(); i++) {
				if (dfsCycle(next.get(i), out, visiting, done)) {
					return true;
				}
			}
		}
		visiting.remove(node);
		done.add(node);
		return false;
	}

	private static void rejectUnknown(JsonNode node, Set<String> allowed, String path,
			List<ValidationIssue> issues) {
		Iterator<String> names = node.fieldNames();
		while (names.hasNext()) {
			String name = names.next();
			if (!allowed.contains(name)) {
				String fieldPath = "/".equals(path) ? "/" + name : path + "/" + name;
				issues.add(ValidationIssue.error("UNKNOWN_PROPERTY", fieldPath, "unknown property: " + name));
			}
		}
	}

	private static String requireString(JsonNode parent, String field, String parentPath, int min, int max,
			List<ValidationIssue> issues) {
		String path = fieldPath(parentPath, field);
		if (!parent.has(field) || parent.get(field).isNull()) {
			issues.add(ValidationIssue.error("MISSING_FIELD", path, "required field missing: " + field));
			return null;
		}
		JsonNode node = parent.get(field);
		if (!node.isTextual()) {
			issues.add(ValidationIssue.error("SCHEMA_INVALID", path, field + " must be a string"));
			return null;
		}
		String value = node.asText();
		if (value.length() < min || value.length() > max) {
			issues.add(ValidationIssue.error("SCHEMA_INVALID", path, field + " length out of range"));
			return null;
		}
		return value;
	}

	private static void requireNullableString(JsonNode parent, String field, String parentPath, int max,
			List<ValidationIssue> issues) {
		String path = fieldPath(parentPath, field);
		if (!parent.has(field)) {
			issues.add(ValidationIssue.error("MISSING_FIELD", path, "required field missing: " + field));
			return;
		}
		JsonNode node = parent.get(field);
		if (node.isNull()) {
			return;
		}
		if (!node.isTextual()) {
			issues.add(ValidationIssue.error("SCHEMA_INVALID", path, field + " must be a string or null"));
			return;
		}
		if (node.asText().length() > max) {
			issues.add(ValidationIssue.error("SCHEMA_INVALID", path, field + " length out of range"));
		}
	}

	private static String requireEnum(JsonNode parent, String field, String parentPath, Set<String> allowed,
			List<ValidationIssue> issues) {
		String value = requireString(parent, field, parentPath, 1, 200, issues);
		if (value != null && !allowed.contains(value)) {
			issues.add(ValidationIssue.error("INVALID_ENUM", fieldPath(parentPath, field),
				field + " is not an allowed value"));
			return null;
		}
		return value;
	}

	private static void requireNullableEnum(JsonNode parent, String field, String parentPath, Set<String> allowed,
			List<ValidationIssue> issues) {
		String path = fieldPath(parentPath, field);
		if (!parent.has(field)) {
			issues.add(ValidationIssue.error("MISSING_FIELD", path, "required field missing: " + field));
			return;
		}
		JsonNode node = parent.get(field);
		if (node.isNull()) {
			return;
		}
		if (!node.isTextual() || !allowed.contains(node.asText())) {
			issues.add(ValidationIssue.error("INVALID_ENUM", path, field + " is not an allowed value"));
		}
	}

	private static void requireDateTime(JsonNode parent, String field, String parentPath,
			List<ValidationIssue> issues) {
		String value = requireString(parent, field, parentPath, 1, 200, issues);
		if (value == null) {
			return;
		}
		if (!isDateTime(value)) {
			issues.add(ValidationIssue.error("INVALID_DATETIME", fieldPath(parentPath, field),
				field + " must be an ISO-8601 date-time"));
		}
	}

	private static boolean isDateTime(String value) {
		try {
			Instant.parse(value);
			return true;
		}
		catch (DateTimeParseException ignored) {
			try {
				OffsetDateTime.parse(value);
				return true;
			}
			catch (DateTimeParseException e) {
				return false;
			}
		}
	}

	private static JsonNode requireArray(JsonNode parent, String field, String parentPath, int minItems,
			int maxItems, List<ValidationIssue> issues) {
		String path = fieldPath(parentPath, field);
		if (!parent.has(field) || parent.get(field).isNull()) {
			issues.add(ValidationIssue.error("MISSING_FIELD", path, "required field missing: " + field));
			return null;
		}
		JsonNode node = parent.get(field);
		if (!node.isArray()) {
			issues.add(ValidationIssue.error("SCHEMA_INVALID", path, field + " must be an array"));
			return null;
		}
		if (node.size() < minItems || node.size() > maxItems) {
			issues.add(ValidationIssue.error("SCHEMA_INVALID", path, field + " size out of range"));
		}
		return node;
	}

	private static void requireInt(JsonNode parent, String field, String parentPath, int min, int max,
			List<ValidationIssue> issues) {
		String path = fieldPath(parentPath, field);
		if (!parent.has(field) || parent.get(field).isNull()) {
			issues.add(ValidationIssue.error("MISSING_FIELD", path, "required field missing: " + field));
			return;
		}
		JsonNode node = parent.get(field);
		if (!node.isIntegralNumber() || node.isBigInteger()) {
			issues.add(ValidationIssue.error("SCHEMA_INVALID", path, field + " must be an integer"));
			return;
		}
		long v = node.longValue();
		if (v < min || v > max) {
			issues.add(ValidationIssue.error("SCHEMA_INVALID", path, field + " out of range"));
		}
	}

	private static String text(JsonNode node, String field) {
		if (node == null || !node.has(field) || node.get(field).isNull() || !node.get(field).isTextual()) {
			return null;
		}
		return node.get(field).asText();
	}

	private static String fieldPath(String parentPath, String field) {
		if ("/".equals(parentPath)) {
			return "/" + field;
		}
		return parentPath + "/" + field;
	}

	private static Set<String> setOf(String... values) {
		return new HashSet<String>(Arrays.asList(values));
	}

	private static final class Rel {
		final String id;
		final String source;
		final String target;
		final String path;

		Rel(String id, String source, String target, String path) {
			this.id = id;
			this.source = source;
			this.target = target;
			this.path = path;
		}
	}
}
