package com.lineage.api.interfaces.dto;

public final class ErrorBody {

	private final String code;
	private final String message;
	private final String requestId;
	private final boolean retryable;

	public ErrorBody(String code, String message, String requestId, boolean retryable) {
		this.code = code;
		this.message = message;
		this.requestId = requestId;
		this.retryable = retryable;
	}

	public String getCode() {
		return code;
	}

	public String getMessage() {
		return message;
	}

	public String getRequestId() {
		return requestId;
	}

	public boolean isRetryable() {
		return retryable;
	}
}
