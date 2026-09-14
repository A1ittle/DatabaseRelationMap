package com.lineage.api.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public final class ClusterDto {

	private final String id;
	private final Integer layoutRank;
	private final String type;
	private final int count;

	public ClusterDto(String id, Integer layoutRank, String type, int count) {
		this.id = id;
		this.layoutRank = layoutRank;
		this.type = type;
		this.count = count;
	}

	public String getId() {
		return id;
	}

	public Integer getLayoutRank() {
		return layoutRank;
	}

	public String getType() {
		return type;
	}

	public int getCount() {
		return count;
	}
}
