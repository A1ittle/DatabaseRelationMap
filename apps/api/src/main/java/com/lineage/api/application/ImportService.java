package com.lineage.api.application;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.lineage.api.infrastructure.CatalogScopeRow;
import com.lineage.api.infrastructure.ImportJdbcRepository;
import com.lineage.api.infrastructure.ImportJdbcRepository.EvidenceRow;
import com.lineage.api.infrastructure.ImportJdbcRepository.ObjectRow;
import com.lineage.api.infrastructure.ImportJdbcRepository.RelationEvidenceRow;
import com.lineage.api.infrastructure.ImportJdbcRepository.RelationRow;
import com.lineage.api.infrastructure.IngestRunRecord;
import com.lineage.api.infrastructure.QualityIssueRow;
import com.lineage.api.interfaces.dto.ErrorItem;
import com.lineage.api.interfaces.dto.ImportDetail;
import com.lineage.api.interfaces.dto.ImportResponse;
import com.lineage.api.interfaces.dto.PageInfo;
import com.lineage.api.interfaces.dto.PublishResponse;

public class ImportService {

	private final ImportJdbcRepository repository;
	private final ImportValidator validator;

	public ImportService(ImportJdbcRepository repository, ImportValidator validator) {
		this.repository = repository;
		this.validator = validator;
	}

	@Transactional
	public ImportResponse importBatch(JsonNode body) {
		if (body == null || !body.isObject()) {
			throw ApiException.importInvalid("ImportBatch must be a JSON object");
		}
		String batchKey = text(body, "batchKey");
		String scopeId = text(body, "scopeId");
		if (batchKey == null || batchKey.isEmpty() || scopeId == null || scopeId.isEmpty()) {
			throw ApiException.importInvalid("batchKey and scopeId are required");
		}
		if (batchKey.length() > 200 || scopeId.length() > 200) {
			throw ApiException.importInvalid("batchKey or scopeId exceeds max length");
		}
		String sha = CanonicalJson.sha256Hex(body);
		repository.ensureScope(scopeId);
		IngestRunRecord existing = repository.findByScopeAndBatchKey(scopeId, batchKey);
		if (existing != null) {
			if (!sha.equals(existing.getPayloadSha256())) {
				throw ApiException.importInvalid(
					"batchKey already used in this scope with a different payload");
			}
			return toResponse(existing);
		}

		List<ValidationIssue> issues = validator.validate(body);
		int errorCount = 0;
		int warningCount = 0;
		for (int i = 0; i < issues.size(); i++) {
			if (issues.get(i).isError()) {
				errorCount++;
			}
			else {
				warningCount++;
			}
		}
		boolean failed = errorCount > 0;
		String status = failed ? "failed" : "ready";
		String runId = UUID.randomUUID().toString();
		Instant capturedAt = parseInstant(text(body, "capturedAt"));
		String sourceVersion = text(body, "sourceVersion");
		if (sourceVersion == null) {
			sourceVersion = "unknown";
		}
		if (capturedAt == null) {
			capturedAt = Instant.now();
		}

		boolean inserted = repository.insertRunIfAbsent(runId, scopeId, batchKey, sha, status, sourceVersion,
			capturedAt, errorCount, warningCount);
		if (!inserted) {
			IngestRunRecord raced = repository.findByScopeAndBatchKey(scopeId, batchKey);
			if (raced != null && sha.equals(raced.getPayloadSha256())) {
				return toResponse(raced);
			}
			throw ApiException.importInvalid("batchKey already used in this scope with a different payload");
		}

		repository.insertIssues(runId, issues);
		String snapshotId = null;
		if (!failed) {
			snapshotId = UUID.randomUUID().toString();
			String coverage = text(body, "coverage");
			if (coverage == null) {
				coverage = "unknown";
			}
			repository.insertSnapshot(snapshotId, scopeId, runId, coverage);
			List<ObjectRow> objects = parseObjects(body.get("objects"));
			repository.insertIdentities(scopeId, objects);
			repository.insertObjectVersions(snapshotId, scopeId, objects);
			List<EvidenceRow> evidence = parseEvidence(body.get("evidence"));
			repository.insertEvidence(snapshotId, evidence);
			List<RelationRow> relations = parseRelations(body.get("relations"));
			repository.insertRelations(snapshotId, relations);
			repository.insertRelationEvidence(snapshotId, parseRelationEvidence(body.get("relations")));
		}
		return new ImportResponse(runId, status, snapshotId, errorCount, warningCount);
	}

