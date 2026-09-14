package com.lineage.api.interfaces.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public final class MetaDto {

	private final String requestId;
	private final String queryId;
	private final String snapshotId;
	private final String coverage;
	private final String computationStatus;
	private final String treeStatus;
	private final String algorithmVersion;
	private final Instant expiresAt;

	public MetaDto(String requestId, String queryId, String snapshotId, String coverage, String computationStatus,
			String treeStatus, String algorithmVersion, Instant expiresAt) {
		this.requestId = requestId;
		this.queryId = queryId;
		this.snapshotId = snapshotId;
		this.coverage = coverage;
		this.computationStatus = computationStatus;
		this.treeStatus = treeStatus;
		this.algorithmVersion = algorithmVersion;
		this.expiresAt = expiresAt;
	}

	public String getRequestId() {
		return requestId;
	}

	public String getQueryId() {
		return queryId;
	}

	public String getSnapshotId() {
		return snapshotId;
	}

	public String getCoverage() {
		return coverage;
	}

	public String getComputationStatus() {
		return computationStatus;
	}

	public String getTreeStatus() {
		return treeStatus;
	}

	public String getAlgorithmVersion() {
		return algorithmVersion;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}
}
