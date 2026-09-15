package com.lineage.api.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * JDBC reads for published snapshots, grants, and policy revision.
 */
public class QueryJdbcRepository {

	private final JdbcTemplate jdbc;

	public QueryJdbcRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public long policyRevision() {
		Long n = jdbc.queryForObject("SELECT revision FROM policy_revision WHERE singleton = true", Long.class);
		return n == null ? 0L : n.longValue();
	}

	public List<ActiveSnapshot> listActiveSnapshots() {
		return jdbc.query(
			"SELECT cs.scope_id, cs.active_snapshot_id, s.coverage FROM catalog_scope cs "
				+ "JOIN snapshot s ON s.snapshot_id = cs.active_snapshot_id "
				+ "WHERE cs.active_snapshot_id IS NOT NULL",
			ACTIVE_MAPPER);
	}

	/**
	 * Active seeds for {@code objectId}, always snapshot-qualified.
	 * Without {@code snapshotId}, returns every matching active snapshot (ordered by
	 * scope then snapshot) so callers can reject cross-scope ambiguity instead of
	 * taking an unordered first row.
	 */
	public List<SeedHit> findActiveSeeds(String objectId, String snapshotId) {
		if (snapshotId == null || snapshotId.isEmpty()) {
			return jdbc.query(
				"SELECT ov.object_id, ov.snapshot_id, s.scope_id, s.coverage, cs.active_snapshot_id "
					+ "FROM object_version ov "
					+ "JOIN snapshot s ON s.snapshot_id = ov.snapshot_id "
					+ "JOIN catalog_scope cs ON cs.scope_id = s.scope_id "
					+ "WHERE ov.object_id = ? AND cs.active_snapshot_id = ov.snapshot_id "
					+ "ORDER BY s.scope_id ASC, ov.snapshot_id ASC",
				SEED_MAPPER, objectId);
		}
		return jdbc.query(
			"SELECT ov.object_id, ov.snapshot_id, s.scope_id, s.coverage, cs.active_snapshot_id "
				+ "FROM object_version ov "
				+ "JOIN snapshot s ON s.snapshot_id = ov.snapshot_id "
				+ "JOIN catalog_scope cs ON cs.scope_id = s.scope_id "
				+ "WHERE ov.object_id = ? AND ov.snapshot_id = ? "
				+ "ORDER BY s.scope_id ASC, ov.snapshot_id ASC",
			SEED_MAPPER, objectId, snapshotId);
	}

	public SnapshotCatalog loadSnapshot(String snapshotId) {
		List<CatalogObject> objects = jdbc.query(
			"SELECT object_id, object_type, namespace, technical_name, display_name, system_name, owner_ref, java_kind "
				+ "FROM object_version WHERE snapshot_id = ? ORDER BY object_id",
			OBJECT_MAPPER, snapshotId);
		List<RelationRecord> relations = jdbc.query(
			"SELECT relation_id, source_id, target_id, relation_type, source_order "
				+ "FROM relation_version WHERE snapshot_id = ? ORDER BY relation_id",
			RELATION_MAPPER, snapshotId);
		List<IdPair> evidenceLinks = jdbc.query(
			"SELECT relation_id, evidence_id FROM relation_evidence WHERE snapshot_id = ? "
				+ "ORDER BY relation_id, evidence_id",
			ID_PAIR_MAPPER, snapshotId);
		Map<String, List<String>> evidenceByRelation = new LinkedHashMap<String, List<String>>();
		for (int i = 0; i < evidenceLinks.size(); i++) {
			IdPair pair = evidenceLinks.get(i);
			List<String> ids = evidenceByRelation.get(pair.left);
			if (ids == null) {
				ids = new ArrayList<String>();
				evidenceByRelation.put(pair.left, ids);
			}
			ids.add(pair.right);
		}
		for (int i = 0; i < relations.size(); i++) {
			RelationRecord rel = relations.get(i);
			List<String> ids = evidenceByRelation.get(rel.id);
			if (ids == null) {
				ids = Collections.emptyList();
			}
			rel.evidenceIds = Collections.unmodifiableList(new ArrayList<String>(ids));
		}
		List<EvidenceRecord> evidence = jdbc.query(
			"SELECT evidence_id, evidence_state, source_ref, observed_at, description "
				+ "FROM evidence WHERE snapshot_id = ? ORDER BY evidence_id",
			EVIDENCE_MAPPER, snapshotId);
		List<ActiveSnapshot> meta = jdbc.query(
			"SELECT s.scope_id, s.snapshot_id, s.coverage FROM snapshot s WHERE s.snapshot_id = ?",
			new RowMapper<ActiveSnapshot>() {
				@Override
				public ActiveSnapshot mapRow(ResultSet rs, int rowNum) throws SQLException {
					return new ActiveSnapshot(rs.getString("scope_id"), rs.getString("snapshot_id"),
						rs.getString("coverage"));
				}
			}, snapshotId);
		if (meta.isEmpty()) {
			return null;
		}
		return new SnapshotCatalog(meta.get(0), objects, relations, evidence);
	}

