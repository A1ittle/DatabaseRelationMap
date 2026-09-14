package com.lineage.api.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.ALWAYS)
public final class PageInfo {

	private final String nextCursor;
	private final boolean hasMore;
	private final Integer total;

	public PageInfo(String nextCursor, boolean hasMore, Integer total) {
		this.nextCursor = nextCursor;
		this.hasMore = hasMore;
		this.total = total;
	}

	public String getNextCursor() {
		return nextCursor;
	}

	@JsonProperty("hasMore")
	public boolean isHasMore() {
		return hasMore;
	}

	public Integer getTotal() {
		return total;
	}
}
