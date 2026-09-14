package com.lineage.api.application;

public final class ValidationIssue {

	private final String code;
	private final String path;
	private final String message;
	private final String severity;

	public ValidationIssue(String code, String path, String message, String severity) {
		this.code = code;
		this.path = path;
		this.message = message;
		this.severity = severity;
	}

	public static ValidationIssue error(String code, String path, String message) {
		return new ValidationIssue(code, path, message, "error");
	}

	public static ValidationIssue warning(String code, String path, String message) {
		return new ValidationIssue(code, path, message, "warning");
	}

	public String getCode() {
		return code;
	}

	public String getPath() {
		return path;
	}

	public String getMessage() {
		return message;
	}

	public String getSeverity() {
		return severity;
	}

	public boolean isError() {
		return "error".equals(severity);
	}
}
