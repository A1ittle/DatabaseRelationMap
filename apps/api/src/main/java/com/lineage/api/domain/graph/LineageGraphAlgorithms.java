package com.lineage.api.domain.graph;

import java.util.Set;

/**
 * Pure-Java BFS / classify / path façade. No Spring, JDBC, or renderer.
 *
 * <p>{@code authorizedObjectIds} is applied <em>before</em> traversal: hidden
 * intermediates must not be walked, and their tails must not be spliced back.
 * {@code null} means every object in the graph is authorized (fixture hook).
 */
public interface LineageGraphAlgorithms {

	ClassificationResult classify(LineageGraph graph, String seedId, Set<String> authorizedObjectIds);

	PathResult shortestPath(LineageGraph graph, String fromId, String toId,
			Set<String> authorizedObjectIds);
}
