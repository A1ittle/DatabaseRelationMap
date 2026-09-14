package com.lineage.api.infrastructure;

public final class QualityIssueRow {

	private final long issueId;
	private final String code;
	private final String severity;
	private final String sourcePath;
	private final String message;

	public QualityIssueRow(long issueId, String code, String severity, String sourcePath, String message) {
		this.issueId = issueId;
		this.code = code;
		this.severity = severity;
		this.sourcePath = sourcePath;
		this.message = message;
	}

	public long getIssueId() {
		return issueId;
	}

	public String getCode() {
		return code;
	}

	public String getSeverity() {
		return severity;
	}

	public String getSourcePath() {
		return sourcePath;
	}

	public String getMessage() {
		return message;
	}
}