	@Transactional(readOnly = true)
	public ImportDetail getImport(String runId, String cursor, Integer limit) {
		if (runId == null || runId.isEmpty() || runId.length() > 200) {
			throw ApiException.invalidArgument("runId is invalid");
		}
		int pageLimit = limit == null ? 50 : limit.intValue();
		if (pageLimit < 1 || pageLimit > 100) {
			throw ApiException.invalidArgument("limit must be between 1 and 100");
		}
		IngestRunRecord run = repository.findByRunId(runId);
		if (run == null) {
			throw ApiException.notFound("import run not found");
		}
		long after = 0L;
		if (cursor != null && !cursor.isEmpty()) {
			if (cursor.length() > 4096) {
				throw ApiException.cursorMismatch("cursor is invalid");
			}
			try {
				after = Long.parseLong(cursor);
			}
			catch (NumberFormatException e) {
				throw ApiException.cursorMismatch("cursor is invalid");
			}
			if (after < 0L) {
				throw ApiException.cursorMismatch("cursor is invalid");
			}
		}
		int total = repository.countIssues(runId);
		List<QualityIssueRow> rows = repository.listIssues(runId, after, pageLimit + 1);
		boolean hasMore = rows.size() > pageLimit;
		if (hasMore) {
			rows = rows.subList(0, pageLimit);
		}
		List<ErrorItem> items = new ArrayList<ErrorItem>(rows.size());
		for (int i = 0; i < rows.size(); i++) {
			QualityIssueRow row = rows.get(i);
			items.add(new ErrorItem(row.getCode(), row.getSourcePath(), row.getMessage(), row.getSeverity()));
		}
		String nextCursor = null;
		if (hasMore && !rows.isEmpty()) {
			nextCursor = Long.toString(rows.get(rows.size() - 1).getIssueId());
		}
		return new ImportDetail(toResponse(run), items, new PageInfo(nextCursor, hasMore, Integer.valueOf(total)));
	}

	@Transactional
	public PublishResponse publish(String runId, JsonNode body) {
		if (runId == null || runId.isEmpty() || runId.length() > 200) {
			throw ApiException.invalidArgument("runId is invalid");
		}
		if (body == null || !body.isObject()) {
			throw ApiException.invalidArgument("PublishRequest must be a JSON object");
		}
		if (!body.has("expectedActiveSnapshotId")) {
			throw ApiException.invalidArgument("expectedActiveSnapshotId is required");
		}
		if (body.size() != 1) {
			throw ApiException.invalidArgument("PublishRequest has unknown properties");
		}
		JsonNode expectedNode = body.get("expectedActiveSnapshotId");
		String expected;
		if (expectedNode == null || expectedNode.isNull()) {
			expected = null;
		}
		else if (expectedNode.isTextual()) {
			expected = expectedNode.asText();
			if (expected.isEmpty() || expected.length() > 200) {
				throw ApiException.invalidArgument("expectedActiveSnapshotId is invalid");
			}
		}
		else {
			throw ApiException.invalidArgument("expectedActiveSnapshotId must be a string or null");
		}

		IngestRunRecord run = repository.findByRunId(runId);
		if (run == null) {
			throw ApiException.notFound("import run not found");
		}
		CatalogScopeRow scope = repository.lockScope(run.getScopeId());
		if (scope == null) {
			throw ApiException.notFound("scope not found");
		}
		if (!Objects.equals(expected, scope.getActiveSnapshotId())) {
			throw ApiException.publishConflict("active snapshot does not match expectedActiveSnapshotId");
		}
		if ("published".equals(run.getStatus())) {
			if (run.getSnapshotId() != null && run.getSnapshotId().equals(scope.getActiveSnapshotId())
				&& run.getPublishedAt() != null) {
				return new PublishResponse(run.getSnapshotId(), run.getPublishedAt());
			}
			throw ApiException.publishConflict("run is already published");
		}
		if (!"ready".equals(run.getStatus()) || run.getSnapshotId() == null) {
			throw ApiException.invalidArgument("only ready import runs can be published");
		}
		Instant publishedAt = repository.publish(run.getScopeId(), run.getRunId(), run.getSnapshotId());
		return new PublishResponse(run.getSnapshotId(), publishedAt);
	}

