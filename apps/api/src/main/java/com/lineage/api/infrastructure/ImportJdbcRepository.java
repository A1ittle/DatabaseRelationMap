package com.lineage.api.infrastructure;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.lineage.api.application.ValidationIssue;

/**
 * JDBC access for ingest runs, unpublished snapshot rows, and CAS publish.
 */
public class ImportJdbcRepository {

	private final JdbcTemplate jdbc;

	public ImportJdbcRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void ensureScope(String scopeId) {
		jdbc.update(
			"INSERT INTO catalog_scope (scope_id, display_name, active_snapshot_id, revision) "
				+ "VALUES (?, ?, NULL, 0) ON CONFLICT (scope_id) DO NOTHING",
			scopeId, scopeId);
	}

	public IngestRunRecord findByScopeAndBatchKey(String scopeId, String batchKey) {
		List<IngestRunRecord> rows = jdbc.query(
			"SELECT r.run_id, r.scope_id, r.batch_key, r.payload_sha256, r.status, "
				+ "s.snapshot_id, r.error_count, r.warning_count, s.published_at "
				+ "FROM ingest_run r LEFT JOIN snapshot s ON s.run_id = r.run_id "
				+ "WHERE r.scope_id = ? AND r.batch_key = ?",
			RUN_MAPPER, scopeId, batchKey);
		if (rows.isEmpty()) {
			return null;
		}
		return rows.get(0);
	}

	public IngestRunRecord findByRunId(String runId) {
		List<IngestRunRecord> rows = jdbc.query(
			"SELECT r.run_id, r.scope_id, r.batch_key, r.payload_sha256, r.status, "
				+ "s.snapshot_id, r.error_count, r.warning_count, s.published_at "
				+ "FROM ingest_run r LEFT JOIN snapshot s ON s.run_id = r.run_id "
				+ "WHERE r.run_id = ?",
			RUN_MAPPER, runId);
		if (rows.isEmpty()) {
			return null;
		}
		return rows.get(0);
	}

	/** @return true if this row was inserted; false on (scope_id, batch_key) conflict */
	public boolean insertRunIfAbsent(String runId, String scopeId, String batchKey, String sha, String status,
			String sourceVersion, Instant capturedAt, int errorCount, int warningCount) {
		int n = jdbc.update(
			"INSERT INTO ingest_run (run_id, scope_id, batch_key, payload_sha256, status, source_version, "
				+ "captured_at, error_count, warning_count) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
				+ "ON CONFLICT (scope_id, batch_key) DO NOTHING",
			runId, scopeId, batchKey, sha, status, sourceVersion, Timestamp.from(capturedAt), errorCount,
			warningCount);
		return n > 0;
	}

	public void insertSnapshot(String snapshotId, String scopeId, String runId, String coverage) {
		jdbc.update(
			"INSERT INTO snapshot (snapshot_id, scope_id, run_id, coverage, published_at) VALUES (?, ?, ?, ?, NULL)",
			snapshotId, scopeId, runId, coverage);
	}

	public void insertIssues(final String runId, final List<ValidationIssue> issues) {
		if (issues.isEmpty()) {
			return;
		}
		jdbc.batchUpdate(
			"INSERT INTO quality_issue (run_id, code, severity, source_path, message) VALUES (?, ?, ?, ?, ?)",
			new BatchPreparedStatementSetter() {
				@Override
				public void setValues(PreparedStatement ps, int i) throws SQLException {
					ValidationIssue issue = issues.get(i);
					ps.setString(1, runId);
					ps.setString(2, issue.getCode());
					ps.setString(3, issue.getSeverity());
					ps.setString(4, issue.getPath());
					ps.setString(5, issue.getMessage());
				}

				@Override
				public int getBatchSize() {
					return issues.size();
				}
			});
	}

	public void insertIdentities(final String scopeId, final List<ObjectRow> objects) {
		if (objects.isEmpty()) {
			return;
		}
		jdbc.batchUpdate(
			"INSERT INTO object_identity (object_id, scope_id, source_identity) VALUES (?, ?, ?) "
				+ "ON CONFLICT (object_id) DO NOTHING",
			new BatchPreparedStatementSetter() {
				@Override
				public void setValues(PreparedStatement ps, int i) throws SQLException {
					ObjectRow row = objects.get(i);
					ps.setString(1, row.id);
					ps.setString(2, scopeId);
					ps.setString(3, row.id);
				}

				@Override
				public int getBatchSize() {
					return objects.size();
				}
			});
	}

