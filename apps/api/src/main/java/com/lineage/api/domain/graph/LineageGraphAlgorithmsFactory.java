package com.lineage.api.domain.graph;

/**
 * Construction seam for tests and the application layer.
 */
public final class LineageGraphAlgorithmsFactory {

	private LineageGraphAlgorithmsFactory() {
	}

	public static LineageGraphAlgorithms create() {
		return new DefaultLineageGraphAlgorithms();
	}
}
