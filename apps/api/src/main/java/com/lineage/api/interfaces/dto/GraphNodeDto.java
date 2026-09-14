package com.lineage.api.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public final class GraphNodeDto {

	private final ObjectDto object;
	private final int minHops;
	private final Integer layoutRank;
	private final String parentEdgeId;
	private final Integer treeChildCount;
	private final Integer crossCount;

	public GraphNodeDto(ObjectDto object, int minHops, Integer layoutRank, String parentEdgeId,
			Integer treeChildCount, Integer crossCount) {
		this.object = object;
		this.minHops = minHops;
		this.layoutRank = layoutRank;
		this.parentEdgeId = parentEdgeId;
		this.treeChildCount = treeChildCount;
		this.crossCount = crossCount;
	}

	public ObjectDto getObject() {
		return object;
	}

	public int getMinHops() {
		return minHops;
	}

	public Integer getLayoutRank() {
		return layoutRank;
	}

	public String getParentEdgeId() {
		return parentEdgeId;
	}

	public Integer getTreeChildCount() {
		return treeChildCount;
	}

	public Integer getCrossCount() {
		return crossCount;
	}
}
