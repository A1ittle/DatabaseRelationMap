package com.lineage.api.domain.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Shortest downstream path on the authorized query graph (not parent-pointer
 * walk). Status {@link PathStatus#UNKNOWN} is reserved for incomplete queries
 * and the 256-object length cap — never a stand-in for "no path".
 */
public final class PathResult {

	private final PathStatus status;
	private final List<String> nodeIds;
	private final List<String> edgeIds;
	private final PathReason reason;

	public PathResult(PathStatus status, List<String> nodeIds, List<String> edgeIds,
			PathReason reason) {
		this.status = Objects.requireNonNull(status, "status");
		this.nodeIds = Collections.unmodifiableList(new ArrayList<String>(nodeIds));
		this.edgeIds = Collections.unmodifiableList(new ArrayList<String>(edgeIds));
		this.reason = reason;
	}

	public static PathResult unknown(PathReason reason) {
		return new PathResult(PathStatus.UNKNOWN, Collections.<String>emptyList(),
			Collections.<String>emptyList(), reason);
	}

	public PathStatus getStatus() {
		return status;
	}

	public List<String> getNodeIds() {
		return nodeIds;
	}

	public List<String> getEdgeIds() {
		return edgeIds;
	}

	public PathReason getReason() {
		return reason;
	}
}
