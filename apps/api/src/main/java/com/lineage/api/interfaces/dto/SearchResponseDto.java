package com.lineage.api.interfaces.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public final class SearchResponseDto {

	private final String requestId;
	private final List<SearchHitDto> items;
	private final PageInfo page;

	public SearchResponseDto(String requestId, List<SearchHitDto> items, PageInfo page) {
		this.requestId = requestId;
		this.items = items;
		this.page = page;
	}

	public String getRequestId() {
		return requestId;
	}

	public List<SearchHitDto> getItems() {
		return items;
	}

	public PageInfo getPage() {
		return page;
	}
}
