package com.lineage.api.domain.graph;

/**
 * Wire values match {@code Meta.treeStatus} in spec/v1/openapi.json.
 */
public enum TreeStatus {
	AVAILABLE("available"),
	UNAVAILABLE_CYCLE("unavailable_cycle"),
	UNAVAILABLE_INCOMPLETE("unavailable_incomplete");

	private final String wire;

	TreeStatus(String wire) {
		this.wire = wire;
	}

	public String wire() {
		return wire;
	}

	public static TreeStatus fromWire(String value) {
		for (TreeStatus status : values()) {
			if (status.wire.equals(value)) {
				return status;
			}
		}
		throw new IllegalArgumentException("Unknown treeStatus: " + value);
	}
}