	public void insertObjectVersions(final String snapshotId, final String scopeId,
			final List<ObjectRow> objects) {
		if (objects.isEmpty()) {
			return;
		}
		jdbc.batchUpdate(
			"INSERT INTO object_version (snapshot_id, object_id, scope_id, object_type, namespace, "
				+ "technical_name, display_name, system_name, owner_ref, java_kind) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
			new BatchPreparedStatementSetter() {
				@Override
				public void setValues(PreparedStatement ps, int i) throws SQLException {
					ObjectRow row = objects.get(i);
					ps.setString(1, snapshotId);
					ps.setString(2, row.id);
					ps.setString(3, scopeId);
					ps.setString(4, row.type);
					ps.setString(5, row.namespace);
					ps.setString(6, row.technicalName);
					ps.setString(7, row.displayName);
					ps.setString(8, row.systemName);
					ps.setString(9, row.owner);
					ps.setString(10, row.javaKind);
				}

				@Override
				public int getBatchSize() {
					return objects.size();
				}
			});
	}

	public void insertEvidence(final String snapshotId, final List<EvidenceRow> rows) {
		if (rows.isEmpty()) {
			return;
		}
		jdbc.batchUpdate(
			"INSERT INTO evidence (snapshot_id, evidence_id, evidence_state, source_ref, observed_at, description) "
				+ "VALUES (?, ?, ?, ?, ?, ?)",
			new BatchPreparedStatementSetter() {
				@Override
				public void setValues(PreparedStatement ps, int i) throws SQLException {
					EvidenceRow row = rows.get(i);
					ps.setString(1, snapshotId);
					ps.setString(2, row.id);
					ps.setString(3, row.state);
					ps.setString(4, row.sourceRef);
					ps.setTimestamp(5, Timestamp.from(row.observedAt));
					ps.setString(6, row.description);
				}

				@Override
				public int getBatchSize() {
					return rows.size();
				}
			});
	}

	public void insertRelations(final String snapshotId, final List<RelationRow> rows) {
		if (rows.isEmpty()) {
			return;
		}
		jdbc.batchUpdate(
			"INSERT INTO relation_version (snapshot_id, relation_id, source_id, target_id, relation_type, source_order) "
				+ "VALUES (?, ?, ?, ?, ?, ?)",
			new BatchPreparedStatementSetter() {
				@Override
				public void setValues(PreparedStatement ps, int i) throws SQLException {
					RelationRow row = rows.get(i);
					ps.setString(1, snapshotId);
					ps.setString(2, row.id);
					ps.setString(3, row.source);
					ps.setString(4, row.target);
					ps.setString(5, row.rel);
					ps.setInt(6, row.sourceOrder);
				}

				@Override
				public int getBatchSize() {
					return rows.size();
				}
			});
	}

	public void insertRelationEvidence(final String snapshotId, final List<RelationEvidenceRow> rows) {
		if (rows.isEmpty()) {
			return;
		}
		jdbc.batchUpdate(
			"INSERT INTO relation_evidence (snapshot_id, relation_id, evidence_id) VALUES (?, ?, ?)",
			new BatchPreparedStatementSetter() {
				@Override
				public void setValues(PreparedStatement ps, int i) throws SQLException {
					RelationEvidenceRow row = rows.get(i);
					ps.setString(1, snapshotId);
					ps.setString(2, row.relationId);
					ps.setString(3, row.evidenceId);
				}

				@Override
				public int getBatchSize() {
					return rows.size();
				}
			});
	}

