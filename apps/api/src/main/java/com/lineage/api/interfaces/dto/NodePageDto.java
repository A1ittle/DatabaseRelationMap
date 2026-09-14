package com.lineage.api.interfaces.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public final class NodePageDto {

	private final MetaDto meta;
	private final List<GraphNodeDto> items;
	private final PageInfo page;

	public NodePageDto(MetaDto meta, List<GraphNodeDto> items, PageInfo page) {
		this.meta = meta;
		this.items = items;
		this.page = page;
	}

	public MetaDto getMeta() {
		return meta;
	}

	public List<GraphNodeDto> getItems() {
		return items;
	}

	public PageInfo getPage() {
		return page;
	}
}
