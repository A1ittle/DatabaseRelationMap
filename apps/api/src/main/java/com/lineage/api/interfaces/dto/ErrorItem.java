package com.lineage.api.interfaces.dto;

public final class ErrorItem {

	private final String code;
	private final String path;
	private final String message;
	private final String severity;

	public ErrorItem(String code, String path, String message, String severity) {
		this.code = code;
		this.path = path;
		this.message = message;
		this.severity = severity;
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
}