	public List<String> scopeViewGroups(String scopeId, List<String> groups) {
		if (groups == null || groups.isEmpty()) {
			return Collections.emptyList();
		}
		String sql = "SELECT DISTINCT group_id FROM scope_grant WHERE scope_id = ? AND permission = 'view' AND group_id IN ("
			+ placeholders(groups.size()) + ")";
		Object[] args = new Object[groups.size() + 1];
		args[0] = scopeId;
		for (int i = 0; i < groups.size(); i++) {
			args[i + 1] = groups.get(i);
		}
		return jdbc.queryForList(sql, String.class, args);
	}

	public List<ObjectGrant> objectGrants(String scopeId, List<String> groups) {
		if (groups == null || groups.isEmpty()) {
			return Collections.emptyList();
		}
		String sql = "SELECT og.object_id, og.group_id, og.effect FROM object_grant og "
			+ "JOIN object_identity oi ON oi.scope_id = og.scope_id AND oi.object_id = og.object_id "
			+ "WHERE og.scope_id = ? AND og.group_id IN (" + placeholders(groups.size()) + ")";
		Object[] args = new Object[groups.size() + 1];
		args[0] = scopeId;
		for (int i = 0; i < groups.size(); i++) {
			args[i + 1] = groups.get(i);
		}
		return jdbc.query(sql, GRANT_MAPPER, args);
	}

	public List<CatalogObject> search(String snapshotId, String likePattern, List<String> authorizedIds) {
		if (authorizedIds != null && authorizedIds.isEmpty()) {
			return Collections.emptyList();
		}
		StringBuilder sql = new StringBuilder();
		sql.append("SELECT object_id, object_type, namespace, technical_name, display_name, system_name, owner_ref, java_kind ");
		sql.append("FROM object_version WHERE snapshot_id = ? AND (POSITION(LOWER(?) IN LOWER(technical_name)) > 0 ");
		sql.append("OR POSITION(LOWER(?) IN LOWER(display_name)) > 0) ");
		List<Object> args = new ArrayList<Object>();
		args.add(snapshotId);
		args.add(likePattern);
		args.add(likePattern);
		if (authorizedIds != null) {
			sql.append("AND object_id IN (").append(placeholders(authorizedIds.size())).append(") ");
			args.addAll(authorizedIds);
		}
		sql.append("ORDER BY LOWER(technical_name) ASC, object_id ASC LIMIT 500");
		return jdbc.query(sql.toString(), OBJECT_MAPPER, args.toArray());
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

	private static final RowMapper<ActiveSnapshot> ACTIVE_MAPPER = new RowMapper<ActiveSnapshot>() {
		@Override
		public ActiveSnapshot mapRow(ResultSet rs, int rowNum) throws SQLException {
			return new ActiveSnapshot(rs.getString("scope_id"), rs.getString("active_snapshot_id"),
				rs.getString("coverage"));
		}
	};

	private static final RowMapper<SeedHit> SEED_MAPPER = new RowMapper<SeedHit>() {
		@Override
		public SeedHit mapRow(ResultSet rs, int rowNum) throws SQLException {
			return new SeedHit(rs.getString("object_id"), rs.getString("snapshot_id"), rs.getString("scope_id"),
				rs.getString("coverage"), rs.getString("active_snapshot_id"));
		}
	};

	private static final RowMapper<CatalogObject> OBJECT_MAPPER = new RowMapper<CatalogObject>() {
		@Override
		public CatalogObject mapRow(ResultSet rs, int rowNum) throws SQLException {
			return new CatalogObject(rs.getString("object_id"), rs.getString("object_type"), rs.getString("namespace"),
				rs.getString("technical_name"), rs.getString("display_name"), rs.getString("system_name"),
				rs.getString("owner_ref"), rs.getString("java_kind"));
		}
	};

	private static final RowMapper<RelationRecord> RELATION_MAPPER = new RowMapper<RelationRecord>() {
		@Override
		public RelationRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
			return new RelationRecord(rs.getString("relation_id"), rs.getString("source_id"),
				rs.getString("target_id"), rs.getString("relation_type"), rs.getInt("source_order"));
		}
	};

