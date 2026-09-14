package com.lineage.api.application;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lineage.api.domain.graph.ClassificationResult;
import com.lineage.api.domain.graph.LineageGraph;
import com.lineage.api.infrastructure.QueryJdbcRepository.CatalogObject;
import com.lineage.api.infrastructure.QueryJdbcRepository.EvidenceRecord;
import com.lineage.api.infrastructure.QueryJdbcRepository.RelationRecord;

/**
 * In-memory query bound to one snapshot, seed, policy revision, and embed groups.
 */
public final class QueryContext {

	public static final String ALGORITHM_VERSION = "lineage-tree-v1";
	static final long IDLE_MS = 15L * 60L * 1000L;
	static final long MAX_MS = 60L * 60L * 1000L;

	private final String queryId;
	private final String snapshotId;
	private final String scopeId;
	private final String seedId;
	private final String coverage;
	private final long policyRevision;
	private final List<String> groups;
	private final Set<String> authorizedObjectIds;
	private final LineageGraph graph;
	private final ClassificationResult classification;
	private final Map<String, CatalogObject> objects;
	private final Map<String, RelationRecord> relations;
	private final Map<String, EvidenceRecord> evidence;
	private final Instant createdAt;
	private volatile Instant lastAccessAt;

	public QueryContext(String queryId, String snapshotId, String scopeId, String seedId, String coverage,
			long policyRevision, List<String> groups, Set<String> authorizedObjectIds, LineageGraph graph,
			ClassificationResult classification, Map<String, CatalogObject> objects,
			Map<String, RelationRecord> relations, Map<String, EvidenceRecord> evidence, Instant createdAt) {
		this.queryId = queryId;
		this.snapshotId = snapshotId;
		this.scopeId = scopeId;
		this.seedId = seedId;
		this.coverage = coverage;
		this.policyRevision = policyRevision;
		this.groups = groups;
		this.authorizedObjectIds = authorizedObjectIds;
		this.graph = graph;
		this.classification = classification;
		this.objects = Collections.unmodifiableMap(new LinkedHashMap<String, CatalogObject>(objects));
		this.relations = Collections.unmodifiableMap(new LinkedHashMap<String, RelationRecord>(relations));
		this.evidence = Collections.unmodifiableMap(new LinkedHashMap<String, EvidenceRecord>(evidence));
		this.createdAt = createdAt;
		this.lastAccessAt = createdAt;
	}

	public String getQueryId() {
		return queryId;
	}

	public String getSnapshotId() {
		return snapshotId;
	}

	public String getScopeId() {
		return scopeId;
	}

	public String getSeedId() {
		return seedId;
	}

	public String getCoverage() {
		return coverage;
	}

	public long getPolicyRevision() {
		return policyRevision;
	}

	public List<String> getGroups() {
		return groups;
	}

	public Set<String> getAuthorizedObjectIds() {
		return authorizedObjectIds;
	}

	public LineageGraph getGraph() {
		return graph;
	}

	public ClassificationResult getClassification() {
		return classification;
	}

	public Map<String, CatalogObject> getObjects() {
		return objects;
	}

	public Map<String, RelationRecord> getRelations() {
		return relations;
	}

	public Map<String, EvidenceRecord> getEvidence() {
		return evidence;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public synchronized Instant touch(Instant now) {
		this.lastAccessAt = now;
		return expiresAt(now);
	}

	public synchronized Instant expiresAt(Instant now) {
		long idle = lastAccessAt.toEpochMilli() + IDLE_MS;
		long max = createdAt.toEpochMilli() + MAX_MS;
		long exp = Math.min(idle, max);
		if (now.toEpochMilli() > exp) {
			return Instant.ofEpochMilli(exp);
		}
		return Instant.ofEpochMilli(Math.min(now.toEpochMilli() + IDLE_MS, max));
	}

	public synchronized boolean expired(Instant now) {
		long idle = lastAccessAt.toEpochMilli() + IDLE_MS;
		long max = createdAt.toEpochMilli() + MAX_MS;
		return now.toEpochMilli() > Math.min(idle, max);
	}
}
