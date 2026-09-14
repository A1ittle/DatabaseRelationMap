package com.lineage.api.interfaces.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public final class RelationPageDto {

	private final MetaDto meta;
	private final List<GraphEdgeDto> items;
	private final List<ObjectDto> endpointObjects;
	private final PageInfo page;

	public RelationPageDto(MetaDto meta, List<GraphEdgeDto> items, List<ObjectDto> endpointObjects, PageInfo page) {
		this.meta = meta;
		this.items = items;
		this.endpointObjects = endpointObjects;
		this.page = page;
	}

	public MetaDto getMeta() {
		return meta;
	}

	public List<GraphEdgeDto> getItems() {
		return items;
	}

	public List<ObjectDto> getEndpointObjects() {
		return endpointObjects;
	}

	public PageInfo getPage() {
		return page;
	}
}
