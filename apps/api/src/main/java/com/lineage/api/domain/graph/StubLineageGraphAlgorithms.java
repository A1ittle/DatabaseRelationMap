package com.lineage.api.domain.graph;

import java.util.Set;

/**
 * Intentional no-op so the P1 counterexample suite compiles and stays red
 * until the real adjacency-list BFS is implemented. Do not weaken tests to
 * match this stub.
 */
final class StubLineageGraphAlgorithms implements LineageGraphAlgorithms {

	@Override
	public ClassificationResult classify(LineageGraph graph, String seedId,
			Set<String> authorizedObjectIds) {
		return ClassificationResult.empty();
	}

	@Override
	public PathResult shortestPath(LineageGraph graph, String fromId, String toId,
			Set<String> authorizedObjectIds) {
		return PathResult.unknown(null);
	}
}
