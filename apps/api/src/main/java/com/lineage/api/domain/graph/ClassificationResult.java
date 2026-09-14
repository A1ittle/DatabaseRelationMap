package com.lineage.api.domain.graph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * BFS reach plus tree/cross partition for one seed. On a directed cycle,
 * {@link TreeStatus#UNAVAILABLE_CYCLE} and parent/tree/cross collections are
 * empty rather than force-rewired.
 */
public final class ClassificationResult {

	private final Set<String> reach;
	private final Map<String, Integer> minHops;
	private final Map<String, Integer> layoutRank;
	private final Map<String, String> parentEdgeIds;
	private final Set<String> treeIds;
	private final Set<String> crossIds;
	private final Set<String> excludedRelationIds;
	private final TreeStatus treeStatus;

	public ClassificationResult(Set<String> reach, Map<String, Integer> minHops,
			Map<String, Integer> layoutRank, Map<String, String> parentEdgeIds,
			Set<String> treeIds, Set<String> crossIds, Set<String> excludedRelationIds,
			TreeStatus treeStatus) {
		this.reach = copySet(reach);
		this.minHops = copyIntMap(minHops);
		this.layoutRank = copyIntMap(layoutRank);
		this.parentEdgeIds = copyStringMap(parentEdgeIds);
		this.treeIds = copySet(treeIds);
		this.crossIds = copySet(crossIds);
		this.excludedRelationIds = copySet(excludedRelationIds);
		this.treeStatus = Objects.requireNonNull(treeStatus, "treeStatus");
	}

	public static ClassificationResult empty() {
		return new ClassificationResult(
			Collections.<String>emptySet(),
			Collections.<String, Integer>emptyMap(),
			Collections.<String, Integer>emptyMap(),
			Collections.<String, String>emptyMap(),
			Collections.<String>emptySet(),
			Collections.<String>emptySet(),
			Collections.<String>emptySet(),
			TreeStatus.AVAILABLE);
	}

	public Set<String> getReach() {
		return reach;
	}

	public Map<String, Integer> getMinHops() {
		return minHops;
	}

	public Map<String, Integer> getLayoutRank() {
		return layoutRank;
	}

	public Map<String, String> getParentEdgeIds() {
		return parentEdgeIds;
	}

	public Set<String> getTreeIds() {
		return treeIds;
	}

	public Set<String> getCrossIds() {
		return crossIds;
	}

	public Set<String> getExcludedRelationIds() {
		return excludedRelationIds;
	}

	public TreeStatus getTreeStatus() {
		return treeStatus;
	}

	private static Set<String> copySet(Set<String> values) {
		return Collections.unmodifiableSet(new LinkedHashSet<String>(values));
	}

	private static Map<String, Integer> copyIntMap(Map<String, Integer> values) {
		return Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(values));
	}

	private static Map<String, String> copyStringMap(Map<String, String> values) {
		return Collections.unmodifiableMap(new LinkedHashMap<String, String>(values));
	}
}