	private static final RowMapper<IdPair> ID_PAIR_MAPPER = new RowMapper<IdPair>() {
		@Override
		public IdPair mapRow(ResultSet rs, int rowNum) throws SQLException {
			return new IdPair(rs.getString("relation_id"), rs.getString("evidence_id"));
		}
	};

	private static final RowMapper<EvidenceRecord> EVIDENCE_MAPPER = new RowMapper<EvidenceRecord>() {
		@Override
		public EvidenceRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
			Timestamp ts = rs.getTimestamp("observed_at");
			return new EvidenceRecord(rs.getString("evidence_id"), rs.getString("evidence_state"),
				rs.getString("source_ref"), ts == null ? Instant.EPOCH : ts.toInstant(), rs.getString("description"));
		}
	};

	private static final RowMapper<ObjectGrant> GRANT_MAPPER = new RowMapper<ObjectGrant>() {
		@Override
		public ObjectGrant mapRow(ResultSet rs, int rowNum) throws SQLException {
			return new ObjectGrant(rs.getString("object_id"), rs.getString("group_id"), rs.getString("effect"));
		}
	};

	public static final class ActiveSnapshot {
		public final String scopeId;
		public final String snapshotId;
		public final String coverage;

		public ActiveSnapshot(String scopeId, String snapshotId, String coverage) {
			this.scopeId = scopeId;
			this.snapshotId = snapshotId;
			this.coverage = coverage;
		}
	}

	public static final class SeedHit {
		public final String objectId;
		public final String snapshotId;
		public final String scopeId;
		public final String coverage;
		public final String activeSnapshotId;

		public SeedHit(String objectId, String snapshotId, String scopeId, String coverage,
				String activeSnapshotId) {
			this.objectId = objectId;
			this.snapshotId = snapshotId;
			this.scopeId = scopeId;
			this.coverage = coverage;
			this.activeSnapshotId = activeSnapshotId;
		}
	}

	public static final class CatalogObject {
		public final String id;
		public final String type;
		public final String namespace;
		public final String technicalName;
		public final String displayName;
		public final String system;
		public final String owner;
		public final String javaKind;

		public CatalogObject(String id, String type, String namespace, String technicalName, String displayName,
				String system, String owner, String javaKind) {
			this.id = id;
			this.type = type;
			this.namespace = namespace;
			this.technicalName = technicalName;
			this.displayName = displayName;
			this.system = system;
			this.owner = owner;
			this.javaKind = javaKind;
		}
	}

	public static final class RelationRecord {
		public final String id;
		public final String source;
		public final String target;
		public final String rel;
		public final int sourceOrder;
		public List<String> evidenceIds = Collections.emptyList();

		public RelationRecord(String id, String source, String target, String rel, int sourceOrder) {
			this.id = id;
			this.source = source;
			this.target = target;
			this.rel = rel;
			this.sourceOrder = sourceOrder;
		}
	}

	public static final class EvidenceRecord {
		public final String id;
		public final String state;
		public final String sourceRef;
		public final Instant observedAt;
		public final String description;

		public EvidenceRecord(String id, String state, String sourceRef, Instant observedAt, String description) {
			this.id = id;
			this.state = state;
			this.sourceRef = sourceRef;
			this.observedAt = observedAt;
			this.description = description;
		}
	}

	public static final class ObjectGrant {
		public final String objectId;
		public final String groupId;
		public final String effect;

		public ObjectGrant(String objectId, String groupId, String effect) {
			this.objectId = objectId;
			this.groupId = groupId;
			this.effect = effect;
		}
	}

	private static final class IdPair {
		private final String left;
		private final String right;

		private IdPair(String left, String right) {
			this.left = left;
			this.right = right;
		}
	}

	public static final class SnapshotCatalog {
		public final ActiveSnapshot meta;
		public final List<CatalogObject> objects;
		public final List<RelationRecord> relations;
		public final List<EvidenceRecord> evidence;

		public SnapshotCatalog(ActiveSnapshot meta, List<CatalogObject> objects, List<RelationRecord> relations,
				List<EvidenceRecord> evidence) {
			this.meta = meta;
			this.objects = objects;
			this.relations = relations;
			this.evidence = evidence;
		}
	}
}
