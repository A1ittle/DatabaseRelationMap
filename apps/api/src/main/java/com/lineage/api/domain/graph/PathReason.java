package com.lineage.api.domain.graph;

/**
 * Wire values match {@code PathResponse.reason} in spec/v1/openapi.json.
 */
public enum PathReason {
	QUERY_INCOMPLETE("QUERY_INCOMPLETE"),
	PATH_LENGTH_LIMIT("PATH_LENGTH_LIMIT");

	private final String wire;

	PathReason(String wire) {
		this.wire = wire;
	}

	public String wire() {
		return wire;
	}

	public static PathReason fromWire(String value) {
		if (value == null) {
			return null;
		}
		for (PathReason reason : values()) {
			if (reason.wire.equals(value)) {
				return reason;
			}
		}
		throw new IllegalArgumentException("Unknown path reason: " + value);
	}
}