	private static ImportResponse toResponse(IngestRunRecord run) {
		return new ImportResponse(run.getRunId(), run.getStatus(), run.getSnapshotId(), run.getErrorCount(),
			run.getWarningCount());
	}

	private static List<ObjectRow> parseObjects(JsonNode array) {
		List<ObjectRow> rows = new ArrayList<ObjectRow>();
		if (array == null || !array.isArray()) {
			return rows;
		}
		for (int i = 0; i < array.size(); i++) {
			JsonNode n = array.get(i);
			rows.add(new ObjectRow(n.get("id").asText(), n.get("type").asText(), n.get("namespace").asText(),
				n.get("technicalName").asText(), n.get("displayName").asText(), n.get("system").asText(),
				n.get("owner").isNull() ? null : n.get("owner").asText(),
				n.get("javaKind").isNull() ? null : n.get("javaKind").asText()));
		}
		return rows;
	}

	private static List<EvidenceRow> parseEvidence(JsonNode array) {
		List<EvidenceRow> rows = new ArrayList<EvidenceRow>();
		if (array == null || !array.isArray()) {
			return rows;
		}
		for (int i = 0; i < array.size(); i++) {
			JsonNode n = array.get(i);
			rows.add(new EvidenceRow(n.get("id").asText(), n.get("state").asText(), n.get("sourceRef").asText(),
				parseInstant(n.get("observedAt").asText()), n.get("description").asText()));
		}
		return rows;
	}

	private static List<RelationRow> parseRelations(JsonNode array) {
		List<RelationRow> rows = new ArrayList<RelationRow>();
		if (array == null || !array.isArray()) {
			return rows;
		}
		for (int i = 0; i < array.size(); i++) {
			JsonNode n = array.get(i);
			rows.add(new RelationRow(n.get("id").asText(), n.get("source").asText(), n.get("target").asText(),
				n.get("rel").asText(), n.get("sourceOrder").asInt()));
		}
		return rows;
	}

	private static List<RelationEvidenceRow> parseRelationEvidence(JsonNode array) {
		List<RelationEvidenceRow> rows = new ArrayList<RelationEvidenceRow>();
		if (array == null || !array.isArray()) {
			return rows;
		}
		for (int i = 0; i < array.size(); i++) {
			JsonNode n = array.get(i);
			String relationId = n.get("id").asText();
			JsonNode ids = n.get("evidenceIds");
			for (int e = 0; e < ids.size(); e++) {
				rows.add(new RelationEvidenceRow(relationId, ids.get(e).asText()));
			}
		}
		return rows;
	}

	private static String text(JsonNode node, String field) {
		if (!node.has(field) || node.get(field).isNull() || !node.get(field).isTextual()) {
			return null;
		}
		return node.get(field).asText();
	}

	private static Instant parseInstant(String value) {
		if (value == null || value.isEmpty()) {
			return null;
		}
		try {
			return Instant.parse(value);
		}
		catch (Exception e) {
			try {
				return OffsetDateTime.parse(value).toInstant();
			}
			catch (Exception ignored) {
				return Instant.now();
			}
		}
	}
}
