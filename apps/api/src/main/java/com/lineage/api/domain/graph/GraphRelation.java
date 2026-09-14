package com.lineage.api.domain.graph;

import java.util.Objects;

/**
 * Directed lineage relation. Parallel edges are distinct {@code id} values
 * even when source and target coincide.
 */
public final class GraphRelation {

	private final String id;
	private final String source;
	private final String target;
	private final String rel;
	private final int sourceOrder;

	public GraphRelation(String id, String source, String target, String rel, int sourceOrder) {
		this.id = Objects.requireNonNull(id, "id");
		this.source = Objects.requireNonNull(source, "source");
		this.target = Objects.requireNonNull(target, "target");
		this.rel = Objects.requireNonNull(rel, "rel");
		this.sourceOrder = sourceOrder;
	}

	public String getId() {
		return id;
	}

	public String getSource() {
		return source;
	}

	public String getTarget() {
		return target;
	}

	public String getRel() {
		return rel;
	}

	public int getSourceOrder() {
		return sourceOrder;
	}

	public boolean isSelfLoop() {
		return source.equals(target);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof GraphRelation)) {
			return false;
		}
		GraphRelation that = (GraphRelation) o;
		return id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public String toString() {
		return "GraphRelation{id=" + id + ", " + source + " -" + rel + "-> " + target + "}";
	}
}
