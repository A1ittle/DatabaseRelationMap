package com.lineage.api.domain.graph;

/**
 * Wire values match {@code PathResponse.status} in spec/v1/openapi.json.
 */
public enum PathStatus {
	FOUND("found"),
	NOT_FOUND("not_found"),
	UNKNOWN("unknown");

	private final String wire;

	PathStatus(String wire) {
		this.wire = wire;
	}

	public String wire() {
		return wire;
	}

	public static PathStatus fromWire(String value) {
		for (PathStatus status : values()) {
			if (status.wire.equals(value)) {
				return status;
			}
		}
		throw new IllegalArgumentException("Unknown path status: " + value);
	}
}
