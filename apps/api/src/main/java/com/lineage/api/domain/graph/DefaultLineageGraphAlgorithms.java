package com.lineage.api.domain.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Authorization-first BFS, Java out-edge truncation, DAG main-parent
 * classification, and shortest path on the usable query graph.
 */
public final class DefaultLineageGraphAlgorithms implements LineageGraphAlgorithms {

	static final int MAX_PATH_OBJECTS = 256;

	private static final Comparator<GraphRelation> BY_ID = new Comparator<GraphRelation>() {
		@Override
		public int compare(GraphRelation left, GraphRelation right) {
			return left.getId().compareTo(right.getId());
		}
	};

	private static final Comparator<GraphRelation> BY_SOURCE_ORDER_THEN_ID = new Comparator<GraphRelation>() {
		@Override
		public int compare(GraphRelation left, GraphRelation right) {
			int order = Integer.compare(left.getSourceOrder(), right.getSourceOrder());
			if (order != 0) {
				return order;
			}
			return left.getId().compareTo(right.getId());
		}
	};

	@Override
	public ClassificationResult classify(LineageGraph graph, String seedId,
			Set<String> authorizedObjectIds) {
		if (graph == null || seedId == null) {
			return ClassificationResult.empty();
		}
		Set<String> authorized = resolveAuthorized(graph, authorizedObjectIds);
		GraphObject seed = graph.object(seedId);
		if (seed == null || !authorized.contains(seedId)) {
			return ClassificationResult.empty();
		}

		UsableEdges usable = buildUsable(graph, authorized);
		Reach reach = bfsReach(seedId, usable);

		for (GraphRelation relation : usable.edges) {
			if (!reach.ids.contains(relation.getSource()) || !reach.ids.contains(relation.getTarget())) {
				usable.excluded.add(relation.getId());
			}
		}

		if (hasDirectedCycle(reach.ids, usable)) {
			return new ClassificationResult(
				reach.ids,
				reach.minHops,
				Collections.<String, Integer>emptyMap(),
				Collections.<String, String>emptyMap(),
				Collections.<String>emptySet(),
				Collections.<String>emptySet(),
				usable.excluded,
				TreeStatus.UNAVAILABLE_CYCLE);
		}

		return classifyDag(seedId, reach, usable);
	}

	@Override
	public PathResult shortestPath(LineageGraph graph, String fromId, String toId,
			Set<String> authorizedObjectIds) {
		if (graph == null || fromId == null || toId == null) {
			return notFound();
		}
		Set<String> authorized = resolveAuthorized(graph, authorizedObjectIds);
		if (graph.object(fromId) == null || graph.object(toId) == null
				|| !authorized.contains(fromId) || !authorized.contains(toId)) {
			return notFound();
		}
		if (fromId.equals(toId)) {
			List<String> nodes = new ArrayList<String>();
			nodes.add(fromId);
			return new PathResult(PathStatus.FOUND, nodes, Collections.<String>emptyList(), null);
		}

		UsableEdges usable = buildUsable(graph, authorized);
		ArrayDeque<String> queue = new ArrayDeque<String>();
		Set<String> visited = new LinkedHashSet<String>();
		Map<String, GraphRelation> via = new LinkedHashMap<String, GraphRelation>();
		visited.add(fromId);
		queue.add(fromId);

		while (!queue.isEmpty()) {
			String nodeId = queue.removeFirst();
			List<GraphRelation> outgoing = new ArrayList<GraphRelation>(usable.out(nodeId));
			Collections.sort(outgoing, BY_SOURCE_ORDER_THEN_ID);
			for (GraphRelation edge : outgoing) {
				String next = edge.getTarget();
				if (visited.contains(next)) {
					continue;
				}
				visited.add(next);
				via.put(next, edge);
				if (next.equals(toId)) {
					return reconstructPath(fromId, toId, via);
				}
				queue.addLast(next);
			}
		}
		return notFound();
	}

	private static PathResult reconstructPath(String fromId, String toId,
			Map<String, GraphRelation> via) {
		List<String> nodes = new ArrayList<String>();
		List<String> edges = new ArrayList<String>();
		String cursor = toId;
		while (!cursor.equals(fromId)) {
			nodes.add(cursor);
			GraphRelation edge = via.get(cursor);
			edges.add(edge.getId());
			cursor = edge.getSource();
		}
		nodes.add(fromId);
		Collections.reverse(nodes);
		Collections.reverse(edges);
		if (nodes.size() > MAX_PATH_OBJECTS) {
			return PathResult.unknown(PathReason.PATH_LENGTH_LIMIT);
		}
		return new PathResult(PathStatus.FOUND, nodes, edges, null);
	}

