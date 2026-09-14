package com.lineage.api.application;

/**
 * Contract Error payload mapped to an HTTP status. Codes are OpenAPI {@code Error.code}.
 */
public class ApiException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final String code;
	private final int status;
	private final boolean retryable;

	public ApiException(String code, int status, boolean retryable, String message) {
		super(message);
		this.code = code;
		this.status = status;
		this.retryable = retryable;
	}

	public String getCode() {
		return code;
	}

	public int getStatus() {
		return status;
	}

	public boolean isRetryable() {
		return retryable;
	}

	public static ApiException importInvalid(String message) {
		return new ApiException("IMPORT_INVALID", 400, false, message);
	}

	public static ApiException invalidArgument(String message) {
		return new ApiException("INVALID_ARGUMENT", 400, false, message);
	}

	public static ApiException notFound(String message) {
		return new ApiException("NOT_FOUND", 404, false, message);
	}

	public static ApiException publishConflict(String message) {
		return new ApiException("PUBLISH_CONFLICT", 409, false, message);
	}

	public static ApiException payloadTooLarge(String message) {
		return new ApiException("PAYLOAD_TOO_LARGE", 413, false, message);
	}

	public static ApiException unauthenticated(String message) {
		return new ApiException("UNAUTHENTICATED", 401, false, message);
	}

	public static ApiException forbidden(String message) {
		return new ApiException("FORBIDDEN", 403, false, message);
	}

	public static ApiException cursorMismatch(String message) {
		return new ApiException("CURSOR_MISMATCH", 400, false, message);
	}

	public static ApiException unavailable(String message) {
		return new ApiException("TEMPORARILY_UNAVAILABLE", 503, true, message);
	}

	public static ApiException invalidSeed(String message) {
		return new ApiException("INVALID_SEED", 400, false, message);
	}

	public static ApiException lineageNotCollected(String message) {
		return new ApiException("LINEAGE_NOT_COLLECTED", 404, false, message);
	}

	public static ApiException queryExpired(String message) {
		return new ApiException("QUERY_EXPIRED", 410, false, message);
	}

	public static ApiException policyChanged(String message) {
		return new ApiException("POLICY_CHANGED", 409, false, message);
	}

	public static ApiException projectionLimit(String message) {
		return new ApiException("PROJECTION_LIMIT", 400, false, message);
	}
}
