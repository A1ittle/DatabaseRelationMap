package com.lineage.api.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public final class ImportResponse {

	private final String runId;
	private final String status;
	private final String snapshotId;
	private final int errorCount;
	private final int warningCount;

	public ImportResponse(String runId, String status, String snapshotId, int errorCount, int warningCount) {
		this.runId = runId;
		this.status = status;
		this.snapshotId = snapshotId;
		this.errorCount = errorCount;
		this.warningCount = warningCount;
	}

	public String getRunId() {
		return runId;
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
}