	private static ClassificationResult classifyDag(String seedId, Reach reach, UsableEdges usable) {
		Map<String, Integer> layoutRank = new LinkedHashMap<String, Integer>();
		Map<String, String> parentEdgeIds = new LinkedHashMap<String, String>();
		Set<String> treeIds = new LinkedHashSet<String>();

		layoutRank.put(seedId, Integer.valueOf(0));

		for (String nodeId : topologicalOrder(reach.ids, usable)) {
			if (seedId.equals(nodeId)) {
				continue;
			}
			GraphRelation parent = selectParent(nodeId, reach.ids, usable, layoutRank);
			if (parent == null) {
				continue;
			}
			int rank = layoutRank.get(parent.getSource()).intValue() + 1;
			layoutRank.put(nodeId, Integer.valueOf(rank));
			parentEdgeIds.put(nodeId, parent.getId());
			treeIds.add(parent.getId());
		}

		Set<String> crossIds = new LinkedHashSet<String>();
		for (GraphRelation relation : usable.edges) {
			if (reach.ids.contains(relation.getSource()) && reach.ids.contains(relation.getTarget())
					&& !treeIds.contains(relation.getId())) {
				crossIds.add(relation.getId());
			}
		}

		return new ClassificationResult(
			reach.ids,
			reach.minHops,
			layoutRank,
			parentEdgeIds,
			treeIds,
			crossIds,
			usable.excluded,
			TreeStatus.AVAILABLE);
	}

	private static GraphRelation selectParent(String nodeId, Set<String> reach, UsableEdges usable,
			Map<String, Integer> layoutRank) {
		GraphRelation best = null;
		for (GraphRelation candidate : usable.in(nodeId)) {
			if (!reach.contains(candidate.getSource())) {
				continue;
			}
			if (!layoutRank.containsKey(candidate.getSource())) {
				continue;
			}
			if (best == null || compareParent(candidate, best, layoutRank) < 0) {
				best = candidate;
			}
		}
		return best;
	}

	/**
	 * Lower is better: parentRank+1 descending, strength descending, sourceOrder
	 * ascending, relationId ascending.
	 */
	private static int compareParent(GraphRelation left, GraphRelation right,
			Map<String, Integer> layoutRank) {
		int leftChildRank = layoutRank.get(left.getSource()).intValue() + 1;
		int rightChildRank = layoutRank.get(right.getSource()).intValue() + 1;
		int cmp = Integer.compare(rightChildRank, leftChildRank);
		if (cmp != 0) {
			return cmp;
		}
		cmp = Integer.compare(strength(right.getRel()), strength(left.getRel()));
		if (cmp != 0) {
			return cmp;
		}
		cmp = Integer.compare(left.getSourceOrder(), right.getSourceOrder());
		if (cmp != 0) {
			return cmp;
		}
		return left.getId().compareTo(right.getId());
	}

	private static int strength(String rel) {
		if ("writes".equals(rel)) {
			return 3;
		}
		if ("derives".equals(rel)) {
			return 2;
		}
		if ("calls".equals(rel)) {
			return 1;
		}
		if ("reads".equals(rel)) {
			return 0;
		}
		return -1;
	}

