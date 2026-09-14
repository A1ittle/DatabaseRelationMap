package com.lineage.api.infrastructure;

import java.time.Instant;

public final class IngestRunRecord {

	private final String runId;
	private final String scopeId;
	private final String batchKey;
	private final String payloadSha256;
	private final String status;
	private final String snapshotId;
	private final int errorCount;
	private final int warningCount;
	private final Instant publishedAt;

	public IngestRunRecord(String runId, String scopeId, String batchKey, String payloadSha256, String status,
			String snapshotId, int errorCount, int warningCount, Instant publishedAt) {
		this.runId = runId;
		this.scopeId = scopeId;
		this.batchKey = batchKey;
		this.payloadSha256 = payloadSha256;
		this.status = status;
		this.snapshotId = snapshotId;
		this.errorCount = errorCount;
		this.warningCount = warningCount;
		this.publishedAt = publishedAt;
	}

	public String getRunId() {
		return runId;
	}

	public String getScopeId() {
		return scopeId;
	}

	public String getBatchKey() {
		return batchKey;
	}

	public String getPayloadSha256() {
		return payloadSha256;
	}

	public String getStatus() {
		return status;
	}

	public String getSnapshotId() {
		return snapshotId;
	}

	public int getErrorCount() {
		return errorCount;
	}

	public int getWarningCount() {
		return warningCount;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}
}