	public int countIssues(String runId) {
		Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM quality_issue WHERE run_id = ?", Integer.class,
			runId);
		return n == null ? 0 : n.intValue();
	}

	public List<QualityIssueRow> listIssues(String runId, long afterIssueId, int limit) {
		return jdbc.query(
			"SELECT issue_id, code, severity, source_path, message FROM quality_issue "
				+ "WHERE run_id = ? AND issue_id > ? ORDER BY issue_id ASC LIMIT ?",
			ISSUE_MAPPER, runId, Long.valueOf(afterIssueId), Integer.valueOf(limit));
	}

	public boolean hasIngestGrant(String scopeId, List<String> groups) {
		if (scopeId == null || groups == null || groups.isEmpty()) {
			return false;
		}
		String sql = "SELECT COUNT(*) FROM scope_grant WHERE scope_id = ? AND permission = 'ingest' AND group_id IN ("
			+ placeholders(groups.size()) + ")";
		Object[] args = new Object[groups.size() + 1];
		args[0] = scopeId;
		for (int i = 0; i < groups.size(); i++) {
			args[i + 1] = groups.get(i);
		}
		Integer n = jdbc.queryForObject(sql, Integer.class, args);
		return n != null && n.intValue() > 0;
	}

	private static String placeholders(int n) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < n; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append('?');
		}
		return sb.toString();
	}

	public CatalogScopeRow lockScope(String scopeId) {
		List<CatalogScopeRow> rows = jdbc.query(
			"SELECT scope_id, active_snapshot_id, revision FROM catalog_scope WHERE scope_id = ? FOR UPDATE",
			SCOPE_MAPPER, scopeId);
		if (rows.isEmpty()) {
			return null;
		}
		return rows.get(0);
	}

	public Instant publish(String scopeId, String runId, String snapshotId) {
		Instant at = Instant.now();
		jdbc.update("UPDATE snapshot SET published_at = ? WHERE snapshot_id = ?", Timestamp.from(at), snapshotId);
		jdbc.update("UPDATE catalog_scope SET active_snapshot_id = ?, revision = revision + 1 WHERE scope_id = ?",
			snapshotId, scopeId);
		jdbc.update("UPDATE ingest_run SET status = 'published' WHERE run_id = ?", runId);
		return at;
	}

	public int countObjectVersions(String snapshotId) {
		Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM object_version WHERE snapshot_id = ?", Integer.class,
			snapshotId);
		return n == null ? 0 : n.intValue();
	}

	public int countRelationVersions(String snapshotId) {
		Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM relation_version WHERE snapshot_id = ?",
			Integer.class, snapshotId);
		return n == null ? 0 : n.intValue();
	}

	public String activeSnapshotId(String scopeId) {
		try {
			return jdbc.queryForObject("SELECT active_snapshot_id FROM catalog_scope WHERE scope_id = ?",
				String.class, scopeId);
		}
		catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	private static Instant instant(Timestamp ts) {
		return ts == null ? null : ts.toInstant();
	}

	private static final RowMapper<IngestRunRecord> RUN_MAPPER = new RowMapper<IngestRunRecord>() {
		@Override
		public IngestRunRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
			return new IngestRunRecord(rs.getString("run_id"), rs.getString("scope_id"), rs.getString("batch_key"),
				rs.getString("payload_sha256"), rs.getString("status"), rs.getString("snapshot_id"),
				rs.getInt("error_count"), rs.getInt("warning_count"), instant(rs.getTimestamp("published_at")));
		}
	};

	private static final RowMapper<QualityIssueRow> ISSUE_MAPPER = new RowMapper<QualityIssueRow>() {
		@Override
		public QualityIssueRow mapRow(ResultSet rs, int rowNum) throws SQLException {
			return new QualityIssueRow(rs.getLong("issue_id"), rs.getString("code"), rs.getString("severity"),
				rs.getString("source_path"), rs.getString("message"));
		}
	};

	private static final RowMapper<CatalogScopeRow> SCOPE_MAPPER = new RowMapper<CatalogScopeRow>() {
		@Override
		public CatalogScopeRow mapRow(ResultSet rs, int rowNum) throws SQLException {
			return new CatalogScopeRow(rs.getString("scope_id"), rs.getString("active_snapshot_id"),
				rs.getLong("revision"));
		}
	};

	public static final class ObjectRow {
		public final String id;
		public final String type;
		public final String namespace;
		public final String technicalName;
		public final String displayName;
		public final String systemName;
		public final String owner;
		public final String javaKind;

		public ObjectRow(String id, String type, String namespace, String technicalName, String displayName,
				String systemName, String owner, String javaKind) {
			this.id = id;
			this.type = type;
			this.namespace = namespace;
			this.technicalName = technicalName;
			this.displayName = displayName;
			this.systemName = systemName;
			this.owner = owner;
			this.javaKind = javaKind;
		}
	}

	public static final class EvidenceRow {
		public final String id;
		public final String state;
		public final String sourceRef;
		public final Instant observedAt;
		public final String description;

		public EvidenceRow(String id, String state, String sourceRef, Instant observedAt, String description) {
			this.id = id;
			this.state = state;
			this.sourceRef = sourceRef;
			this.observedAt = observedAt;
			this.description = description;
		}
	}

	public static final class RelationRow {
		public final String id;
		public final String source;
		public final String target;
		public final String rel;
		public final int sourceOrder;

		public RelationRow(String id, String source, String target, String rel, int sourceOrder) {
			this.id = id;
			this.source = source;
			this.target = target;
			this.rel = rel;
			this.sourceOrder = sourceOrder;
		}
	}

	public static final class RelationEvidenceRow {
		public final String relationId;
		public final String evidenceId;

		public RelationEvidenceRow(String relationId, String evidenceId) {
			this.relationId = relationId;
			this.evidenceId = evidenceId;
		}
	}

}