	private static List<String> topologicalOrder(Set<String> reach, UsableEdges usable) {
		Map<String, Integer> indegree = new LinkedHashMap<String, Integer>();
		for (String id : reach) {
			indegree.put(id, Integer.valueOf(0));
		}
		for (GraphRelation relation : usable.edges) {
			if (reach.contains(relation.getSource()) && reach.contains(relation.getTarget())) {
				indegree.put(relation.getTarget(),
					Integer.valueOf(indegree.get(relation.getTarget()).intValue() + 1));
			}
		}
		TreeSet<String> queue = new TreeSet<String>();
		for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
			if (entry.getValue().intValue() == 0) {
				queue.add(entry.getKey());
			}
		}
		List<String> order = new ArrayList<String>();
		while (!queue.isEmpty()) {
			String nodeId = queue.pollFirst();
			order.add(nodeId);
			for (GraphRelation relation : usable.out(nodeId)) {
				if (!reach.contains(relation.getTarget())) {
					continue;
				}
				int next = indegree.get(relation.getTarget()).intValue() - 1;
				indegree.put(relation.getTarget(), Integer.valueOf(next));
				if (next == 0) {
					queue.add(relation.getTarget());
				}
			}
		}
		return order;
	}

	private static boolean hasDirectedCycle(Set<String> reach, UsableEdges usable) {
		return topologicalOrder(reach, usable).size() != reach.size();
	}

	private static Reach bfsReach(String seedId, UsableEdges usable) {
		Set<String> ids = new LinkedHashSet<String>();
		Map<String, Integer> minHops = new LinkedHashMap<String, Integer>();
		ArrayDeque<String> queue = new ArrayDeque<String>();
		ids.add(seedId);
		minHops.put(seedId, Integer.valueOf(0));
		queue.add(seedId);
		while (!queue.isEmpty()) {
			String nodeId = queue.removeFirst();
			int hops = minHops.get(nodeId).intValue();
			for (GraphRelation edge : usable.out(nodeId)) {
				String next = edge.getTarget();
				if (ids.contains(next)) {
					continue;
				}
				ids.add(next);
				minHops.put(next, Integer.valueOf(hops + 1));
				queue.addLast(next);
			}
		}
		return new Reach(ids, minHops);
	}

	private static UsableEdges buildUsable(LineageGraph graph, Set<String> authorized) {
		List<GraphRelation> sorted = new ArrayList<GraphRelation>(graph.getRelations());
		Collections.sort(sorted, BY_ID);
		List<GraphRelation> usable = new ArrayList<GraphRelation>();
		Set<String> excluded = new LinkedHashSet<String>();
		for (GraphRelation relation : sorted) {
			GraphObject source = graph.object(relation.getSource());
			GraphObject target = graph.object(relation.getTarget());
			if (source == null || target == null) {
				continue;
			}
			if (!authorized.contains(relation.getSource()) || !authorized.contains(relation.getTarget())) {
				continue;
			}
			if (relation.isSelfLoop()) {
				excluded.add(relation.getId());
				continue;
			}
			if (source.isJava()) {
				excluded.add(relation.getId());
				continue;
			}
			usable.add(relation);
		}
		return new UsableEdges(usable, excluded);
	}

	private static Set<String> resolveAuthorized(LineageGraph graph, Set<String> authorizedObjectIds) {
		if (authorizedObjectIds == null) {
			Set<String> all = new LinkedHashSet<String>();
			for (GraphObject object : graph.getObjects()) {
				all.add(object.getId());
			}
			return all;
		}
		return authorizedObjectIds;
	}

	private static PathResult notFound() {
		return new PathResult(PathStatus.NOT_FOUND, Collections.<String>emptyList(),
			Collections.<String>emptyList(), null);
	}

	private static final class Reach {
		private final Set<String> ids;
		private final Map<String, Integer> minHops;

		private Reach(Set<String> ids, Map<String, Integer> minHops) {
			this.ids = ids;
			this.minHops = minHops;
		}
	}

	private static final class UsableEdges {
		private final List<GraphRelation> edges;
		private final Set<String> excluded;
		private final Map<String, List<GraphRelation>> outgoing;
		private final Map<String, List<GraphRelation>> incoming;

		private UsableEdges(List<GraphRelation> edges, Set<String> excluded) {
			this.edges = edges;
			this.excluded = excluded;
			this.outgoing = new LinkedHashMap<String, List<GraphRelation>>();
			this.incoming = new LinkedHashMap<String, List<GraphRelation>>();
			for (GraphRelation relation : edges) {
				List<GraphRelation> out = this.outgoing.get(relation.getSource());
				if (out == null) {
					out = new ArrayList<GraphRelation>();
					this.outgoing.put(relation.getSource(), out);
				}
				out.add(relation);
				List<GraphRelation> in = this.incoming.get(relation.getTarget());
				if (in == null) {
					in = new ArrayList<GraphRelation>();
					this.incoming.put(relation.getTarget(), in);
				}
				in.add(relation);
			}
		}

		private List<GraphRelation> out(String nodeId) {
			List<GraphRelation> edges = outgoing.get(nodeId);
			if (edges == null) {
				return Collections.emptyList();
			}
			return edges;
		}

		private List<GraphRelation> in(String nodeId) {
			List<GraphRelation> edges = incoming.get(nodeId);
			if (edges == null) {
				return Collections.emptyList();
			}
			return edges;
		}
	}
}
