package com.lineage.api.domain.graph;

/**
 * Construction seam for tests. Swap the stub for the real implementation in
 * the algorithm PR without editing assertion literals.
 */
public final class LineageGraphAlgorithmsFactory {

	private LineageGraphAlgorithmsFactory() {
	}

	public static LineageGraphAlgorithms create() {
		return new StubLineageGraphAlgorithms();
	}
}
